import type { GameSnapshot, Hero } from "../../../objects/types";
import type { ReplayFrame } from "../../../../replay/objects/replayTypes";
import { getLocalReplayPlaybackById, loadLocalReplayPlaybackById, saveLocalReplayPlayback } from "../../../../replay/lib/localReplayStore";
import { compactReplayFrames, hasMeaningfulReplayFrames } from "../../../../replay/objects/replayRecorder";
import { getCurrentAuthHandle, getCurrentAuthUser } from "../../../../identity/api/authGateway";
import {
  isPlayableIdentityHandle,
  normalizePlayableIdentityHandle,
  normalizePlayerHandleKey
} from "../../../../identity/objects/identityHandlePolicy";
import { syncBattleResultToBackend } from "./battleResultSync";
import { syncReplayToBackend } from "../../../../replay/api/replayApi";
import { finalizeBattleReplayFrames } from "../session/battleFinalizationReplay";
import {
  createBotOnlyBattleClosure,
  type BotOnlyBattleClosure
} from "../session/botOnlyBattleClosure";
import { BATTLE_MATCH_DURATION_MS as RULE_BATTLE_MATCH_DURATION_MS } from "../../../objects/battleRules";

export const BATTLE_MATCH_DURATION_MS = RULE_BATTLE_MATCH_DURATION_MS;
const STORAGE_KEY = "slay-demo.truthful-battle-data.v2";
const DEFAULT_RATING = 1200;
const FALLBACK_STORED_BATTLE_RECORDS = 12;
const FALLBACK_STORED_BATTLE_MAILS = 24;
const EMERGENCY_STORED_BATTLE_RECORDS = 3;
const BACKFILL_STATE_KEY = "slay-demo.truthful-battle-backfill.v1";
const STATE_READ_LIMIT_BYTES = 900_000;

let backfillPromise: Promise<void> | null = null;

export interface LocalReplayEntry {
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
  ratingBefore: number | null;
  ratingAfter: number | null;
  ratingDelta: number | null;
  durationMs: number;
  aliveAtEnd: boolean;
  frameCount: number;
  playbackAvailable: boolean;
}

export interface LocalMailEntry {
  id: string;
  sourceBattleId?: string;
  backendSyncDisabled?: boolean;
  subject: string;
  excerpt: string;
  kind: "system" | "battle" | "reward";
  unread: boolean;
  important: boolean;
  senderLabel: string;
  receivedLabel: string;
  sourceLabel?: string;
  sourcePath?: string;
}

export interface LocalRatingEntry {
  rank: number;
  handle: string;
  score: number;
  winRate: string;
  title: string;
  highlight: string;
  recentForm: string;
  matchCount: number;
}

export interface LocalProfileSummary {
  handle: string;
  score: number | null;
  winRate: string | null;
  title: string;
  motto: string;
  currentLoadout: string | null;
  recentRecord: string;
  recentMatches: Array<{
    title: string;
    detail: string;
  }>;
}

export interface LocalBattleReturnTouchpoint {
  label: string;
  path: string;
  detail: string;
}

export interface LocalBattleSettlementCard {
  label: string;
  value: string;
  detail: string;
}

export interface LocalBattleReturnSummary {
  outcome: "finished";
  score: number;
  placement: number | null;
  durationLabel: string;
  ratingDeltaLabel: string;
  resultLine: string;
  highlightLine: string;
  nextStepLabel: string;
  settlementCards: LocalBattleSettlementCard[];
  touchpoints: LocalBattleReturnTouchpoint[];
}

export interface LocalBattleLiveSummary {
  phaseLabel: string;
  playerName: string;
  elapsedLabel: string;
  liveHint: string;
  score: number;
  placement: number | null;
  aliveStatusLabel: string;
}

interface StoredBattleRecord {
  id: string;
  handle: string;
  backendSyncDisabled?: boolean;
  finishedAt: number;
  finishedAtLabel: string;
  durationMs: number;
  playerName: string;
  score: number;
  placement: number | null;
  aliveAtEnd: boolean;
  participantNames: string[];
  mapLabel: string;
  highlightLine: string;
  resultLabel: string;
  coverLabel: string;
  playersLine: string;
  timelineHint: string;
  ratingBefore: number;
  ratingDelta: number;
  ratingAfter: number;
  thumbnailDataUrl: string | null;
  currentLoadout: string | null;
}

interface StoredBattleMail {
  id: string;
  ownerHandle?: string | null;
  sourceBattleId?: string;
  backendSyncDisabled?: boolean;
  subject: string;
  excerpt: string;
  kind: "system" | "battle" | "reward";
  important: boolean;
  unread: boolean;
  senderLabel: string;
  createdAt: number;
  sourceLabel?: string;
  sourcePath?: string;
}

interface BattleTruthState {
  version: 2;
  records: StoredBattleRecord[];
  mails: StoredBattleMail[];
}

export interface FinalizeBattleInput {
  battleId?: string;
  snapshot: GameSnapshot;
  finishedAt: number;
  thumbnailDataUrl: string | null;
  replayFrames: ReplayFrame[];
  botOnlyClosure?: BotOnlyBattleClosure | null;
  allowBotOnlyClosure?: boolean;
  syncBackend?: boolean;
}

export interface FinalizeBattleOutput {
  returnSummary: LocalBattleReturnSummary;
  replay: LocalReplayEntry;
}

interface BattleTruthBackfillState {
  version: 1;
  attemptedIds: string[];
}

