import { useEffect, useMemo, useState, useSyncExternalStore, type ReactNode } from "react";
import { Link } from "react-router-dom";
import { getCurrentAuthUser, subscribeAuthState } from "../../../identity/api/authGateway";
import { submitGovernanceReviewNotification } from "../../../governance/api/governanceGateway";
import { loadReplaySummaries, parseReplayPlayersLine, type ReplaySummary } from "../../api/replayGateway";
import { getReplayDisplayTitle } from "../../objects/replayPresentation";
import { ShellLayout } from "../../../../shared/ui/ShellLayout";
import { cn } from "../../../../shared/ui/classNames";

type FeedbackModalState = { replay: ReplaySummary; kind: "proposal" | "report" } | null;
type ReplayLibraryFilter = "all" | "summary" | "playable";

const REPLAY_FILTERS: Array<{ id: ReplayLibraryFilter; label: string }> = [
  { id: "all", label: "总数" },
  { id: "summary", label: "仅摘要" },
  { id: "playable", label: "可播放" }
];

const primaryButton =
  "inline-flex h-10 items-center justify-center rounded-md bg-emerald-600 px-4 text-sm font-semibold text-white shadow-sm transition hover:bg-emerald-700 disabled:cursor-not-allowed disabled:opacity-50";
const secondaryButton =
  "inline-flex h-10 items-center justify-center rounded-md border border-slate-300 bg-white px-4 text-sm font-semibold text-slate-700 transition hover:bg-slate-50";
const dangerButton =
  "inline-flex h-10 items-center justify-center rounded-md border border-rose-200 bg-rose-50 px-4 text-sm font-semibold text-rose-700 transition hover:bg-rose-100";
const pillClassName = "inline-flex rounded-full bg-slate-100 px-3 py-1 text-xs font-semibold text-slate-600";

