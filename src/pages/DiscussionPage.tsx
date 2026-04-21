import { useMemo, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import {
  getDiscussionSummaries,
  submitDiscussionReport,
  submitDiscussionTopic,
  submitDiscussionVote,
  type DiscussionSummary,
  type DiscussionVote
} from "../features/forum/forumGateway";
import { ShellLayout } from "../shared/ui/ShellLayout";

const DEFAULT_TAG = "战术讨论";
const DISCUSSION_TAGS = ["战术讨论", "组队招募", "版本反馈"] as const;

export function DiscussionPage() {
  const [refreshKey, setRefreshKey] = useState(0);
  const [composerOpen, setComposerOpen] = useState(false);
  const [title, setTitle] = useState("");
  const [tag, setTag] = useState(DEFAULT_TAG);
  const [body, setBody] = useState("");
  const [reportTarget, setReportTarget] = useState<DiscussionSummary | null>(null);
  const [reportBody, setReportBody] = useState("");
  const [reportMessage, setReportMessage] = useState<string | null>(null);
  const discussionSummaries = useMemo(() => getDiscussionSummaries(), [refreshKey]);
  const replyCount = discussionSummaries.reduce((sum, topic) => sum + topic.replies, 0);

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const created = submitDiscussionTopic({ title, tag, body });
    if (!created) {
      return;
    }

    setTitle("");
    setTag(DEFAULT_TAG);
    setBody("");
    setComposerOpen(false);
    setRefreshKey((value) => value + 1);
  }

  function handleVote(topicId: string, vote: DiscussionVote): void {
    submitDiscussionVote(topicId, vote);
    setRefreshKey((value) => value + 1);
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

  function handleReportSubmit(event: FormEvent<HTMLFormElement>): void {
    event.preventDefault();
    if (!reportTarget) {
      return;
    }

    const saved = submitDiscussionReport(reportTarget.id, reportBody);
    if (saved) {
      setReportBody("");
      setReportMessage("举报已记录，感谢你帮忙维护讨论区。");
    }
  }

  return (
    <ShellLayout title="讨论区" subtitle="真实话题" hidePageHeader>
      <section className="forum-board">
        <header className="forum-board__lead">
          <div>
            <p className="replay-library__eyebrow">Forum</p>
            <h3>讨论区</h3>
            <p>只展示真实发起的话题和回复。</p>
          </div>
          <div className="forum-board__summary" aria-label="讨论区统计">
            <span>{discussionSummaries.length} 话题</span>
            <span>{replyCount} 回复</span>
            <button type="button" className="button-link button-link--primary forum-board__compose" onClick={() => setComposerOpen(true)}>
              发帖
            </button>
          </div>
        </header>

        {discussionSummaries.length === 0 ? (
          <section className="detail-card empty-state empty-state--dense">
            <h3>还没有话题</h3>
            <p>这里不会伪造热闹。发起第一条话题后，它会保留在当前设备的讨论区。</p>
            <button type="button" className="button-link button-link--primary" onClick={() => setComposerOpen(true)}>
              发起话题
            </button>
          </section>
        ) : (
          <section className="forum-list forum-list--compact" aria-label="讨论话题列表">
            {discussionSummaries.map((topic) => (
              <article key={topic.id} className="forum-topic forum-topic--row">
                <Link className="forum-topic__main" to={`/discussion/${topic.id}`}>
                  <strong>{topic.title}</strong>
                  <div className="forum-topic__meta">
                    <span>@{topic.author}</span>
                    <span>{topic.updatedAt}</span>
                    <span className="forum-topic__tag">{topic.tag}</span>
                    <span>{topic.replies} 回复</span>
                  </div>
                </Link>
                <div className="forum-topic__actions" aria-label={`${topic.title} 操作`}>
                  <button
                    type="button"
                    className={`forum-action${topic.viewerVote === "up" ? " forum-action--active" : ""}`}
                    aria-pressed={topic.viewerVote === "up"}
                    onClick={() => handleVote(topic.id, "up")}
                  >
                    Up
                  </button>
                  <button
                    type="button"
                    className={`forum-action${topic.viewerVote === "down" ? " forum-action--active" : ""}`}
                    aria-pressed={topic.viewerVote === "down"}
                    onClick={() => handleVote(topic.id, "down")}
                  >
                    Down
                  </button>
                  <button type="button" className="forum-action forum-action--report" onClick={() => openReport(topic)}>
                    举报
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
            <button type="button" className="forum-modal__close" onClick={() => setComposerOpen(false)} aria-label="关闭">
              x
            </button>
            <h3>发起话题</h3>
            <p>发布后会保留在当前设备的讨论区。</p>
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
                <span>正文</span>
                <textarea value={body} onChange={(event) => setBody(event.target.value)} rows={5} maxLength={400} required />
              </label>
              <div className="cta-row">
                <button className="button-link button-link--primary" type="submit" disabled={!title.trim() || !body.trim()}>
                  发布
                </button>
                <Link className="button-link" to="/battle">
                  先去打一局
                </Link>
              </div>
            </form>
          </div>
        </div>
      ) : null}

      {reportTarget ? (
        <div className="forum-modal" role="dialog" aria-modal="true" aria-label="举报话题">
          <div className="forum-modal__panel">
            <button type="button" className="forum-modal__close" onClick={closeReport} aria-label="关闭">
              x
            </button>
            <h3>举报话题</h3>
            <p>{reportTarget.title}</p>
            <form className="truth-form" onSubmit={handleReportSubmit}>
              <label className="truth-form__field">
                <span>说明</span>
                <textarea
                  value={reportBody}
                  onChange={(event) => setReportBody(event.target.value)}
                  rows={4}
                  maxLength={300}
                  placeholder="说明你看到的问题"
                  required
                />
              </label>
              {reportMessage ? <span className="forum-modal__thanks">{reportMessage}</span> : null}
              <button className="button-link button-link--primary" type="submit" disabled={!reportBody.trim()}>
                发送举报
              </button>
            </form>
          </div>
        </div>
      ) : null}
    </ShellLayout>
  );
}
