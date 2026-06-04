import { compactReplayFrames } from "../../../runtime/battle/microservices/projections/functions/BattleReplayFrameRecorder";
import { isBattleComplete, isLocalPlayerEliminated } from "../../../runtime/battle/microservices/runtime/functions/BattleRuntimeFinishRules";
import {
  advanceActiveBattleSessionElapsed,
  isSharedAuthoritativeSession,
  normalizeCompletedActiveBattleSession,
  resolveCompletedActiveBattleSessionNormalizationReason,
  resolveStoredSessionFinalizationReason
} from "../functions/activeBattleSessionCompletionRules";
import {
  normalizeHandle,
  normalizeSessionOwner,
  resolveStoredSessionOwner
} from "../functions/activeBattleSessionOwnerRules";
import { mergeCompletedActiveBattleSessions } from "../functions/mergeCompletedActiveBattleSessions";
import type { ActiveBattleSession, ActiveBattleSessionOwner } from "../objects/BattlePageState";

const LEGACY_ACTIVE_BATTLE_SESSION_KEY = "slay-demo.active-battle-session.v1";
const LEGACY_COMPLETED_BATTLE_SESSION_KEY = "slay-demo.completed-battle-session.v1";
const ACTIVE_BATTLE_SESSION_KEY_PREFIX = "slay-demo.active-battle-session.v2";
const COMPLETED_BATTLE_SESSION_KEY_PREFIX = "slay-demo.completed-battle-session.v2";
const ACTIVE_BATTLE_SESSION_EPOCH_KEY_PREFIX = "slay-demo.active-battle-session-epoch.v1";
const ACTIVE_SESSION_REPLAY_FRAME_LIMIT = 120;
const ACTIVE_SESSION_EMERGENCY_FRAME_LIMIT = 24;
const ACTIVE_SESSION_READ_LIMIT_BYTES = 900_000;


