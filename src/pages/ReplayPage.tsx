import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { saveLocalFeedback } from "../features/governance/localFeedbackStore";
import { getReplaySummaries, loadReplaySummaries, parseReplayPlayersLine, type ReplaySummary } from "../features/replay/replayGateway";
import { getReplayDisplayTitle } from "../features/replay/replayPresentation";
import { ShellLayout } from "../shared/ui/ShellLayout";

type FeedbackModalState = { replay: ReplaySummary; kind: "proposal" | "report" } | null;
type ReplayLibraryFilter = "all" | "summary" | "playable";

const REPLAY_FILTERS: Array<{ id: ReplayLibraryFilter; label: string }> = [
  { id: "all", label: "总数" },
  { id: "summary", label: "仅摘要" },
  { id: "playable", label: "可播放" }
];

export function ReplayPage() {
  const [replaySummaries, setReplaySummaries] = useState<ReplaySummary[]>(() => getReplaySummaries());
  const [replayFilter, setReplayFilter] = useState<ReplayLibraryFilter>("all");
  const [feedbackModal, setFeedbackModal] = useState<FeedbackModalState>(null);
  const [feedbackBody, setFeedbackBody] = useState("");
  const [sentMessage, setSentMessage] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    void loadReplaySummaries().then((remote) => {
      if (!active || !remote || remote.length === 0) {
        return;
      }

      setReplaySummaries(remote);
    });

    return () => {
      active = false;
    };
  }, []);

  const sortedReplays = useMemo(
    () => [...replaySummaries].sort((left, right) => getReplayTimestamp(right.id) - getReplayTimestamp(left.id)),
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

  const openFeedback = (event: React.MouseEvent, replay: ReplaySummary, kind: "proposal" | "report"): void => {
    event.preventDefault();
    event.stopPropagation();
    setFeedbackModal({ replay, kind });
    setFeedbackBody("");
    setSentMessage(null);
  };

  const closeFeedback = (): void => {
    setFeedbackModal(null);
    setFeedbackBody("");
  };

  const sendFeedback = (): void => {
    if (!feedbackModal) {
      return;
    }

    const saved = saveLocalFeedback({
      replayId: feedbackModal.replay.id,
      kind: feedbackModal.kind,
      author: "本地用户",
      body: feedbackBody
    });

    if (saved) {
      setSentMessage(feedbackModal.kind === "proposal" ? "提议已本地记录，感谢反馈。" : "举报已本地记录，感谢反馈。");
      setFeedbackBody("");
    }
  };

  return (
    <ShellLayout title="回放室" subtitle="本地战报与回放列表" hidePageHeader>
      <section className="replay-library__lead">
        <h3>回放室</h3>
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
          <p>完成一局战斗后，这里会出现本地战报。</p>
          <div className="cta-row">
            <Link className="button-link button-link--primary" to="/battle">
              进入战斗
            </Link>
          </div>
        </section>
      ) : filteredReplays.length === 0 ? (
        <section className="detail-card empty-state empty-state--dense">
          <h3>没有符合筛选的回放</h3>
          <p>切换到其他筛选查看当前回放库。</p>
        </section>
      ) : (
        <section className="replay-library replay-library--rows">
          {filteredReplays.map((replay) => (
            <Link key={replay.id} to={`/replay/${replay.id}`} className="replay-card replay-card--row">
              <div className="replay-card__cover replay-card__cover--thumb">
                {replay.thumbnailDataUrl ? (
                  <img src={replay.thumbnailDataUrl} alt={`${replay.title} 缩略图`} className="replay-card__image" />
                ) : (
                  <BattleThumbFallback label={replay.coverLabel} />
                )}
              </div>

              <div className="replay-card__body">
                <div className="replay-card__header">
                  <strong>{getReplayDisplayTitle(replay)}</strong>
                  <span className="replay-card__time">{replay.finishedAtLabel}</span>
                </div>

                <div className="replay-card__meta">
                  <span className="pill replay-card__mode">{replay.modeLabel}</span>
                  {getReplayTags(replay).map((tag) => (
                    <span key={`${replay.id}-${tag}`} className="pill">
                      {tag}
                    </span>
                  ))}
                  <span className={`pill replay-card__status replay-card__status--${replay.playbackAvailable ? "playable" : "summary"}`}>
                    {replay.playbackAvailable ? "可播放" : "仅摘要"}
                  </span>
                </div>

                <p className="replay-card__players" aria-label="玩家排名摘要">{getRankedPlayerSummary(replay)}</p>
              </div>

              <div className="replay-card__actions">
                <button type="button" className="replay-feedback replay-feedback--proposal" onClick={(event) => openFeedback(event, replay, "proposal")}>
                  提议
                </button>
                <button type="button" className="replay-feedback replay-feedback--report" onClick={(event) => openFeedback(event, replay, "report")}>
                  举报
                </button>
              </div>
            </Link>
          ))}
        </section>
      )}

      {feedbackModal ? (
        <div className="replay-modal" role="dialog" aria-modal="true" aria-label={feedbackModal.kind === "proposal" ? "提交提议" : "提交举报"}>
          <div className="replay-modal__panel">
            <button type="button" className="replay-modal__close" onClick={closeFeedback} aria-label="关闭">
              x
            </button>
            <h3>{feedbackModal.kind === "proposal" ? "提议" : "举报"}</h3>
            <p>{feedbackModal.replay.title}</p>
            <textarea
              value={feedbackBody}
              onChange={(event) => setFeedbackBody(event.target.value)}
              placeholder={feedbackModal.kind === "proposal" ? "写下你对这条回放的建议" : "说明需要举报的问题"}
            />
            {sentMessage ? <span className="replay-modal__thanks">{sentMessage}</span> : null}
            <button type="button" className="button-link button-link--primary" onClick={sendFeedback} disabled={!feedbackBody.trim()}>
              Send
            </button>
          </div>
        </div>
      ) : null}
    </ShellLayout>
  );
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

function getRankedPlayerSummary(replay: ReplaySummary): string {
  const names = parseReplayPlayersLine(replay.playersLine);
  return names.length > 0 ? names.slice(0, 5).map((player, index) => `#${index + 1} ${player}`).join(" · ") : "暂无玩家排序";
}

function getReplayTags(replay: ReplaySummary): string[] {
  return Array.from(new Set([replay.mapLabel, replay.resultLabel].filter(Boolean)));
}

function getReplayTimestamp(id: string): number {
  const match = id.match(/(\d{10,})/);
  return match ? Number(match[1]) : 0;
}
