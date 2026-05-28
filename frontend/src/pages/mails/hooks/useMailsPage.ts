import { useEffect, useState, useSyncExternalStore } from "react";
import { CONTRIBUTION_ADJUSTMENTS_CHANGED_EVENT, loadContributionAdjustments } from "../../../apis/governance/governanceGateway";
import { getCurrentAuthUser, isBuiltinAdminHandle, subscribeAuthState } from "../../../apis/identity/authGateway";
import {
  getMailSummaries,
  isRemoteMailSourceConfigured,
  loadMergedMailSummaries,
  MAIL_SUMMARIES_CHANGED_EVENT,
  markMailAsRead,
  markMailAsReadRemote,
  REMOTE_MAIL_REFRESH_INTERVAL_MS,
  type FriendRequestMailStatus,
  type MailSummary
} from "../../../apis/mail/mailsGateway";
import { loadRemoteFriendRequests, respondToFriendRequest, type FriendRequestDecision } from "../../../apis/social/friendRequestGateway";

export type { FriendRequestDecision } from "../../../apis/social/friendRequestGateway";
export type { FriendRequestMailStatus, MailSummary } from "../../../apis/mail/mailsGateway";

export type FriendRequestActionState = FriendRequestMailStatus | "processing" | "failed";
export type MailFilter = "all" | "unread" | "read" | "important" | "battle" | "friend";
export type ContributionAdjustmentRecords = NonNullable<Awaited<ReturnType<typeof loadContributionAdjustments>>>;

interface MailReadSyncResult {
  ok: boolean;
  requiresRemoteRefresh: boolean;
}

export interface MailsPageState {
  battleCount: number;
  contributionAdjustments: ContributionAdjustmentRecords;
  decideFriendRequest: (mail: MailSummary, decision: FriendRequestDecision) => Promise<void>;
  emptyDetail: string;
  emptyTitle: string;
  filteredMailSummaries: MailSummary[];
  friendCount: number;
  friendRequestActions: Record<string, FriendRequestActionState>;
  importantCount: number;
  isAdminUser: boolean;
  mailFilter: MailFilter;
  mailLoadFailed: boolean;
  mailReadFailures: Record<string, string>;
  mailSummaries: MailSummary[];
  markFilteredRead: () => Promise<void>;
  markFilteredReadLabel: string;
  markMailRead: (mail: MailSummary) => Promise<void>;
  mergedBattleCount: number;
  readCount: number;
  remoteMailSource: boolean;
  setMailFilter: (filter: MailFilter) => void;
  unreadCount: number;
  visibleUnreadMails: MailSummary[];
}

