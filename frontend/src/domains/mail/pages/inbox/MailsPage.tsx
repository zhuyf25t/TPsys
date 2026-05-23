import { useEffect, useState, useSyncExternalStore, type MouseEvent, type ReactNode } from "react";
import { Link } from "react-router-dom";
import { getCurrentAuthUser, isBuiltinAdminHandle, subscribeAuthState } from "../../../identity/api/authGateway";
import { CONTRIBUTION_ADJUSTMENTS_CHANGED_EVENT, loadContributionAdjustments } from "../../../governance/api/governanceGateway";
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
} from "../../api/mailsGateway";
import { loadRemoteFriendRequests, respondToFriendRequest, type FriendRequestDecision } from "../../../social/api/friendRequestGateway";
import { UserActionDot } from "../../../social/components/user-action-dot/UserActionDot";
import { ShellLayout } from "../../../../shared/ui/ShellLayout";
import { cn } from "../../../../shared/ui/classNames";

type FriendRequestActionState = FriendRequestMailStatus | "processing" | "failed";
type MailFilter = "all" | "unread" | "read" | "important" | "battle" | "friend";
interface MailReadSyncResult {
  ok: boolean;
  requiresRemoteRefresh: boolean;
}
type ContributionAdjustmentRecords = NonNullable<Awaited<ReturnType<typeof loadContributionAdjustments>>>;

const primaryButton =
  "inline-flex h-10 items-center justify-center rounded-md bg-emerald-600 px-4 text-sm font-semibold text-white shadow-sm transition hover:bg-emerald-700 disabled:cursor-not-allowed disabled:opacity-50";
const secondaryButton =
  "inline-flex h-10 items-center justify-center rounded-md border border-slate-300 bg-white px-4 text-sm font-semibold text-slate-700 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50";
const filterButtonBase = "inline-flex items-center rounded-full border px-3 py-1 text-xs font-semibold transition";

