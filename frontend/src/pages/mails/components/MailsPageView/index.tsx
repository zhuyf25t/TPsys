import { Link } from "react-router-dom";
import type { ReactNode } from "react";
import type {
  ContributionAdjustmentRecords,
  FriendRequestActionState,
  FriendRequestDecision,
  FriendRequestMailStatus,
  MailFilter,
  MailSummary,
  MailsPageState
} from "../../hooks/useMailsPage";
import { ShellLayout } from "../../../../components/ui/ShellLayout";
import { UserActionDot } from "../../../shared/components/user-action-dot/UserActionDot";

/** 中文名称：站内信视图。游戏职责：渲染邮件摘要、已读操作、治理入口和好友申请处理。 */
export function MailsPageView({
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
}: MailsPageState) {
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
        <p className="mails-overview__copy">战报、评分更新与好友消息会在这里同步；旧版战报和评分邮件会合并显示成一条。</p>
        <div className="mails-overview__stats" aria-label="站内信统计">
          <OverviewTile label="总计" value={mailSummaries.length} detail="当前账号可见" />
          <OverviewTile label="未读" value={unreadCount} detail="需要处理" tone="hot" />
          <OverviewTile label="战报" value={battleCount} detail="含回放入口" />
          <OverviewTile label="好友" value={friendCount} detail="申请与回执" />
          <OverviewTile label="已合并" value={mergedBattleCount} detail="战报+评分" tone="merged" />
        </div>
        <div className="mails-overview__toolbar">
          <div className="pill-row mail-filter-row" aria-label="邮件筛选">
            <MailFilterChip activeFilter={mailFilter} filter="all" onClick={setMailFilter}>
              总计 {mailSummaries.length}
            </MailFilterChip>
            <MailFilterChip activeFilter={mailFilter} filter="unread" onClick={setMailFilter}>
              未读 {unreadCount}
            </MailFilterChip>
            <MailFilterChip activeFilter={mailFilter} filter="read" onClick={setMailFilter}>
              已读 {readCount}
            </MailFilterChip>
            <MailFilterChip activeFilter={mailFilter} filter="important" onClick={setMailFilter}>
              重要 {importantCount}
            </MailFilterChip>
            <MailFilterChip activeFilter={mailFilter} filter="battle" onClick={setMailFilter}>
              战报 {battleCount}
            </MailFilterChip>
            <MailFilterChip activeFilter={mailFilter} filter="friend" onClick={setMailFilter}>
              好友申请 {friendCount}
            </MailFilterChip>
          </div>
          <button
            type="button"
            className="mail-bulk-read-button"
            disabled={visibleUnreadMails.length === 0}
            onClick={() => {
              void markFilteredRead();
            }}
          >
            {visibleUnreadMails.length > 0 ? `${markFilteredReadLabel} ${visibleUnreadMails.length}` : "当前筛选无未读"}
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
          {filteredMailSummaries.map((mail) => (
            <MailCard
              key={mail.id}
              contributionAdjustments={contributionAdjustments}
              decideFriendRequest={decideFriendRequest}
              friendRequestActions={friendRequestActions}
              isAdminUser={isAdminUser}
              mail={mail}
              markMailRead={markMailRead}
              readFailure={mailReadFailures[mail.id]}
            />
          ))}
        </section>
      )}
    </ShellLayout>
  );
}

