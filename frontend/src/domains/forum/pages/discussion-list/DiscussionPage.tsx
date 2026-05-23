import { useEffect, useState, type FormEvent, type ReactNode } from "react";
import { Link } from "react-router-dom";
import {
  fetchDiscussionSummaries,
  submitDiscussionReportRemote,
  submitDiscussionTopicRemote,
  submitDiscussionVoteRemote,
  type DiscussionSummary,
  type DiscussionVote
} from "../../api/forumGateway";
import { ShellLayout } from "../../../../shared/ui/ShellLayout";
import { UserActionDot } from "../../../social/components/user-action-dot/UserActionDot";
import { cn } from "../../../../shared/ui/classNames";

const DEFAULT_TAG = "战术讨论";
const DISCUSSION_TAGS = ["战术讨论", "组队招募", "版本反馈"] as const;

const actionButtonBase =
  "inline-flex h-9 items-center justify-center rounded-md border px-3 text-xs font-semibold transition disabled:cursor-not-allowed disabled:opacity-50";
const primaryButton =
  "inline-flex h-10 items-center justify-center rounded-md bg-emerald-600 px-4 text-sm font-semibold text-white shadow-sm transition hover:bg-emerald-700 disabled:cursor-not-allowed disabled:opacity-50";
const secondaryButton =
  "inline-flex h-10 items-center justify-center rounded-md border border-slate-300 bg-white px-4 text-sm font-semibold text-slate-700 transition hover:bg-slate-50";
const fieldInput =
  "mt-2 w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-950 outline-none transition placeholder:text-slate-400 focus:border-emerald-500 focus:ring-2 focus:ring-emerald-500/20";