/** 中文名：构建live战斗摘要（buildLiveBattleSummary）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function buildLiveBattleSummary(snapshot: GameSnapshot): LocalBattleLiveSummary | null {
  const player = getPlayer(snapshot);
  if (!player) {
    return null;
  }

  const aliveCount = snapshot.heroes.filter((hero) => hero.alive).length;
  const placement = getPlacement(snapshot, player.heroId);
  const score = getPlacementScore(placement, snapshot.heroes.length);

  return {
    phaseLabel: aliveCount <= 1 || snapshot.elapsedMs >= BATTLE_MATCH_DURATION_MS ? "本局结算中" : "对局进行中",
    playerName: player.displayName,
    elapsedLabel: formatDuration(snapshot.elapsedMs),
    liveHint: "6 人竞技场 · 单命淘汰 · 按死亡顺序结算得分。",
    score,
    placement,
    aliveStatusLabel: player.alive
      ? `仍在场上 · 剩余 ${aliveCount} 人`
      : `已淘汰 · 当前第 ${placement ?? "-"} 名`
  };
}

/** 中文名：finalize战斗andpersist（finalizeBattleAndPersist）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function finalizeBattleAndPersist(input: FinalizeBattleInput): FinalizeBattleOutput | null {
  const allowBotOnlyClosure = input.allowBotOnlyClosure ?? true;
  const finalSnapshot = allowBotOnlyClosure ? normalizeExitedFinalSnapshot(input.snapshot) : input.snapshot;
  const resolvedBotOnlyClosure =
    input.botOnlyClosure !== undefined
      ? input.botOnlyClosure
      : allowBotOnlyClosure
        ? createBotOnlyBattleClosure(finalSnapshot, { maxElapsedMs: BATTLE_MATCH_DURATION_MS })
        : null;
  const authoritativeFinalSnapshot = resolvedBotOnlyClosure?.snapshot ?? finalSnapshot;
  const player = getPlayer(authoritativeFinalSnapshot);
  if (!player) {
    return null;
  }

  const state = readState();
  const syncBackend = input.syncBackend ?? true;
  const activeHandle = normalizePlayableIdentityHandle(getCurrentAuthHandle());
  const recordHandle = activeHandle ?? normalizeHandle(getCurrentAuthHandle() || player.displayName || "Player-1");
  const existingRecordId = normalizeBattleRecordId(input.battleId);
  const existingRecord = activeHandle && existingRecordId
    ? state.records.find((storedRecord) => storedRecord.id === existingRecordId && isPlayableStoredBattleRecord(storedRecord)) ?? null
    : null;
  const ratingBefore = activeHandle
    ? existingRecord?.ratingBefore ?? getCurrentRatingFromState(state, activeHandle)
    : DEFAULT_RATING;
  const record = createBattleRecord(
    authoritativeFinalSnapshot,
    player,
    input.finishedAt,
    ratingBefore,
    input.thumbnailDataUrl,
    recordHandle,
    input.battleId,
    !syncBackend || !activeHandle
  );
  const replayFrames = finalizeBattleReplayFrames(
    input.replayFrames,
    authoritativeFinalSnapshot,
    resolvedBotOnlyClosure
  );
  if (!activeHandle) {
    return {
      returnSummary: buildReturnSummary(record),
      replay: toReplayEntry(record, replayFrames)
    };
  }

  if (existingRecord) {
    const playback = getLocalReplayPlaybackById(existingRecord.id);
    const existingReplayFrames = playback?.frames ?? [];
    const mergedRecord = mergeExistingBattleRecord(existingRecord, record);
    const resolvedReplayFrames = selectMoreCompleteBattleReplayFrames(
      existingReplayFrames,
      replayFrames,
      mergedRecord.durationMs
    );
    const mails = createMailsForRecord(mergedRecord);
    const storedReplayFrames = compactReplayFrames(resolvedReplayFrames);
    const nextRecords = upsertStoredBattleRecord(state.records, mergedRecord).slice(0, 50);
    const nextMails = mergeStoredBattleMails(state.mails, mails).slice(0, 100);
    const persistedRecord = nextRecords.find((storedRecord) => storedRecord.id === mergedRecord.id) ?? mergedRecord;
    const nextState: BattleTruthState = {
      version: 2,
      records: nextRecords,
      mails: nextMails
    };
    writeState(nextState);
    reconcileExistingBattleReplayPlayback(persistedRecord, resolvedReplayFrames, existingReplayFrames);
    if (syncBackend) {
      void syncStoredBattleTruthToBackend(
        persistedRecord,
        persistedRecord.handle,
        storedReplayFrames,
        false
      ).then((synced) => {
        if (synced) {
          markBattleTruthBackfillAttempt(persistedRecord.id);
        }
      });
    }

    return {
      returnSummary: buildReturnSummary(persistedRecord),
      replay: toReplayEntry(persistedRecord, resolvedReplayFrames)
    };
  }

  const existingPlayback = getLocalReplayPlaybackById(record.id);
  const existingReplayFrames = existingPlayback?.frames ?? [];
  const resolvedReplayFrames = selectMoreCompleteBattleReplayFrames(
    existingReplayFrames,
    replayFrames,
    record.durationMs
  );
  const shouldDeferReplayPersistence = Boolean(existingPlayback && existingReplayFrames.length === 0);
  const mails = createMailsForRecord(record);
  const playback = buildReplayPlayback(record, resolvedReplayFrames);
  const storedReplayFrames = compactReplayFrames(resolvedReplayFrames);

  const nextState: BattleTruthState = {
    version: 2,
    records: [record, ...state.records].slice(0, 50),
    mails: mergeStoredBattleMails(state.mails, mails).slice(0, 100)
  };

  if (shouldDeferReplayPersistence) {
    reconcileExistingBattleReplayPlayback(record, replayFrames, existingReplayFrames);
  } else {
    saveLocalReplayPlayback(playback);
  }
  writeState(nextState);
  if (syncBackend) {
    void syncStoredBattleTruthToBackend(
      record,
      activeHandle,
      storedReplayFrames,
      !shouldDeferReplayPersistence
    ).then((synced) => {
      if (synced) {
        markBattleTruthBackfillAttempt(record.id);
      }
    });
  }

  return {
    returnSummary: buildReturnSummary(record),
    replay: toReplayEntry(record, resolvedReplayFrames)
  };
}

function normalizeExitedFinalSnapshot(snapshot: GameSnapshot): GameSnapshot {
  const isAlreadyComplete =
    snapshot.elapsedMs >= BATTLE_MATCH_DURATION_MS ||
    snapshot.heroes.filter((hero) => hero.alive && hero.lifeState === "alive" && hero.hp > 0).length <= 1;
  if (isAlreadyComplete) {
    return snapshot;
  }

  const playerIndex = snapshot.heroes.findIndex((hero) => hero.heroId === snapshot.playerHeroId);
  if (playerIndex < 0) {
    return snapshot;
  }

  const player = snapshot.heroes[playerIndex];
  if (!player.alive || player.lifeState !== "alive" || player.hp <= 0) {
    return snapshot;
  }

  const heroes = [...snapshot.heroes];
  heroes[playerIndex] = {
    ...player,
    alive: false,
    lifeState: "dead",
    hp: 0,
    preparedSkill: null,
    velocity: { x: 0, y: 0 },
    respawnMs: 0,
    eliminatedAtMs: snapshot.elapsedMs
  };

  return {
    ...snapshot,
    heroes,
    events: [
      ...snapshot.events,
      {
        eventId: `battle-exit-${player.heroId}-${Math.round(snapshot.elapsedMs)}`,
        type: "kill" as const,
        message: `${player.displayName} 已退出战斗。`,
        ttlMs: 1200
      }
    ].slice(-6)
  };
}

/** 中文名：backfill本地战斗truth转为backend（backfillLocalBattleTruthToBackend）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function backfillLocalBattleTruthToBackend(): Promise<void> {
  if (typeof window === "undefined") {
    return Promise.resolve();
  }

  if (backfillPromise) {
    return backfillPromise;
  }

  backfillPromise = runLocalBattleTruthBackfill().finally(() => {
    backfillPromise = null;
  });

  return backfillPromise;
}

/** 中文名：获取latest战斗return摘要（getLatestBattleReturnSummary）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function getLatestBattleReturnSummary(): LocalBattleReturnSummary | null {
  const latest = readState().records.find(isPlayableStoredBattleRecord);
  return latest ? buildReturnSummary(latest) : null;
}

/** 中文名：获取回放entries（getReplayEntries）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function getReplayEntries(): LocalReplayEntry[] {
  return readState().records
    .filter(isPlayableStoredBattleRecord)
    .map((record) => {
      const playback = getLocalReplayPlaybackById(record.id);
      return toReplayEntry(record, playback?.frames ?? []);
    });
}

/** 中文名：获取回放entryby标识（getReplayEntryById）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function getReplayEntryById(id: string): LocalReplayEntry | undefined {
  return getReplayEntries().find((entry) => entry.id === id);
}

/** 中文名：判断是否回放entrybackendsyncdisabled（isReplayEntryBackendSyncDisabled）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function isReplayEntryBackendSyncDisabled(id: string): boolean {
  const normalizedId = normalizeBattleRecordId(id);
  if (!normalizedId) {
    return false;
  }

  return readState().records.some((record) => record.id === normalizedId && record.backendSyncDisabled === true);
}

/** 中文名：获取mailentries（getMailEntries）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function getMailEntries(): LocalMailEntry[] {
  const state = readState();
  const visibleMails = filterVisibleMails(state.mails);
  const recordById = new Map(
    state.records
      .filter(isPlayableStoredBattleRecord)
      .map((record) => [record.id, record])
  );

  return visibleMails.map((mail) => ({
    id: mail.id,
    sourceBattleId: getStoredMailBattleId(mail) ?? undefined,
    backendSyncDisabled:
      mail.backendSyncDisabled === true ||
      recordById.get(getStoredMailBattleId(mail) ?? "")?.backendSyncDisabled === true ||
      undefined,
    subject: mail.subject,
    excerpt: mail.excerpt,
    kind: mail.kind,
    unread: mail.unread,
    important: mail.important,
    senderLabel: mail.senderLabel,
    receivedLabel: formatRelativeTime(mail.createdAt),
    sourceLabel: mail.sourceLabel?.trim() || undefined,
    sourcePath: mail.sourcePath?.trim() || undefined
  }));
}

/** 中文名：标记mail读取（markMailRead）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function markMailRead(mailId: string): boolean {
  if (!mailId.trim()) {
    return false;
  }

  const currentUser = getCurrentAuthUser();
  const normalizedHandle = normalizePlayableIdentityHandle(currentUser?.handle);
  if (!normalizedHandle) {
    return false;
  }

  const state = readState();
  const normalizedHandleKey = normalizePlayerHandleKey(normalizedHandle);
  let found = false;
  let changed = false;

  const mails = state.mails.map((mail) => {
    if (mail.id !== mailId) {
      return mail;
    }

    const ownerHandleKey = normalizePlayableMailOwnerKey(mail);
    if (!ownerHandleKey || ownerHandleKey !== normalizedHandleKey || !mail.unread) {
      if (ownerHandleKey && ownerHandleKey === normalizedHandleKey) {
        found = true;
      }
      return mail;
    }

    found = true;
    changed = true;
    return { ...mail, unread: false };
  });

  if (!found) {
    return false;
  }

  if (changed) {
    writeState({
      ...state,
      mails
    });
  }

  return true;
}

/** 中文名：获取积分entries（getRatingEntries）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function getRatingEntries(): LocalRatingEntry[] {
  const state = readState();
  if (state.records.length === 0) {
    return [];
  }

  const entries = Array.from(groupRecordsByHandle(state.records).entries())
    .map(([handle, records]) => buildRatingEntryForRecords(handle, records))
    .filter((entry): entry is LocalRatingEntry => entry !== null)
    .sort((left, right) => right.score - left.score || left.handle.localeCompare(right.handle));

  return entries.map((entry, index) => ({ ...entry, rank: index + 1 }));

  /* return [
    {
      rank: 1,
      handle: activeHandle,
      score: latest.ratingAfter,
      winRate: `${winRate}%`,
      title: getRatingTitle(latest.ratingAfter),
      highlight: latest.placement === 1 ? "最近一局拿下最后幸存。" : "最近一局已经记入当前评分。",
      recentForm,
      matchCount: state.records.length
    }
  ]; */
}

