import { compactReplayFrames } from "../../replay/replayRecorder";
import type { GameSnapshot } from "../../../domain/types";
import { BATTLE_MATCH_DURATION_MS } from "../local/battleLocalGateway";
import { isBattleComplete, isLocalPlayerEliminated } from "../runtime-local/session/battleCompletion";
import type { ActiveBattleSession, ActiveBattleSessionOwner } from "./battlePageTypes";

const LEGACY_ACTIVE_BATTLE_SESSION_KEY = "slay-demo.active-battle-session.v1";
const LEGACY_COMPLETED_BATTLE_SESSION_KEY = "slay-demo.completed-battle-session.v1";
const ACTIVE_BATTLE_SESSION_KEY_PREFIX = "slay-demo.active-battle-session.v2";
const COMPLETED_BATTLE_SESSION_KEY_PREFIX = "slay-demo.completed-battle-session.v2";
const ACTIVE_BATTLE_SESSION_EPOCH_KEY_PREFIX = "slay-demo.active-battle-session-epoch.v1";
const ACTIVE_SESSION_REPLAY_FRAME_LIMIT = 120;
const ACTIVE_SESSION_EMERGENCY_FRAME_LIMIT = 24;
const ACTIVE_SESSION_READ_LIMIT_BYTES = 900_000;

type StoredSessionFinalizationReason = "complete" | "player-eliminated" | "time-elapsed";
type StoredSessionOwnerResolution =
  | { status: "match"; owner: ActiveBattleSessionOwner }
  | { status: "mismatch" }
  | { status: "invalid" };

export function readActiveBattleSession(owner: ActiveBattleSessionOwner): ActiveBattleSession | null {
  const session = readStoredActiveBattleSession(getActiveBattleSessionStorageKey(owner), owner, { advanceElapsed: true })
    ?? migrateLegacyStoredBattleSession(LEGACY_ACTIVE_BATTLE_SESSION_KEY, getActiveBattleSessionStorageKey(owner), owner, {
      advanceElapsed: true
    });
  if (!session) {
    return null;
  }

  const finalizationReason = getStoredSessionFinalizationReason(session, session.snapshot);
  if (finalizationReason) {
    writeCompletedActiveBattleSession(normalizeCompletedActiveBattleSession(session, finalizationReason));
    return null;
  }

  return session;
}

export function publishActiveBattleSessionEpoch(owner: ActiveBattleSessionOwner): string {
  const epoch = createActiveBattleSessionEpoch();
  writeActiveBattleSessionEpoch(owner, epoch);
  return epoch;
}

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
  return normalizeCompletedActiveBattleSession(session);
}

export function readCompletedActiveBattleSession(owner: ActiveBattleSessionOwner): ActiveBattleSession | null {
  const completedSession =
    readStoredActiveBattleSession(getCompletedBattleSessionStorageKey(owner), owner)
    ?? migrateLegacyStoredBattleSession(
      LEGACY_COMPLETED_BATTLE_SESSION_KEY,
      getCompletedBattleSessionStorageKey(owner),
      owner
    );
  if (completedSession) {
    return normalizeCompletedActiveBattleSession(completedSession);
  }

  const legacySession =
    readStoredActiveBattleSession(getActiveBattleSessionStorageKey(owner), owner)
    ?? migrateLegacyStoredBattleSession(LEGACY_ACTIVE_BATTLE_SESSION_KEY, getActiveBattleSessionStorageKey(owner), owner);
  if (!legacySession) {
    return null;
  }

  const reason = getStoredSessionFinalizationReason(legacySession, legacySession.snapshot);
  return reason ? normalizeCompletedActiveBattleSession(legacySession, reason) : null;
}

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

export function writeCompletedActiveBattleSession(session: ActiveBattleSession): void {
  if (!isSessionEpochCurrent(session)) {
    return;
  }

  const normalizedSession = normalizeCompletedActiveBattleSession(session);
  const completedStorageKey = getCompletedBattleSessionStorageKey(normalizedSession.owner);
  const previousSession = readStoredActiveBattleSession(completedStorageKey, normalizedSession.owner);
  const sessionToStore =
    previousSession && previousSession.battleId === normalizedSession.battleId
      ? mergeCompletedActiveBattleSessions(normalizeCompletedActiveBattleSession(previousSession), normalizedSession)
      : normalizedSession;

  writeStoredBattleSession(completedStorageKey, sessionToStore);
  clearStoredBattleSession(getActiveBattleSessionStorageKey(normalizedSession.owner));
  clearStoredBattleSession(LEGACY_ACTIVE_BATTLE_SESSION_KEY);
}