/** 中文名称：站内信页。游戏职责：展示邮件摘要、通知、已读状态和好友请求处理。 */
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

  const handleMailClick = async (mail: MailSummary): Promise<void> => {
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

  const handleMarkReadClick = (event: MouseEvent<HTMLButtonElement>, mail: MailSummary): void => {
    event.stopPropagation();
    void handleMailClick(mail);
  };

  const handleMarkFilteredReadClick = async (): Promise<void> => {
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

  const handleFriendRequestDecision = async (event: MouseEvent<HTMLButtonElement>, mail: MailSummary, decision: FriendRequestDecision): Promise<void> => {
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
        <div className="flex flex-wrap items-center gap-2 text-sm font-semibold text-slate-600" aria-label="站内信快捷操作">
          <span className="rounded-full bg-slate-100 px-3 py-1">15 秒同步</span>
          <Link className="hover:text-emerald-700" to="/replay">
            查看战报
          </Link>
          <Link className="hover:text-emerald-700" to="/rating">
            评分榜
          </Link>
        </div>
      }
    >
      <section className="mx-auto flex w-full max-w-6xl flex-col gap-5">
        <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
          <div className="flex flex-wrap items-start justify-between gap-4">
            <div>
              <h3 className="text-xl font-semibold text-slate-950">消息概览</h3>
              <p className="mt-2 text-sm leading-6 text-slate-600">战报、评分更新与好友消息会在这里同步；旧版战报和评分邮件会合并显示成一条。</p>
            </div>
            <button
              type="button"
              className={secondaryButton}
              disabled={visibleUnreadMails.length === 0}
              onClick={() => {
                void handleMarkFilteredReadClick();
              }}
            >
              {visibleUnreadMails.length > 0 ? `${markFilteredReadLabel} ${visibleUnreadMails.length}` : "当前筛选无未读"}
            </button>
          </div>

          <div className="mt-5 grid gap-3 sm:grid-cols-2 lg:grid-cols-5" aria-label="站内信统计">
            <OverviewTile label="总计" value={mailSummaries.length} detail="当前账号可见" />
            <OverviewTile label="未读" value={unreadCount} detail="需要处理" hot />
            <OverviewTile label="战报" value={battleCount} detail="含回放入口" />
            <OverviewTile label="好友" value={friendCount} detail="申请与回执" />
            <OverviewTile label="已合并" value={mergedBattleCount} detail="战报+评分" merged />
          </div>

          <div className="mt-5 flex flex-wrap gap-2" aria-label="邮件筛选">
            <FilterButton activeFilter={mailFilter} filter="all" onClick={setMailFilter}>
              总计 {mailSummaries.length}
            </FilterButton>
            <FilterButton activeFilter={mailFilter} filter="unread" onClick={setMailFilter}>
              未读 {unreadCount}
            </FilterButton>
            <FilterButton activeFilter={mailFilter} filter="read" onClick={setMailFilter}>
              已读 {readCount}
            </FilterButton>
            <FilterButton activeFilter={mailFilter} filter="important" onClick={setMailFilter}>
              重要 {importantCount}
            </FilterButton>
            <FilterButton activeFilter={mailFilter} filter="battle" onClick={setMailFilter}>
              战报 {battleCount}
            </FilterButton>
            <FilterButton activeFilter={mailFilter} filter="friend" onClick={setMailFilter}>
              好友申请 {friendCount}
            </FilterButton>
          </div>
        </section>

        {mailSummaries.length === 0 ? (
          <EmptyState title={emptyTitle} body={emptyDetail}>
            {!remoteMailSource && !mailLoadFailed ? (
              <Link className={primaryButton} to="/battle?new=1">
                进入战斗
              </Link>
            ) : null}
          </EmptyState>
        ) : filteredMailSummaries.length === 0 ? (
          <EmptyState title="没有符合筛选的邮件" body="切换上方筛选条件可查看其他站内信。" />
        ) : (
          <section className="flex flex-col gap-3">
            {filteredMailSummaries.map((mail) => {
              const friendRequestId = getFriendRequestId(mail);
              const actionStatus = friendRequestActions[mail.id];
              const friendRequestStatus = resolveFriendRequestStatus(mail.friendRequestStatus, actionStatus);
              const friendRequestStatusLabel = getFriendRequestStatusLabel(friendRequestStatus, actionStatus);
              const readFailure = mailReadFailures[mail.id];
              const canRespondToFriendRequest = mail.kind === "friend" && friendRequestId !== null && friendRequestStatus === "pending";
              const isProcessing = actionStatus === "processing";
              const isGovernancePendingMail = isAdminUser && mail.kind === "governance" && isPendingGovernanceSubject(mail.subject);
              const governanceActorHandle = mail.governanceActorHandle?.trim() || "";
              const governanceTargetPath = mail.governanceTargetPath?.trim() || "";
              const mailSourcePath = mail.sourcePath?.trim() || "";
              const mailSourceLabel = mail.sourceLabel?.trim() || "查看来源";
              const matchedContributionAdjustment = isAdminUser ? findMatchingContributionAdjustment(mail, contributionAdjustments) : null;
              const mergedBattleLabel = getMergedBattleMailLabel(mail);

              return (
                <article
                  key={mail.id}
                  role="button"
                  tabIndex={0}
                  className={cn(
                    "grid cursor-pointer gap-4 rounded-lg border bg-white p-4 shadow-sm transition hover:border-emerald-300 md:grid-cols-[minmax(0,1fr)_260px]",
                    mail.unread ? "border-emerald-300 ring-1 ring-emerald-100" : "border-slate-200"
                  )}
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
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className={getMailKindClass(mail)}>{getMailKindLabel(mail)}</span>
                      {mail.unread ? <span className="h-2 w-2 rounded-full bg-emerald-500" aria-label="未读邮件" /> : null}
                      <span className={cn("rounded-full px-2 py-1 text-xs font-semibold", mail.unread ? "bg-emerald-50 text-emerald-700" : "bg-slate-100 text-slate-500")}>
                        {mail.unread ? "未读" : "已读"}
                      </span>
                      {mail.important ? <span className="rounded-full bg-amber-50 px-2 py-1 text-xs font-semibold text-amber-700">重要</span> : null}
                      {mergedBattleLabel ? <span className="rounded-full bg-cyan-50 px-2 py-1 text-xs font-semibold text-cyan-700">{mergedBattleLabel}</span> : null}
                    </div>
                    <strong className="mt-3 block truncate text-lg font-semibold text-slate-950">{mail.subject}</strong>
                    <span className="mt-2 block text-sm leading-6 text-slate-600">{mail.excerpt}</span>
                  </div>
                  <div
                    className="flex flex-col items-start gap-3 md:items-end"
                    onClick={(event) => {
                      event.stopPropagation();
                    }}
                    onKeyDown={(event) => {
                      event.stopPropagation();
                    }}
                  >
                    <div className="text-left text-xs text-slate-500 md:text-right">
                      <small className="block">{mail.senderLabel}</small>
                      <small className="block">{mail.receivedLabel}</small>
                    </div>
                    {mail.unread ? (
                      <button type="button" className={secondaryButton} onClick={(event) => handleMarkReadClick(event, mail)}>
                        标为已读
                      </button>
                    ) : null}
                    {mailSourcePath && !isGovernancePendingMail ? <SourceLink path={mailSourcePath} label={mailSourceLabel} /> : null}
                    {isGovernancePendingMail ? (
                      <div className="flex flex-wrap items-center gap-2 md:justify-end">
                        {governanceTargetPath ? <SourceLink path={governanceTargetPath} label="打开来源" /> : null}
                        {governanceActorHandle ? <small className="text-xs font-semibold text-slate-500">处理 @{governanceActorHandle}</small> : null}
                        {matchedContributionAdjustment ? (
                          <small className="text-xs font-semibold text-emerald-700">已处理，已有裁决 {formatDelta(matchedContributionAdjustment.delta)}</small>
                        ) : null}
                        {governanceActorHandle ? <UserActionDot handle={governanceActorHandle} sourceLabel={mail.subject} sourcePath={governanceTargetPath} /> : null}
                      </div>
                    ) : null}
                    {canRespondToFriendRequest ? (
                      <span className="flex flex-wrap items-center gap-2 md:justify-end">
                        <button
                          type="button"
                          className={primaryButton}
                          disabled={isProcessing}
                          onClick={(event) => {
                            void handleFriendRequestDecision(event, mail, "accepted");
                          }}
                        >
                          同意
                        </button>
                        <button
                          type="button"
                          className={secondaryButton}
                          disabled={isProcessing}
                          onClick={(event) => {
                            void handleFriendRequestDecision(event, mail, "rejected");
                          }}
                        >
                          拒绝
                        </button>
                        {friendRequestStatusLabel ? <small className="text-xs font-semibold text-slate-500">{friendRequestStatusLabel}</small> : null}
                      </span>
                    ) : null}
                    {!canRespondToFriendRequest && friendRequestStatusLabel ? <small className="text-xs font-semibold text-slate-500">{friendRequestStatusLabel}</small> : null}
                    {readFailure ? <small className="text-xs font-semibold text-rose-700">{readFailure}</small> : null}
                  </div>
                </article>
              );
            })}
          </section>
        )}
      </section>
    </ShellLayout>
  );
}

