import { useEffect, useState, type FormEvent } from "react";
import { Link, useParams } from "react-router-dom";
import {
  fetchDiscussionSummaryById,
  submitDiscussionReplyRemote,
  submitDiscussionReplyReportRemote,
  submitDiscussionReplyVoteRemote,
  submitDiscussionReportRemote,
  submitDiscussionVoteRemote,
  type DiscussionReply,
  type DiscussionSummary,
  type DiscussionVote
} from "../../api/forumGateway";
import { ShellLayout } from "../../../../shared/ui/ShellLayout";
import { UserActionDot } from "../../../social/components/user-action-dot/UserActionDot";

type DiscussionReportTarget =
  | { kind: "topic"; topic: DiscussionSummary }
  | { kind: "reply"; topic: DiscussionSummary; reply: DiscussionReply };

/** 中文名：discussiondetailpage（DiscussionDetailPage）。游戏职责：在前端论坛域中组织讨论数据、发帖回帖和投票交互，支撑玩家社区内容。 */
export function DiscussionDetailPage() {
  const { id } = useParams<{ id: string }>();
  const [topic, setTopic] = useState<DiscussionSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [missing, setMissing] = useState(false);
  const [replyBody, setReplyBody] = useState("");
  const [reportTarget, setReportTarget] = useState<DiscussionReportTarget | null>(null);
  const [reportBody, setReportBody] = useState("");
  const [reportMessage, setReportMessage] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function loadTopic(): Promise<void> {
      setLoading(true);
      setMissing(false);

      const loaded = await fetchDiscussionSummaryById(id ?? "");
      if (cancelled) {
        return;
      }

      setTopic(loaded);
      setMissing(!loaded);
      setLoading(false);
    }

    void loadTopic();

    return () => {
      cancelled = true;
    };
  }, [id]);

  async function refreshTopic(): Promise<void> {
    const loaded = await fetchDiscussionSummaryById(id ?? "");
    setTopic(loaded);
    setMissing(!loaded);
    setLoading(false);
  }

  async function handleReplySubmit(event: FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault();
    if (!topic) {
      return;
    }

    const updated = await submitDiscussionReplyRemote(topic.id, { body: replyBody });
    if (!updated) {
      setReportMessage("回复失败，请稍后重试。");
      return;
    }

    setReplyBody("");
    await refreshTopic();
  }

  async function handleVote(vote: DiscussionVote): Promise<void> {
    if (!topic) {
      return;
    }

    const updated = await submitDiscussionVoteRemote(topic.id, topic.viewerVote === vote ? null : vote);
    if (!updated) {
      setReportMessage("投票失败，请稍后重试。");
      return;
    }

    await refreshTopic();
  }

  async function handleReplyVote(reply: DiscussionReply, vote: DiscussionVote): Promise<void> {
    if (!topic) {
      return;
    }

    const updated = await submitDiscussionReplyVoteRemote(topic.id, reply.id, reply.viewerVote === vote ? null : vote);
    if (!updated) {
      setReportMessage("投票失败，请稍后重试。");
      return;
    }

    await refreshTopic();
  }

  async function handleReportSubmit(event: FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault();
    if (!topic || !reportTarget) {
      return;
    }

    const notification =
      reportTarget.kind === "topic"
        ? await submitDiscussionReportRemote(topic, reportBody)
        : await submitDiscussionReplyReportRemote(topic, reportTarget.reply, reportBody);

    if (!notification) {
      setReportMessage("通知管理员失败。");
      return;
    }

    setReportBody("");
    setReportMessage(notification.ok ? "已通知管理员处理。" : "通知管理员失败。");
  }

  function closeReport(): void {
    setReportTarget(null);
    setReportBody("");
    setReportMessage(null);
  }

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
            <button
              type="button"
              className={`forum-action${topic.viewerVote === "up" ? " forum-action--active" : ""}`}
              aria-pressed={topic.viewerVote === "up"}
              onClick={() => void handleVote("up")}
            >
              Up
            </button>
            <button
              type="button"
              className={`forum-action${topic.viewerVote === "down" ? " forum-action--active" : ""}`}
              aria-pressed={topic.viewerVote === "down"}
              onClick={() => void handleVote("down")}
            >
              Down
            </button>
            <button type="button" className="forum-action forum-action--report" onClick={() => setReportTarget({ kind: "topic", topic })}>
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
                  <article key={reply.id} id={`reply-${reply.id}`} className="forum-reply">
                    <div className="forum-reply__meta">
                      <span className="user-handle-row user-handle-row--inline">
                        <Link to={profilePath(reply.author)}>{reply.author}</Link>
                        <UserActionDot
                          handle={reply.author}
                          sourceLabel={`论坛评论：${topic.title}`}
                          sourcePath={`/discussion/${encodeURIComponent(topic.id)}#reply-${encodeURIComponent(reply.id)}`}
                        />
                      </span>
                      <small>{reply.publishedAt}</small>
                    </div>
                    <p>{reply.body}</p>
                    <div className="forum-reply__actions" aria-label={`@${reply.author} 的评论操作`}>
                      <span className="forum-topic__score">{formatVoteSummary(reply.score, reply.viewerVote)}</span>
                      <button
                        type="button"
                        className={`forum-action${reply.viewerVote === "up" ? " forum-action--active" : ""}`}
                        aria-pressed={reply.viewerVote === "up"}
                        onClick={() => void handleReplyVote(reply, "up")}
                      >
                        Up
                      </button>
                      <button
                        type="button"
                        className={`forum-action${reply.viewerVote === "down" ? " forum-action--active" : ""}`}
                        aria-pressed={reply.viewerVote === "down"}
                        onClick={() => void handleReplyVote(reply, "down")}
                      >
                        Down
                      </button>
                      <button type="button" className="forum-action forum-action--report" onClick={() => setReportTarget({ kind: "reply", topic, reply })}>
                        Report
                      </button>
                    </div>
                  </article>
                ))
              ) : (
                <article className="forum-reply forum-reply--empty">
                  <strong>还没有回复</strong>
                  <p>先发一条吧。</p>
                </article>
              )}
            </div>

            <form className="forum-reply-composer" onSubmit={handleReplySubmit}>
              <label className="truth-form__field">
                <span>回复</span>
                <textarea
                  value={replyBody}
                  onChange={(event) => setReplyBody(event.target.value)}
                  rows={4}
                  maxLength={300}
                  placeholder="补一条评论"
                  required
                />
              </label>
              <button className="button-link button-link--primary" type="submit" disabled={!replyBody.trim()}>
                发送
              </button>
            </form>
          </aside>
        </div>
      </section>

      {reportTarget ? (
        <div
          className="forum-modal"
          role="dialog"
          aria-modal="true"
          aria-label={reportTarget.kind === "topic" ? "举报话题" : "举报评论"}
        >
          <div className="forum-modal__panel">
            <button type="button" className="forum-modal__close" onClick={closeReport} aria-label="关闭">
              ×
            </button>
            <h3>{reportTarget.kind === "topic" ? "举报帖子" : "举报评论"}</h3>
            <p>{reportTarget.kind === "topic" ? reportTarget.topic.title : `@${reportTarget.reply.author}：${reportTarget.reply.body.slice(0, 80)}`}</p>
            <form className="truth-form" onSubmit={handleReportSubmit}>
              <label className="truth-form__field">
                <span>说明</span>
                <textarea
                  value={reportBody}
                  onChange={(event) => setReportBody(event.target.value)}
                  rows={4}
                  maxLength={300}
                  placeholder="请简要说明"
                  required
                />
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