/** 中文名称：论坛列表页。游戏职责：组织讨论数据、发帖和投票交互。 */
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
      <section className="mx-auto flex w-full max-w-6xl flex-col gap-5">
        <header className="flex flex-wrap items-end justify-between gap-4 rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">Forum</p>
            <h3 className="mt-2 text-2xl font-semibold text-slate-950">讨论区</h3>
            <p className="mt-1 text-sm leading-6 text-slate-600">保留帖子本体、投票和回复入口。</p>
          </div>
          <div className="flex flex-wrap items-center gap-2 text-sm font-semibold text-slate-600" aria-label="讨论区统计">
            <span className="rounded-full bg-slate-100 px-3 py-1">{loading ? "加载中" : `${discussionSummaries.length} 话题`}</span>
            <span className="rounded-full bg-slate-100 px-3 py-1">{loading ? "..." : `${replyCount} 回复`}</span>
            <button type="button" className={primaryButton} onClick={() => setComposerOpen(true)}>
              发帖
            </button>
          </div>
        </header>

        {error ? (
          <EmptyState title="论坛加载失败" body={error}>
            <button type="button" className={primaryButton} onClick={() => void refresh()}>
              重试
            </button>
          </EmptyState>
        ) : loading ? (
          <EmptyState title="加载话题中" body="后端数据正在拉取。" />
        ) : discussionSummaries.length === 0 ? (
          <EmptyState title="暂无话题" body="当前后端没有返回任何共享讨论。">
            <button type="button" className={primaryButton} onClick={() => setComposerOpen(true)}>
              发起话题
            </button>
          </EmptyState>
        ) : (
          <section className="flex flex-col gap-3" aria-label="讨论话题列表">
            {discussionSummaries.map((topic) => (
              <article key={topic.id} className="grid gap-4 rounded-lg border border-slate-200 bg-white p-4 shadow-sm md:grid-cols-[minmax(0,1fr)_auto]">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <Link className="min-w-0 text-lg font-semibold text-slate-950 hover:text-emerald-700" to={`/discussion/${topic.id}`}>
                      {topic.title}
                    </Link>
                    <span className="rounded-full bg-emerald-50 px-2 py-1 text-xs font-semibold text-emerald-700">{topic.tag}</span>
                  </div>
                  <div className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-slate-500">
                    <span className="inline-flex min-w-0 items-center gap-1">
                      <Link className="font-semibold text-slate-700 hover:text-emerald-700" to={profilePath(topic.author)}>
                        {topic.author}
                      </Link>
                      <UserActionDot handle={topic.author} sourceLabel={`论坛帖子：${topic.title}`} sourcePath={`/discussion/${encodeURIComponent(topic.id)}`} />
                    </span>
                    <span>{topic.updatedAt}</span>
                    <span>{topic.replies} 回复</span>
                    <span className="font-semibold text-slate-700">{formatVoteSummary(topic.score, topic.viewerVote)}</span>
                  </div>
                  {topic.excerpt ? <p className="mt-3 line-clamp-2 text-sm leading-6 text-slate-600">{topic.excerpt}</p> : null}
                </div>
                <div className="flex flex-wrap items-center gap-2 md:justify-end" aria-label={`${topic.title} 操作`}>
                  <VoteButton active={topic.viewerVote === "up"} onClick={() => void handleVote(topic, "up")}>
                    Up
                  </VoteButton>
                  <VoteButton active={topic.viewerVote === "down"} onClick={() => void handleVote(topic, "down")}>
                    Down
                  </VoteButton>
                  <button
                    type="button"
                    className={cn(actionButtonBase, "border-rose-200 bg-rose-50 text-rose-700 hover:bg-rose-100")}
                    onClick={() => openReport(topic)}
                  >
                    Report
                  </button>
                </div>
              </article>
            ))}
          </section>
        )}
      </section>

      {composerOpen ? (
        <Modal title="发帖" ariaLabel="发起新话题" onClose={resetComposer}>
          <form className="flex flex-col gap-4" onSubmit={handleSubmit}>
            <Field label="标题">
              <input className={fieldInput} value={title} onChange={(event) => setTitle(event.target.value)} maxLength={48} required />
            </Field>
            <Field label="标签">
              <select className={fieldInput} value={tag} onChange={(event) => setTag(event.target.value)}>
                {DISCUSSION_TAGS.map((item) => (
                  <option key={item} value={item}>
                    {item}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="内容">
              <textarea className={fieldInput} value={body} onChange={(event) => setBody(event.target.value)} rows={6} maxLength={500} required />
            </Field>
            <div className="flex flex-wrap gap-2">
              <button className={primaryButton} type="submit" disabled={!title.trim() || !body.trim()}>
                发布
              </button>
              <button type="button" className={secondaryButton} onClick={resetComposer}>
                关闭
              </button>
            </div>
          </form>
        </Modal>
      ) : null}

      {reportTarget ? (
        <Modal title="举报" ariaLabel="举报话题" onClose={closeReport}>
          <p className="text-sm font-semibold text-slate-700">{reportTarget.title}</p>
          <form className="mt-4 flex flex-col gap-4" onSubmit={handleReportSubmit}>
            <Field label="说明">
              <textarea
                className={fieldInput}
                value={reportBody}
                onChange={(event) => setReportBody(event.target.value)}
                rows={4}
                maxLength={300}
                placeholder="请简要说明"
                required
              />
            </Field>
            {reportMessage ? <span className="text-sm font-semibold text-emerald-700">{reportMessage}</span> : null}
            <div className="flex flex-wrap gap-2">
              <button className={primaryButton} type="submit" disabled={!reportBody.trim()}>
                提交
              </button>
              <button type="button" className={secondaryButton} onClick={closeReport}>
                关闭
              </button>
            </div>
          </form>
        </Modal>
      ) : null}
    </ShellLayout>
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

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="block text-sm font-semibold text-slate-700">
      <span>{label}</span>
      {children}
    </label>
  );
}

function Modal({ title, ariaLabel, onClose, children }: { title: string; ariaLabel: string; onClose: () => void; children: ReactNode }) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/45 px-4 py-6" role="dialog" aria-modal="true" aria-label={ariaLabel}>
      <div className="relative max-h-[90vh] w-full max-w-xl overflow-auto rounded-lg bg-white p-6 shadow-2xl">
        <button
          type="button"
          className="absolute right-3 top-3 inline-flex h-9 w-9 items-center justify-center rounded-full border border-slate-200 text-lg font-semibold text-slate-500 hover:bg-slate-50"
          onClick={onClose}
          aria-label="关闭"
        >
          x
        </button>
        <h3 className="pr-10 text-xl font-semibold text-slate-950">{title}</h3>
        <div className="mt-4">{children}</div>
      </div>
    </div>
  );
}

function VoteButton({ active, onClick, children }: { active: boolean; onClick: () => void; children: ReactNode }) {
  return (
    <button
      type="button"
      className={cn(
        actionButtonBase,
        active ? "border-emerald-500 bg-emerald-600 text-white" : "border-slate-300 bg-white text-slate-700 hover:bg-slate-50"
      )}
      aria-pressed={active}
      onClick={onClick}
    >
      {children}
    </button>
  );
}

function profilePath(handle: string): string {
  return `/profile/${encodeURIComponent(handle)}`;
}

function formatVoteSummary(score: number, viewerVote: DiscussionVote): string {
  const scoreLabel = score > 0 ? `+${score}` : `${score}`;
  if (viewerVote === "up") {
    return `${scoreLabel} | 已赞`;
  }

  if (viewerVote === "down") {
    return `${scoreLabel} | 已踩`;
  }

  return `${scoreLabel} | 未投`;
}
