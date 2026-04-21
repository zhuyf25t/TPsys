import { BATTLE_MATCH_DURATION_MS } from "../local/battleLocalGateway";
import { compactReplayFrames } from "../../replay/replayRecorder";
import type { GameSnapshot } from "../../../domain/types";
import { getBattleAliveHeroCount, type ActiveBattleSession } from "./battlePageTypes";

const ACTIVE_BATTLE_SESSION_KEY = "slay-demo.active-battle-session.v1";
const ACTIVE_SESSION_REPLAY_FRAME_LIMIT = 6;
const ACTIVE_SESSION_EMERGENCY_FRAME_LIMIT = 0;

export function readActiveBattleSession(): ActiveBattleSession | null {
  if (typeof window === "undefined") {
    return null;
  }

  let raw: string | null = null;
  try {
    raw = window.localStorage.getItem(ACTIVE_BATTLE_SESSION_KEY);
  } catch {
    return null;
  }
  if (!raw) {
    return null;
  }

  try {
    const parsed = JSON.parse(raw) as Partial<ActiveBattleSession>;
    const snapshot = parsed.snapshot;
    if (!snapshot || !Array.isArray(snapshot.heroes) || snapshot.heroes.length === 0) {
      clearActiveBattleSession();
      return null;
    }

    if (isStoredSnapshotComplete(snapshot)) {
      clearActiveBattleSession();
      return null;
    }

    return {
      version: 1,
      savedAt: typeof parsed.savedAt === "number" ? parsed.savedAt : Date.now(),
      snapshot,
      replayFrames: compactReplayFrames(
        Array.isArray(parsed.replayFrames) ? parsed.replayFrames : [],
        ACTIVE_SESSION_REPLAY_FRAME_LIMIT
      ),
      lastReplaySampleElapsed:
        typeof parsed.lastReplaySampleElapsed === "number" ? parsed.lastReplaySampleElapsed : null
    };
  } catch {
    clearActiveBattleSession();
    return null;
  }
}

export function writeActiveBattleSession(session: ActiveBattleSession): void {
  if (typeof window === "undefined") {
    return;
  }

  const compactSession = compactActiveBattleSession(session, ACTIVE_SESSION_REPLAY_FRAME_LIMIT);
  try {
    window.localStorage.setItem(ACTIVE_BATTLE_SESSION_KEY, JSON.stringify(compactSession));
    return;
  } catch {
    // Active battle recovery is useful, but it must never crash the whole app.
  }

  try {
    window.localStorage.setItem(
      ACTIVE_BATTLE_SESSION_KEY,
      JSON.stringify(compactActiveBattleSession(session, ACTIVE_SESSION_EMERGENCY_FRAME_LIMIT))
    );
  } catch {
    clearActiveBattleSession();
  }
}

export function clearActiveBattleSession(): void {
  if (typeof window === "undefined") {
    return;
  }

  try {
    window.localStorage.removeItem(ACTIVE_BATTLE_SESSION_KEY);
  } catch {
    // Storage access can fail in restricted browser modes; recovery state is optional.
  }
}

function compactActiveBattleSession(session: ActiveBattleSession, frameLimit: number): ActiveBattleSession {
  return {
    ...session,
    replayFrames: compactReplayFrames(session.replayFrames, frameLimit)
  };
}

function isStoredSnapshotComplete(snapshot: GameSnapshot): boolean {
  return getBattleAliveHeroCount(snapshot) <= 1 || snapshot.elapsedMs >= BATTLE_MATCH_DURATION_MS;
}