/** 中文名：读取active战斗会话（readActiveBattleSession）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function readActiveBattleSession(owner: ActiveBattleSessionOwner): ActiveBattleSession | null {
  const session = readStoredActiveBattleSession(getActiveBattleSessionStorageKey(owner), owner, { advanceElapsed: true })
    ?? migrateLegacyStoredBattleSession(LEGACY_ACTIVE_BATTLE_SESSION_KEY, getActiveBattleSessionStorageKey(owner), owner, {
      advanceElapsed: true
    });
  if (!session) {
    return null;
  }

  const finalizationReason = resolveStoredSessionFinalizationReason(session, session.snapshot, Date.now());
  if (finalizationReason) {
    writeCompletedActiveBattleSession(normalizeCompletedActiveBattleSession(session, finalizationReason));
    return null;
  }

  return session;
}

/** 中文名：publishactive战斗会话epoch（publishActiveBattleSessionEpoch）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function publishActiveBattleSessionEpoch(owner: ActiveBattleSessionOwner): string {
  const epoch = createActiveBattleSessionEpoch();
  writeActiveBattleSessionEpoch(owner, epoch);
  return epoch;
}

/** 中文名：consumecompletedactive战斗会话（consumeCompletedActiveBattleSession）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function consumeCompletedActiveBattleSession(owner: ActiveBattleSessionOwner): ActiveBattleSession | null {
  const session =
    readStoredActiveBattleSession(getCompletedBattleSessionStorageKey(owner), owner)
    ?? migrateLegacyStoredBattleSession(
      LEGACY_COMPLETED_BATTLE_SESSION_KEY,
      getCompletedBattleSessionStorageKey(owner),
      owner
    )
    ?? consumeLegacyCompletedFromActiveSession(owner);
  if (!session) {
    return null;
  }

  clearStoredBattleSession(getCompletedBattleSessionStorageKey(owner));
  clearStoredBattleSession(getActiveBattleSessionStorageKey(owner));
  return normalizeCompletedActiveBattleSession(
    session,
    resolveCompletedActiveBattleSessionNormalizationReason(session, Date.now())
  );
}

/** 中文名：读取completedactive战斗会话（readCompletedActiveBattleSession）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function readCompletedActiveBattleSession(owner: ActiveBattleSessionOwner): ActiveBattleSession | null {
  const completedSession =
    readStoredActiveBattleSession(getCompletedBattleSessionStorageKey(owner), owner)
    ?? migrateLegacyStoredBattleSession(
      LEGACY_COMPLETED_BATTLE_SESSION_KEY,
      getCompletedBattleSessionStorageKey(owner),
      owner
    );
  if (completedSession) {
    return normalizeCompletedActiveBattleSession(
      completedSession,
      resolveCompletedActiveBattleSessionNormalizationReason(completedSession, Date.now())
    );
  }

  const legacySession =
    readStoredActiveBattleSession(getActiveBattleSessionStorageKey(owner), owner)
    ?? migrateLegacyStoredBattleSession(LEGACY_ACTIVE_BATTLE_SESSION_KEY, getActiveBattleSessionStorageKey(owner), owner);
  if (!legacySession) {
    return null;
  }

  const reason = resolveStoredSessionFinalizationReason(legacySession, legacySession.snapshot, Date.now());
  return reason ? normalizeCompletedActiveBattleSession(legacySession, reason) : null;
}

/** 中文名：writeactive战斗会话（writeActiveBattleSession）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function writeActiveBattleSession(session: ActiveBattleSession): void {
  if (!isSessionEpochCurrent(session)) {
    return;
  }

  if (isSharedAuthoritativeSession(session)) {
    writeStoredBattleSession(getActiveBattleSessionStorageKey(session.owner), session);
    return;
  }

  if (isBattleComplete(session.snapshot) || isLocalPlayerEliminated(session.snapshot)) {
    writeCompletedActiveBattleSession(session);
    return;
  }

  writeStoredBattleSession(getActiveBattleSessionStorageKey(session.owner), session);
}

/** 中文名：writecompletedactive战斗会话（writeCompletedActiveBattleSession）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function writeCompletedActiveBattleSession(session: ActiveBattleSession): void {
  if (!isSessionEpochCurrent(session)) {
    return;
  }

  const normalizedSession = normalizeCompletedActiveBattleSession(
    session,
    resolveCompletedActiveBattleSessionNormalizationReason(session, Date.now())
  );
  const completedStorageKey = getCompletedBattleSessionStorageKey(normalizedSession.owner);
  const previousSession = readStoredActiveBattleSession(completedStorageKey, normalizedSession.owner);
  const sessionToStore =
    previousSession && previousSession.battleId === normalizedSession.battleId
      ? mergeCompletedActiveBattleSessions(
          normalizeCompletedActiveBattleSession(
            previousSession,
            resolveCompletedActiveBattleSessionNormalizationReason(previousSession, Date.now())
          ),
          normalizedSession
        )
      : normalizedSession;

  writeStoredBattleSession(completedStorageKey, sessionToStore);
  clearStoredBattleSession(getActiveBattleSessionStorageKey(normalizedSession.owner));
  clearStoredBattleSession(LEGACY_ACTIVE_BATTLE_SESSION_KEY);
}

/** 中文名：clearactive战斗会话（clearActiveBattleSession）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function clearActiveBattleSession(owner: ActiveBattleSessionOwner): void {
  clearStoredBattleSession(getActiveBattleSessionStorageKey(owner));
  clearStoredBattleSession(getCompletedBattleSessionStorageKey(owner));
  clearStoredBattleSession(LEGACY_ACTIVE_BATTLE_SESSION_KEY);
  clearStoredBattleSession(LEGACY_COMPLETED_BATTLE_SESSION_KEY);
}

/** 中文名：clearactive战斗会话progress（clearActiveBattleSessionProgress）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function clearActiveBattleSessionProgress(owner: ActiveBattleSessionOwner): void {
  clearStoredBattleSession(getActiveBattleSessionStorageKey(owner));
  clearStoredBattleSession(LEGACY_ACTIVE_BATTLE_SESSION_KEY);
}

function writeStoredBattleSession(storageKey: string, session: ActiveBattleSession): void {
  if (typeof window === "undefined") {
    return;
  }

  const compactSession = compactActiveBattleSession(session, ACTIVE_SESSION_REPLAY_FRAME_LIMIT);
  try {
    window.localStorage.setItem(storageKey, JSON.stringify(compactSession));
    return;
  } catch {
    // Active battle recovery is useful, but it must never crash the whole app.
  }

  try {
    window.localStorage.setItem(
      storageKey,
      JSON.stringify(compactActiveBattleSession(session, ACTIVE_SESSION_EMERGENCY_FRAME_LIMIT))
    );
  } catch {
    clearStoredBattleSession(storageKey);
  }
}

function clearStoredBattleSession(storageKey: string): void {
  if (typeof window === "undefined") {
    return;
  }

  try {
    window.localStorage.removeItem(storageKey);
  } catch {
    // Storage access can fail in restricted browser modes; recovery state is optional.
  }
}

function getActiveBattleSessionStorageKey(owner: ActiveBattleSessionOwner): string {
  return `${ACTIVE_BATTLE_SESSION_KEY_PREFIX}.${buildBattleSessionStorageOwnerKey(owner)}`;
}

function getCompletedBattleSessionStorageKey(owner: ActiveBattleSessionOwner): string {
  return `${COMPLETED_BATTLE_SESSION_KEY_PREFIX}.${buildBattleSessionStorageOwnerKey(owner)}`;
}

function getActiveBattleSessionEpochStorageKey(owner: ActiveBattleSessionOwner): string {
  return `${ACTIVE_BATTLE_SESSION_EPOCH_KEY_PREFIX}.${buildBattleSessionStorageOwnerKey(owner)}`;
}

function buildBattleSessionStorageOwnerKey(owner: ActiveBattleSessionOwner): string {
  const normalizedOwner = normalizeSessionOwner(owner);
  const handle = encodeURIComponent(normalizeHandle(normalizedOwner.handle));
  const sessionToken = normalizedOwner.sessionToken ? encodeURIComponent(normalizedOwner.sessionToken) : "guest";
  return `${handle}.${sessionToken}`;
}

function compactActiveBattleSession(session: ActiveBattleSession, frameLimit: number): ActiveBattleSession {
  return {
    ...session,
    replayFrames: compactReplayFrames(session.replayFrames, frameLimit)
  };
}

interface ReadStoredActiveBattleSessionOptions {
  advanceElapsed?: boolean;
}

function readStoredActiveBattleSession(
  storageKey: string,
  owner: ActiveBattleSessionOwner,
  options: ReadStoredActiveBattleSessionOptions = {}
): ActiveBattleSession | null {
  if (typeof window === "undefined") {
    return null;
  }

  let raw: string | null = null;
  try {
    raw = window.localStorage.getItem(storageKey);
  } catch {
    return null;
  }
  if (!raw) {
    return null;
  }
  if (raw.length > ACTIVE_SESSION_READ_LIMIT_BYTES) {
    clearStoredBattleSession(storageKey);
    return null;
  }

  try {
    const parsed = JSON.parse(raw) as Partial<ActiveBattleSession>;
    const snapshot = parsed.snapshot;
    if (!snapshot || !Array.isArray(snapshot.heroes) || snapshot.heroes.length === 0) {
      clearStoredBattleSession(storageKey);
      return null;
    }

    const ownerResolution = resolveStoredSessionOwner(parsed.owner, snapshot, owner);
    if (ownerResolution.status === "invalid") {
      clearStoredBattleSession(storageKey);
      return null;
    }
    if (ownerResolution.status === "mismatch") {
      return null;
    }

    const savedAt = typeof parsed.savedAt === "number" ? parsed.savedAt : Date.now();
    const sessionEpoch = normalizeOptionalStoredString(parsed.sessionEpoch);
    if (!isStoredSessionEpochReadable(ownerResolution.owner, sessionEpoch)) {
      clearStoredBattleSession(storageKey);
      return null;
    }

    const localAuthoritativePlayerId = normalizeOptionalStoredString(parsed.localAuthoritativePlayerId);
    const localAuthoritativeTicketId = normalizeOptionalStoredString(parsed.localAuthoritativeTicketId);
    const mapId = normalizeOptionalStoredString(parsed.mapId);
    const session: ActiveBattleSession = {
      version: 1,
      owner: ownerResolution.owner,
      ...(sessionEpoch ? { sessionEpoch } : {}),
      battleId: typeof parsed.battleId === "string" && parsed.battleId.trim() ? parsed.battleId : `battle-${savedAt}`,
      ...(mapId ? { mapId } : {}),
      ...(parsed.sharedAuthoritativeRuntime === true ? { sharedAuthoritativeRuntime: true } : {}),
      ...(localAuthoritativePlayerId ? { localAuthoritativePlayerId } : {}),
      ...(localAuthoritativeTicketId ? { localAuthoritativeTicketId } : {}),
      savedAt,
      snapshot,
      replayFrames: compactReplayFrames(
        Array.isArray(parsed.replayFrames) ? parsed.replayFrames : [],
        ACTIVE_SESSION_REPLAY_FRAME_LIMIT
      ),
      lastReplaySampleElapsed:
        typeof parsed.lastReplaySampleElapsed === "number" ? parsed.lastReplaySampleElapsed : null
    };

    return options.advanceElapsed ? advanceActiveBattleSessionElapsed(session, Date.now()) : session;
  } catch {
    clearStoredBattleSession(storageKey);
    return null;
  }
}

function readActiveBattleSessionEpoch(owner: ActiveBattleSessionOwner): string | null {
  if (typeof window === "undefined") {
    return null;
  }

  try {
    return normalizeOptionalStoredString(window.localStorage.getItem(getActiveBattleSessionEpochStorageKey(owner)));
  } catch {
    return null;
  }
}

function writeActiveBattleSessionEpoch(owner: ActiveBattleSessionOwner, epoch: string): void {
  if (typeof window === "undefined") {
    return;
  }

  try {
    window.localStorage.setItem(getActiveBattleSessionEpochStorageKey(owner), epoch);
  } catch {
    // Epoch guards are best-effort; session reads remain backward compatible without them.
  }
}

function isStoredSessionEpochReadable(owner: ActiveBattleSessionOwner, sessionEpoch: string | null): boolean {
  const currentEpoch = readActiveBattleSessionEpoch(owner);
  return !currentEpoch || sessionEpoch === currentEpoch;
}

function isSessionEpochCurrent(session: ActiveBattleSession): boolean {
  const currentEpoch = readActiveBattleSessionEpoch(session.owner);
  return !currentEpoch || session.sessionEpoch === currentEpoch;
}

function createActiveBattleSessionEpoch(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return `epoch-${crypto.randomUUID()}`;
  }

  return `epoch-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

function migrateLegacyStoredBattleSession(
  legacyStorageKey: string,
  targetStorageKey: string,
  owner: ActiveBattleSessionOwner,
  options: ReadStoredActiveBattleSessionOptions = {}
): ActiveBattleSession | null {
  const legacySession = readStoredActiveBattleSession(legacyStorageKey, owner, options);
  if (!legacySession) {
    return null;
  }

  writeStoredBattleSession(targetStorageKey, legacySession);
  clearStoredBattleSession(legacyStorageKey);
  return legacySession;
}

function consumeLegacyCompletedFromActiveSession(owner: ActiveBattleSessionOwner): ActiveBattleSession | null {
  const legacySession =
    readStoredActiveBattleSession(getActiveBattleSessionStorageKey(owner), owner)
    ?? migrateLegacyStoredBattleSession(LEGACY_ACTIVE_BATTLE_SESSION_KEY, getActiveBattleSessionStorageKey(owner), owner);
  if (!legacySession) {
    return null;
  }

  const reason = resolveStoredSessionFinalizationReason(legacySession, legacySession.snapshot, Date.now());
  if (!reason) {
    return null;
  }

  return normalizeCompletedActiveBattleSession(legacySession, reason);
}

function normalizeOptionalStoredString(value: unknown): string | null {
  return typeof value === "string" && value.trim() ? value.trim() : null;
}


