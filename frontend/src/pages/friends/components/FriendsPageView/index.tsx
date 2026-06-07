import { Link } from "react-router-dom";
import type { ReactNode } from "react";
import { ShellLayout } from "../../../../components/ui/ShellLayout";
import type { FriendRequestDecision, FriendRequestRecord } from "../../../../apis/social/friendRequestGateway";
import { UserActionDot } from "../../../shared/components/user-action-dot/UserActionDot";
import {
  formatRequestTime,
  isIncomingRequest,
  resolvePeerHandle,
  type FriendContact,
  type FriendFilter,
  type FriendRequestActionState,
  type FriendsPageState
} from "../../hooks/useFriendsPage";

/** 中文名称：好友页视图。游戏职责：展示好友列表、好友请求和处理入口。 */
export function FriendsPageView({
  acceptedContacts,
  currentHandle,
  decideFriendRequest,
  filteredRequests,
  friendFilter,
  incomingPendingCount,
  loadFailed,
  outgoingPendingCount,
  requestActions,
  requests,
  setFriendFilter
}: FriendsPageState) {
  return (
    <ShellLayout
      title="好友列表"
      subtitle="好友请求、已通过联系人和处理状态集中展示。"
      headerAside={
        <div className="mails-header-actions" aria-label="好友页快捷操作">
          <span>{currentHandle ? `当前 ${currentHandle}` : "未登录"}</span>
          <Link to="/mails">站内信</Link>
          <Link to="/rating">评分榜</Link>
        </div>
      }
    >
      <section className="detail-card friends-overview">
        <h3>社交概览</h3>
        <p className="mails-overview__copy">好友列表会同步已接受的申请；收到的待处理请求可以直接同意或拒绝。</p>
        <div className="mails-overview__stats" aria-label="好友统计">
          <OverviewTile label="好友" value={acceptedContacts.length} detail="已通过联系人" />
          <OverviewTile label="待处理" value={incomingPendingCount} detail="需要回应" tone="hot" />
          <OverviewTile label="已发出" value={outgoingPendingCount} detail="等待对方处理" />
          <OverviewTile label="记录" value={requests.length} detail="全部请求状态" tone="merged" />
        </div>
        {loadFailed ? <p className="friends-overview__warning">好友请求同步失败，当前展示本地缓存。</p> : null}
        <div className="pill-row mail-filter-row" aria-label="好友筛选">
          <FriendFilterChip activeFilter={friendFilter} filter="all" onClick={setFriendFilter}>
            全部 {requests.length}
          </FriendFilterChip>
          <FriendFilterChip activeFilter={friendFilter} filter="incoming" onClick={setFriendFilter}>
            待处理 {incomingPendingCount}
          </FriendFilterChip>
          <FriendFilterChip activeFilter={friendFilter} filter="outgoing" onClick={setFriendFilter}>
            已发出 {outgoingPendingCount}
          </FriendFilterChip>
          <FriendFilterChip activeFilter={friendFilter} filter="accepted" onClick={setFriendFilter}>
            已同意 {acceptedContacts.length}
          </FriendFilterChip>
          <FriendFilterChip activeFilter={friendFilter} filter="rejected" onClick={setFriendFilter}>
            已拒绝 {requests.filter((request) => request.status === "rejected").length}
          </FriendFilterChip>
        </div>
      </section>

      {!currentHandle ? (
        <section className="detail-card empty-state empty-state--dense">
          <h3>登录后查看好友列表</h3>
          <p>好友请求和联系人需要账号身份才能同步。</p>
          <div className="cta-row">
            <Link className="button-link button-link--primary" to="/">
              返回大厅登录
            </Link>
          </div>
        </section>
      ) : (
        <section className="friends-board">
          <section className="content-page__panel content-page__panel--side">
            <div className="panel-header panel-header--dense">
              <div>
                <p className="eyebrow">Contacts</p>
                <h4>已通过好友</h4>
              </div>
              <span className="panel-header__meta">{acceptedContacts.length}</span>
            </div>
            {acceptedContacts.length > 0 ? (
              <div className="friends-contact-list">
                {acceptedContacts.map((contact) => (
                  <ContactCard key={contact.handle} contact={contact} />
                ))}
              </div>
            ) : (
              <div className="empty-state empty-state--dense friends-empty-panel">
                <h3>暂无已通过好友</h3>
                <p>在排行榜、论坛或回放里点击玩家旁边的社交按钮可以发送好友申请。</p>
              </div>
            )}
          </section>

          <section className="content-page__panel content-page__panel--main">
            <div className="panel-header panel-header--dense">
              <div>
                <p className="eyebrow">Requests</p>
                <h4>好友请求</h4>
              </div>
              <span className="panel-header__meta">{filteredRequests.length}</span>
            </div>
            {filteredRequests.length > 0 ? (
              <div className="friends-request-list">
                {filteredRequests.map((request) => (
                  <FriendRequestCard
                    key={request.id}
                    currentHandle={currentHandle}
                    decideFriendRequest={decideFriendRequest}
                    request={request}
                    requestAction={requestActions[request.id]}
                  />
                ))}
              </div>
            ) : (
              <div className="empty-state empty-state--dense friends-empty-panel">
                <h3>没有符合筛选的好友请求</h3>
                <p>切换上方筛选条件，或在其他玩家资料入口发起新的好友申请。</p>
              </div>
            )}
          </section>
        </section>
      )}
    </ShellLayout>
  );
}

