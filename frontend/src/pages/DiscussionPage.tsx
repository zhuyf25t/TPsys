import { useEffect, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import {
  fetchDiscussionSummaries,
  submitDiscussionReportRemote,
  submitDiscussionTopicRemote,
  submitDiscussionVoteRemote,
  type DiscussionSummary,
  type DiscussionVote
} from "../features/forum/forumGateway";
import { ShellLayout } from "../shared/ui/ShellLayout";
import { UserActionDot } from "../shared/ui/UserActionDot";

const DEFAULT_TAG = "战术讨论";
const DISCUSSION_TAGS = ["战术讨论", "组队招募", "版本反馈"] as const;

export function DiscussionPage() {
  const [discussionSummaries, setDiscussionSummaries] = useState<DiscussionSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [composerOpen, setComposerOpen] = useState(false);
  const [title, setTitle] = useState("");
  const [tag, setTag] = useState(DEFAULT_TAG);
  const [body, setBody] = useState("");
  const [reportTarget, setReportTarget] = useState<DiscussionSummary | null>(null);
  const [reportBody, setReportBody] = useState("");
  const [reportMessage, setReportMessage] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function loadTopics(): Promise<void> {
      setLoading(true);
      const items = await fetchDiscussionSummaries();

      if (cancelled) {
        return;
      }

      setDiscussionSummaries(items);
      setError(null);
      setLoading(false);
    }

    void loadTopics();

    return () => {
      cancelled = true;
    };
  }, []);

  const replyCount = discussionSummaries.reduce((sum, topic) => sum + topic.replies, 0);

  async function refresh(): Promise<void> {
    setLoading(true);
    const items = await fetchDiscussionSummaries();
    setDiscussionSummaries(items);
    setError(null);
    setLoading(false);
  }

  function resetComposer(): void {
    setTitle("");
    setTag(DEFAULT_TAG);
    setBody("");
    setComposerOpen(false);
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault();
    const created = await submitDiscussionTopicRemote({ title, tag, body });
    if (!created) {
      setError("发帖失败，请稍后重试。");
      return;
    }

    resetComposer();
    await refresh();
  }

  async function handleVote(topic: DiscussionSummary, vote: DiscussionVote): Promise<void> {
    const updated = await submitDiscussionVoteRemote(topic.id, topic.viewerVote === vote ? null : vote);
    if (!updated) {
      setError("投票失败，请稍后重试。");
      return;
    }

    await refresh();
  }

  function openReport(topic: DiscussionSummary): void {
    setReportTarget(topic);
    setReportBody("");
    setReportMessage(null);
  }

  function closeReport(): void {
    setReportTarget(null);
    setReportBody("");
    setReportMessage(null);
  }

  async function handleReportSubmit(event: FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault();
    if (!reportTarget) {
      return;
    }

    const notification = await submitDiscussionReportRemote(reportTarget, reportBody);
    if (!notification) {
      setReportMessage("通知管理员失败。");
      return;
    }

    setReportBody("");
    setReportMessage(notification.ok ? "已通知管理员处理。" : "通知管理员失败。");
  }

  return (
    <ShellLayout title="论坛" subtitle="查看全部讨论" hidePageHeader>
      <section className="forum-board">
        <header className="forum-board__lead">
          <div className="forum-board__title">
            <p className="replay-library__eyebrow">Forum</p>
            <h3>讨论区</h3>
            <p>只保留帖子本体、投票和回复入口。</p>
          </div>
          <div className="forum-board__summary" aria-label="讨论区统计">
            <span>{loading ? "加载中" : `${discussionSummaries.length} 话题`}</span>
            <span>{loading ? "..." : `${replyCount} 回复`}</span>
            <button
              type="button"
              className="button-link button-link--primary forum-board__compose"
              onClick={() => setComposerOpen(true)}
            >
              发帖
            </button>
          </div>
        </header>

        {error ? (
          <section className="detail-card empty-state empty-state--dense">
            <h3>论坛加载失败</h3>
            <p>{error}</p>
            <button type="button" className="button-link button-link--primary" onClick={() => void refresh()}>
              重试
            </button>
          </section>
        ) : loading ? (
          <section className="detail-card empty-state empty-state--dense">
            <h3>加载话题中</h3>
            <p>后端数据还在拉取。</p>
          </section>
        ) : discussionSummaries.length === 0 ? (
          <section className="detail-card empty-state empty-state--dense">
            <h3>暂无话题</h3>
            <p>当前后端没有返回任何共享讨论。</p>
            <button type="button" className="button-link button-link--primary" onClick={() => setComposerOpen(true)}>
              发起话题
            </button>
          </section>
        ) : (
          <section className="forum-list forum-list--compact" aria-label="讨论话题列表">
            {discussionSummaries.map((topic) => (
              <article key={topic.id} className="forum-topic forum-topic--row">
                <div className="forum-topic__main">
                  <div className="forum-topic__headline">
                    <Link className="forum-topic__link" to={`/discussion/${topic.id}`}>
                      <strong>{topic.title}</strong>
                    </Link>
                    <span className="forum-topic__tag">{topic.tag}</span>
                  </div>
                  <div className="forum-topic__meta">
                    <span className="user-handle-row user-handle-row--inline">
                      <Link to={profilePath(topic.author)}>{topic.author}</Link>
                      <UserActionDot handle={topic.author} sourceLabel={`论坛帖子：${topic.title}`} sourcePath={`/discussion/${encodeURIComponent(topic.id)}`} />
                    </span>
                    <span>{topic.updatedAt}</span>
                    <span>{topic.replies} 回复</span>
                    <span className="forum-topic__score">{formatVoteSummary(topic.score, topic.viewerVote)}</span>
                  </div>
                  {topic.excerpt ? <p className="forum-topic__excerpt">{topic.excerpt}</p> : null}
                </div>
                <div className="forum-topic__actions" aria-label={`${topic.title} 操作`}>
                  <button
                    type="button"
                    className={`forum-action${topic.viewerVote === "up" ? " forum-action--active" : ""}`}
                    aria-pressed={topic.viewerVote === "up"}
                    onClick={() => void handleVote(topic, "up")}
                  >
                    Up
                  </button>
                  <button
                    type="button"
                    className={`forum-action${topic.viewerVote === "down" ? " forum-action--active" : ""}`}
                    aria-pressed={topic.viewerVote === "down"}
                    onClick={() => void handleVote(topic, "down")}
                  >
                    Down
                  </button>
                  <button type="button" className="forum-action forum-action--report" onClick={() => openReport(topic)}>
                    Report
                  </button>
                </div>
              </article>
            ))}
          </section>
        )}
      </section>

      {composerOpen ? (
        <div className="forum-modal" role="dialog" aria-modal="true" aria-label="发起新话题">
          <div className="forum-modal__panel">
            <button type="button" className="forum-modal__close" onClick={resetComposer} aria-label="关闭">
              ×
            </button>
            <h3>发帖</h3>
            <form className="truth-form" onSubmit={handleSubmit}>
              <label className="truth-form__field">
                <span>标题</span>
                <input value={title} onChange={(event) => setTitle(event.target.value)} maxLength={48} required />
              </label>
              <label className="truth-form__field">
                <span>标签</span>
                <select value={tag} onChange={(event) => setTag(event.target.value)}>
                  {DISCUSSION_TAGS.map((item) => (
                    <option key={item} value={item}>
                      {item}
                    </option>
                  ))}
                </select>
              </label>
              <label className="truth-form__field">
                <span>内容</span>
                <textarea value={body} onChange={(event) => setBody(event.target.value)} rows={6} maxLength={500} required />
              </label>
              <div className="cta-row">
                <button className="button-link button-link--primary" type="submit" disabled={!title.trim() || !body.trim()}>
                  发布
                </button>
                <button type="button" className="button-link" onClick={resetComposer}>
                  关闭
                </button>
              </div>
            </form>
          </div>
        </div>
      ) : null}

      {reportTarget ? (
        <div className="forum-modal" role="dialog" aria-modal="true" aria-label="举报话题">
          <div className="forum-modal__panel">
            <button type="button" className="forum-modal__close" onClick={closeReport} aria-label="关闭">
              ×
            </button>
            <h3>举报</h3>
            <p>{reportTarget.title}</p>
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