/** 中文名称：回放页。游戏职责：组织回放摘要、时间线和治理反馈入口。 */
export function ReplayPage() {
  const authUser = useSyncExternalStore(subscribeAuthState, getCurrentAuthUser, getCurrentAuthUser);
  const [replaySummaries, setReplaySummaries] = useState<ReplaySummary[]>([]);
  const [replayFilter, setReplayFilter] = useState<ReplayLibraryFilter>("all");
  const [feedbackModal, setFeedbackModal] = useState<FeedbackModalState>(null);
  const [feedbackBody, setFeedbackBody] = useState("");
  const [feedbackSending, setFeedbackSending] = useState(false);
  const [feedbackMessage, setFeedbackMessage] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    void loadReplaySummaries().then((remote) => {
      if (!active || remote == null) {
        return;
      }

      setReplaySummaries(remote);
    });

    return () => {
      active = false;
    };
  }, []);

  const sortedReplays = useMemo(() => [...replaySummaries].sort(compareReplayRecency), [replaySummaries]);

  const replayFilterCounts = useMemo(
    () => ({
      all: sortedReplays.length,
      summary: sortedReplays.filter((replay) => !replay.playbackAvailable).length,
      playable: sortedReplays.filter((replay) => replay.playbackAvailable).length
    }),
    [sortedReplays]
  );

  const filteredReplays = useMemo(
    () =>
      sortedReplays.filter((replay) => {
        if (replayFilter === "playable") {
          return replay.playbackAvailable;
        }

        if (replayFilter === "summary") {
          return !replay.playbackAvailable;
        }

        return true;
      }),
    [replayFilter, sortedReplays]
  );

  const openFeedback = (replay: ReplaySummary, kind: "proposal" | "report"): void => {
    setFeedbackModal({ replay, kind });
    setFeedbackBody("");
    setFeedbackMessage(authUser ? null : "请先登录后再提交。");
    setFeedbackSending(false);
  };

  const closeFeedback = (): void => {
    setFeedbackModal(null);
    setFeedbackBody("");
    setFeedbackSending(false);
    setFeedbackMessage(null);
  };

  const sendFeedback = async (): Promise<void> => {
    if (!feedbackModal || !authUser) {
      setFeedbackMessage("请先登录后再提交。");
      return;
    }

    const body = feedbackBody.trim();
    if (!body || feedbackSending) {
      return;
    }

    setFeedbackSending(true);
    const notification = await submitGovernanceReviewNotification({
      actorHandle: authUser.handle,
      kind: feedbackModal.kind === "proposal" ? "replay_proposal" : "replay_report",
      targetType: "replay",
      targetId: feedbackModal.replay.id,
      targetTitle: getReplayDisplayTitle(feedbackModal.replay),
      targetPath: `/replay/${feedbackModal.replay.id}`,
      body
    });

    setFeedbackMessage(notification.ok ? "已提交，感谢反馈。" : "提交失败，请稍后再试。");
    if (notification.ok) {
      setFeedbackBody("");
    }
    setFeedbackSending(false);
  };

  return (
    <ShellLayout title="回放列表" subtitle="本地战报和治理建议队列。" hidePageHeader>
      <section className="mx-auto flex w-full max-w-6xl flex-col gap-5">
        <header className="flex flex-wrap items-end justify-between gap-4 rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
          <div>
            <p className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">Replay</p>
            <h3 className="mt-2 text-2xl font-semibold text-slate-950">回放列表</h3>
            <p className="mt-1 text-sm text-slate-600">
              总计 {replayFilterCounts.all} 条 | 仅摘要 {replayFilterCounts.summary} 条 | 可播放 {replayFilterCounts.playable} 条
            </p>
          </div>

          <div className="flex flex-wrap gap-2" role="group" aria-label="回放筛选">
            {REPLAY_FILTERS.map((filter) => (
              <button
                key={filter.id}
                type="button"
                className={cn(
                  "inline-flex items-center gap-2 rounded-md border px-3 py-2 text-sm font-semibold transition",
                  replayFilter === filter.id ? "border-emerald-500 bg-emerald-600 text-white" : "border-slate-300 bg-white text-slate-700 hover:bg-slate-50"
                )}
                aria-pressed={replayFilter === filter.id}
                onClick={() => setReplayFilter(filter.id)}
              >
                <span>{filter.label}</span>
                <strong>{replayFilterCounts[filter.id]}</strong>
              </button>
            ))}
          </div>
        </header>

        {sortedReplays.length === 0 ? (
          <EmptyState title="暂无回放" body="完成一局战斗后，这里会显示本地摘要和评审数据。">
            <Link className={primaryButton} to="/battle?new=1">
              进入战斗
            </Link>
          </EmptyState>
        ) : filteredReplays.length === 0 ? (
          <EmptyState title="没有符合筛选的回放" body="试试其他筛选，或者返回完整列表。" />
        ) : (
          <section className="flex flex-col gap-3">
            {filteredReplays.map((replay) => (
              <article key={replay.id} className="grid gap-4 overflow-hidden rounded-lg border border-slate-200 bg-white p-4 shadow-sm md:grid-cols-[180px_minmax(0,1fr)_auto]">
                <div className="min-h-28 overflow-hidden rounded-lg bg-slate-900">
                  {replay.thumbnailDataUrl ? (
                    <img src={replay.thumbnailDataUrl} alt={`${replay.title} 缩略图`} className="h-full w-full object-cover" />
                  ) : (
                    <BattleThumbFallback label={replay.coverLabel} />
                  )}
                </div>

                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <strong className="text-lg font-semibold text-slate-950">{getReplayDisplayTitle(replay)}</strong>
                    <span className="text-sm font-medium text-slate-500">{replay.finishedAtLabel}</span>
                  </div>
                  <p className="mt-2 text-sm leading-6 text-slate-600" aria-label="玩家排名摘要">
                    {getRankedPlayerSummary(replay)}
                  </p>

                  <div className="mt-3 flex flex-wrap gap-2">
                    <span className={pillClassName}>{replay.modeLabel}</span>
                    {getReplayTags(replay).map((tag) => (
                      <span key={`${replay.id}-${tag}`} className={pillClassName}>
                        {tag}
                      </span>
                    ))}
                  </div>
                </div>

                <div className="flex flex-wrap items-center gap-2 md:flex-col md:items-stretch md:justify-center">
                  <Link className={primaryButton} to={`/replay/${replay.id}`}>
                    查看回放
                  </Link>
                  <button type="button" className={secondaryButton} onClick={() => openFeedback(replay, "proposal")}>
                    建议
                  </button>
                  <button type="button" className={dangerButton} onClick={() => openFeedback(replay, "report")}>
                    举报
                  </button>
                </div>
              </article>
            ))}
          </section>
        )}
      </section>

      {feedbackModal ? (
        <Modal title={feedbackModal.kind === "proposal" ? "建议" : "举报"} ariaLabel={feedbackModal.kind === "proposal" ? "提交建议" : "提交举报"} onClose={closeFeedback}>
          <p className="text-sm font-semibold text-slate-700">{feedbackModal.replay.title}</p>
          {authUser ? (
            <div className="mt-4 flex flex-col gap-4">
              <textarea
                className="min-h-32 rounded-md border border-slate-300 bg-white px-3 py-2 text-sm text-slate-950 outline-none transition placeholder:text-slate-400 focus:border-emerald-500 focus:ring-2 focus:ring-emerald-500/20"
                value={feedbackBody}
                onChange={(event) => setFeedbackBody(event.target.value)}
                placeholder={feedbackModal.kind === "proposal" ? "写一条建议" : "描述问题"}
              />
              {feedbackMessage ? <span className="text-sm font-semibold text-emerald-700">{feedbackMessage}</span> : null}
              <button
                type="button"
                className={primaryButton}
                onClick={() => {
                  void sendFeedback();
                }}
                disabled={!feedbackBody.trim() || feedbackSending}
              >
                {feedbackSending ? "提交中..." : "提交"}
              </button>
            </div>
          ) : (
            <span className="mt-4 block text-sm font-semibold text-rose-700">{feedbackMessage ?? "请先登录后再提交。"}</span>
          )}
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

function getRankedPlayerSummary(replay: ReplaySummary): string {
  const names = parseReplayPlayersLine(replay.playersLine);
  if (names.length === 0) {
    return "暂无排名摘要";
  }

  const topPlayers = names.slice(0, 4).map((player, index) => `#${index + 1} ${player}`);
  if (names.length > 4) {
    topPlayers.push(`+${names.length - 4}`);
  }

  return topPlayers.join(" | ");
}

function getReplayTags(replay: ReplaySummary): string[] {
  return Array.from(new Set([replay.mapLabel, replay.resultLabel].filter(Boolean)));
}

function compareReplayRecency(left: ReplaySummary, right: ReplaySummary): number {
  return getReplayRecency(right) - getReplayRecency(left);
}

function getReplayRecency(replay: ReplaySummary): number {
  return Number.isFinite(replay.finishedAt) && replay.finishedAt > 0 ? replay.finishedAt : getReplayTimestamp(replay.id);
}

function getReplayTimestamp(id: string): number {
  const match = id.match(/(\d{10,})/);
  return match ? Number(match[1]) : 0;
}

function BattleThumbFallback({ label }: { label: string }) {
  return (
    <div className="relative flex h-full min-h-28 items-end overflow-hidden bg-gradient-to-br from-slate-800 via-emerald-950 to-slate-950 p-4 text-white">
      <span className="relative z-10 text-sm font-semibold">{label}</span>
      <i className="absolute right-8 top-6 h-10 w-10 rounded-full bg-emerald-400/70" />
      <i className="absolute right-16 top-12 h-6 w-6 rounded-full bg-amber-300/70" />
      <i className="absolute bottom-8 left-8 h-2 w-10 rotate-12 rounded-full bg-cyan-200/70" />
    </div>
  );
}
