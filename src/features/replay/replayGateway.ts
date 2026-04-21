import { getReplayEntries } from "../battle/local/battleTruthStore";
import { getLocalReplayPlaybackById, loadLocalReplayPlaybackById, saveLocalReplayPlayback } from "./localReplayStore";
import { hasMeaningfulReplayFrames as hasMeaningfulReplayFrameList, hasReplayFrameVisualDelta } from "./replayRecorder";
import { loadReplayCatalog as loadReplayCatalogFromBackend, loadReplayPlayback as loadReplayPlaybackFromBackend } from "./replayApi";
import type { ReplayExportArtifact, ReplayFrame, ReplayHeroFrame, ReplayPlayback } from "./replayTypes";

export interface ReplaySummary {
  id: string;
  title: string;
  modeLabel: string;
  resultLabel: string;
  highlights: string;
  highlightLine: string;
  mapLabel: string;
  coverLabel: string;
  playersLine: string;
  timelineHint: string;
  finishedAtLabel: string;
  thumbnailDataUrl: string | null;
  score: number;
  placement: number | null;
  ratingBefore: number | null;
  ratingAfter: number | null;
  ratingDelta: number | null;
  durationMs: number;
  aliveAtEnd: boolean;
  frameCount: number;
  playbackAvailable: boolean;
}

export interface ReplayTimelineMoment {
  timeLabel: string;
  title: string;
  detail: string;
  tone: "neutral" | "success" | "warning" | "danger";
}

export interface ReplayRosterRow {
  heroId: string;
  displayName: string;
  placementLabel: string;
  scoreLabel: string;
  scoreValue: number;
  hpLabel: string;
  weaponLabel: string;
  statusLabel: string;
  eliminatedAtLabel: string | null;
  alive: boolean;
}

export interface ReplayRoomInsights {
  modeLabel: string;
  modeDescription: string;
  frameCount: number;
  frameCountLabel: string;
  statusLabel: string;
  summaryLine: string;
  timelineHint: string;
  timeline: ReplayTimelineMoment[];
  roster: ReplayRosterRow[];
}

export function hasMeaningfulReplayFrames(replay: ReplayPlayback | ReplayFrame[] | null | undefined): boolean {
  if (!replay) {
    return false;
  }

  const frames = Array.isArray(replay) ? replay : replay.frames;
  return hasMeaningfulReplayFrameList(frames);
}

export function parseReplayPlayersLine(playersLine: string): string[] {
  return playersLine
    .split(/[·|,，/]+|\s路\s|\s-\s/g)
    .map((name) => name.trim())
    .filter(Boolean);
}

export function getReplaySummaries(): ReplaySummary[] {
  return getReplayEntries().map((entry) => ({
    ...entry,
    highlights: entry.highlightLine
  }));
}

export async function loadReplaySummaries(): Promise<ReplaySummary[] | null> {
  const local = getReplaySummaries();
  const remote = await loadReplayCatalogFromBackend();
  if (!remote || remote.length === 0) {
    return local.length > 0 ? local : null;
  }

  const remoteSummaries = remote.map((entry) => ({
    id: entry.id,
    title: entry.title,
    modeLabel: entry.modeLabel,
    resultLabel: entry.resultLabel,
    highlights: entry.highlightLine,
    highlightLine: entry.highlightLine,
    mapLabel: entry.mapLabel,
    coverLabel: entry.coverLabel,
    playersLine: entry.playersLine,
    timelineHint: entry.timelineHint,
    finishedAtLabel: entry.finishedAtLabel,
    thumbnailDataUrl: entry.thumbnailDataUrl,
    score: entry.score,
    placement: entry.placement,
    ratingBefore: null,
    ratingAfter: null,
    ratingDelta: null,
    durationMs: entry.durationMs,
    aliveAtEnd: entry.aliveAtEnd,
    frameCount: entry.frameCount,
    playbackAvailable: entry.playbackAvailable && entry.frameCount >= 2
  }));

  return mergeReplaySummaries(local, remoteSummaries);
}

export function getReplaySummaryById(id: string): ReplaySummary | undefined {
  const entry = getReplayEntries().find((replay) => replay.id === id);
  return entry
    ? {
        ...entry,
        highlights: entry.highlightLine
      }
    : undefined;
}

