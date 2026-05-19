import { useEffect, useMemo, useState, useSyncExternalStore } from "react";
import { Link } from "react-router-dom";
import { getCurrentAuthUser, subscribeAuthState } from "../../../identity/api/authGateway";
import { submitGovernanceReviewNotification } from "../../../governance/api/governanceGateway";
import { loadReplaySummaries, parseReplayPlayersLine, type ReplaySummary } from "../../api/replayGateway";
import { getReplayDisplayTitle } from "../../objects/replayPresentation";
import { ShellLayout } from "../../../../shared/ui/ShellLayout";

type FeedbackModalState = { replay: ReplaySummary; kind: "proposal" | "report" } | null;
type ReplayLibraryFilter = "all" | "summary" | "playable";

const REPLAY_FILTERS: Array<{ id: ReplayLibraryFilter; label: string }> = [
  { id: "all", label: "总数" },
  { id: "summary", label: "仅摘要" },
  { id: "playable", label: "可播放" }
];

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

  const sortedReplays = useMemo(
    () => [...replaySummaries].sort(compareReplayRecency),
    [replaySummaries]
  );

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
    <ShellLayout title="回放列表" subtitle="本地战报和治理建议队列" hidePageHeader>
      <section className="replay-library__lead">
        <div className="replay-library__heading">
          <p className="replay-library__eyebrow">回放列表</p>
          <h3>回放列表</h3>
          <p className="replay-library__meta">
            总计 {replayFilterCounts.all} 条 · 仅摘要 {replayFilterCounts.summary} 条 · 可播放 {replayFilterCounts.playable} 条
          </p>
        </div>

        <div className="replay-library__filters" role="group" aria-label="回放筛选">
          {REPLAY_FILTERS.map((filter) => (
            <button
              key={filter.id}
              type="button"
              className={`replay-filter-chip${replayFilter === filter.id ? " replay-filter-chip--active" : ""}`}
              aria-pressed={replayFilter === filter.id}
              onClick={() => setReplayFilter(filter.id)}
            >
              <span>{filter.label}</span>
              <strong>{replayFilterCounts[filter.id]}</strong>
            </button>
          ))}
        </div>
      </section>

      {sortedReplays.length === 0 ? (
        <section className="detail-card empty-state empty-state--dense">
          <h3>暂无回放</h3>
          <p>完成一局战斗后，这里会显示本地摘要和评审数据。</p>
          <div className="cta-row">
            <Link className="button-link button-link--primary" to="/battle?new=1">
              进入战斗
            </Link>
          </div>
        </section>
      ) : filteredReplays.length === 0 ? (
        <section className="detail-card empty-state empty-state--dense">
          <h3>没有符合筛选的回放</h3>
          <p>试试其他筛选，或者返回完整列表。</p>
        </section>
      ) : (
        <section className="replay-library replay-library--rows">
          {filteredReplays.map((replay) => (
            <article key={replay.id} className="replay-card replay-card--row">
              <div className="replay-card__cover replay-card__cover--thumb">
                {replay.thumbnailDataUrl ? (
                  <img src={replay.thumbnailDataUrl} alt={`${replay.title} 缩略图`} className="replay-card__image" />
                ) : (
                  <BattleThumbFallback label={replay.coverLabel} />
                )}
              </div>

              <div className="replay-card__body">
                <div className="replay-card__header">
                  <div className="replay-card__title-row">
                    <strong>{getReplayDisplayTitle(replay)}</strong>
                    <span className="replay-card__time">{replay.finishedAtLabel}</span>
                  </div>
                  <p className="replay-card__players" aria-label="玩家排名摘要">
                    {getRankedPlayerSummary(replay)}
                  </p>

                  <div className="replay-card__meta">
                    <span className="pill replay-card__mode">{replay.modeLabel}</span>
                    {getReplayTags(replay).map((tag) => (
                      <span key={`${replay.id}-${tag}`} className="pill">
                        {tag}
                      </span>
                    ))}
                  </div>
                </div>
              </div>

              <div className="replay-card__actions">
                <Link className="button-link button-link--primary replay-card__view" to={`/replay/${replay.id}`}>
                  查看回放
                </Link>
                <button type="button" className="replay-feedback replay-feedback--proposal" onClick={() => openFeedback(replay, "proposal")}>
                  提议
                </button>
                <button type="button" className="replay-feedback replay-feedback--report" onClick={() => openFeedback(replay, "report")}>
                  举报
                </button>
              </div>
            </article>
          ))}
        </section>
      )}

      {feedbackModal ? (
        <div className="replay-modal" role="dialog" aria-modal="true" aria-label={feedbackModal.kind === "proposal" ? "提交建议" : "提交举报"}>
          <div className="replay-modal__panel">
            <button type="button" className="replay-modal__close" onClick={closeFeedback} aria-label="关闭">
              ×
            </button>
            <h3>{feedbackModal.kind === "proposal" ? "建议" : "举报"}</h3>
            <p>{feedbackModal.replay.title}</p>
            {authUser ? (
              <>
                <textarea
                  value={feedbackBody}
                  onChange={(event) => setFeedbackBody(event.target.value)}
                  placeholder={feedbackModal.kind === "proposal" ? "写一条建议" : "描述问题"}
                />
                {feedbackMessage ? <span className="replay-modal__thanks">{feedbackMessage}</span> : null}
                <button
                  type="button"
                  className="button-link button-link--primary"
                  onClick={() => {
                    void sendFeedback();
                  }}
                  disabled={!feedbackBody.trim() || feedbackSending}
                >
                  {feedbackSending ? "提交中..." : "提交"}
                </button>
              </>
            ) : (
              <span className="replay-modal__thanks">{feedbackMessage ?? "请先登录后再提交。"}</span>
            )}
          </div>
        </div>
      ) : null}
    </ShellLayout>
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

  return topPlayers.join(" · ");
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
    <div className="replay-thumb-fallback">
      <span>{label}</span>
      <i className="replay-thumb-fallback__hero replay-thumb-fallback__hero--one" />
      <i className="replay-thumb-fallback__hero replay-thumb-fallback__hero--two" />
      <i className="replay-thumb-fallback__spark replay-thumb-fallback__spark--one" />
      <i className="replay-thumb-fallback__spark replay-thumb-fallback__spark--two" />
    </div>
  );
}