/** 中文名：获取积分entryby玩家名（getRatingEntryByHandle）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function getRatingEntryByHandle(handle: string): LocalRatingEntry | undefined {
  const normalizedHandle = normalizePlayableIdentityHandle(handle);
  if (!normalizedHandle) {
    return undefined;
  }

  const normalizedHandleKey = normalizePlayerHandleKey(normalizedHandle);
  return getRatingEntries().find((entry) => normalizePlayerHandleKey(entry.handle) === normalizedHandleKey);
}

/** 中文名：获取profile摘要（getProfileSummary）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function getProfileSummary(handle: string): LocalProfileSummary | undefined {
  const state = readState();
  const requestedHandle = handle.trim();
  const resolvedHandle = requestedHandle
    ? normalizePlayableIdentityHandle(requestedHandle)
    : getResolvedProfileHandle(state);
  if (!resolvedHandle) {
    return undefined;
  }

  const records = getRecordsForHandle(state.records, resolvedHandle);
  const latest = records[0];
  const ratingEntry = buildRatingEntryForRecords(resolvedHandle, records);

  if (!latest || !ratingEntry) {
    return {
      handle: resolvedHandle,
      score: null,
      winRate: null,
      title: "竞技新兵",
      motto: "先完成一场对局，个人主页才会开始记录你的真实战绩。",
      currentLoadout: null,
      recentRecord: "暂时还没有战绩。",
      recentMatches: []
    };
  }

  return {
    handle: resolvedHandle,
    score: ratingEntry.score,
    winRate: ratingEntry.winRate,
    title: ratingEntry.title,
    motto: latest.placement === 1 ? "最近一局成为最后幸存者。" : "继续完成更多对局，主页会逐渐丰富。",
    currentLoadout: latest.currentLoadout,
    recentRecord: `已记录 ${records.length} 场对局，最近一局${latest.placement === 1 ? "拿下头名" : "完成结算"}。`,
    recentMatches: records.slice(0, 5).map((record) => ({
      title: record.resultLabel,
      detail: `${record.finishedAtLabel} · 得分 ${record.score}${record.placement ? ` · 排名 #${record.placement}` : ""}`
    }))
  };
}

function createBattleRecord(
  snapshot: GameSnapshot,
  player: Hero,
  finishedAt: number,
  ratingBefore: number,
  thumbnailDataUrl: string | null,
  handle: string,
  battleId?: string,
  backendSyncDisabled = false
): StoredBattleRecord {
  const placement = getPlacement(snapshot, player.heroId);
  const score = getPlacementScore(placement, snapshot.heroes.length);
  const ratingDelta = calculateRatingDelta(score, placement, player.alive);
  const participants = sortByPlacement(snapshot.heroes).map((hero) => hero.displayName);
  const currentWeapon = player.weapons[player.currentWeaponIndex]?.weaponKind ?? null;

  return {
    id: normalizeBattleRecordId(battleId) ?? `replay-${finishedAt}`,
    handle: normalizeHandle(handle),
    backendSyncDisabled,
    finishedAt,
    finishedAtLabel: formatFinishedAt(finishedAt),
    durationMs: Math.min(snapshot.elapsedMs, BATTLE_MATCH_DURATION_MS),
    playerName: player.displayName,
    score,
    placement,
    aliveAtEnd: player.alive,
    participantNames: participants,
    mapLabel: "6 人竞技场",
    highlightLine: buildHighlightLine(score, placement, player.alive),
    resultLabel: placement === 1 ? "最后幸存" : "淘汰结算",
    coverLabel: placement === 1 ? "冠军战报" : "战报归档",
    playersLine: participants.slice(0, 6).join(" · "),
    timelineHint: "已记录本局缩略图与结算结果，可随时回看。",
    ratingBefore,
    ratingDelta,
    ratingAfter: ratingBefore + ratingDelta,
    thumbnailDataUrl,
    currentLoadout: currentWeapon ? `${currentWeapon} / 闪现 / 冲刺` : null,
  };
}

function mergeExistingBattleRecord(existingRecord: StoredBattleRecord, nextRecord: StoredBattleRecord): StoredBattleRecord {
  const resolvedFinishedAt = Math.max(existingRecord.finishedAt, nextRecord.finishedAt);
  const useNextFinishedAtLabel = resolvedFinishedAt === nextRecord.finishedAt;

  return {
    ...existingRecord,
    ...nextRecord,
    id: existingRecord.id,
    handle: normalizeHandle(nextRecord.handle || existingRecord.handle),
    backendSyncDisabled: existingRecord.backendSyncDisabled || nextRecord.backendSyncDisabled || undefined,
    finishedAt: resolvedFinishedAt,
    finishedAtLabel: useNextFinishedAtLabel ? nextRecord.finishedAtLabel : existingRecord.finishedAtLabel,
    durationMs: Math.max(existingRecord.durationMs, nextRecord.durationMs),
    participantNames: nextRecord.participantNames.length > 0 ? nextRecord.participantNames : existingRecord.participantNames,
    mapLabel: nextRecord.mapLabel || existingRecord.mapLabel,
    highlightLine: nextRecord.highlightLine || existingRecord.highlightLine,
    resultLabel: nextRecord.resultLabel || existingRecord.resultLabel,
    coverLabel: nextRecord.coverLabel || existingRecord.coverLabel,
    playersLine: nextRecord.playersLine || existingRecord.playersLine,
    timelineHint: nextRecord.timelineHint || existingRecord.timelineHint,
    ratingBefore: existingRecord.ratingBefore,
    ratingDelta: nextRecord.ratingDelta,
    ratingAfter: nextRecord.ratingAfter,
    thumbnailDataUrl: nextRecord.thumbnailDataUrl ?? existingRecord.thumbnailDataUrl,
    currentLoadout: nextRecord.currentLoadout ?? existingRecord.currentLoadout
  };
}

function upsertStoredBattleRecord(
  records: StoredBattleRecord[],
  nextRecord: StoredBattleRecord
): StoredBattleRecord[] {
  return [nextRecord, ...records.filter((storedRecord) => storedRecord.id !== nextRecord.id)].sort(
    (left, right) => right.finishedAt - left.finishedAt || left.id.localeCompare(right.id)
  );
}

function mergeStoredBattleMails(
  existingMails: StoredBattleMail[],
  nextMails: StoredBattleMail[]
): StoredBattleMail[] {
  const mergedById = new Map<string, StoredBattleMail>();

  existingMails.forEach((mail) => {
    mergedById.set(mail.id, mail);
  });
  nextMails.forEach((mail) => {
    mergedById.set(mail.id, mail);
  });

  return Array.from(mergedById.values()).sort(
    (left, right) => right.createdAt - left.createdAt || left.id.localeCompare(right.id)
  );
}

function createMailsForRecord(record: StoredBattleRecord): StoredBattleMail[] {
  const createdAt = record.finishedAt;
  const replayPath = `/replay/${record.id}`;
  return [
    {
      id: `mail-battle-${record.id}`,
      ownerHandle: record.handle,
      sourceBattleId: record.id,
      backendSyncDisabled: record.backendSyncDisabled || undefined,
      subject: "战斗结算与评分更新",
      excerpt: buildBattleCloseoutMailExcerpt(record),
      kind: "battle",
      important: true,
      unread: true,
      senderLabel: "战斗档案",
      createdAt,
      sourceLabel: "查看回放",
      sourcePath: replayPath
    }
  ];
}

function buildReturnSummary(record: StoredBattleRecord): LocalBattleReturnSummary {
  const mailCount = 1;
  const placementLabel = record.placement ? `第 ${record.placement} 名` : "已完成归档";

  return {
    outcome: "finished",
    score: record.score,
    placement: record.placement,
    durationLabel: formatDuration(record.durationMs),
    ratingDeltaLabel: formatRatingDelta(record.ratingDelta),
    resultLine: `本局于 ${record.finishedAtLabel} 结束并归档。`,
    highlightLine: record.highlightLine,
    nextStepLabel: "你可以直接开始下一局，或先去看回放、站内信和评分变化。",
    settlementCards: [
      {
        label: "本局名次",
        value: placementLabel,
        detail: "5 分钟上限 · 单命淘汰"
      },
      {
        label: "结算得分",
        value: `${record.score}`,
        detail: "按淘汰顺序结算"
      },
      {
        label: "评分变化",
        value: formatRatingDelta(record.ratingDelta),
        detail: `当前评分 ${record.ratingAfter}`
      },
      {
        label: "新通知",
        value: `${mailCount} 条`,
        detail: "战报和评分变化都已写入站内信"
      }
    ],
    touchpoints: [
      {
        label: "查看回放",
        path: `/replay/${record.id}`,
        detail: "打开这局的战报详情"
      },
      {
        label: "打开站内信",
        path: "/mails",
        detail: "查看这局产生的通知"
      },
      {
        label: "查看评分",
        path: "/rating",
        detail: "确认评分变化和近期战绩"
      }
    ]
  };
}

function toReplayEntry(record: StoredBattleRecord, replayFrames: ReplayFrame[] = []): LocalReplayEntry {
  const frameCount = replayFrames.length;
  return {
    id: record.id,
    backendSyncDisabled: record.backendSyncDisabled || undefined,
    title: `${record.resultLabel} · ${record.finishedAtLabel}`,
    modeLabel: "6 人竞技场",
    resultLabel: record.resultLabel,
    highlightLine: record.highlightLine,
    mapLabel: record.mapLabel,
    coverLabel: record.coverLabel,
    playersLine: record.playersLine,
    timelineHint: record.timelineHint,
    finishedAt: record.finishedAt,
    finishedAtLabel: record.finishedAtLabel,
    thumbnailDataUrl: record.thumbnailDataUrl,
    score: record.score,
    placement: record.placement,
    ratingBefore: record.ratingBefore,
    ratingAfter: record.ratingAfter,
    ratingDelta: record.ratingDelta,
    durationMs: record.durationMs,
    aliveAtEnd: record.aliveAtEnd,
    frameCount,
    playbackAvailable: hasMeaningfulReplayFrames(replayFrames)
  };
}

function buildReplayPlayback(record: StoredBattleRecord, replayFrames: ReplayFrame[]) {
  const entry = toReplayEntry(record, replayFrames);

  return {
    id: entry.id,
    title: entry.title,
    modeLabel: entry.modeLabel,
    resultLabel: entry.resultLabel,
    finishedAtLabel: entry.finishedAtLabel,
    mapLabel: entry.mapLabel,
    highlightLine: entry.highlightLine,
    timelineHint: entry.timelineHint,
    playersLine: entry.playersLine,
    score: entry.score,
    placement: entry.placement,
    ratingBefore: entry.ratingBefore,
    ratingAfter: entry.ratingAfter,
    ratingDelta: entry.ratingDelta,
    durationMs: entry.durationMs,
    aliveAtEnd: entry.aliveAtEnd,
    thumbnailDataUrl: entry.thumbnailDataUrl,
    frames: replayFrames
  };
}

function reconcileExistingBattleReplayPlayback(
  record: StoredBattleRecord,
  candidateReplayFrames: ReplayFrame[],
  knownExistingReplayFrames: ReplayFrame[]
): void {
  if (candidateReplayFrames.length === 0) {
    return;
  }

  void loadLocalReplayPlaybackById(record.id)
    .then((persistedPlayback) => {
      const persistedReplayFrames = persistedPlayback?.frames ?? knownExistingReplayFrames;
      if (isBattleReplayFrameSetMoreComplete(candidateReplayFrames, persistedReplayFrames, record.durationMs)) {
        saveLocalReplayPlayback(buildReplayPlayback(record, candidateReplayFrames));
        void syncStoredBattleReplayToBackend(record, record.handle, compactReplayFrames(candidateReplayFrames));
      } else if (persistedReplayFrames.length > 0) {
        saveLocalReplayPlayback(buildReplayPlayback(record, persistedReplayFrames));
        void syncStoredBattleReplayToBackend(record, record.handle, compactReplayFrames(persistedReplayFrames));
      }
    })
    .catch(() => {
      if (isBattleReplayFrameSetMoreComplete(candidateReplayFrames, knownExistingReplayFrames, record.durationMs)) {
        saveLocalReplayPlayback(buildReplayPlayback(record, candidateReplayFrames));
        void syncStoredBattleReplayToBackend(record, record.handle, compactReplayFrames(candidateReplayFrames));
      }
    });
}

function selectMoreCompleteBattleReplayFrames(
  currentReplayFrames: ReplayFrame[],
  candidateReplayFrames: ReplayFrame[],
  expectedDurationMs: number
): ReplayFrame[] {
  return isBattleReplayFrameSetMoreComplete(candidateReplayFrames, currentReplayFrames, expectedDurationMs)
    ? candidateReplayFrames
    : currentReplayFrames;
}

function isBattleReplayFrameSetMoreComplete(
  candidateReplayFrames: ReplayFrame[],
  currentReplayFrames: ReplayFrame[],
  expectedDurationMs: number
): boolean {
  if (candidateReplayFrames.length === 0) {
    return false;
  }
  if (currentReplayFrames.length === 0) {
    return true;
  }

  const candidateQuality = getBattleReplayFrameQuality(candidateReplayFrames, expectedDurationMs);
  const currentQuality = getBattleReplayFrameQuality(currentReplayFrames, expectedDurationMs);

  if (candidateQuality.reachesExpectedDuration !== currentQuality.reachesExpectedDuration) {
    return candidateQuality.reachesExpectedDuration;
  }
  if (candidateQuality.lastElapsedMs !== currentQuality.lastElapsedMs) {
    return candidateQuality.lastElapsedMs > currentQuality.lastElapsedMs;
  }
  if (candidateQuality.hasMeaningfulDelta !== currentQuality.hasMeaningfulDelta) {
    return candidateQuality.hasMeaningfulDelta;
  }
  if (candidateQuality.spanMs !== currentQuality.spanMs) {
    return candidateQuality.spanMs > currentQuality.spanMs;
  }

  return candidateQuality.frameCount > currentQuality.frameCount;
}

function getBattleReplayFrameQuality(frames: ReplayFrame[], expectedDurationMs: number) {
  const chronologicalFrames = frames
    .filter((frame) => Number.isFinite(frame.elapsedMs))
    .sort((left, right) => left.elapsedMs - right.elapsedMs);
  const firstFrame = chronologicalFrames[0];
  const lastFrame = chronologicalFrames[chronologicalFrames.length - 1];
  const firstElapsedMs = firstFrame?.elapsedMs ?? 0;
  const lastElapsedMs = lastFrame?.elapsedMs ?? 0;
  const normalizedExpectedDurationMs = Math.max(0, expectedDurationMs);

  return {
    frameCount: chronologicalFrames.length,
    spanMs: Math.max(0, lastElapsedMs - firstElapsedMs),
    lastElapsedMs,
    reachesExpectedDuration: normalizedExpectedDurationMs > 0 && lastElapsedMs >= normalizedExpectedDurationMs,
    hasMeaningfulDelta: hasMeaningfulReplayFrames(chronologicalFrames)
  };
}

function getPlacement(snapshot: GameSnapshot, heroId: string): number | null {
  const ranking = sortByPlacement(snapshot.heroes);
  const index = ranking.findIndex((hero) => hero.heroId === heroId);
  return index >= 0 ? index + 1 : null;
}

function sortByPlacement(heroes: readonly Hero[]): Hero[] {
  return [...heroes].sort(comparePlacementOrder);
}

function comparePlacementOrder(left: Hero, right: Hero): number {
  if (left.alive !== right.alive) {
    return left.alive ? -1 : 1;
  }

  if (left.alive && right.alive) {
    if (right.score !== left.score) {
      return right.score - left.score;
    }

    if (right.hp !== left.hp) {
      return right.hp - left.hp;
    }

    return left.displayName.localeCompare(right.displayName);
  }

  if (left.eliminatedAtMs !== right.eliminatedAtMs) {
    return (right.eliminatedAtMs ?? -1) - (left.eliminatedAtMs ?? -1);
  }

  if (right.score !== left.score) {
    return right.score - left.score;
  }

  return left.displayName.localeCompare(right.displayName);
}

function getPlayer(snapshot: GameSnapshot): Hero | undefined {
  return snapshot.heroes.find((hero) => hero.heroId === snapshot.playerHeroId);
}

function getPlacementScore(placement: number | null, playerCount: number): number {
  if (placement === null) {
    return 0;
  }

  const ladder = [12, 9, 7, 5, 3, 1];
  return ladder[Math.min(Math.max(placement - 1, 0), Math.max(playerCount - 1, 0))] ?? 0;
}

function calculateRatingDelta(score: number, placement: number | null, aliveAtEnd: boolean): number {
  const placementFactor = placement === null ? 0 : Math.max(-12, 16 - placement * 4);
  const scoreFactor = Math.min(6, Math.floor(score / 2));
  const aliveFactor = aliveAtEnd ? 2 : -1;
  return placementFactor + scoreFactor + aliveFactor;
}

function getCurrentRatingFromState(state: BattleTruthState, handle: string): number {
  const latest = getRecordsForHandle(state.records, handle)[0];
  return latest ? latest.ratingAfter : DEFAULT_RATING;
}

function getResolvedProfileHandle(state: BattleTruthState): string | null {
  const authHandle = normalizePlayableIdentityHandle(getCurrentAuthHandle());
  if (authHandle) {
    return authHandle;
  }

  const latestPlayableRecord = state.records.find(isPlayableStoredBattleRecord);
  return latestPlayableRecord ? getRecordHandle(latestPlayableRecord) : null;
}

function filterVisibleMails(mails: StoredBattleMail[]): StoredBattleMail[] {
  const currentUser = getCurrentAuthUser();

  if (!currentUser) {
    return mails.filter((mail) => !mail.ownerHandle?.trim());
  }

  const normalizedHandle = normalizePlayableIdentityHandle(currentUser.handle);
  if (!normalizedHandle) {
    return [];
  }

  const normalizedHandleKey = normalizePlayerHandleKey(normalizedHandle);
  return mails.filter((mail) => {
    const ownerHandleKey = normalizePlayableMailOwnerKey(mail);
    if (!ownerHandleKey) {
      return false;
    }

    return ownerHandleKey === normalizedHandleKey;
  });
}

function normalizePlayableMailOwnerKey(mail: StoredBattleMail): string | null {
  const ownerHandle = normalizePlayableIdentityHandle(mail.ownerHandle);
  return ownerHandle ? normalizePlayerHandleKey(ownerHandle) : null;
}

function getStoredMailBattleId(mail: StoredBattleMail): string | null {
  const explicit = mail.sourceBattleId?.trim();
  if (explicit) {
    return explicit;
  }

  const sourcePathPrefix = "/replay/";
  const sourcePath = mail.sourcePath?.trim() ?? "";
  if (sourcePath.startsWith(sourcePathPrefix)) {
    const replayId = sourcePath.slice(sourcePathPrefix.length).trim();
    if (replayId) {
      return replayId;
    }
  }

  for (const prefix of ["mail-battle-", "mail-rating-"]) {
    if (mail.id.startsWith(prefix)) {
      const battleId = mail.id.slice(prefix.length).trim();
      if (battleId) {
        return battleId;
      }
    }
  }

  return null;
}

function groupRecordsByHandle(records: StoredBattleRecord[]): Map<string, StoredBattleRecord[]> {
  const grouped = new Map<string, StoredBattleRecord[]>();

  records.forEach((record) => {
    if (!isPlayableStoredBattleRecord(record)) {
      return;
    }

    const handle = getRecordHandle(record);
    const handleKey = normalizePlayerHandleKey(handle);
    const bucket = grouped.get(handleKey) ?? [];
    bucket.push(record);
    grouped.set(handleKey, bucket);
  });

  return grouped;
}

function getRecordsForHandle(records: StoredBattleRecord[], handle: string): StoredBattleRecord[] {
  const normalizedHandle = normalizePlayableIdentityHandle(handle);
  if (!normalizedHandle) {
    return [];
  }

  const normalizedHandleKey = normalizePlayerHandleKey(normalizedHandle);
  return records.filter((record) => {
    if (!isPlayableStoredBattleRecord(record)) {
      return false;
    }

    return normalizePlayerHandleKey(getRecordHandle(record)) === normalizedHandleKey;
  });
}

function buildRatingEntryForRecords(handle: string, records: StoredBattleRecord[]): LocalRatingEntry | null {
  if (records.length === 0) {
    return null;
  }

  const latest = records[0];
  const displayHandle = normalizePlayableIdentityHandle(latest.handle) ?? normalizePlayableIdentityHandle(handle);
  if (!displayHandle) {
    return null;
  }

  const wins = records.filter((record) => record.placement === 1).length;
  const winRate = Math.round((wins / records.length) * 100);
  const recentForm = records
    .slice(0, 5)
    .map((record) => (record.placement === 1 ? "W" : "L"))
    .join(" ");

  return {
    rank: 0,
    handle: displayHandle,
    score: latest.ratingAfter,
    winRate: `${winRate}%`,
    title: getRatingTitle(latest.ratingAfter),
    highlight: latest.placement === 1 ? "最近一局成为最后幸存者。" : "最近一局已计入当前评分。",
    recentForm,
    matchCount: records.length
  };
}

function getRecordHandle(record: StoredBattleRecord): string {
  return normalizePlayableIdentityHandle(record.handle) ?? normalizeHandle(record.handle || "Player-1");
}

function isPlayableStoredBattleRecord(record: StoredBattleRecord): boolean {
  return isPlayableIdentityHandle(record.handle);
}

function normalizeHandle(handle: string): string {
  const trimmed = handle.trim();
  return trimmed.length > 0 ? trimmed : "Player-1";
}

function normalizeBattleRecordId(battleId: string | undefined): string | null {
  const normalized = battleId?.trim();
  return normalized ? normalized : null;
}

function getRatingTitle(rating: number): string {
  if (rating >= 1500) {
    return "前锋";
  }
  if (rating >= 1350) {
    return "突击手";
  }
  return "竞技新兵";
}

function buildHighlightLine(score: number, placement: number | null, aliveAtEnd: boolean): string {
  if (placement === 1) {
    return `你成为最后幸存者，本局结算得分 ${score}。`;
  }

  if (aliveAtEnd) {
    return `时间结束时你仍然存活，本局按生存顺序结算得到 ${score}。`;
  }

  if (placement !== null) {
    return `你以第 ${placement} 名出局，本局按淘汰顺序结算得到 ${score}。`;
  }

  return `这场对局已经完成归档，本局得分 ${score}。`;
}

function formatDuration(durationMs: number): string {
  const totalSeconds = Math.max(0, Math.floor(durationMs / 1000));
  const minutes = Math.floor(totalSeconds / 60)
    .toString()
    .padStart(2, "0");
  const seconds = (totalSeconds % 60).toString().padStart(2, "0");
  return `${minutes}:${seconds}`;
}

function formatRelativeTime(timestamp: number): string {
  const deltaMs = Date.now() - timestamp;
  const deltaMinutes = Math.max(0, Math.floor(deltaMs / 60000));
  if (deltaMinutes < 1) {
    return "刚刚";
  }
  if (deltaMinutes < 60) {
    return `${deltaMinutes} 分钟前`;
  }

  const deltaHours = Math.floor(deltaMinutes / 60);
  if (deltaHours < 24) {
    return `${deltaHours} 小时前`;
  }

  return formatFinishedAt(timestamp);
}

function formatFinishedAt(timestamp: number): string {
  return new Intl.DateTimeFormat("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit"
  }).format(timestamp);
}

function formatRatingDelta(delta: number): string {
  if (delta > 0) {
    return `+${delta}`;
  }
  if (delta < 0) {
    return `${delta}`;
  }
  return "0";
}

function buildBattleCloseoutMailExcerpt(record: StoredBattleRecord): string {
  const placementLabel = record.placement ? `第 ${record.placement} 名` : "已归档";
  const ratingLabel =
    record.ratingDelta === 0
      ? `评分 ${record.ratingAfter}`
      : `评分 ${formatRatingDelta(record.ratingDelta)} 至 ${record.ratingAfter}`;

  return `${placementLabel} | 得分 ${record.score} | ${ratingLabel}。回放已归档，可随时查看。`;
}

function readState(): BattleTruthState {
  if (typeof window === "undefined") {
    return { version: 2, records: [], mails: [] };
  }

  let raw: string | null = null;
  try {
    raw = window.localStorage.getItem(STORAGE_KEY);
  } catch {
    return { version: 2, records: [], mails: [] };
  }
  if (!raw) {
    return { version: 2, records: [], mails: [] };
  }
  if (raw.length > STATE_READ_LIMIT_BYTES) {
    clearState();
    return { version: 2, records: [], mails: [] };
  }

  try {
    const parsed = JSON.parse(raw) as Partial<BattleTruthState> & {
      records?: Array<Partial<StoredBattleRecord> & { replayFrames?: ReplayFrame[] }>;
    };
    const legacyRecords = Array.isArray(parsed.records)
      ? (parsed.records as Array<Partial<StoredBattleRecord> & { replayFrames?: ReplayFrame[] }>)
      : [];
    const records = legacyRecords.map(normalizeStoredBattleRecord);
    legacyRecords.forEach((record, index) => {
      const frames = Array.isArray(record.replayFrames) ? record.replayFrames : [];
      const normalized = records[index];
      if (frames.length > 0 && normalized && !getLocalReplayPlaybackById(normalized.id)) {
        saveLocalReplayPlayback(buildReplayPlayback(normalized, frames));
      }
    });
    return {
      version: 2,
      records,
      mails: Array.isArray(parsed.mails) ? (parsed.mails as StoredBattleMail[]) : []
    };
  } catch {
    return { version: 2, records: [], mails: [] };
  }
}

function writeState(state: BattleTruthState): void {
  if (typeof window === "undefined") {
    return;
  }

  if (tryWriteState(state)) {
    return;
  }

  if (
    tryWriteState({
      version: 2,
      records: state.records.slice(0, FALLBACK_STORED_BATTLE_RECORDS).map(stripBattleRecordPreview),
      mails: state.mails.slice(0, FALLBACK_STORED_BATTLE_MAILS)
    })
  ) {
    return;
  }

  if (
    tryWriteState({
      version: 2,
      records: state.records.slice(0, EMERGENCY_STORED_BATTLE_RECORDS).map(stripBattleRecordPreview),
      mails: []
    })
  ) {
    return;
  }

  clearState();
}

function tryWriteState(state: BattleTruthState): boolean {
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
    return true;
  } catch {
    return false;
  }
}

function clearState(): void {
  try {
    window.localStorage.removeItem(STORAGE_KEY);
  } catch {
    // Battle settlement already returned to the caller; local archive persistence is best effort.
  }
}

function stripBattleRecordPreview(record: StoredBattleRecord): StoredBattleRecord {
  return {
    ...record,
    thumbnailDataUrl: null
  };
}

function normalizeStoredBattleRecord(record: Partial<StoredBattleRecord> & { replayFrames?: ReplayFrame[] }): StoredBattleRecord {
  return {
    id: record.id ?? `replay-${Date.now()}`,
    handle: normalizeStoredBattleRecordHandle(record.handle),
    finishedAt: record.finishedAt ?? Date.now(),
    finishedAtLabel: record.finishedAtLabel ?? formatFinishedAt(record.finishedAt ?? Date.now()),
    durationMs: record.durationMs ?? 0,
    playerName: record.playerName ?? "Player-1",
    score: record.score ?? 0,
    placement: record.placement ?? null,
    aliveAtEnd: record.aliveAtEnd ?? false,
    participantNames: Array.isArray(record.participantNames) ? record.participantNames : [],
    mapLabel: record.mapLabel ?? "6 人竞技场",
    highlightLine: record.highlightLine ?? "尚未生成回放摘要",
    resultLabel: record.resultLabel ?? "未记录",
    coverLabel: record.coverLabel ?? "战报",
    playersLine: record.playersLine ?? "",
    timelineHint: record.timelineHint ?? "暂无时间轴",
    ratingBefore: record.ratingBefore ?? DEFAULT_RATING,
    ratingDelta: record.ratingDelta ?? 0,
    ratingAfter: record.ratingAfter ?? DEFAULT_RATING,
    thumbnailDataUrl: record.thumbnailDataUrl ?? null,
    currentLoadout: record.currentLoadout ?? null,
    backendSyncDisabled: record.backendSyncDisabled === true || undefined
  };
}

function normalizeStoredBattleRecordHandle(handle: string | null | undefined): string {
  return (handle ?? "").trim();
}

async function runLocalBattleTruthBackfill(): Promise<void> {
  const state = readState();
  if (state.records.length === 0) {
    return;
  }

  const backfillState = readBattleTruthBackfillState();

  for (const record of state.records) {
    if (record.backendSyncDisabled || !isPlayableStoredBattleRecord(record)) {
      markBattleTruthBackfillAttempt(record.id);
      continue;
    }

    if (backfillState.attemptedIds.includes(record.id)) {
      continue;
    }

    const playback = await loadLocalReplayPlaybackById(record.id).catch(() => undefined);
    const frames = playback?.frames ?? [];
    const replayFrames = hasMeaningfulReplayFrames(frames) ? frames : [];
    const activeHandle = getRecordHandle(record);

    const synced = await syncStoredBattleTruthToBackend(record, activeHandle, replayFrames);
    if (synced) {
      backfillState.attemptedIds.push(record.id);
      writeBattleTruthBackfillState(backfillState);
    }

  }
}

function readBattleTruthBackfillState(): BattleTruthBackfillState {
  if (typeof window === "undefined") {
    return { version: 1, attemptedIds: [] };
  }

  try {
    const raw = window.localStorage.getItem(BACKFILL_STATE_KEY);
    if (!raw) {
      return { version: 1, attemptedIds: [] };
    }

    const parsed = JSON.parse(raw) as Partial<BattleTruthBackfillState>;
    return {
      version: 1,
      attemptedIds: Array.isArray(parsed.attemptedIds) ? parsed.attemptedIds.filter((id) => typeof id === "string") : []
    };
  } catch {
    return { version: 1, attemptedIds: [] };
  }
}

function writeBattleTruthBackfillState(state: BattleTruthBackfillState): void {
  if (typeof window === "undefined") {
    return;
  }

  try {
    window.localStorage.setItem(BACKFILL_STATE_KEY, JSON.stringify(state));
  } catch {
    // Backfill is best-effort; a storage write failure should not block startup.
  }
}

async function syncStoredBattleTruthToBackend(
  record: StoredBattleRecord,
  handle: string,
  replayFrames: ReplayFrame[],
  syncReplay = true
): Promise<boolean> {
  if (record.backendSyncDisabled) {
    return false;
  }

  const playableHandle = normalizePlayableIdentityHandle(handle);
  if (!playableHandle || !isPlayableStoredBattleRecord(record)) {
    return false;
  }

  const resultSynced = await syncBattleResultToBackend({
    battleId: record.id,
    handle: playableHandle,
    displayName: record.playerName,
    finishedAt: record.finishedAt,
    finishedAtLabel: record.finishedAtLabel,
    durationMs: record.durationMs,
    score: record.score,
    placement: record.placement,
    aliveAtEnd: record.aliveAtEnd,
    ratingBefore: record.ratingBefore,
    ratingDelta: record.ratingDelta,
    ratingAfter: record.ratingAfter,
    resultLabel: record.resultLabel,
    modeLabel: "6 人竞技场",
    mapLabel: record.mapLabel,
    highlightLine: record.highlightLine,
    playersLine: record.playersLine,
    timelineHint: record.timelineHint,
    currentLoadout: record.currentLoadout
  });

  const replaySynced = syncReplay ? await syncStoredBattleReplayToBackend(record, playableHandle, replayFrames) : true;

  return resultSynced && replaySynced;
}

async function syncStoredBattleReplayToBackend(
  record: StoredBattleRecord,
  handle: string,
  replayFrames: ReplayFrame[]
): Promise<boolean> {
  if (record.backendSyncDisabled) {
    return false;
  }

  const playableHandle = normalizePlayableIdentityHandle(handle);
  if (!playableHandle || !isPlayableStoredBattleRecord(record)) {
    return false;
  }

  return await syncReplayToBackend({
    replayId: record.id,
    battleId: record.id,
    handle: playableHandle,
    displayName: record.playerName,
    finishedAt: record.finishedAt,
    finishedAtLabel: record.finishedAtLabel,
    title: `${record.resultLabel} · ${record.finishedAtLabel}`,
    modeLabel: "6 人竞技场",
    resultLabel: record.resultLabel,
    mapLabel: record.mapLabel,
    highlightLine: record.highlightLine,
    coverLabel: record.coverLabel,
    playersLine: record.playersLine,
    timelineHint: record.timelineHint,
    score: record.score,
    placement: record.placement,
    durationMs: record.durationMs,
    aliveAtEnd: record.aliveAtEnd,
    thumbnailDataUrl: record.thumbnailDataUrl,
    currentLoadout: record.currentLoadout,
    frameCount: replayFrames.length,
    playbackAvailable: hasMeaningfulReplayFrames(replayFrames),
    frames: replayFrames
  });
}

function markBattleTruthBackfillAttempt(id: string): void {
  const normalizedId = id.trim();
  if (!normalizedId) {
    return;
  }

  const state = readBattleTruthBackfillState();
  if (state.attemptedIds.includes(normalizedId)) {
    return;
  }

  state.attemptedIds.push(normalizedId);
  writeBattleTruthBackfillState(state);
}