export function getReplayPlayback(id: string): ReplayPlayback | undefined {
  const playback = getLocalReplayPlaybackById(id);
  return playback ? normalizeReplayPlaybackRatings(playback) : undefined;
}

export async function loadReplayPlaybackById(id: string): Promise<ReplayPlayback | undefined> {
  const local = await loadLocalReplayPlaybackById(id);
  if (local) {
    return normalizeReplayPlaybackRatings(local);
  }

  const remote = await loadReplayPlaybackFromBackend(id);
  if (!remote) {
    return local;
  }

  const playback: ReplayPlayback = {
    id: remote.id,
    title: remote.title,
    modeLabel: remote.modeLabel,
    resultLabel: remote.resultLabel,
    finishedAtLabel: remote.finishedAtLabel,
    mapLabel: remote.mapLabel,
    highlightLine: remote.highlightLine,
    timelineHint: remote.timelineHint,
    playersLine: remote.playersLine,
    score: remote.score,
    placement: remote.placement,
    ratingBefore: null,
    ratingAfter: null,
    ratingDelta: null,
    durationMs: remote.durationMs,
    aliveAtEnd: remote.aliveAtEnd,
    thumbnailDataUrl: remote.thumbnailDataUrl,
    frames: remote.frames
  };

  saveLocalReplayPlayback(playback);
  return playback;
}

function normalizeReplayPlaybackRatings(playback: ReplayPlayback): ReplayPlayback {
  const legacyPlayback = playback as ReplayPlayback & Partial<Pick<ReplayPlayback, "ratingBefore" | "ratingAfter" | "ratingDelta">>;
  return {
    ...playback,
    ratingBefore: legacyPlayback.ratingBefore ?? null,
    ratingAfter: legacyPlayback.ratingAfter ?? null,
    ratingDelta: legacyPlayback.ratingDelta ?? null
  };
}

export function getReplayDisplayFrames(replay: ReplayPlayback): ReplayFrame[] {
  return normalizeReplayFramesForDisplay(replay.frames, replay);
}

function normalizeReplayFramesForDisplay(
  frames: ReplayFrame[],
  replay?: Pick<ReplayPlayback, "aliveAtEnd" | "placement" | "playersLine" | "resultLabel">
): ReplayFrame[] {
  if (frames.length === 0) {
    return [];
  }

  const startIndex = findReplayVisualStartIndex(frames);
  const trimmedFrames = frames.slice(startIndex);
  if (trimmedFrames.length === 0) {
    return [];
  }

  const normalizedFrames: ReplayFrame[] = [cloneReplayFrame(trimmedFrames[0], 0)];
  let elapsedMs = 0;

  for (let index = 1; index < trimmedFrames.length; index += 1) {
    const previousFrame = trimmedFrames[index - 1];
    const currentFrame = trimmedFrames[index];
    elapsedMs += compressReplayGap(currentFrame.elapsedMs - previousFrame.elapsedMs, trimmedFrames.length, previousFrame, currentFrame);
    normalizedFrames.push(cloneReplayFrame(currentFrame, elapsedMs));
  }

  if (replay) {
    normalizedFrames[normalizedFrames.length - 1] = reconcileReplayFinalFrame(replay, normalizedFrames[normalizedFrames.length - 1]);
  }

  return normalizedFrames;
}

function findReplayVisualStartIndex(frames: ReplayFrame[]): number {
  const firstFrame = frames[0];
  const firstChangedIndex = frames.findIndex((frame, index) => index > 0 && hasReplayFrameVisualDelta(firstFrame, frame));

  if (firstChangedIndex <= 0) {
    return 0;
  }

  const leadingStaticMs = Math.max(0, frames[firstChangedIndex].elapsedMs - firstFrame.elapsedMs);
  if (leadingStaticMs < 2500) {
    return 0;
  }

  let startIndex = firstChangedIndex;
  while (startIndex > 0 && frames[firstChangedIndex].elapsedMs - frames[startIndex - 1].elapsedMs <= 600) {
    startIndex -= 1;
  }

  return startIndex;
}