function OverviewTile({ label, value, detail, hot, merged }: { label: string; value: number; detail: string; hot?: boolean; merged?: boolean }) {
  return (
    <article className={cn("rounded-lg border p-4", hot ? "border-emerald-200 bg-emerald-50" : merged ? "border-cyan-200 bg-cyan-50" : "border-slate-200 bg-slate-50")}>
      <span className="text-xs font-semibold text-slate-500">{label}</span>
      <strong className="mt-1 block text-2xl font-semibold text-slate-950">{value}</strong>
      <small className="mt-1 block text-xs text-slate-500">{detail}</small>
    </article>
  );
}

function FilterButton({ activeFilter, filter, onClick, children }: { activeFilter: MailFilter; filter: MailFilter; onClick: (filter: MailFilter) => void; children: ReactNode }) {
  return (
    <button
      type="button"
      className={cn(filterButtonBase, activeFilter === filter ? "border-emerald-500 bg-emerald-600 text-white" : "border-slate-300 bg-white text-slate-700 hover:bg-slate-50")}
      onClick={() => onClick(filter)}
    >
      {children}
    </button>
  );
}

function EmptyState({ title, body, children }: { title: string; body: string; children?: ReactNode }) {
  return (
    <section className="flex flex-col items-start rounded-lg border border-dashed border-slate-300 bg-white p-6 shadow-sm">
      <h3 className="text-xl font-semibold text-slate-950">{title}</h3>
      <p className="mt-2 text-sm leading-6 text-slate-600">{body}</p>
      {children ? <div className="mt-5">{children}</div> : null}
    </section>
  );
}