export function clearActiveBattleSession(owner: ActiveBattleSessionOwner): void {
  clearStoredBattleSession(getActiveBattleSessionStorageKey(owner));
  clearStoredBattleSession(getCompletedBattleSessionStorageKey(owner));
  clearStoredBattleSession(LEGACY_ACTIVE_BATTLE_SESSION_KEY);
  clearStoredBattleSession(LEGACY_COMPLETED_BATTLE_SESSION_KEY);
}

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
    const session: ActiveBattleSession = {
      version: 1,
      owner: ownerResolution.owner,
      ...(sessionEpoch ? { sessionEpoch } : {}),
      battleId: typeof parsed.battleId === "string" && parsed.battleId.trim() ? parsed.battleId : `battle-${savedAt}`,
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

  const reason = getStoredSessionFinalizationReason(legacySession, legacySession.snapshot);
  if (!reason) {
    return null;
  }

  return normalizeCompletedActiveBattleSession(legacySession, reason);
}

function resolveStoredSessionOwner(
  storedOwner: Partial<ActiveBattleSessionOwner> | null | undefined,
  snapshot: GameSnapshot,
  expectedOwner: ActiveBattleSessionOwner
): StoredSessionOwnerResolution {
  const normalizedExpectedOwner = normalizeSessionOwner(expectedOwner);
  if (storedOwner) {
    const normalizedStoredOwner = normalizeStoredSessionOwner(storedOwner);
    if (!normalizedStoredOwner) {
      return { status: "invalid" };
    }

    return isSameSessionOwner(normalizedStoredOwner, normalizedExpectedOwner)
      ? { status: "match", owner: normalizedStoredOwner }
      : { status: "mismatch" };
  }

  if (normalizedExpectedOwner.sessionToken) {
    return { status: "mismatch" };
  }

  return doesLegacySessionMatchOwner(snapshot, normalizedExpectedOwner)
    ? { status: "match", owner: normalizedExpectedOwner }
    : { status: "mismatch" };
}

function normalizeStoredSessionOwner(
  owner: Partial<ActiveBattleSessionOwner> | null | undefined
): ActiveBattleSessionOwner | null {
  if (!owner) {
    return null;
  }

  const handle = typeof owner.handle === "string" ? owner.handle.trim() : "";
  if (!handle) {
    return null;
  }

  const sessionToken =
    typeof owner.sessionToken === "string" && owner.sessionToken.trim() ? owner.sessionToken.trim() : null;
  return {
    handle,
    sessionToken
  };
}

function normalizeOptionalStoredString(value: unknown): string | null {
  return typeof value === "string" && value.trim() ? value.trim() : null;
}

function normalizeSessionOwner(owner: ActiveBattleSessionOwner): ActiveBattleSessionOwner {
  return {
    handle: owner.handle.trim(),
    sessionToken: owner.sessionToken?.trim() ? owner.sessionToken.trim() : null
  };
}

function isSameSessionOwner(left: ActiveBattleSessionOwner, right: ActiveBattleSessionOwner): boolean {
  if (normalizeHandle(left.handle) !== normalizeHandle(right.handle)) {
    return false;
  }

  if (left.sessionToken || right.sessionToken) {
    return left.sessionToken !== null && right.sessionToken !== null && left.sessionToken === right.sessionToken;
  }

  return true;
}

function doesLegacySessionMatchOwner(snapshot: GameSnapshot, owner: ActiveBattleSessionOwner): boolean {
  const player = snapshot.heroes.find((hero) => hero.heroId === snapshot.playerHeroId);
  return normalizeHandle(player?.displayName ?? "") === normalizeHandle(owner.handle);
}

function advanceActiveBattleSessionElapsed(session: ActiveBattleSession, now: number): ActiveBattleSession {
  if (isSharedAuthoritativeSession(session)) {
    return session;
  }

  const savedAt = Number.isFinite(session.savedAt) ? session.savedAt : now;
  const offlineElapsedMs = Math.max(0, now - savedAt);
  if (offlineElapsedMs <= 0) {
    return session;
  }

  const snapshotElapsedMs = normalizeBattleElapsedMs(session.snapshot.elapsedMs);
  const nextElapsedMs = Math.min(BATTLE_MATCH_DURATION_MS, snapshotElapsedMs + offlineElapsedMs);

  return {
    ...session,
    savedAt: now,
    snapshot: {
      ...session.snapshot,
      elapsedMs: nextElapsedMs
    }
  };
}

function normalizeBattleElapsedMs(elapsedMs: number): number {
  if (!Number.isFinite(elapsedMs)) {
    return 0;
  }

  return Math.max(0, Math.min(elapsedMs, BATTLE_MATCH_DURATION_MS));
}

function normalizeCompletedActiveBattleSession(
  session: ActiveBattleSession,
  reason = getStoredSessionFinalizationReason(session, session.snapshot) ??
    (isSharedAuthoritativeSession(session) ? "complete" : "time-elapsed")
): ActiveBattleSession {
  if (reason !== "time-elapsed" || isBattleComplete(session.snapshot)) {
    return session;
  }

  const snapshot = session.snapshot as GameSnapshot;
  return {
    ...session,
    snapshot: {
      ...snapshot,
      elapsedMs: BATTLE_MATCH_DURATION_MS
    }
  };
}

