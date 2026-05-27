import { Link } from "react-router-dom";
import type { DiscussionPageState, DiscussionVote } from "../../hooks/discussion-page/useDiscussionPage";
import { ShellLayout } from "../../shared/ui/ShellLayout";
import { UserActionDot } from "../user-action-dot/UserActionDot";

/** 中文名称：论坛列表视图。游戏职责：渲染话题列表、发帖表单和举报弹窗。 */
export function DiscussionPageView({
  body,
  closeReport,
  composerOpen,
  discussionSummaries,
  discussionTags,
  error,
  loading,
  openComposer,
  openReport,
  refresh,
  replyCount,
  reportBody,
  reportMessage,
  reportTarget,
  resetComposer,
  setBody,
  setReportBody,
  setTag,
  setTitle,
  submitReport,
  submitTopic,
  submitVote,
  tag,
  title
}: DiscussionPageState) {
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
            <button type="button" className="button-link button-link--primary forum-board__compose" onClick={openComposer}>
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
            <button type="button" className="button-link button-link--primary" onClick={openComposer}>
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
                  <VoteButton active={topic.viewerVote === "up"} onClick={() => void submitVote(topic, "up")} />
                  <VoteButton active={topic.viewerVote === "down"} vote="down" onClick={() => void submitVote(topic, "down")} />
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
            <form
              className="truth-form"
              onSubmit={(event) => {
                event.preventDefault();
                void submitTopic();
              }}
            >
              <label className="truth-form__field">
                <span>标题</span>
                <input value={title} onChange={(event) => setTitle(event.target.value)} maxLength={48} required />
              </label>
              <label className="truth-form__field">
                <span>标签</span>
                <select value={tag} onChange={(event) => setTag(event.target.value)}>
                  {discussionTags.map((item) => (
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
