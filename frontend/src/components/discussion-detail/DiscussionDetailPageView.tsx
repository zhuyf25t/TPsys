import { Link } from "react-router-dom";
import type { DiscussionDetailPageState, DiscussionReply, DiscussionVote } from "../../hooks/discussion-detail-page/useDiscussionDetailPage";
import { ShellLayout } from "../../shared/ui/ShellLayout";
import { UserActionDot } from "../user-action-dot/UserActionDot";

/** 中文名称：论坛详情视图。游戏职责：渲染单个话题、回复、投票和举报交互。 */
export function DiscussionDetailPageView({
  closeReport,
  loading,
  missing,
  openReplyReport,
  openTopicReport,
  replyBody,
  reportBody,
  reportMessage,
  reportTarget,
  setReplyBody,
  setReportBody,
  submitReply,
  submitReplyVote,
  submitReport,
  submitTopicVote,
  topic
}: DiscussionDetailPageState) {
  if (loading) {
    return (
      <ShellLayout title="帖子详情" subtitle="加载中" hidePageHeader backTo="/discussion" backLabel="返回论坛">
        <section className="detail-card empty-state">
          <h3>加载帖子中</h3>
          <p>后端正在返回内容。</p>
        </section>
      </ShellLayout>
    );
  }

  if (!topic || missing) {
    return (
      <ShellLayout title="帖子详情" subtitle="未找到" hidePageHeader backTo="/discussion" backLabel="返回论坛">
        <section className="detail-card empty-state">
          <h3>没找到这条帖子</h3>
          <p>它可能已被删除，或者当前后端没有这条真实话题。</p>
          <div className="cta-row">
            <Link className="button-link" to="/discussion">
              返回论坛
            </Link>
          </div>
        </section>
      </ShellLayout>
    );
  }

  return (
    <ShellLayout title="帖子详情" subtitle={topic.title} hidePageHeader backTo="/discussion" backLabel="返回论坛">
      <section className="forum-detail">
        <header className="forum-detail__header">
          <div className="forum-detail__title">
            <span className="forum-topic__tag">{topic.tag}</span>
            <h3>{topic.title}</h3>
            <div className="forum-topic__meta">
              <span className="user-handle-row user-handle-row--inline">
                <Link to={profilePath(topic.author)}>{topic.author}</Link>
                <UserActionDot handle={topic.author} sourceLabel={`论坛帖子：${topic.title}`} sourcePath={`/discussion/${encodeURIComponent(topic.id)}`} />
              </span>
              <span>{topic.updatedAt}</span>
              <span>{topic.replies} 回复</span>
              <span className="forum-topic__score">{formatVoteSummary(topic.score, topic.viewerVote)}</span>
            </div>
          </div>
          <div className="forum-topic__actions forum-topic__actions--detail">
            <VoteButton active={topic.viewerVote === "up"} onClick={() => void submitTopicVote("up")} />
            <VoteButton active={topic.viewerVote === "down"} vote="down" onClick={() => void submitTopicVote("down")} />
            <button type="button" className="forum-action forum-action--report" onClick={openTopicReport}>
              Report
            </button>
          </div>
        </header>

        <div className="forum-detail__body">
          <article className="detail-card forum-post-card">
            <p>{topic.body}</p>
          </article>

          <aside className="detail-card forum-replies">
            <div className="forum-replies__header">
              <h3>回复</h3>
              <span>{topic.replyItems.length}</span>
            </div>
            <div className="forum-replies__list">
              {topic.replyItems.length > 0 ? (
                topic.replyItems.map((reply) => (
                  <ReplyCard key={reply.id} reply={reply} topicId={topic.id} topicTitle={topic.title} onReport={openReplyReport} onVote={submitReplyVote} />
                ))
              ) : (
                <article className="forum-reply forum-reply--empty">
                  <strong>还没有回复</strong>
                  <p>先发一条吧。</p>
                </article>
              )}
            </div>

            <form
              className="forum-reply-composer"
              onSubmit={(event) => {
                event.preventDefault();
                void submitReply();
              }}
            >
              <label className="truth-form__field">
                <span>回复</span>
                <textarea value={replyBody} onChange={(event) => setReplyBody(event.target.value)} rows={4} maxLength={300} placeholder="补一条评论" required />
              </label>
              <button className="button-link button-link--primary" type="submit" disabled={!replyBody.trim()}>
                发送
              </button>
            </form>
          </aside>
        </div>
      </section>

      {reportTarget ? (
        <div className="forum-modal" role="dialog" aria-modal="true" aria-label={reportTarget.kind === "topic" ? "举报话题" : "举报评论"}>
          <div className="forum-modal__panel">
            <button type="button" className="forum-modal__close" onClick={closeReport} aria-label="关闭">
              ×
            </button>
            <h3>{reportTarget.kind === "topic" ? "举报帖子" : "举报评论"}</h3>
            <p>{reportTarget.kind === "topic" ? reportTarget.topic.title : `@${reportTarget.reply.author}：${reportTarget.reply.body.slice(0, 80)}`}</p>
            <form
              className="truth-form"
              onSubmit={(event) => {
                event.preventDefault();
                void submitReport();
              }}
            >
              <label className="truth-form__field">
                <span>说明</span>
                <textarea value={reportBody} onChange={(event) => setReportBody(event.target.value)} rows={4} maxLength={300} placeholder="请简要说明" required />
              </label>
              {reportMessage ? <span className="forum-modal__thanks">{reportMessage}</span> : null}
              <div className="cta-row">
                <button className="button-link button-link--primary" type="submit" disabled={!reportBody.trim()}>
                  提交
                </button>
                <button type="button" className="button-link" onClick={closeReport}>
                  关闭
                </button>
              </div>
            </form>
          </div>
        </div>
      ) : null}
    </ShellLayout>
  );
}