function MailCard({
  contributionAdjustments,
  decideFriendRequest,
  friendRequestActions,
  isAdminUser,
  mail,
  markMailRead,
  readFailure
}: {
  contributionAdjustments: ContributionAdjustmentRecords;
  decideFriendRequest: (mail: MailSummary, decision: FriendRequestDecision) => Promise<void>;
  friendRequestActions: Record<string, FriendRequestActionState>;
  isAdminUser: boolean;
  mail: MailSummary;
  markMailRead: (mail: MailSummary) => Promise<void>;
  readFailure?: string;
}) {
  const friendRequestId = getFriendRequestId(mail);
  const actionStatus = friendRequestActions[mail.id];
  const friendRequestStatus = resolveFriendRequestStatus(mail.friendRequestStatus, actionStatus);
  const friendRequestStatusLabel = getFriendRequestStatusLabel(friendRequestStatus, actionStatus);
  const canRespondToFriendRequest = mail.kind === "friend" && friendRequestId !== null && friendRequestStatus === "pending";
  const isProcessing = actionStatus === "processing";
  const isGovernancePendingMail = isAdminUser && mail.kind === "governance" && isPendingGovernanceSubject(mail.subject);
  const governanceActorHandle = mail.governanceActorHandle?.trim() || "";
  const governanceTargetPath = mail.governanceTargetPath?.trim() || "";
  const mailSourcePath = mail.sourcePath?.trim() || "";
  const mailSourceLabel = mail.sourceLabel?.trim() || "查看来源";
  const governanceSource = isGovernancePendingMail ? getGovernanceMailSource(mail) : null;
  const matchedContributionAdjustment = isAdminUser ? findMatchingContributionAdjustment(mail, contributionAdjustments) : null;
  const mergedBattleLabel = getMergedBattleMailLabel(mail);

  return (
    <article
      role="button"
      tabIndex={0}
      className={`mail-card${mail.unread ? " mail-card--unread" : " mail-card--read"}`}
      onClick={() => {
        void markMailRead(mail);
      }}
      onKeyDown={(event) => {
        if (event.key === "Enter" || event.key === " ") {
          event.preventDefault();
          void markMailRead(mail);
        }
      }}
      aria-label={`${mail.subject}${mail.unread ? "，未读" : "，已读"}`}
    >
      <div className="mail-card__main">
        <div className="mail-card__meta">
          <span className={`mail-card__flag mail-card__flag--${mail.kind}`}>{getMailKindLabel(mail)}</span>
          {mail.unread ? <span className="mail-card__dot" aria-label="未读邮件" /> : null}
          <span className={`mail-card__status mail-card__status--${mail.unread ? "unread" : "read"}`}>{mail.unread ? "未读" : "已读"}</span>
          {mail.important ? <span className="mail-card__status">重要</span> : null}
          {mergedBattleLabel ? <span className="mail-card__status mail-card__status--merged">{mergedBattleLabel}</span> : null}
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
            onClick={(event) => {
              event.stopPropagation();
              void markMailRead(mail);
            }}
          >
            标为已读
          </button>
        ) : null}
        {mailSourcePath && !isGovernancePendingMail ? <SourceLink path={mailSourcePath} label={mailSourceLabel} /> : null}
        {isGovernancePendingMail ? (
          <div className="mail-card__actions">
            {governanceSource ? <GovernanceSourcePanel source={governanceSource} /> : null}
            {governanceSource?.path ? <SourceLink path={governanceSource.path} label="打开来源" /> : null}
            {governanceActorHandle ? <small className="mail-card__action-status">处理 @{governanceActorHandle}</small> : null}
            {matchedContributionAdjustment ? (
              <small className="mail-card__action-status">已处理，已有裁决 {formatDelta(matchedContributionAdjustment.delta)}</small>
            ) : null}
            {governanceActorHandle ? (
              <UserActionDot
                handle={governanceActorHandle}
                sourceLabel={governanceSource?.label ?? mail.subject}
                sourcePath={governanceSource?.path ?? governanceTargetPath}
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
                event.stopPropagation();
                void decideFriendRequest(mail, "accepted");
              }}
            >
              同意
            </button>
            <button
              type="button"
              className="mail-card__action"
              disabled={isProcessing}
              onClick={(event) => {
                event.stopPropagation();
                void decideFriendRequest(mail, "rejected");
              }}
            >
              拒绝
            </button>
            {friendRequestStatusLabel ? <small className="mail-card__action-status">{friendRequestStatusLabel}</small> : null}
          </span>
        ) : null}
        {!canRespondToFriendRequest && friendRequestStatusLabel ? <small className="mail-card__action-status">{friendRequestStatusLabel}</small> : null}
        {readFailure ? <small className="mail-card__action-status mail-card__action-status--failed">{readFailure}</small> : null}
      </div>
    </article>
  );
}

function OverviewTile({ label, value, detail, tone }: { label: string; value: number; detail: string; tone?: "hot" | "merged" }) {
  const className =
    tone === "hot" ? "mails-overview__tile mails-overview__tile--hot" : tone === "merged" ? "mails-overview__tile mails-overview__tile--merged" : "mails-overview__tile";

  return (
    <article className={className}>
      <span>{label}</span>
      <strong>{value}</strong>
      <small>{detail}</small>
    </article>
  );
}

function MailFilterChip({ activeFilter, filter, onClick, children }: { activeFilter: MailFilter; filter: MailFilter; onClick: (filter: MailFilter) => void; children: ReactNode }) {
  return (
    <button type="button" className={`pill mail-filter-chip${activeFilter === filter ? " mail-filter-chip--active" : ""}`} onClick={() => onClick(filter)}>
      {children}
    </button>
  );
}

function SourceLink({ path, label }: { path: string; label: string }) {
  return path.startsWith("/") ? (
    <Link className="mail-card__action mail-card__action--accept" to={path}>
      {label}
    </Link>
  ) : (
    <a className="mail-card__action mail-card__action--accept" href={path}>
      {label}
    </a>
  );
}

interface GovernanceMailSource {
  label: string;
  path: string;
}

function GovernanceSourcePanel({ source }: { source: GovernanceMailSource }) {
  return (
    <div className="mail-card__governance-source">
      <small>来源</small>
      <strong>{source.label}</strong>
      {source.path ? <span>{source.path}</span> : null}
    </div>
  );
}

function getGovernanceMailSource(mail: MailSummary): GovernanceMailSource | null {
  const path = mail.governanceTargetPath?.trim() || mail.sourcePath?.trim() || "";
  const targetLabel = mail.governanceTargetLabel?.trim() || stripGovernanceSubjectPrefix(mail.subject);
  const sourceType = getGovernanceSourceType(mail, path);
  const label = [sourceType, targetLabel].filter(Boolean).join(" / ");

  if (!label && !path) {
    return null;
  }

  return {
    label: label || path,
    path
  };
}

function getGovernanceSourceType(mail: MailSummary, path: string): string {
  const normalizedPath = path.trim().toLowerCase();
  const normalizedSubject = mail.subject.trim().toLowerCase();

  if (normalizedPath.startsWith("/replay/") || normalizedSubject.includes("replay")) {
    return "回放";
  }

  if (normalizedPath.startsWith("/discussion/") || normalizedSubject.includes("discussion")) {
    return "论坛";
  }

  if (normalizedPath.startsWith("/profile/") || normalizedSubject.includes("bot")) {
    return "Bot";
  }

  return "治理";
}

function stripGovernanceSubjectPrefix(subject: string): string {
  return subject.replace(/^\[.*?]\s*/, "").trim();
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

function isPendingGovernanceSubject(subject: string): boolean {
  const normalizedSubject = subject.trim().toLowerCase();
  return (
    subject.startsWith("[待处理]") ||
    subject.includes("待处理") ||
    subject.includes("举报") ||
    normalizedSubject.startsWith("[review]") ||
    normalizedSubject.includes(" report") ||
    normalizedSubject.includes(" proposal") ||
    normalizedSubject.includes(" suggestion")
  );
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
