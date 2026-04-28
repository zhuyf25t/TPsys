import type { GameSnapshot } from "../../../domain/types";
import { BATTLE_MATCH_DURATION_MS } from "../local/battleLocalGateway";
import { isBattleComplete, isLocalPlayerEliminated } from "../runtime-local/session/battleCompletion";
import type { InitialBattleParticipantsConfig } from "../runtime-local/session/initialBattleSnapshot";
import {
  createBotOnlyBattleClosure,
  type BotOnlyBattleClosure
} from "../runtime-local/session/botOnlyBattleClosure";
import type { ActiveBattleSession } from "./battlePageTypes";
import type { MatchmakingQueueState } from "./matchmakingQueueTypes";

export const START_BATTLE_QUEUE_REFRESH_TIMEOUT_MS = 400;
export const MATCH_START_RECHECK_MS = 25;
export const BATTLE_COMPLETION_CHECK_INTERVAL_MS = 100;

export function buildInitialBattleParticipants(
  localPlayerHandle: string,
  queueState: MatchmakingQueueState | null
): InitialBattleParticipantsConfig {
  const bootstrapSeats = queueState?.battleSession?.bootstrap?.seats;
  if (bootstrapSeats && bootstrapSeats.length > 0) {
    return {
      localPlayerHandle,
      localPlayerId: queueState?.playerId,
      queuedHandles: bootstrapSeats.filter((seat) => !seat.isBot).map((seat) => seat.handle),
      capacity: queueState?.battleSession?.capacity ?? queueState?.capacity ?? bootstrapSeats.length,
      seats: bootstrapSeats.map((seat) => ({
        seat: seat.seat,
        playerId: seat.playerId,
        heroId: seat.heroId,
        handle: seat.handle,
        displayName: seat.displayName,
        joinedAt: seat.joinedAt,
        isBot: seat.isBot,
        spawnPointIndex: seat.spawnPointIndex,
        ...(seat.rating !== undefined ? { rating: seat.rating } : {}),
        ...(seat.avatar ? { avatar: seat.avatar } : {}),
        ...(seat.skin ? { skin: seat.skin } : {})
      }))
    };
  }

  const rosterHandles =
    queueState?.battleSession?.roster.map((entry) => entry.handle).filter((handle) => handle.trim().length > 0) ?? [];

  return {
    localPlayerHandle,
    localPlayerId: queueState?.playerId,
    queuedHandles:
      rosterHandles.length > 0
        ? rosterHandles
        : queueState?.queuedHandles.length
          ? queueState.queuedHandles
          : [localPlayerHandle],
    capacity: queueState?.battleSession?.capacity ?? queueState?.capacity ?? 6
  };
}

export function resolveBackendBattleId(queueState: MatchmakingQueueState | null): string | null {
  const battleId = queueState?.battleSession?.battleId?.trim();
  return battleId ? battleId : null;
}

export function requiresAuthoritativeBattleId(queueState: MatchmakingQueueState | null): boolean {
  return queueState?.source === "backend";
}

export function createLocalBattleId(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return `battle-${crypto.randomUUID()}`;
  }

  return `battle-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

export function shouldFinalizeBattleSnapshotOnExit(
  snapshot: GameSnapshot | null,
  durationExpired: boolean
): snapshot is GameSnapshot {
  return shouldStoreCompletedBattleSession(snapshot, durationExpired);
}

export function shouldStoreCompletedBattleSession(snapshot: GameSnapshot | null, durationExpired: boolean): boolean {
  if (!snapshot) {
    return false;
  }

  return isBattleComplete(snapshot) || durationExpired || isLocalPlayerEliminated(snapshot);
}

export function shouldFinalizeBattleSnapshot(
  snapshot: GameSnapshot | null,
  durationExpired: boolean,
  forceCurrentSnapshot: boolean
): snapshot is GameSnapshot {
  return Boolean(
    snapshot && (isBattleComplete(snapshot) || durationExpired || forceCurrentSnapshot || isLocalPlayerEliminated(snapshot))
  );
}

export function createExitedBattleSnapshot(snapshot: GameSnapshot): GameSnapshot {
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

export interface BattleFinalizationResolution {
  finalSnapshot: GameSnapshot;
  botOnlyClosure: BotOnlyBattleClosure | null;
}

export function resolveBattleFinalizationSnapshot(
  snapshot: GameSnapshot,
  forceTimeLimit: boolean
): BattleFinalizationResolution {
  const timeResolvedSnapshot =
    !forceTimeLimit || snapshot.elapsedMs >= BATTLE_MATCH_DURATION_MS
      ? snapshot
      : {
          ...snapshot,
          elapsedMs: BATTLE_MATCH_DURATION_MS
        };
  const botOnlyClosure = createBotOnlyBattleClosure(timeResolvedSnapshot, {
    maxElapsedMs: BATTLE_MATCH_DURATION_MS
  });

  return {
    finalSnapshot: botOnlyClosure?.snapshot ?? timeResolvedSnapshot,
    botOnlyClosure
  };
}

export function createFinalBattleSnapshot(snapshot: GameSnapshot, forceTimeLimit: boolean): GameSnapshot {
  return resolveBattleFinalizationSnapshot(snapshot, forceTimeLimit).finalSnapshot;
}

export function isActiveBattleSessionForLocalPlayer(
  session: ActiveBattleSession,
  localPlayerHandle: string
): boolean {
  const expectedHandle = normalizeHandle(localPlayerHandle);
  if (!expectedHandle) {
    return true;
  }

  const player = session.snapshot.heroes.find((hero) => hero.heroId === session.snapshot.playerHeroId);
  return normalizeHandle(player?.displayName ?? "") === expectedHandle;
}

export function isActiveBattleSessionCompatibleWithQueueState(
  session: ActiveBattleSession,
  queueState: MatchmakingQueueState | null
): boolean {
  const backendBattleId = resolveBackendBattleId(queueState);
  if (!backendBattleId) {
    return true;
  }

  return session.battleId.trim() === backendBattleId;
}

function normalizeHandle(handle: string): string {
  return handle.trim().toLowerCase();
}