function ContactCard({ contact }: { contact: FriendContact }) {
  return (
    <article className="friend-contact-card">
      <div>
        <strong>@{contact.handle}</strong>
        <span>{contact.sinceLabel} 建立联系</span>
      </div>
      <UserActionDot handle={contact.handle} sourceLabel="好友列表" sourcePath="/friends" />
    </article>
  );
}

function FriendRequestCard({
  currentHandle,
  decideFriendRequest,
  request,
  requestAction
}: {
  currentHandle: string;
  decideFriendRequest: (request: FriendRequestRecord, decision: FriendRequestDecision) => Promise<void>;
  request: FriendRequestRecord;
  requestAction?: FriendRequestActionState;
}) {
  const owner = currentHandle.trim().toLowerCase();
  const incoming = isIncomingRequest(request, owner);
  const peerHandle = resolvePeerHandle(request, owner);
  const canRespond = incoming && request.status === "pending";
  const processing = requestAction === "processing";

  return (
    <article className={`friend-request-card friend-request-card--${request.status}`}>
      <div className="friend-request-card__main">
        <div className="mail-card__meta">
          <span className="mail-card__flag mail-card__flag--friend">{incoming ? "收到" : "发出"}</span>
          <span className={`mail-card__status mail-card__status--${request.status === "pending" ? "unread" : "read"}`}>{getStatusLabel(request.status)}</span>
          {requestAction === "failed" ? <span className="mail-card__action-status mail-card__action-status--failed">处理失败</span> : null}
        </div>
        <strong>@{peerHandle}</strong>
        <span>
          来源 @{request.sourceHandle} / 去向 @{request.targetHandle} / {formatRequestTime(request.respondedAt ?? request.createdAt)}
        </span>
      </div>
      <div className="friend-request-card__actions">
        <UserActionDot handle={peerHandle} sourceLabel="好友请求" sourcePath="/friends" />
        {canRespond ? (
          <>
            <button
              type="button"
              className="mail-card__action mail-card__action--accept"
              disabled={processing}
              onClick={() => {
                void decideFriendRequest(request, "accepted");
              }}
            >
              同意
            </button>
            <button
              type="button"
              className="mail-card__action"
              disabled={processing}
              onClick={() => {
                void decideFriendRequest(request, "rejected");
              }}
            >
              拒绝
            </button>
          </>
        ) : null}
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

function FriendFilterChip({ activeFilter, filter, onClick, children }: { activeFilter: FriendFilter; filter: FriendFilter; onClick: (filter: FriendFilter) => void; children: ReactNode }) {
  return (
    <button type="button" className={`pill mail-filter-chip${activeFilter === filter ? " mail-filter-chip--active" : ""}`} onClick={() => onClick(filter)}>
      {children}
    </button>
  );
}

function getStatusLabel(status: FriendRequestRecord["status"]): string {
  switch (status) {
    case "pending":
      return "待处理";
    case "accepted":
      return "已同意";
    case "rejected":
      return "已拒绝";
  }
}