function compressReplayGap(rawGapMs: number, frameCount: number, previousFrame: ReplayFrame, currentFrame: ReplayFrame): number {
  if (rawGapMs <= 0) {
    return 0;
  }

  const sparseReplay = frameCount <= 8;
  const visualDelta = hasReplayFrameVisualDelta(previousFrame, currentFrame);
  const gapCap = visualDelta ? (sparseReplay ? 900 : 1200) : 420;
  const scaledGap = Math.round(rawGapMs * (sparseReplay ? 0.2 : 0.28));
  const minimumGap = visualDelta ? 180 : 100;

  return Math.max(0, Math.min(rawGapMs, gapCap, Math.max(minimumGap, scaledGap)));
}

function reconcileReplayFinalFrame(
  replay: Pick<ReplayPlayback, "aliveAtEnd" | "placement" | "playersLine" | "resultLabel">,
  frame: ReplayFrame
): ReplayFrame {
  if (!shouldReconcileReplayFinalState(replay, frame)) {
    return frame;
  }

  const winnerName = resolveReplayFinalWinnerName(replay, frame);
  if (!winnerName) {
    return frame;
  }

  const winnerKey = normalizeReplayName(winnerName);
  const winnerExists = frame.heroes.some((hero) => normalizeReplayName(hero.displayName) === winnerKey);
  if (!winnerExists) {
    return frame;
  }

  return {
    ...frame,
    heroes: frame.heroes.map((hero) => {
      if (normalizeReplayName(hero.displayName) === winnerKey) {
        return {
          ...hero,
          alive: true,
          hp: Math.max(1, hero.hp),
          eliminatedAtMs: null
        };
      }

      if (!hero.alive && hero.hp <= 0) {
        return hero;
      }

      return {
        ...hero,
        alive: false,
        hp: 0,
        eliminatedAtMs: hero.eliminatedAtMs ?? frame.elapsedMs
      };
    })
  };
}

function shouldReconcileReplayFinalState(replay: Pick<ReplayPlayback, "aliveAtEnd" | "placement" | "resultLabel">, frame: ReplayFrame): boolean {
  if (frame.heroes.filter((hero) => hero.alive).length <= 1) {
    return false;
  }

  const resultLabel = replay.resultLabel.toLowerCase();
  return replay.placement === 1 || resultLabel.includes("last") || resultLabel.includes("survivor");
}

function resolveReplayFinalWinnerName(replay: Pick<ReplayPlayback, "playersLine">, frame: ReplayFrame): string | null {
  const recordedRank = parseReplayPlayersLine(replay.playersLine);
  for (const name of recordedRank) {
    const matchedHero = frame.heroes.find((hero) => normalizeReplayName(hero.displayName) === normalizeReplayName(name));
    if (matchedHero) {
      return matchedHero.displayName;
    }
  }

  const aliveHero = [...frame.heroes].filter((hero) => hero.alive).sort(compareFrameHeroRank)[0];
  if (aliveHero) {
    return aliveHero.displayName;
  }

  return [...frame.heroes].sort(compareFrameHeroRank)[0]?.displayName ?? null;
}

function cloneReplayFrame(frame: ReplayFrame, elapsedMs: number): ReplayFrame {
  return {
    ...frame,
    elapsedMs,
    worldSize: { ...frame.worldSize },
    heroes: frame.heroes.map((hero) => ({
      ...hero,
      position: { ...hero.position }
    })),
    projectiles: frame.projectiles.map((projectile) => ({
      ...projectile,
      position: { ...projectile.position }
    })),
    pickups: frame.pickups.map((pickup) => ({
      ...pickup,
      position: { ...pickup.position }
    })),
    eventMessages: [...frame.eventMessages]
  };
}

