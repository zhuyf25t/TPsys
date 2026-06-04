import { getLocalReplayPlaybackById, loadLocalReplayPlaybackById, saveLocalReplayPlayback } from "./local/localReplayStore";
import {
  REPLAY_PERSIST_FRAME_LIMIT,
  compactReplayFrames,
  hasMeaningfulReplayFrames as hasMeaningfulReplayFrameList,
  normalizeReplayFramesForPlayback
} from "../../objects/replay/replayRecorder";
import {
  loadReplayCatalog as loadReplayCatalogFromBackend,
  loadReplayPlayback as loadReplayPlaybackFromBackend,
  type ReplayBackendPlaybackItem
} from "./replayApi";
import type { ReplayExportArtifact, ReplayFrame, ReplayHeroFrame, ReplayPlayback } from "../../objects/replay/replayTypes";
import { getCurrentAuthUser } from "../identity/authGateway";
import { getReplayEntries, getReplayEntryById, isReplayEntryBackendSyncDisabled } from "../../runtime/battle/local/state/battleTruthStore";
import {
  loadBattleResultByBattleId,
  type BackendBattleResultRecord
} from "../battle/microservices/results/api/BattleResultsApi";
import { normalizePlayableIdentityHandle, normalizePlayerHandleKey } from "../../objects/identity/identityHandlePolicy";

const DISPLAY_FRAME_LIMIT = REPLAY_PERSIST_FRAME_LIMIT;
const DISPLAY_POSITION_EPSILON = 4;
const DISPLAY_PROJECTILE_POSITION_EPSILON = 8;
const DISPLAY_FACING_EPSILON = 0.04;
let cachedRemoteReplaySummaries: ReplaySummary[] | null = null;
const replayRatingHydrationCache = new Map<string, Promise<BackendBattleResultRecord | null>>();