function SourceLink({ path, label }: { path: string; label: string }) {
  return path.startsWith("/") ? (
    <Link className={secondaryButton} to={path}>
      {label}
    </Link>
  ) : (
    <a className={secondaryButton} href={path}>
      {label}
    </a>
  );
}

function getMailReadTargetIds(mail: MailSummary): string[] {
  return uniqueStrings([mail.id, ...(mail.relatedMailIds ?? [])]);
}

function isMergedBattleMail(mail: MailSummary): boolean {
  return getMergedBattleMailSourceCount(mail) > 1;
}

function getMergedBattleMailLabel(mail: MailSummary): string | null {
  const mergedSourceCount = getMergedBattleMailSourceCount(mail);
  return mergedSourceCount > 1 ? `合并 ${mergedSourceCount} 条` : null;
}

function getMergedBattleMailSourceCount(mail: MailSummary): number {
  if (mail.kind !== "battle") {
    return 0;
  }

  return uniqueStrings(mail.relatedMailIds ?? []).length;
}

function getMailKindClass(mail: MailSummary): string {
  switch (mail.kind) {
    case "battle":
      return "rounded-full bg-emerald-50 px-2 py-1 text-xs font-semibold text-emerald-700";
    case "reward":
      return "rounded-full bg-amber-50 px-2 py-1 text-xs font-semibold text-amber-700";
    case "friend":
      return "rounded-full bg-cyan-50 px-2 py-1 text-xs font-semibold text-cyan-700";
    case "governance":
      return "rounded-full bg-purple-50 px-2 py-1 text-xs font-semibold text-purple-700";
    default:
      return "rounded-full bg-slate-100 px-2 py-1 text-xs font-semibold text-slate-600";
  }
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

function findMatchingContributionAdjustment(mail: MailSummary, records: ContributionAdjustmentRecords): ContributionAdjustmentRecords[number] | null {
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

function resolveFriendRequestStatus(mailStatus: FriendRequestMailStatus | undefined, actionStatus: FriendRequestActionState | undefined): FriendRequestMailStatus {
  if (actionStatus === "accepted" || actionStatus === "rejected") {
    return actionStatus;
  }

  return mailStatus ?? "pending";
}

function getFriendRequestStatusLabel(status: FriendRequestMailStatus, actionStatus: FriendRequestActionState | undefined): string {
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

function isPendingGovernanceSubject(subject: string): boolean {
  return subject.startsWith("[待处理]") || subject.startsWith("[寰呭");
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
    return isPendingGovernanceSubject(mail.subject) ? "待处理治理" : "治理通知";
  }

  return "系统";
}