export function buildReplayRoomInsights(replay: ReplayPlayback): ReplayRoomInsights {
  const frameCount = replay.frames.length;
  const hasFrames = frameCount > 0;
  const displayFrames = getReplayDisplayFrames(replay);
  const playableReplay = hasMeaningfulReplayFrames(displayFrames);
  const modeLabel = playableReplay ? (frameCount >= 4 ? "完整回放" : "关键帧回放") : hasFrames ? "单帧预览" : "摘要归档";
  const modeDescription = playableReplay
    ? frameCount >= 4
      ? "具备逐帧时间线，可以拖动查看整局过程。"
      : "只有关键帧时间线，但仍可回看结算、关键节点和结果。"
    : hasFrames
      ? "已保存单帧预览，可先查看结算和终局画面。"
      : "当前只有战报摘要，没有可播放帧。";

  return {
    modeLabel,
    modeDescription,
    frameCount,
    frameCountLabel: frameCount > 0 ? `${frameCount} 帧` : "无帧",
    statusLabel: playableReplay ? (frameCount >= 4 ? "可拖动播放" : "可播放关键帧") : hasFrames ? "单帧预览" : "仅战报摘要",
    summaryLine: replay.highlightLine,
    timelineHint: replay.timelineHint,
    timeline: buildReplayTimeline(replay, displayFrames),
    roster: buildReplayRoster(replay, displayFrames)
  };
}

export function buildReplayExportArtifact(id: string): ReplayExportArtifact | undefined {
  const playback = getReplayPlayback(id);
  const summary = getReplaySummaryById(id);
  const source = playback ?? summary;

  if (!source) {
    return undefined;
  }

  const exportPayload = {
    schema: "slay-demo.replay-export.v1",
    exportedAt: new Date().toISOString(),
    replay: {
      id: source.id,
      title: source.title,
      modeLabel: source.modeLabel,
      resultLabel: source.resultLabel,
      finishedAtLabel: source.finishedAtLabel,
      mapLabel: source.mapLabel,
      highlightLine: source.highlightLine,
      timelineHint: source.timelineHint,
      playersLine: source.playersLine,
      score: source.score,
      placement: source.placement,
      ratingBefore: source.ratingBefore,
      ratingAfter: source.ratingAfter,
      ratingDelta: source.ratingDelta,
      durationMs: source.durationMs,
      aliveAtEnd: source.aliveAtEnd,
      thumbnailDataUrl: source.thumbnailDataUrl,
      frames: playback?.frames ?? []
    }
  };

  return {
    filename: `${slugifyReplayTitle(source.title || source.id)}.json`,
    json: JSON.stringify(exportPayload, null, 2)
  };
}

export function buildReplayExportArtifactFromPlayback(playback: ReplayPlayback): ReplayExportArtifact | undefined {
  if (!playback.frames.length) {
    return undefined;
  }

  return {
    filename: `${slugifyReplayTitle(playback.title || playback.id)}.json`,
    json: JSON.stringify(
      {
        schema: "slay-demo.replay-export.v1",
        exportedAt: new Date().toISOString(),
        replay: {
          id: playback.id,
          title: playback.title,
          modeLabel: playback.modeLabel,
          resultLabel: playback.resultLabel,
          finishedAtLabel: playback.finishedAtLabel,
          mapLabel: playback.mapLabel,
          highlightLine: playback.highlightLine,
          timelineHint: playback.timelineHint,
          playersLine: playback.playersLine,
          score: playback.score,
          placement: playback.placement,
          ratingBefore: playback.ratingBefore,
          ratingAfter: playback.ratingAfter,
          ratingDelta: playback.ratingDelta,
          durationMs: playback.durationMs,
          aliveAtEnd: playback.aliveAtEnd,
          thumbnailDataUrl: playback.thumbnailDataUrl,
          frames: playback.frames
        }
      },
      null,
      2
    )
  };
}

function buildReplayTimeline(replay: ReplayPlayback, frames: ReplayFrame[]): ReplayTimelineMoment[] {
  if (!hasMeaningfulReplayFrames(frames) || frames.length === 0) {
    return [
      {
        timeLabel: replay.finishedAtLabel,
        title: "摘要结算",
        detail: replay.highlightLine,
        tone: "warning"
      },
      {
        timeLabel: replay.finishedAtLabel,
        title: "战报说明",
        detail: replay.timelineHint,
        tone: "neutral"
      }
    ];
  }

  const moments: ReplayTimelineMoment[] = [];
  const firstFrame = frames[0];
  moments.push({
    timeLabel: formatClock(firstFrame.elapsedMs),
    title: "开局",
    detail: `${firstFrame.heroes.length} 名玩家进入战局`,
    tone: "neutral"
  });

  const middleFrame = frames[Math.floor(frames.length / 2)];
  if (middleFrame && middleFrame !== firstFrame) {
    moments.push(buildProgressMoment(middleFrame));
  }

  moments.push(...buildEliminationMoments(frames).slice(0, 2));

  const finalFrame = frames[frames.length - 1];
  moments.push({
    timeLabel: formatClock(finalFrame.elapsedMs),
    title: "结算",
    detail: replay.highlightLine || replay.resultLabel,
    tone: "success"
  });

  return dedupeReplayMoments(moments).slice(0, 8);
}