/** 中文名称：站内信页Hook。游戏职责：封装邮件同步、已读状态、治理记录和好友请求副作用。 */
export function useMailsPage(): MailsPageState {
  const currentUser = useSyncExternalStore(subscribeAuthState, getCurrentAuthUser, getCurrentAuthUser);
  const remoteMailSource = isRemoteMailSourceConfigured();
  const [mailSummaries, setMailSummaries] = useState<MailSummary[]>(() => getMailSummaries(currentUser?.handle));
  const [contributionAdjustments, setContributionAdjustments] = useState<ContributionAdjustmentRecords>([]);
  const [mailLoadFailed, setMailLoadFailed] = useState(false);
  const [mailReadFailures, setMailReadFailures] = useState<Record<string, string>>({});
  const [friendRequestActions, setFriendRequestActions] = useState<Record<string, FriendRequestActionState>>({});
  const [mailFilter, setMailFilter] = useState<MailFilter>("all");
  const isAdminUser = isBuiltinAdminHandle(currentUser?.handle);

  const emptyTitle = mailLoadFailed ? "站内信暂时不可用" : "暂无通知";
  const emptyDetail = mailLoadFailed ? "后端暂时不可用，当前无法同步站内信。" : "当前没有新的站内信。";

  useEffect(() => {
    let cancelled = false;
    let loading = false;

    const ownerHandle = currentUser?.handle?.trim();
    setMailSummaries(ownerHandle ? getMailSummaries(ownerHandle) : []);
    setMailLoadFailed(false);
    setMailReadFailures({});
    setFriendRequestActions({});

    if (!remoteMailSource || !ownerHandle) {
      return () => {
        cancelled = true;
      };
    }

    const refresh = (): void => {
      if (loading) {
        return;
      }

      loading = true;
      void loadMergedMailSummaries(ownerHandle)
        .then((remoteSummaries) => {
          if (cancelled) {
            return;
          }

          if (remoteSummaries == null) {
            setMailSummaries(getMailSummaries(ownerHandle));
            setMailLoadFailed(true);
            return;
          }

          setMailSummaries(remoteSummaries);
          setMailReadFailures({});
          setMailLoadFailed(false);
        })
        .finally(() => {
          loading = false;
        });
    };

    const handleFocus = (): void => {
      refresh();
    };
    const handleVisibilityChange = (): void => {
      if (document.visibilityState === "visible") {
        refresh();
      }
    };
    const handleMailSummariesChanged = (): void => {
      refresh();
    };

    refresh();
    const intervalId = window.setInterval(refresh, REMOTE_MAIL_REFRESH_INTERVAL_MS);
    window.addEventListener("focus", handleFocus);
    window.addEventListener(MAIL_SUMMARIES_CHANGED_EVENT, handleMailSummariesChanged);
    document.addEventListener("visibilitychange", handleVisibilityChange);

    return () => {
      cancelled = true;
      window.clearInterval(intervalId);
      window.removeEventListener("focus", handleFocus);
      window.removeEventListener(MAIL_SUMMARIES_CHANGED_EVENT, handleMailSummariesChanged);
      document.removeEventListener("visibilitychange", handleVisibilityChange);
    };
  }, [currentUser?.handle, remoteMailSource]);

  useEffect(() => {
    let cancelled = false;
    const ownerHandle = currentUser?.handle?.trim();

    if (!isAdminUser || !ownerHandle) {
      setContributionAdjustments([]);
      return () => {
        cancelled = true;
      };
    }

    const refreshContributionAdjustments = (): void => {
      void loadContributionAdjustments().then((records) => {
        if (!cancelled) {
          setContributionAdjustments(records ?? []);
        }
      });
    };

    refreshContributionAdjustments();
    window.addEventListener(CONTRIBUTION_ADJUSTMENTS_CHANGED_EVENT, refreshContributionAdjustments);

    return () => {
      cancelled = true;
      window.removeEventListener(CONTRIBUTION_ADJUSTMENTS_CHANGED_EVENT, refreshContributionAdjustments);
    };
  }, [currentUser?.handle, isAdminUser]);

  const unreadCount = mailSummaries.filter((mail) => mail.unread).length;
  const readCount = mailSummaries.length - unreadCount;
  const importantCount = mailSummaries.filter((mail) => mail.important).length;
  const battleCount = mailSummaries.filter((mail) => mail.kind === "battle").length;
  const friendCount = mailSummaries.filter((mail) => mail.kind === "friend").length;
  const mergedBattleCount = mailSummaries.filter(isMergedBattleMail).length;
  const filteredMailSummaries = mailSummaries.filter((mail) => {
    switch (mailFilter) {
      case "unread":
        return mail.unread;
      case "read":
        return !mail.unread;
      case "important":
        return mail.important;
      case "battle":
        return mail.kind === "battle";
      case "friend":
        return mail.kind === "friend";
      case "all":
        return true;
    }
  });
  const visibleUnreadMails = filteredMailSummaries.filter((mail) => mail.unread);
  const markFilteredReadLabel = mailFilter === "all" ? "全部标为已读" : "当前筛选标为已读";

  const setMailReadOptimistically = (mailIds: readonly string[]): void => {
    const targetIds = new Set(mailIds);
    setMailSummaries((current) =>
      current.map((mail) =>
        targetIds.has(mail.id) || mail.relatedMailIds?.some((relatedMailId) => targetIds.has(relatedMailId)) ? { ...mail, unread: false } : mail
      )
    );
  };

  const restoreMailReadState = (previousMailSummaries: readonly MailSummary[], failedMails: readonly MailSummary[]): void => {
    const previousById = new Map(previousMailSummaries.map((mail) => [mail.id, mail]));
    const failedTargetIds = new Set(failedMails.flatMap(getMailReadTargetIds));
    setMailSummaries((current) =>
      current.map((mail) =>
        failedTargetIds.has(mail.id) || mail.relatedMailIds?.some((relatedMailId) => failedTargetIds.has(relatedMailId)) ? previousById.get(mail.id) ?? mail : mail
      )
    );
  };

  const clearMailReadFailures = (mailIds: readonly string[]): void => {
    const targetIds = new Set(mailIds);
    setMailReadFailures((current) => {
      let changed = false;
      const next = { ...current };

      targetIds.forEach((mailId) => {
        if (next[mailId]) {
          delete next[mailId];
          changed = true;
        }
      });

      return changed ? next : current;
    });
  };

  const setFriendRequestStatusOptimistically = (mailId: string, requestId: string, status: FriendRequestMailStatus): void => {
    setMailSummaries((current) =>
      current.map((mail) =>
        mail.id === mailId
          ? {
              ...mail,
              friendRequestId: requestId,
              friendRequestStatus: status,
              unread: false
            }
          : mail
      )
    );
  };

  const refreshRemoteMails = async (): Promise<void> => {
    const ownerHandle = currentUser?.handle?.trim() ?? "";
    if (!remoteMailSource || !ownerHandle) {
      setMailSummaries(ownerHandle ? getMailSummaries(ownerHandle) : []);
      setMailLoadFailed(false);
      return;
    }

    const remoteSummaries = await loadMergedMailSummaries(ownerHandle);
    if (remoteSummaries == null) {
      setMailSummaries(getMailSummaries(ownerHandle));
      setMailLoadFailed(true);
      return;
    }

    setMailSummaries(remoteSummaries);
    setMailReadFailures({});
    setMailLoadFailed(false);
  };

  const syncMailReadState = async (mail: MailSummary, ownerHandle: string): Promise<MailReadSyncResult> => {
    const mailIds = getMailReadTargetIds(mail);
    if (mailIds.every((mailId) => mailId.startsWith("battle:"))) {
      return {
        ok: mailIds.map((mailId) => markMailAsRead(mailId, ownerHandle)).every(Boolean),
        requiresRemoteRefresh: false
      };
    }

    if (remoteMailSource) {
      if (!ownerHandle) {
        return { ok: false, requiresRemoteRefresh: true };
      }

      const readSynced = (await Promise.all(mailIds.map((relatedMailId) => markMailAsReadRemote(ownerHandle, relatedMailId).catch(() => false)))).every(Boolean);
      return { ok: readSynced, requiresRemoteRefresh: true };
    }

    return {
      ok: mailIds.map((relatedMailId) => markMailAsRead(relatedMailId, ownerHandle)).every(Boolean),
      requiresRemoteRefresh: false
    };
  };

  const markMailRead = async (mail: MailSummary): Promise<void> => {
    if (!mail.unread) {
      return;
    }

    const mailIds = getMailReadTargetIds(mail);
    const mailId = mail.id;
    const previousMailSummaries = mailSummaries;
    clearMailReadFailures([mail.id]);
    setMailReadOptimistically(mailIds);

    const ownerHandle = currentUser?.handle?.trim() ?? "";
    const result = await syncMailReadState(mail, ownerHandle);
    if (!result.ok) {
      restoreMailReadState(previousMailSummaries, [mail]);
      setMailReadFailures((current) => ({ ...current, [mailId]: "已读同步失败，请重试" }));
      if (result.requiresRemoteRefresh) {
        setMailLoadFailed(true);
      }
      return;
    }

    clearMailReadFailures([mailId]);
    if (result.requiresRemoteRefresh) {
      setMailLoadFailed(false);
      await refreshRemoteMails();
    }
  };

  const markFilteredRead = async (): Promise<void> => {
    if (visibleUnreadMails.length === 0) {
      return;
    }

    const ownerHandle = currentUser?.handle?.trim() ?? "";
    const previousMailSummaries = mailSummaries;
    const mailIds = uniqueStrings(visibleUnreadMails.flatMap(getMailReadTargetIds));
    clearMailReadFailures(visibleUnreadMails.map((mail) => mail.id));
    setMailReadOptimistically(mailIds);

    const results = await Promise.all(
      visibleUnreadMails.map(async (mail) => ({
        mail,
        result: await syncMailReadState(mail, ownerHandle)
      }))
    );
    const failedResults = results.filter(({ result }) => !result.ok);
    if (failedResults.length > 0) {
      const failedMails = failedResults.map(({ mail }) => mail);
      restoreMailReadState(previousMailSummaries, failedMails);
      setMailReadFailures((current) => {
        const next = { ...current };
        failedMails.forEach((mail) => {
          next[mail.id] = "已读同步失败，请重试";
        });
        return next;
      });
      if (failedResults.some(({ result }) => result.requiresRemoteRefresh)) {
        setMailLoadFailed(true);
      }
      return;
    }

    clearMailReadFailures(visibleUnreadMails.map((mail) => mail.id));
    if (results.some(({ result }) => result.requiresRemoteRefresh)) {
      setMailLoadFailed(false);
      await refreshRemoteMails();
    }
  };

  const decideFriendRequest = async (mail: MailSummary, decision: FriendRequestDecision): Promise<void> => {
    const requestId = getFriendRequestId(mail);
    const actorHandle = currentUser?.handle?.trim() ?? "";
    if (!requestId || !actorHandle) {
      return;
    }

    setFriendRequestActions((current) => ({ ...current, [mail.id]: "processing" }));
    const result = await respondToFriendRequest({
      actorHandle,
      decision,
      requestId,
      sourceHandle: mail.friendRequestSourceHandle
    });
    if (!result.ok) {
      setFriendRequestActions((current) => ({ ...current, [mail.id]: "failed" }));
      return;
    }

    const nextStatus = resolveResultStatus(result.request?.status, decision);
    setFriendRequestStatusOptimistically(mail.id, requestId, nextStatus);
    setMailReadOptimistically([mail.id]);
    const readSynced = await markMailAsReadRemote(actorHandle, mail.id).catch(() => false);
    if (!readSynced) {
      setMailReadFailures((current) => ({ ...current, [mail.id]: "好友申请状态已更新，邮件已读刷新失败" }));
    }
    void loadRemoteFriendRequests(actorHandle);
    await refreshRemoteMails();
    setFriendRequestActions((current) => ({
      ...current,
      [mail.id]: nextStatus
    }));
  };

  return {
    battleCount,
    contributionAdjustments,
    decideFriendRequest,
    emptyDetail,
    emptyTitle,
    filteredMailSummaries,
    friendCount,
    friendRequestActions,
    importantCount,
    isAdminUser,
    mailFilter,
    mailLoadFailed,
    mailReadFailures,
    mailSummaries,
    markFilteredRead,
    markFilteredReadLabel,
    markMailRead,
    mergedBattleCount,
    readCount,
    remoteMailSource,
    setMailFilter,
    unreadCount,
    visibleUnreadMails
  };
}

