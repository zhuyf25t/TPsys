import { useEffect, useState, type FormEvent, type ReactNode } from "react";
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
import { cn } from "../../../../shared/ui/classNames";

type DiscussionReportTarget =
  | { kind: "topic"; topic: DiscussionSummary }
  | { kind: "reply"; topic: DiscussionSummary; reply: DiscussionReply };

const actionButtonBase =
  "inline-flex h-9 items-center justify-center rounded-md border px-3 text-xs font-semibold transition disabled:cursor-not-allowed disabled:opacity-50";
const primaryButton =
  "inline-flex h-10 items-center justify-center rounded-md bg-emerald-600 px-4 text-sm font-semibold text-white shadow-sm transition hover:bg-emerald-700 disabled:cursor-not-allowed disabled:opacity-50";
const secondaryButton =
  "inline-flex h-10 items-center justify-center rounded-md border border-slate-300 bg-white px-4 text-sm font-semibold text-slate-700 transition hover:bg-slate-50";
const fieldInput =
  "mt-2 w-full rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-950 outline-none transition placeholder:text-slate-400 focus:border-emerald-500 focus:ring-2 focus:ring-emerald-500/20";

/** 中文名称：论坛详情页。游戏职责：组织单个话题、回复、投票和举报交互。 */
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
    setReportMessage(null);
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
        <EmptyState title="加载帖子中" body="后端正在返回内容。" />
      </ShellLayout>
    );
  }

  if (!topic || missing) {
    return (
      <ShellLayout title="帖子详情" subtitle="未找到" hidePageHeader backTo="/discussion" backLabel="返回论坛">
        <EmptyState title="没有找到这条帖子" body="它可能已经被删除，或者当前后端没有这条真实话题。">
          <Link className={secondaryButton} to="/discussion">
            返回论坛
          </Link>
        </EmptyState>
      </ShellLayout>
    );
  }

  return (
    <ShellLayout title="帖子详情" subtitle={topic.title} hidePageHeader backTo="/discussion" backLabel="返回论坛">
      <section className="mx-auto flex w-full max-w-6xl flex-col gap-5">
        <header className="grid gap-4 rounded-lg border border-slate-200 bg-white p-5 shadow-sm lg:grid-cols-[minmax(0,1fr)_auto]">
          <div className="min-w-0">
            <span className="inline-flex rounded-full bg-emerald-50 px-2 py-1 text-xs font-semibold text-emerald-700">{topic.tag}</span>
            <h3 className="mt-3 text-2xl font-semibold text-slate-950">{topic.title}</h3>
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
          </div>
          <div className="flex flex-wrap items-center gap-2 lg:justify-end">
            <VoteButton active={topic.viewerVote === "up"} onClick={() => void handleVote("up")}>
              Up
            </VoteButton>
            <VoteButton active={topic.viewerVote === "down"} onClick={() => void handleVote("down")}>
              Down
            </VoteButton>
            <button
              type="button"
              className={cn(actionButtonBase, "border-rose-200 bg-rose-50 text-rose-700 hover:bg-rose-100")}
              onClick={() => setReportTarget({ kind: "topic", topic })}
            >
              Report
            </button>
          </div>
        </header>

        <div className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_420px]">
          <article className="rounded-lg border border-slate-200 bg-white p-5 text-sm leading-7 text-slate-700 shadow-sm">
            <p className="whitespace-pre-wrap">{topic.body}</p>
          </article>

          <aside className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
            <div className="flex items-center justify-between border-b border-slate-200 pb-3">
              <h3 className="text-lg font-semibold text-slate-950">回复</h3>
              <span className="rounded-full bg-slate-100 px-2 py-1 text-xs font-semibold text-slate-600">{topic.replyItems.length}</span>
            </div>
            <div className="mt-4 flex flex-col gap-3">
              {topic.replyItems.length > 0 ? (
                topic.replyItems.map((reply) => (
                  <article key={reply.id} id={`reply-${reply.id}`} className="rounded-lg border border-slate-200 bg-slate-50 p-4">
                    <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-slate-500">
                      <span className="inline-flex min-w-0 items-center gap-1">
                        <Link className="font-semibold text-slate-700 hover:text-emerald-700" to={profilePath(reply.author)}>
                          {reply.author}
                        </Link>
                        <UserActionDot
                          handle={reply.author}
                          sourceLabel={`论坛评论：${topic.title}`}
                          sourcePath={`/discussion/${encodeURIComponent(topic.id)}#reply-${encodeURIComponent(reply.id)}`}
                        />
                      </span>
                      <small>{reply.publishedAt}</small>
                    </div>
                    <p className="mt-3 whitespace-pre-wrap text-sm leading-6 text-slate-700">{reply.body}</p>
                    <div className="mt-3 flex flex-wrap items-center gap-2" aria-label={`@${reply.author} 的评论操作`}>
                      <span className="mr-auto text-xs font-semibold text-slate-600">{formatVoteSummary(reply.score, reply.viewerVote)}</span>
                      <VoteButton active={reply.viewerVote === "up"} onClick={() => void handleReplyVote(reply, "up")}>
                        Up
                      </VoteButton>
                      <VoteButton active={reply.viewerVote === "down"} onClick={() => void handleReplyVote(reply, "down")}>
                        Down
                      </VoteButton>
                      <button
                        type="button"
                        className={cn(actionButtonBase, "border-rose-200 bg-rose-50 text-rose-700 hover:bg-rose-100")}
                        onClick={() => setReportTarget({ kind: "reply", topic, reply })}
                      >
                        Report
                      </button>
                    </div>
                  </article>
                ))
              ) : (
                <article className="rounded-lg border border-dashed border-slate-300 bg-slate-50 p-4">
                  <strong className="text-sm text-slate-800">还没有回复</strong>
                  <p className="mt-1 text-sm text-slate-500">先发一条吧。</p>
                </article>
              )}
            </div>

            <form className="mt-5 flex flex-col gap-3 border-t border-slate-200 pt-4" onSubmit={handleReplySubmit}>
              <Field label="回复">
                <textarea
                  className={fieldInput}
                  value={replyBody}
                  onChange={(event) => setReplyBody(event.target.value)}
                  rows={4}
                  maxLength={300}
                  placeholder="补一条评论"
                  required
                />
              </Field>
              {reportMessage && !reportTarget ? <span className="text-sm font-semibold text-rose-700">{reportMessage}</span> : null}
              <button className={primaryButton} type="submit" disabled={!replyBody.trim()}>
                发送
              </button>
            </form>
          </aside>
        </div>
      </section>

      {reportTarget ? (
        <Modal
          title={reportTarget.kind === "topic" ? "举报帖子" : "举报评论"}
          ariaLabel={reportTarget.kind === "topic" ? "举报话题" : "举报评论"}
          onClose={closeReport}
        >
          <p className="text-sm font-semibold text-slate-700">
            {reportTarget.kind === "topic" ? reportTarget.topic.title : `@${reportTarget.reply.author}：${reportTarget.reply.body.slice(0, 80)}`}
          </p>
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
    <section className="mx-auto flex max-w-xl flex-col items-start rounded-lg border border-dashed border-slate-300 bg-white p-6 shadow-sm">
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
