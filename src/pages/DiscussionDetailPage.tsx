import { useMemo, useState, type FormEvent } from "react";
import { Link, useParams } from "react-router-dom";
import {
  getDiscussionSummaryById,
  submitDiscussionReply,
  submitDiscussionReport,
  submitDiscussionVote,
  type DiscussionVote
} from "../features/forum/forumGateway";
import { ShellLayout } from "../shared/ui/ShellLayout";

export function DiscussionDetailPage() {
  const { id } = useParams<{ id: string }>();
  const [refreshKey, setRefreshKey] = useState(0);
  const [replyBody, setReplyBody] = useState("");
  const [reportOpen, setReportOpen] = useState(false);
  const [reportBody, setReportBody] = useState("");
  const [reportMessage, setReportMessage] = useState<string | null>(null);
  const topic = useMemo(() => getDiscussionSummaryById(id ?? ""), [id, refreshKey]);

  function handleReplySubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!topic) {
      return;
    }

    const updated = submitDiscussionReply(topic.id, { body: replyBody });
    if (!updated) {
      return;
    }

    setReplyBody("");
    setRefreshKey((value) => value + 1);
  }

  function handleVote(vote: DiscussionVote): void {
    if (!topic) {
      return;
    }

    submitDiscussionVote(topic.id, vote);
    setRefreshKey((value) => value + 1);
  }

  function handleReportSubmit(event: FormEvent<HTMLFormElement>): void {
    event.preventDefault();
    if (!topic) {
      return;
    }

    const saved = submitDiscussionReport(topic.id, reportBody);
    if (saved) {
      setReportBody("");
      setReportMessage("举报已记录，感谢你帮忙维护讨论区。");
    }
  }

  function closeReport(): void {
    setReportOpen(false);
    setReportBody("");
    setReportMessage(null);
  }

  if (!topic) {
    return (
      <ShellLayout title="帖子详情" subtitle="没有找到这条话题。" backTo="/discussion" backLabel="返回讨论区">
        <section className="detail-card empty-state">
          <h3>没有找到这条帖子</h3>
          <p>它可能不在当前设备的讨论区里。</p>
          <div className="cta-row">
            <Link className="button-link" to="/discussion">
              返回讨论区
            </Link>
            <Link className="button-link button-link--primary" to="/battle">
              进入战斗
            </Link>
          </div>
        </section>
      </ShellLayout>
    );
  }

  return (
    <ShellLayout title="帖子详情" subtitle={topic.title} hidePageHeader backTo="/discussion" backLabel="返回讨论区">
      <section className="forum-detail">
        <header className="forum-detail__header">
          <div className="forum-detail__title">
            <span className="forum-topic__tag">{topic.tag}</span>
            <h3>{topic.title}</h3>
            <div className="forum-topic__meta">
              <span>楼主 @{topic.author}</span>
              <span>{topic.updatedAt}</span>
              <span>{topic.replies} 回复</span>
            </div>
          </div>
          <div className="forum-topic__actions forum-topic__actions--detail">
            <button
              type="button"
              className={`forum-action${topic.viewerVote === "up" ? " forum-action--active" : ""}`}
              aria-pressed={topic.viewerVote === "up"}
              onClick={() => handleVote("up")}
            >
              Up
            </button>
            <button
              type="button"
              className={`forum-action${topic.viewerVote === "down" ? " forum-action--active" : ""}`}
              aria-pressed={topic.viewerVote === "down"}
              onClick={() => handleVote("down")}
            >
              Down
            </button>
            <button type="button" className="forum-action forum-action--report" onClick={() => setReportOpen(true)}>
              举报
            </button>
          </div>
        </header>

        <div className="forum-detail__body">
          <main className="detail-card forum-post-card">
            <p>{topic.body}</p>
          </main>

          <aside className="detail-card forum-replies">
            <div className="forum-replies__header">
              <h3>回复</h3>
              <span>{topic.replyItems.length}</span>
            </div>
            <div className="forum-replies__list">
              {topic.replyItems.length > 0 ? (
                topic.replyItems.map((reply) => (
                  <article key={reply.id} className="forum-reply">
                    <div className="forum-reply__meta">
                      <strong>@{reply.author}</strong>
                      <small>{reply.publishedAt}</small>
                    </div>
                    <p>{reply.body}</p>
                  </article>
                ))
              ) : (
                <article className="forum-reply forum-reply--empty">
                  <strong>还没有回复</strong>
                  <p>你可以先发出第一条回应。</p>
                </article>
              )}
            </div>

            <form className="forum-reply-composer" onSubmit={handleReplySubmit}>
              <label className="truth-form__field">
                <span>写下回复</span>
                <textarea
                  value={replyBody}
                  onChange={(event) => setReplyBody(event.target.value)}
                  rows={4}
                  maxLength={300}
                  placeholder="补充你的看法"
                  required
                />
              </label>
              <button className="button-link button-link--primary" type="submit" disabled={!replyBody.trim()}>
                发送回复
              </button>
            </form>
          </aside>
        </div>
      </section>

      {reportOpen ? (
        <div className="forum-modal" role="dialog" aria-modal="true" aria-label="举报话题">
          <div className="forum-modal__panel">
            <button type="button" className="forum-modal__close" onClick={closeReport} aria-label="关闭">
              x
            </button>
            <h3>举报话题</h3>
            <p>{topic.title}</p>
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