function mergeReplaySummaries(local: ReplaySummary[], remote: ReplaySummary[]): ReplaySummary[] {
  const byId = new Map<string, ReplaySummary>();
  local.forEach((summary) => byId.set(summary.id, summary));

  remote.forEach((summary) => {
    const existing = byId.get(summary.id);
    if (!existing || (!existing.playbackAvailable && summary.playbackAvailable) || (summary.frameCount > existing.frameCount && !existing.playbackAvailable)) {
      byId.set(summary.id, mergeReplaySummaryRatings(summary, existing));
    }
  });

  return Array.from(byId.values());
}

function mergeReplaySummaryRatings(summary: ReplaySummary, fallback: ReplaySummary | undefined): ReplaySummary {
  return {
    ...summary,
    ratingBefore: summary.ratingBefore ?? fallback?.ratingBefore ?? null,
    ratingAfter: summary.ratingAfter ?? fallback?.ratingAfter ?? null,
    ratingDelta: summary.ratingDelta ?? fallback?.ratingDelta ?? null
  };
}

function buildProgressMoment(frame: ReplayFrame): ReplayTimelineMoment {
  const leader = getLeadingHero(frame.heroes);
  const aliveCount = frame.heroes.filter((hero) => hero.alive).length;
  const eventMessage = lastNonEmptyMessage(frame.eventMessages);

  return {
    timeLabel: formatClock(frame.elapsedMs),
    title: eventMessage ? "战况更新" : "局势推进",
    detail: eventMessage ?? (leader ? `${leader.displayName} 领先 ${Math.round(leader.score)} 分，仍有 ${aliveCount} 人存活` : `${aliveCount} 人仍在场中`),
    tone: eventMessage ? "warning" : "neutral"
  };
}

function buildEliminationMoments(frames: ReplayFrame[]): ReplayTimelineMoment[] {
  const moments: ReplayTimelineMoment[] = [];
  const seenHeroes = new Set<string>();

  for (let index = 1; index < frames.length; index += 1) {
    const previous = frames[index - 1];
    const current = frames[index];
    current.heroes.forEach((hero) => {
      if (seenHeroes.has(hero.heroId)) {
        return;
      }

      const previousHero = previous.heroes.find((candidate) => candidate.heroId === hero.heroId);
      if (previousHero?.alive && !hero.alive) {
        seenHeroes.add(hero.heroId);
        moments.push({
          timeLabel: formatClock(current.elapsedMs),
          title: `${hero.displayName} 出局`,
          detail: `${hero.displayName} 在这一刻失去战斗资格。`,
          tone: "danger"
        });
      }
    });
  }

  return moments;
}

function buildReplayRoster(replay: ReplayPlayback, frames: ReplayFrame[]): ReplayRosterRow[] {
  const finalFrame = frames[frames.length - 1];
  if (!finalFrame) {
    return [];
  }

  const recordedRank = buildRecordedRankMap(replay.playersLine);
  const reconciledFinalFrame = reconcileReplayFinalFrame(replay, finalFrame);
  const preferRecordedRank = shouldPreferRecordedReplayRank(replay, reconciledFinalFrame, recordedRank);
  const heroes = [...reconciledFinalFrame.heroes].sort((left, right) => {
    if (preferRecordedRank) {
      const recordedOrder = compareRecordedReplayRank(left, right, recordedRank);
      if (recordedOrder !== 0) {
        return recordedOrder;
      }
    }

    return compareFrameHeroRank(left, right);
  });

  return heroes.map((hero, index) => ({
    heroId: hero.heroId,
    displayName: hero.displayName,
    placementLabel: `#${index + 1}`,
    scoreLabel: `积分 ${Math.round(hero.score)}`,
    scoreValue: Math.round(hero.score),
    hpLabel: `${Math.max(0, Math.round(hero.hp))}/${Math.max(1, Math.round(hero.maxHp))} HP`,
    weaponLabel: hero.currentWeaponKind ? humanizeLabel(hero.currentWeaponKind) : "空手",
    statusLabel: preferRecordedRank ? "按结算排名" : hero.alive ? "存活" : "出局",
    eliminatedAtLabel: hero.alive ? null : hero.eliminatedAtMs != null ? `出局于 ${formatClock(hero.eliminatedAtMs)}` : "已出局",
    alive: hero.alive
  }));
}