export interface ReplaySummary {
  id: string;
  localBackendSyncDisabled?: boolean;
  title: string;
  modeLabel: string;
  resultLabel: string;
  highlights: string;
  highlightLine: string;
  mapLabel: string;
  coverLabel: string;
  playersLine: string;
  timelineHint: string;
  finishedAt: number;
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

interface LoadReplayPlaybackOptions {
  ratingHandle?: string | null;
}

/** 中文名：判断是否有meaningful回放frames（hasMeaningfulReplayFrames）。游戏职责：在前端回放域中组织回放帧、时间线和导出数据，复现战斗过程。 */
export function hasMeaningfulReplayFrames(replay: ReplayPlayback | ReplayFrame[] | null | undefined): boolean {
  if (!replay) {
    return false;
  }

  const frames = Array.isArray(replay) ? replay : replay.frames;
  return hasMeaningfulReplayFrameList(frames);
}

/** 中文名：解析回放players文本行（parseReplayPlayersLine）。游戏职责：在前端回放域中组织回放帧、时间线和导出数据，复现战斗过程。 */
export function parseReplayPlayersLine(playersLine: string): string[] {
  if (!playersLine.trim()) {
    return [];
  }

  return playersLine
    .replace(/\s+路\s+/g, " | ")
    .replace(/[，,、]/g, " | ")
    .replace(/\s*[-–—]\s*/g, " | ")
    .split(/\s*\|\s*/g)
    .map((name) => name.trim())
    .filter(Boolean);
}

/** 中文名：获取回放summaries（getReplaySummaries）。游戏职责：在前端回放域中组织回放帧、时间线和导出数据，复现战斗过程。 */
export function getReplaySummaries(): ReplaySummary[] {
  return mergeReplaySummaries(getReplayEntries().map(toReplaySummary), cachedRemoteReplaySummaries ?? []);
}

export async function loadReplaySummaries(): Promise<ReplaySummary[] | null> {
  const localSummaries = getReplaySummaries();
  if (localSummaries.some((summary) => summary.localBackendSyncDisabled)) {
    return refreshRemoteReplaySummaries(localSummaries);
  }

  if (localSummaries.length > 0) {
    void refreshRemoteReplaySummaries(localSummaries);
    return localSummaries;
  }

  return refreshRemoteReplaySummaries(localSummaries);
}

async function refreshRemoteReplaySummaries(localSummaries: ReplaySummary[]): Promise<ReplaySummary[] | null> {
  const remoteCatalog = await loadReplayCatalogFromBackend().catch(() => null);
  if (remoteCatalog === null) {
    return localSummaries;
  }

  const remoteSummaries = remoteCatalog.map((entry) => toReplaySummary({ ...entry, id: entry.replayId }));
  const merged = mergeReplaySummaries(localSummaries, remoteSummaries);
  cachedRemoteReplaySummaries = remoteSummaries;
  return merged;
}

/** 中文名：获取回放摘要by标识（getReplaySummaryById）。游戏职责：在前端回放域中组织回放帧、时间线和导出数据，复现战斗过程。 */
export function getReplaySummaryById(id: string): ReplaySummary | undefined {
  const normalizedId = id.trim();
  if (!normalizedId) {
    return undefined;
  }

  const localEntry = getReplayEntryById(normalizedId);
  const localSummary = localEntry ? toReplaySummary(localEntry) : undefined;
  const remoteSummary = cachedRemoteReplaySummaries?.find((summary) => normalizeReplaySummaryId(summary.id) === normalizedId);
  return remoteSummary ? choosePreferredReplaySummary(localSummary, remoteSummary) : localSummary;
}

/** 中文名：获取回放playback（getReplayPlayback）。游戏职责：在前端回放域中组织回放帧、时间线和导出数据，复现战斗过程。 */
export function getReplayPlayback(id: string): ReplayPlayback | undefined {
  const playback = getLocalReplayPlaybackById(id);
  return playback ? normalizeReplayPlaybackRatings(playback) : undefined;
}

export async function loadReplayPlaybackById(
  id: string,
  options: LoadReplayPlaybackOptions = {}
): Promise<ReplayPlayback | undefined> {
  const normalizedId = id.trim();
  if (!normalizedId) {
    return undefined;
  }

  const localPlayback = getLocalReplayPlaybackById(normalizedId);
  const localIsDisabledSyncFallback = isReplayEntryBackendSyncDisabled(normalizedId);
  if (localPlayback && hasMeaningfulReplayFrameList(localPlayback.frames) && !localIsDisabledSyncFallback) {
    return normalizeReplayPlaybackRatings(localPlayback);
  }

  const remote = await loadReplayPlaybackFromBackend(normalizedId, { ratingHandle: options.ratingHandle }).catch(() => undefined);
  if (remote) {
    const playback = await toReplayPlaybackFromBackend(remote, options);
    if (remote.playbackAvailable && hasMeaningfulReplayFrameList(playback.frames)) {
      saveLocalReplayPlayback(playback);
    }
    return playback;
  }

  if (localPlayback) {
    return normalizeReplayPlaybackRatings(localPlayback);
  }

  const local = await loadLocalReplayPlaybackById(normalizedId).catch(() => undefined);
  return local ? normalizeReplayPlaybackRatings(local) : undefined;
}

async function toReplayPlaybackFromBackend(
  remote: ReplayBackendPlaybackItem,
  options: LoadReplayPlaybackOptions = {}
): Promise<ReplayPlayback> {
  const playback = normalizeReplayPlaybackRatings({
    id: remote.replayId,
    playbackAvailable: remote.playbackAvailable,
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
    ratingBefore: remote.ratingBefore ?? null,
    ratingAfter: remote.ratingAfter ?? null,
    ratingDelta: remote.ratingDelta ?? null,
    durationMs: remote.durationMs,
    aliveAtEnd: remote.aliveAtEnd,
    thumbnailDataUrl: remote.thumbnailDataUrl,
    frames: remote.frames
  });

  return hydrateReplayRatingsFromBattleResult(playback, remote, options);
}

async function hydrateReplayRatingsFromBattleResult(
  playback: ReplayPlayback,
  remote: ReplayBackendPlaybackItem,
  options: LoadReplayPlaybackOptions
): Promise<ReplayPlayback> {
  const requestedHandle = normalizePlayableIdentityHandle(options.ratingHandle);
  const authHandle = normalizePlayableIdentityHandle(getCurrentAuthUser()?.handle);
  const remoteHandle = normalizePlayableIdentityHandle(remote.handle);
  const shouldHydrateForOwner =
    Boolean(requestedHandle) ||
    Boolean(authHandle && normalizeHandleKey(authHandle) !== normalizeHandleKey(remoteHandle));

  if (hasReplayPlaybackRatings(playback) && !shouldHydrateForOwner) {
    return playback;
  }

  const battleId = remote.battleId.trim();
  if (!battleId) {
    return playback;
  }

  const handle = resolveReplayRatingHandle(remote, requestedHandle, authHandle);
  const cacheKey = `${battleId}|${handle ?? ""}`;
  const resultPromise =
    replayRatingHydrationCache.get(cacheKey) ??
    loadBattleResultByBattleId(battleId, handle).catch(() => null);
  replayRatingHydrationCache.set(cacheKey, resultPromise);

  const result = await resultPromise;
  if (!result) {
    return shouldHydrateForOwner
      ? {
          ...playback,
          ratingBefore: null,
          ratingAfter: null,
          ratingDelta: null
        }
      : playback;
  }

  return {
    ...playback,
    ratingBefore: result.ratingBefore,
    ratingAfter: result.ratingAfter,
    ratingDelta: result.ratingDelta
  };
}

function resolveReplayRatingHandle(
  remote: ReplayBackendPlaybackItem,
  requestedHandle: string | null,
  authHandle: string | null
): string | null {
  if (requestedHandle) {
    return requestedHandle;
  }

  if (authHandle) {
    return authHandle;
  }

  const replayHandle = normalizePlayableIdentityHandle(remote.handle);
  if (replayHandle) {
    return replayHandle;
  }

  const displayName = normalizePlayableIdentityHandle(remote.displayName);
  if (displayName) {
    return displayName;
  }

  return null;
}

function normalizeHandleKey(handle: string | null): string {
  return normalizePlayerHandleKey(handle);
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

function hasReplayPlaybackRatings(
  playback: ReplayPlayback
): playback is ReplayPlayback & { ratingBefore: number; ratingAfter: number; ratingDelta: number } {
  return typeof playback.ratingBefore === "number" && typeof playback.ratingAfter === "number" && typeof playback.ratingDelta === "number";
}

/** 中文名：获取回放展示frames（getReplayDisplayFrames）。游戏职责：在前端回放域中组织回放帧、时间线和导出数据，复现战斗过程。 */
export function getReplayDisplayFrames(replay: ReplayPlayback): ReplayFrame[] {
  return normalizeReplayFramesForDisplay(replay.frames);
}

/** 中文名：构建回放live时间线（buildReplayLiveTimeline）。游戏职责：在前端回放域中组织回放帧、时间线和导出数据，复现战斗过程。 */
export function buildReplayLiveTimeline(frames: ReplayFrame[], playheadMs: number): ReplayTimelineMoment[] {
  if (frames.length === 0) {
    return [];
  }

  const activeIndex = findReplayLiveTimelineIndex(frames, playheadMs);
  if (activeIndex < 0) {
    return [];
  }

  const moments: ReplayTimelineMoment[] = [];
  const seen = new Set<string>();
  for (let index = activeIndex; index >= 0 && moments.length < 5; index -= 1) {
    const currentFrame = frames[index];
    const previousFrame = index > 0 ? frames[index - 1] : undefined;
    const moment = buildReplayLiveTimelineMoment(previousFrame, currentFrame);
    if (!moment) {
      continue;
    }

    const dedupeKey = `${moment.timeLabel}|${moment.title}|${moment.detail}`;
    if (seen.has(dedupeKey)) {
      continue;
    }

    seen.add(dedupeKey);
    moments.unshift(moment);
  }

  return moments;
}

function normalizeReplayFramesForDisplay(frames: ReplayFrame[]): ReplayFrame[] {
  if (frames.length === 0) {
    return [];
  }

  const displayFrames = normalizeReplayDisplayFrames(frames, DISPLAY_FRAME_LIMIT);
  if (displayFrames.length === 0) {
    return [];
  }

  return displayFrames;
}

function normalizeReplayDisplayFrames(frames: ReplayFrame[], limit: number): ReplayFrame[] {
  const compactFrames = compactReplayFrames(frames, limit);
  if (compactFrames.length === 0) {
    return [];
  }

  const signalFrames = collapseReplayDisplayFrames(compactFrames);
  return normalizeReplayFramesForPlayback(signalFrames, limit).map((frame) => cloneReplayFrame(frame));
}

function collapseReplayDisplayFrames(frames: ReplayFrame[]): ReplayFrame[] {
  if (frames.length === 0) {
    return [];
  }

  const collapsedFrames: ReplayFrame[] = [cloneReplayFrame(frames[0])];

  for (let index = 1; index < frames.length; index += 1) {
    const previousFrame = collapsedFrames[collapsedFrames.length - 1];
    const currentFrame = frames[index];
    if (!hasReplayDisplaySignalChange(previousFrame, currentFrame)) {
      continue;
    }

    collapsedFrames.push(cloneReplayFrame(currentFrame));
  }

  return collapsedFrames;
}

function getReplayDistance(left: { x: number; y: number }, right: { x: number; y: number }): number {
  return Math.hypot(left.x - right.x, left.y - right.y);
}

function cloneReplayFrame(frame: ReplayFrame, elapsedMs = frame.elapsedMs): ReplayFrame {
  return {
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

/** 中文名：构建回放房间insights（buildReplayRoomInsights）。游戏职责：在前端回放域中组织回放帧、时间线和导出数据，复现战斗过程。 */
export function buildReplayRoomInsights(replay: ReplayPlayback): ReplayRoomInsights {
  const rawFrameCount = replay.frames.length;
  const displayFrames = getReplayDisplayFrames(replay);
  const displayFrameCount = displayFrames.length;
  const hasFrames = displayFrameCount > 0;
  const playableReplay = replay.playbackAvailable !== false && hasMeaningfulReplayFrames(displayFrames);
  const compactReplay = hasFrames && displayFrameCount < rawFrameCount;

  const modeLabel = playableReplay
    ? displayFrameCount >= 4
      ? "完整回放"
      : "关键帧回放"
    : hasFrames
      ? "仅关键帧"
      : "战报摘要";

  const modeDescription = playableReplay
    ? displayFrameCount >= 4
      ? "已压缩静止段并保留可视变化，支持拖动时间轴查看整局。"
      : "只有少量关键帧，但仍可回看结算和关键变化。"
    : hasFrames
      ? "当前只有少量关键帧，保留战报结果，不假装完整回放。"
      : "当前只有战报摘要，没有可播放画面。";

  return {
    modeLabel,
    modeDescription,
    frameCount: displayFrameCount,
    frameCountLabel: displayFrameCount > 0 ? `${displayFrameCount} 帧` : "无帧",
    statusLabel: playableReplay
      ? compactReplay
        ? "可拖动播放 / 已压缩静止段"
        : "可拖动播放"
      : hasFrames
        ? "仅关键帧 / 摘要"
        : "仅战报摘要",
    summaryLine: replay.highlightLine,
    timelineHint: replay.timelineHint,
    timeline: buildReplayTimeline(replay, displayFrames),
    roster: buildReplayRoster(replay, displayFrames)
  };
}

/** 中文名：构建回放导出产物（buildReplayExportArtifact）。游戏职责：在前端回放域中组织回放帧、时间线和导出数据，复现战斗过程。 */
export function buildReplayExportArtifact(id: string): ReplayExportArtifact | undefined {
  const playback = getReplayPlayback(id);

  if (!playback) {
    return undefined;
  }

  const exportPayload = {
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
      playbackAvailable: playback.playbackAvailable,
      frames: playback.frames
    }
  };

  return {
    filename: `${slugifyReplayTitle(playback.title || playback.id)}.json`,
    json: JSON.stringify(exportPayload, null, 2)
  };
}

/** 中文名：构建回放导出产物从playback（buildReplayExportArtifactFromPlayback）。游戏职责：在前端回放域中组织回放帧、时间线和导出数据，复现战斗过程。 */
export function buildReplayExportArtifactFromPlayback(playback: ReplayPlayback): ReplayExportArtifact | undefined {
  if (playback.frames.length === 0) {
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
          playbackAvailable: playback.playbackAvailable,
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
        title: "战报结算",
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

function buildReplayRoster(replay: ReplayPlayback, frames: ReplayFrame[]): ReplayRosterRow[] {
  const finalFrame = frames[frames.length - 1];
  if (!finalFrame) {
    return [];
  }

  const recordedRank = buildRecordedRankMap(replay.playersLine);
  const preferRecordedRank = shouldPreferRecordedReplayRank(replay, finalFrame, recordedRank);

  const heroes = [...finalFrame.heroes].sort((left, right) => {
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
    statusLabel: preferRecordedRank ? "按结算排序" : hero.alive ? "存活" : "出局",
    eliminatedAtLabel: hero.alive ? null : hero.eliminatedAtMs != null ? `出局于 ${formatClock(hero.eliminatedAtMs)}` : "已出局",
    alive: hero.alive
  }));
}

function buildProgressMoment(frame: ReplayFrame): ReplayTimelineMoment {
  const leader = getLeadingHero(frame.heroes);
  const aliveCount = frame.heroes.filter((hero) => hero.alive).length;
  const eventMessage = lastNonEmptyMessage(frame.eventMessages);

  return {
    timeLabel: formatClock(frame.elapsedMs),
    title: eventMessage ? "战况更新" : "局势推进",
    detail: eventMessage ?? (leader ? `${leader.displayName} 领先 ${Math.round(leader.score)} 分，仍有 ${aliveCount} 人存活` : `${aliveCount} 人仍在场上`),
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
  const lastSurvivorResult = resultLabel.includes("last") || resultLabel.includes("survivor") || resultLabel.includes("幸存");
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

function findReplayLiveTimelineIndex(frames: ReplayFrame[], playheadMs: number): number {
  let activeIndex = -1;
  for (let index = 0; index < frames.length; index += 1) {
    if (frames[index].elapsedMs <= playheadMs) {
      activeIndex = index;
      continue;
    }

    break;
  }

  return activeIndex;
}

function buildReplayLiveTimelineMoment(previousFrame: ReplayFrame | undefined, currentFrame: ReplayFrame): ReplayTimelineMoment | null {
  if (!previousFrame) {
    return {
      timeLabel: formatClock(currentFrame.elapsedMs),
      title: "开局",
      detail: `${currentFrame.heroes.length} 名玩家进入战局`,
      tone: "neutral"
    };
  }

  const eventMessage = lastNonEmptyMessage(currentFrame.eventMessages);
  const previousMessage = lastNonEmptyMessage(previousFrame.eventMessages);
  if (eventMessage && eventMessage !== previousMessage) {
    return {
      timeLabel: formatClock(currentFrame.elapsedMs),
      title: "战况更新",
      detail: eventMessage,
      tone: "warning"
    };
  }

  const eliminatedHero = findReplayEliminatedHero(previousFrame, currentFrame);
  if (eliminatedHero) {
    return {
      timeLabel: formatClock(currentFrame.elapsedMs),
      title: `${eliminatedHero.displayName} 出局`,
      detail: `${eliminatedHero.displayName} 在这一段被淘汰`,
      tone: "danger"
    };
  }

  if (hasReplayPickupKeyChange(previousFrame, currentFrame)) {
    return {
      timeLabel: formatClock(currentFrame.elapsedMs),
      title: "补给变化",
      detail: `${currentFrame.pickups.length} 个补给点刷新`,
      tone: "neutral"
    };
  }

  if (hasReplayProjectileKeyChange(previousFrame, currentFrame)) {
    return {
      timeLabel: formatClock(currentFrame.elapsedMs),
      title: "交火推进",
      detail: "弹道与战斗状态发生变化",
      tone: "neutral"
    };
  }

  if (hasReplayHeroProgressChange(previousFrame, currentFrame)) {
    const leader = getLeadingHero(currentFrame.heroes);
    return {
      timeLabel: formatClock(currentFrame.elapsedMs),
      title: "战况推进",
      detail: leader ? `${leader.displayName} 领先 ${Math.round(leader.score)} 分` : "战局持续推进",
      tone: "neutral"
    };
  }

  return null;
}

function findReplayEliminatedHero(previousFrame: ReplayFrame, currentFrame: ReplayFrame): ReplayHeroFrame | undefined {
  return currentFrame.heroes.find((hero) => {
    const previousHero = previousFrame.heroes.find((candidate) => candidate.heroId === hero.heroId);
    return Boolean(previousHero?.alive && !hero.alive);
  });
}

function hasReplayHeroProgressChange(previousFrame: ReplayFrame, currentFrame: ReplayFrame): boolean {
  if (previousFrame.heroes.length !== currentFrame.heroes.length) {
    return true;
  }

  return currentFrame.heroes.some((hero) => {
    const previousHero = previousFrame.heroes.find((candidate) => candidate.heroId === hero.heroId);
    if (!previousHero) {
      return true;
    }

    return (
      previousHero.alive !== hero.alive ||
      previousHero.lifeState !== hero.lifeState ||
      previousHero.currentWeaponKind !== hero.currentWeaponKind ||
      previousHero.eliminatedAtMs !== hero.eliminatedAtMs ||
      Math.round(previousHero.hp) !== Math.round(hero.hp) ||
      Math.round(previousHero.score) !== Math.round(hero.score) ||
      getReplayAngleDelta(previousHero.facing, hero.facing) >= DISPLAY_FACING_EPSILON ||
      getReplayDistance(previousHero.position, hero.position) >= DISPLAY_POSITION_EPSILON
    );
  });
}

function getReplayAngleDelta(left: number, right: number): number {
  const delta = Math.abs(left - right) % (Math.PI * 2);
  return Math.min(delta, Math.PI * 2 - delta);
}

function hasReplayDisplaySignalChange(previousFrame: ReplayFrame, currentFrame: ReplayFrame): boolean {
  if (hasReplayHeroProgressChange(previousFrame, currentFrame)) {
    return true;
  }

  if (hasReplayProjectileKeyChange(previousFrame, currentFrame)) {
    return true;
  }

  if (hasReplayPickupKeyChange(previousFrame, currentFrame)) {
    return true;
  }

  return hasReplayEventMessageChange(previousFrame, currentFrame);
}

function hasReplayProjectileKeyChange(previousFrame: ReplayFrame, currentFrame: ReplayFrame): boolean {
  const previousProjectiles = previousFrame.projectiles.filter((projectile) => projectile.alive);
  const currentProjectiles = currentFrame.projectiles.filter((projectile) => projectile.alive);
  if (previousProjectiles.length !== currentProjectiles.length) {
    return true;
  }

  return currentProjectiles.some((projectile) => {
    const previousProjectile = previousProjectiles.find((candidate) => candidate.projectileId === projectile.projectileId);
    return (
      !previousProjectile ||
      previousProjectile.kind !== projectile.kind ||
      getReplayDistance(previousProjectile.position, projectile.position) >= DISPLAY_PROJECTILE_POSITION_EPSILON
    );
  });
}

function hasReplayPickupKeyChange(previousFrame: ReplayFrame, currentFrame: ReplayFrame): boolean {
  if (previousFrame.pickups.length !== currentFrame.pickups.length) {
    return true;
  }

  return currentFrame.pickups.some((pickup) => {
    const previousPickup = previousFrame.pickups.find((candidate) => candidate.id === pickup.id);
    return !previousPickup || previousPickup.kind !== pickup.kind || previousPickup.available !== pickup.available;
  });
}

function hasReplayEventMessageChange(previousFrame: ReplayFrame, currentFrame: ReplayFrame): boolean {
  const previousMessage = lastNonEmptyMessage(previousFrame.eventMessages);
  const currentMessage = lastNonEmptyMessage(currentFrame.eventMessages);
  return Boolean(currentMessage && currentMessage !== previousMessage);
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

function mergeReplaySummaries(localSummaries: ReplaySummary[], remoteSummaries: ReplaySummary[]): ReplaySummary[] {
  const merged = new Map<string, ReplaySummary>();

  localSummaries.forEach((summary) => {
    const key = normalizeReplaySummaryId(summary.id);
    if (key) {
      merged.set(key, summary);
    }
  });

  remoteSummaries.forEach((summary) => {
    const key = normalizeReplaySummaryId(summary.id);
    if (key) {
      const existing = merged.get(key);
      merged.set(key, choosePreferredReplaySummary(existing, summary));
    }
  });

  return Array.from(merged.values());
}

function choosePreferredReplaySummary(existing: ReplaySummary | undefined, candidate: ReplaySummary): ReplaySummary {
  if (!existing) {
    return candidate;
  }

  if (existing.localBackendSyncDisabled && !candidate.localBackendSyncDisabled) {
    return candidate;
  }

  if (existing.playbackAvailable !== candidate.playbackAvailable) {
    return existing.playbackAvailable ? existing : candidate;
  }
  if (existing.frameCount !== candidate.frameCount) {
    return existing.frameCount > candidate.frameCount ? existing : candidate;
  }
  if (Boolean(existing.thumbnailDataUrl) !== Boolean(candidate.thumbnailDataUrl)) {
    return existing.thumbnailDataUrl ? existing : candidate;
  }
  if (hasReplayRatings(existing) !== hasReplayRatings(candidate)) {
    return hasReplayRatings(existing) ? existing : candidate;
  }

  return candidate;
}

function hasReplayRatings(summary: ReplaySummary): boolean {
  return typeof summary.ratingBefore === "number" && typeof summary.ratingAfter === "number" && typeof summary.ratingDelta === "number";
}

function normalizeReplaySummaryId(id: string): string {
  return id.trim();
}

function toReplaySummary(entry: {
  id: string;
  backendSyncDisabled?: boolean;
  title: string;
  modeLabel: string;
  resultLabel: string;
  highlightLine: string;
  mapLabel: string;
  coverLabel: string;
  playersLine: string;
  timelineHint: string;
  finishedAt: number;
  finishedAtLabel: string;
  thumbnailDataUrl: string | null;
  score: number;
  placement: number | null;
  ratingBefore?: number | null;
  ratingAfter?: number | null;
  ratingDelta?: number | null;
  durationMs: number;
  aliveAtEnd: boolean;
  frameCount: number;
  playbackAvailable: boolean;
}): ReplaySummary {
  return {
    ...entry,
    localBackendSyncDisabled: entry.backendSyncDisabled || undefined,
    highlights: entry.highlightLine,
    ratingBefore: entry.ratingBefore ?? null,
    ratingAfter: entry.ratingAfter ?? null,
    ratingDelta: entry.ratingDelta ?? null
  };
}
