import { useEffect, useState, useSyncExternalStore, type MouseEvent } from "react";
import { Link } from "react-router-dom";
import { getCurrentAuthUser, isBuiltinAdminHandle, subscribeAuthState } from "../features/auth/authGateway";
import {
  CONTRIBUTION_ADJUSTMENTS_CHANGED_EVENT,
  loadContributionAdjustments
} from "../features/governance/governanceGateway";
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
} from "../features/mails/mailsGateway";
import {
  loadRemoteFriendRequests,
  respondToFriendRequest,
  type FriendRequestDecision
} from "../features/social/friendRequestGateway";
import { UserActionDot } from "../shared/ui/UserActionDot";
import { ShellLayout } from "../shared/ui/ShellLayout";

export function MailsPage() {
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
  const emptyDetail = mailLoadFailed
    ? "后端暂时不可用，当前无法同步站内信。"
    : "当前没有新的站内信。";

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
  const friendCount = mailSummaries.filter((mail) => mail.kind === "friend").length;
  const filteredMailSummaries = mailSummaries.filter((mail) => {
    switch (mailFilter) {
      case "unread":
        return mail.unread;
      case "read":
        return !mail.unread;
      case "important":
        return mail.important;
      case "friend":
        return mail.kind === "friend";
      case "all":
        return true;
    }
  });

  const setMailReadOptimistically = (mailIds: readonly string[]): void => {
    const targetIds = new Set(mailIds);
    setMailSummaries((current) =>
      current.map((mail) =>
        targetIds.has(mail.id) || mail.relatedMailIds?.some((relatedMailId) => targetIds.has(relatedMailId))
          ? { ...mail, unread: false }
          : mail
      )
    );
  };

  const clearMailReadFailure = (mailId: string): void => {
    setMailReadFailures((current) => {
      if (!current[mailId]) {
        return current;
      }

      const { [mailId]: _ignored, ...next } = current;
      return next;
    });
  };

  const setFriendRequestStatusOptimistically = (
    mailId: string,
    requestId: string,
    status: FriendRequestMailStatus
  ): void => {
    setMailSummaries((current) =>
      current.map((mail) =>
        mail.id === mailId
          ? {
              ...mail,
              unread: false,
              friendRequestId: requestId,
              friendRequestStatus: status
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

  const handleMailClick = async (mail: MailSummary): Promise<void> => {
    if (!mail.unread) {
      return;
    }

    const mailIds = getMailReadTargetIds(mail);
    const mailId = mail.id;
    const previousMailSummaries = mailSummaries;
    clearMailReadFailure(mail.id);
    setMailReadOptimistically(mailIds);

    const ownerHandle = currentUser?.handle?.trim() ?? "";
    if (mailIds.every((mailId) => mailId.startsWith("battle:"))) {
      const localRead = mailIds.map((mailId) => markMailAsRead(mailId, ownerHandle)).every(Boolean);
      if (!localRead) {
        setMailSummaries(previousMailSummaries);
        setMailReadFailures((current) => ({ ...current, [mailId]: "已读同步失败，请重试" }));
      }
      return;
    }

    if (remoteMailSource) {
      if (!ownerHandle) {
        setMailSummaries(previousMailSummaries);
        return;
      }

      const readSynced = (
        await Promise.all(mailIds.map((relatedMailId) => markMailAsReadRemote(ownerHandle, relatedMailId).catch(() => false)))
      ).every(Boolean);
      if (!readSynced) {
        setMailSummaries(previousMailSummaries);
        setMailReadFailures((current) => ({ ...current, [mailId]: "已读同步失败，请重试" }));
        setMailLoadFailed(true);
        return;
      }

      clearMailReadFailure(mailId);
      setMailLoadFailed(false);
      await refreshRemoteMails();
      return;
    }

    const localRead = mailIds.map((relatedMailId) => markMailAsRead(relatedMailId, ownerHandle)).every(Boolean);
    if (!localRead) {
      setMailSummaries(previousMailSummaries);
      setMailReadFailures((current) => ({ ...current, [mailId]: "已读同步失败，请重试" }));
    }
  };

  const handleMarkReadClick = (event: MouseEvent<HTMLButtonElement>, mail: MailSummary): void => {
    event.stopPropagation();
    void handleMailClick(mail);
  };

  const handleFriendRequestDecision = async (
    event: MouseEvent<HTMLButtonElement>,
    mail: MailSummary,
    decision: FriendRequestDecision
  ): Promise<void> => {
    event.stopPropagation();
    const requestId = getFriendRequestId(mail);
    const actorHandle = currentUser?.handle?.trim() ?? "";
    if (!requestId || !actorHandle) {
      return;
    }

    setFriendRequestActions((current) => ({ ...current, [mail.id]: "processing" }));
    const result = await respondToFriendRequest({
      requestId,
      actorHandle,
      decision,
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

  return (
    <ShellLayout
      title="站内信"
      subtitle="战报、评分变化、好友请求和系统通知都在这里。"
      headerAside={
        <div className="mails-header-actions" aria-label="站内信快捷操作">
          <span>15 秒同步</span>
          <Link to="/replay">查看战报</Link>
          <Link to="/rating">评分榜</Link>
        </div>
      }
    >
      <section className="detail-card mails-overview">
        <h3>消息概览</h3>
        <p className="mails-overview__copy">
          战报、评分更新与好友消息会在这里同步；旧版战报和评分邮件会合并显示成一条。
        </p>
        <div className="pill-row mail-filter-row" aria-label="邮件筛选">
          <button type="button" className={getMailFilterChipClass(mailFilter, "all")} onClick={() => setMailFilter("all")}>
            总计 {mailSummaries.length}
          </button>
          <button type="button" className={getMailFilterChipClass(mailFilter, "unread")} onClick={() => setMailFilter("unread")}>
            未读 {unreadCount}
          </button>
          <button type="button" className={getMailFilterChipClass(mailFilter, "read")} onClick={() => setMailFilter("read")}>
            已读 {readCount}
          </button>
          <button type="button" className={getMailFilterChipClass(mailFilter, "important")} onClick={() => setMailFilter("important")}>
            重要 {importantCount}
          </button>
          <button type="button" className={getMailFilterChipClass(mailFilter, "friend")} onClick={() => setMailFilter("friend")}>
            好友申请 {friendCount}
          </button>
        </div>
      </section>

      {mailSummaries.length === 0 ? (
        <section className="detail-card empty-state">
          <h3>{emptyTitle}</h3>
          <p>{emptyDetail}</p>
          {!remoteMailSource && !mailLoadFailed ? (
            <div className="cta-row">
              <Link className="button-link button-link--primary" to="/battle?new=1">
                进入战斗
              </Link>
            </div>
          ) : null}
        </section>
      ) : filteredMailSummaries.length === 0 ? (
        <section className="detail-card empty-state empty-state--dense">
          <h3>没有符合筛选的邮件</h3>
          <p>切换上方筛选条件可查看其他站内信。</p>
        </section>
      ) : (
        <section className="mail-list">
          {filteredMailSummaries.map((mail) => {
            const friendRequestId = getFriendRequestId(mail);
            const actionStatus = friendRequestActions[mail.id];
            const friendRequestStatus = resolveFriendRequestStatus(mail.friendRequestStatus, actionStatus);
            const friendRequestStatusLabel = getFriendRequestStatusLabel(friendRequestStatus, actionStatus);
            const readFailure = mailReadFailures[mail.id];
            const canRespondToFriendRequest =
              mail.kind === "friend" && friendRequestId !== null && friendRequestStatus === "pending";
            const isProcessing = actionStatus === "processing";
            const isGovernancePendingMail = isAdminUser && mail.kind === "governance" && mail.subject.startsWith("[待处理]");
            const governanceActorHandle = mail.governanceActorHandle?.trim() || "";
            const governanceTargetPath = mail.governanceTargetPath?.trim() || "";
            const mailSourcePath = mail.sourcePath?.trim() || "";
            const mailSourceLabel = mail.sourceLabel?.trim() || "查看来源";
            const matchedContributionAdjustment = isAdminUser
              ? findMatchingContributionAdjustment(mail, contributionAdjustments)
              : null;

            return (
              <article
                key={mail.id}
                role="button"
                tabIndex={0}
                className={`mail-card${mail.unread ? " mail-card--unread" : " mail-card--read"}`}
                onClick={() => {
                  void handleMailClick(mail);
                }}
                onKeyDown={(event) => {
                  if (event.key === "Enter" || event.key === " ") {
                    event.preventDefault();
                    void handleMailClick(mail);
                  }
                }}
                aria-label={`${mail.subject}${mail.unread ? "，未读" : "，已读"}`}
              >
                <div className="mail-card__main">
                  <div className="mail-card__meta">
                    <span className={`mail-card__flag mail-card__flag--${mail.kind}`}>{getMailKindLabel(mail)}</span>
                    {mail.unread ? <span className="mail-card__dot" aria-label="未读邮件" /> : null}
                    {mail.unread ? <span className="mail-card__status mail-card__status--unread">未读</span> : null}
                    {!mail.unread ? <span className="mail-card__status mail-card__status--read">已读</span> : null}
                    {mail.important ? <span className="mail-card__status">重要</span> : null}
                  </div>
                  <strong>{mail.subject}</strong>
                  <span className="mail-card__excerpt">{mail.excerpt}</span>
                </div>
                <div
                  className="mail-card__side"
                  onClick={(event) => {
                    event.stopPropagation();
                  }}
                  onKeyDown={(event) => {
                    event.stopPropagation();
                  }}
                >
                  <div className="mail-card__source">
                    <small>{mail.senderLabel}</small>
                    <small>{mail.receivedLabel}</small>
                  </div>
                  {mail.unread ? (
                    <button
                      type="button"
                      className="mail-card__action mail-card__action--mark-read"
                      onClick={(event) => handleMarkReadClick(event, mail)}
                    >
                      标为已读
                    </button>
                  ) : null}
                  {mailSourcePath && !isGovernancePendingMail ? (
                    <div className="mail-card__actions">
                      {mailSourcePath.startsWith("/") ? (
                        <Link className="mail-card__action mail-card__action--accept" to={mailSourcePath}>
                          {mailSourceLabel}
                        </Link>
                      ) : (
                        <a className="mail-card__action mail-card__action--accept" href={mailSourcePath}>
                          {mailSourceLabel}
                        </a>
                      )}
                    </div>
                  ) : null}
                  {isGovernancePendingMail ? (
                    <div className="mail-card__actions">
                      {governanceTargetPath ? (
                        governanceTargetPath.startsWith("/") ? (
                          <Link className="mail-card__action mail-card__action--accept" to={governanceTargetPath}>
                            打开来源
                          </Link>
                        ) : (
                          <a className="mail-card__action mail-card__action--accept" href={governanceTargetPath}>
                            打开来源
                          </a>
                        )
                      ) : null}
                      {governanceActorHandle ? <small className="mail-card__action-status">处理 @{governanceActorHandle}</small> : null}
                      {matchedContributionAdjustment ? (
                        <small className="mail-card__action-status">
                          已处理，已有裁决 {formatDelta(matchedContributionAdjustment.delta)}
                        </small>
                      ) : null}
                      {governanceActorHandle ? (
                        <UserActionDot
                          handle={governanceActorHandle}
                          sourceLabel={mail.subject}
                          sourcePath={governanceTargetPath}
                        />
                      ) : null}
                    </div>
                  ) : null}
                  {canRespondToFriendRequest ? (
                    <span className="mail-card__actions">
                      <button
                        type="button"
                        className="mail-card__action mail-card__action--accept"
                        disabled={isProcessing}
                        onClick={(event) => {
                          void handleFriendRequestDecision(event, mail, "accepted");
                        }}
                      >
                        同意
                      </button>
                      <button
                        type="button"
                        className="mail-card__action"
                        disabled={isProcessing}
                        onClick={(event) => {
                          void handleFriendRequestDecision(event, mail, "rejected");
                        }}
                      >
                        拒绝
                      </button>
                      {friendRequestStatusLabel ? (
                        <small className="mail-card__action-status">{friendRequestStatusLabel}</small>
                      ) : null}
                    </span>
                  ) : null}
                  {!canRespondToFriendRequest && friendRequestStatusLabel ? (
                    <small className="mail-card__action-status">{friendRequestStatusLabel}</small>
                  ) : null}
                  {readFailure ? <small className="mail-card__action-status mail-card__action-status--failed">{readFailure}</small> : null}
                </div>
              </article>
            );
          })}
        </section>
      )}
    </ShellLayout>
  );
}

type FriendRequestActionState = FriendRequestMailStatus | "processing" | "failed";
type MailFilter = "all" | "unread" | "read" | "important" | "friend";
type ContributionAdjustmentRecords = NonNullable<Awaited<ReturnType<typeof loadContributionAdjustments>>>;

function getMailReadTargetIds(mail: MailSummary): string[] {
  return uniqueStrings([mail.id, ...(mail.relatedMailIds ?? [])]);
}

function getMailFilterChipClass(activeFilter: MailFilter, filter: MailFilter): string {
  return `pill mail-filter-chip${activeFilter === filter ? " mail-filter-chip--active" : ""}`;
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

function findMatchingContributionAdjustment(
  mail: MailSummary,
  records: ContributionAdjustmentRecords
): ContributionAdjustmentRecords[number] | null {
  const normalizedActorHandle = normalizeHandle(mail.governanceActorHandle ?? "");
  if (!normalizedActorHandle) {
    return null;
  }

  const normalizedTargetPath = (mail.governanceTargetPath ?? "").trim();
  const normalizedMailSubject = mail.subject.trim();

  for (const record of records) {
    if (normalizeHandle(record.targetHandle) !== normalizedActorHandle) {
      continue;
    }

    const recordSourcePath = (record.sourcePath ?? "").trim();
    const recordSourceLabel = (record.sourceLabel ?? "").trim();

    if (normalizedTargetPath) {
      if (recordSourcePath === normalizedTargetPath) {
        return record;
      }
      continue;
    }

    if (recordSourceLabel === normalizedMailSubject) {
      return record;
    }
  }

  return null;
}

function formatDelta(delta: number): string {
  return delta > 0 ? `+${delta}` : `${delta}`;
}

function normalizeHandle(handle: string): string {
  return handle.trim().toLowerCase();
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

function resolveFriendRequestStatus(
  mailStatus: FriendRequestMailStatus | undefined,
  actionStatus: FriendRequestActionState | undefined
): FriendRequestMailStatus {
  if (actionStatus === "accepted" || actionStatus === "rejected") {
    return actionStatus;
  }

  return mailStatus ?? "pending";
}

function getFriendRequestStatusLabel(
  status: FriendRequestMailStatus,
  actionStatus: FriendRequestActionState | undefined
): string {
  if (actionStatus === "processing") {
    return "处理中...";
  }

  if (actionStatus === "failed") {
    return "处理失败，请重试";
  }

  if (status === "accepted") {
    return "已同意";
  }

  if (status === "rejected") {
    return "已拒绝";
  }

  return "";
}

function resolveResultStatus(status: string | undefined, fallback: FriendRequestDecision): FriendRequestMailStatus {
  return status === "accepted" || status === "rejected" ? status : fallback;
}

function getMailKindLabel(mail: MailSummary): string {
  if (mail.kind === "battle") {
    return "战报";
  }

  if (mail.kind === "reward") {
    return "奖励";
  }

  if (mail.kind === "friend") {
    return "好友请求";
  }

  if (mail.kind === "governance") {
    return mail.subject.startsWith("[待处理]") ? "待处理治理" : "治理通知";
  }

  return "系统";
}