function mergeCompletedActiveBattleSessions(
  previousSession: ActiveBattleSession,
  nextSession: ActiveBattleSession
): ActiveBattleSession {
  const snapshot =
    nextSession.snapshot.elapsedMs >= previousSession.snapshot.elapsedMs ? nextSession.snapshot : previousSession.snapshot;
  const replayFrames = selectMoreCompleteReplayFrames(previousSession.replayFrames, nextSession.replayFrames);

  return {
    ...nextSession,
    savedAt: Math.max(previousSession.savedAt, nextSession.savedAt),
    snapshot,
    replayFrames,
    localAuthoritativePlayerId:
      nextSession.localAuthoritativePlayerId ?? previousSession.localAuthoritativePlayerId,
    localAuthoritativeTicketId:
      nextSession.localAuthoritativeTicketId ?? previousSession.localAuthoritativeTicketId,
    lastReplaySampleElapsed: maxNullableNumber(
      previousSession.lastReplaySampleElapsed,
      nextSession.lastReplaySampleElapsed,
      getLastReplayFrameElapsedMs(replayFrames)
    )
  };
}

function selectMoreCompleteReplayFrames(
  previousFrames: ActiveBattleSession["replayFrames"],
  nextFrames: ActiveBattleSession["replayFrames"]
): ActiveBattleSession["replayFrames"] {
  return isReplayFrameSetMoreComplete(nextFrames, previousFrames) ? nextFrames : previousFrames;
}

function isReplayFrameSetMoreComplete(
  candidateFrames: ActiveBattleSession["replayFrames"],
  currentFrames: ActiveBattleSession["replayFrames"]
): boolean {
  if (candidateFrames.length === 0) {
    return false;
  }
  if (currentFrames.length === 0) {
    return true;
  }

  const candidateLastElapsedMs = getLastReplayFrameElapsedMs(candidateFrames) ?? 0;
  const currentLastElapsedMs = getLastReplayFrameElapsedMs(currentFrames) ?? 0;
  if (candidateLastElapsedMs !== currentLastElapsedMs) {
    return candidateLastElapsedMs > currentLastElapsedMs;
  }

  const candidateSpanMs = getReplayFrameSpanMs(candidateFrames);
  const currentSpanMs = getReplayFrameSpanMs(currentFrames);
  if (candidateSpanMs !== currentSpanMs) {
    return candidateSpanMs > currentSpanMs;
  }

  return candidateFrames.length > currentFrames.length;
}

function getReplayFrameSpanMs(frames: ActiveBattleSession["replayFrames"]): number {
  if (frames.length === 0) {
    return 0;
  }

  const firstFrame = frames[0];
  const lastElapsedMs = getLastReplayFrameElapsedMs(frames) ?? firstFrame.elapsedMs;
  return Math.max(0, lastElapsedMs - firstFrame.elapsedMs);
}

function getLastReplayFrameElapsedMs(frames: ActiveBattleSession["replayFrames"]): number | null {
  const lastFrame = frames[frames.length - 1];
  return lastFrame && Number.isFinite(lastFrame.elapsedMs) ? lastFrame.elapsedMs : null;
}

function maxNullableNumber(...values: Array<number | null>): number | null {
  const finiteValues = values.filter((value): value is number => typeof value === "number" && Number.isFinite(value));
  return finiteValues.length > 0 ? Math.max(...finiteValues) : null;
}

function getStoredSessionFinalizationReason(
  session: Partial<ActiveBattleSession>,
  snapshot: GameSnapshot
): StoredSessionFinalizationReason | null {
  if (isSharedAuthoritativeSession(session)) {
    return null;
  }

  if (isBattleComplete(snapshot)) {
    return "complete";
  }

  if (!isSharedAuthoritativeSession(session) && isLocalPlayerEliminated(snapshot)) {
    return "player-eliminated";
  }

  const savedAt = typeof session.savedAt === "number" && Number.isFinite(session.savedAt) ? session.savedAt : null;
  if (savedAt === null) {
    return null;
  }

  const elapsedMs = Math.max(0, Math.min((snapshot as GameSnapshot).elapsedMs, BATTLE_MATCH_DURATION_MS));
  const remainingMs = Math.max(0, BATTLE_MATCH_DURATION_MS - elapsedMs);
  return savedAt + remainingMs <= Date.now() ? "time-elapsed" : null;
}

function isSharedAuthoritativeSession(session: Partial<ActiveBattleSession>): boolean {
  return session.sharedAuthoritativeRuntime === true;
}

function normalizeHandle(handle: string): string {
  return handle.trim().toLowerCase();
}