function getMailReadTargetIds(mail: MailSummary): string[] {
  return uniqueStrings([mail.id, ...(mail.relatedMailIds ?? [])]);
}

function isMergedBattleMail(mail: MailSummary): boolean {
  return getMergedBattleMailSourceCount(mail) > 1;
}

function getMergedBattleMailSourceCount(mail: MailSummary): number {
  if (mail.kind !== "battle") {
    return 0;
  }

  return uniqueStrings(mail.relatedMailIds ?? []).length;
}

function uniqueStrings(values: string[]): string[] {
  const seen = new Set<string>();
  const result: string[] = [];

  values.forEach((value) => {
    const normalized = value.trim();
    if (!normalized || seen.has(normalized)) {
      return;
    }

    seen.add(normalized);
    result.push(normalized);
  });

  return result;
}

function getFriendRequestId(mail: MailSummary): string | null {
  return mail.friendRequestId?.trim() || getFriendRequestIdFromMail(mail.id);
}

function getFriendRequestIdFromMail(mailId: string): string | null {
  const prefix = "mail-friend-";
  if (!mailId.startsWith(prefix) || mailId.startsWith("mail-friend-response-")) {
    return null;
  }

  const requestId = mailId.slice(prefix.length).trim();
  return requestId ? requestId : null;
}

function resolveResultStatus(status: string | undefined, fallback: FriendRequestDecision): FriendRequestMailStatus {
  return status === "accepted" || status === "rejected" ? status : fallback;
}