function buildRecordedRankMap(playersLine: string): Map<string, number> {
  const rank = new Map<string, number>();
  parseReplayPlayersLine(playersLine).forEach((name, index) => {
    const key = normalizeReplayName(name);
    if (!rank.has(key)) {
      rank.set(key, index);
    }
  });
  return rank;
}

function shouldPreferRecordedReplayRank(replay: ReplayPlayback, finalFrame: ReplayFrame, recordedRank: Map<string, number>): boolean {
  const resultLabel = replay.resultLabel.toLowerCase();
  const lastSurvivorResult = resultLabel.includes("最后幸存") || resultLabel.includes("last") || resultLabel.includes("survivor");
  return recordedRank.size > 1 && finalFrame.heroes.filter((hero) => hero.alive).length > 1 && (lastSurvivorResult || replay.placement !== null);
}

function compareRecordedReplayRank(left: ReplayHeroFrame, right: ReplayHeroFrame, recordedRank: Map<string, number>): number {
  const leftRank = recordedRank.get(normalizeReplayName(left.displayName));
  const rightRank = recordedRank.get(normalizeReplayName(right.displayName));

  if (leftRank != null && rightRank != null && leftRank !== rightRank) {
    return leftRank - rightRank;
  }
  if (leftRank != null) {
    return -1;
  }
  if (rightRank != null) {
    return 1;
  }
  return 0;
}

function compareFrameHeroRank(left: ReplayHeroFrame, right: ReplayHeroFrame): number {
  if (left.alive !== right.alive) {
    return left.alive ? -1 : 1;
  }

  if (!left.alive && !right.alive && left.eliminatedAtMs !== right.eliminatedAtMs) {
    return (right.eliminatedAtMs ?? -1) - (left.eliminatedAtMs ?? -1);
  }

  return right.score - left.score || right.hp - left.hp || left.displayName.localeCompare(right.displayName, "zh-Hans-CN");
}

function normalizeReplayName(name: string): string {
  return name.trim().toLowerCase();
}

function getLeadingHero(heroes: ReplayHeroFrame[]): ReplayHeroFrame | undefined {
  return [...heroes].sort((left, right) => right.score - left.score || Number(right.alive) - Number(left.alive))[0];
}

function lastNonEmptyMessage(messages: string[]): string | null {
  for (let index = messages.length - 1; index >= 0; index -= 1) {
    const message = messages[index]?.trim();
    if (message) {
      return message;
    }
  }

  return null;
}

function dedupeReplayMoments(moments: ReplayTimelineMoment[]): ReplayTimelineMoment[] {
  const seen = new Set<string>();
  return moments.filter((moment) => {
    const key = `${moment.timeLabel}|${moment.title}|${moment.detail}`;
    if (seen.has(key)) {
      return false;
    }

    seen.add(key);
    return true;
  });
}

function humanizeLabel(value: string): string {
  return value.replace(/[_-]+/g, " ").replace(/([a-z])([A-Z])/g, "$1 $2");
}

function formatClock(elapsedMs: number): string {
  const totalSeconds = Math.max(0, Math.floor(elapsedMs / 1000));
  const minutes = Math.floor(totalSeconds / 60)
    .toString()
    .padStart(2, "0");
  const seconds = (totalSeconds % 60).toString().padStart(2, "0");
  return `${minutes}:${seconds}`;
}

function slugifyReplayTitle(value: string): string {
  return value
    .trim()
    .toLowerCase()
    .replace(/[\s/]+/g, "-")
    .replace(/[^a-z0-9\u4e00-\u9fa5_-]/g, "")
    .replace(/-+/g, "-")
    .replace(/^-|-$/g, "");
}