function ReplyCard({
  onReport,
  onVote,
  reply,
  topicId,
  topicTitle
}: {
  onReport: (reply: DiscussionReply) => void;
  onVote: (reply: DiscussionReply, vote: DiscussionVote) => Promise<void>;
  reply: DiscussionReply;
  topicId: string;
  topicTitle: string;
}) {
  return (
    <article id={`reply-${reply.id}`} className="forum-reply">
      <div className="forum-reply__meta">
        <span className="user-handle-row user-handle-row--inline">
          <Link to={profilePath(reply.author)}>{reply.author}</Link>
          <UserActionDot
            handle={reply.author}
            sourceLabel={`论坛评论：${topicTitle}`}
            sourcePath={`/discussion/${encodeURIComponent(topicId)}#reply-${encodeURIComponent(reply.id)}`}
          />
        </span>
        <small>{reply.publishedAt}</small>
      </div>
      <p>{reply.body}</p>
      <div className="forum-reply__actions" aria-label={`@${reply.author} 的评论操作`}>
        <span className="forum-topic__score">{formatVoteSummary(reply.score, reply.viewerVote)}</span>
        <VoteButton active={reply.viewerVote === "up"} onClick={() => void onVote(reply, "up")} />
        <VoteButton active={reply.viewerVote === "down"} vote="down" onClick={() => void onVote(reply, "down")} />
        <button type="button" className="forum-action forum-action--report" onClick={() => onReport(reply)}>
          Report
        </button>
      </div>
    </article>
  );
}

function VoteButton({ active, vote = "up", onClick }: { active: boolean; vote?: DiscussionVote; onClick: () => void }) {
  return (
    <button type="button" className={`forum-action${active ? " forum-action--active" : ""}`} aria-pressed={active} onClick={onClick}>
      {vote === "up" ? "Up" : "Down"}
    </button>
  );
}

function profilePath(handle: string): string {
  return `/profile/${encodeURIComponent(handle)}`;
}

function formatVoteSummary(score: number, viewerVote: DiscussionVote): string {
  const scoreLabel = score > 0 ? `+${score}` : `${score}`;
  if (viewerVote === "up") {
    return `${scoreLabel} · 已赞`;
  }

  if (viewerVote === "down") {
    return `${scoreLabel} · 已踩`;
  }

  return `${scoreLabel} · 未投`;
}
