import type { BattleGameSnapshot as GameSnapshot } from "../../../objects/battle/microservices/session/objects/state/BattleGameSnapshot";
import { BATTLE_MATCH_DURATION_MS } from "../../../runtime/battle/local/state/battleLocalGateway";
import { isBattleComplete, isLocalPlayerEliminated } from "../../../runtime/battle/microservices/runtime/functions/BattleRuntimeFinishRules";
import type { BattleInitialParticipantsConfig } from "../../../objects/battle/microservices/session/objects/state/BattleInitialParticipants";
import {
  createBotOnlyBattleClosure,
  type BotOnlyBattleClosure
} from "../../../runtime/battle/microservices/projections/functions/BattleBotOnlyClosureReplayRules";
import type { ActiveBattleSession } from "../objects/BattlePageState";
import type { MatchmakingQueueState } from "../../../runtime/battle/matchmaking/matchmakingQueueTypes";
import { BATTLE_RUNTIME_STARTUP_QUEUE_REFRESH_TIMEOUT_MS } from "../../../runtime/battle/BattleRuntimeNetworkConfig";

export const START_BATTLE_QUEUE_REFRESH_TIMEOUT_MS = BATTLE_RUNTIME_STARTUP_QUEUE_REFRESH_TIMEOUT_MS;
export const MATCH_START_RECHECK_MS = 25;
export const BATTLE_COMPLETION_CHECK_INTERVAL_MS = 100;
const ZOMBIE_BOT_SKIN_ID = "zombie";

/** 中文名：构建initial战斗participants（buildInitialBattleParticipants）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function buildInitialBattleParticipants(
  localPlayerHandle: string,
  queueState: MatchmakingQueueState | null
): BattleInitialParticipantsConfig {
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
        displayName: seat.isBot ? `Zombie ${Math.max(1, seat.seat)}` : seat.displayName,
        joinedAt: seat.joinedAt,
        isBot: seat.isBot,
        spawnPointIndex: seat.spawnPointIndex,
        ...(seat.rating !== undefined ? { rating: seat.rating } : {}),
        ...(seat.avatar ? { avatar: seat.avatar } : {}),
        ...(seat.isBot ? { skin: ZOMBIE_BOT_SKIN_ID } : seat.skin ? { skin: seat.skin } : {})
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

/** 中文名：解析backend战斗标识（resolveBackendBattleId）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function resolveBackendBattleId(queueState: MatchmakingQueueState | null): string | null {
  const battleId = queueState?.battleSession?.battleId?.trim();
  return battleId ? battleId : null;
}

/** 中文名：requiresauthoritative战斗标识（requiresAuthoritativeBattleId）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function requiresAuthoritativeBattleId(queueState: MatchmakingQueueState | null): boolean {
  return queueState?.source === "backend";
}

/** 中文名：创建本地战斗标识（createLocalBattleId）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function createLocalBattleId(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return `battle-${crypto.randomUUID()}`;
  }

  return `battle-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

/** 中文名：shouldfinalize战斗快照onexit（shouldFinalizeBattleSnapshotOnExit）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function shouldFinalizeBattleSnapshotOnExit(
  snapshot: GameSnapshot | null,
  durationExpired: boolean
): snapshot is GameSnapshot {
  return shouldStoreCompletedBattleSession(snapshot, durationExpired);
}

/** 中文名：shouldstorecompleted战斗会话（shouldStoreCompletedBattleSession）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function shouldStoreCompletedBattleSession(snapshot: GameSnapshot | null, durationExpired: boolean): boolean {
  if (!snapshot) {
    return false;
  }

  return isBattleComplete(snapshot) || durationExpired || isLocalPlayerEliminated(snapshot);
}

/** 中文名：shouldfinalize战斗快照（shouldFinalizeBattleSnapshot）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function shouldFinalizeBattleSnapshot(
  snapshot: GameSnapshot | null,
  durationExpired: boolean,
  forceCurrentSnapshot: boolean
): snapshot is GameSnapshot {
  return Boolean(
    snapshot && (isBattleComplete(snapshot) || durationExpired || forceCurrentSnapshot || isLocalPlayerEliminated(snapshot))
  );
}

/** 中文名：创建exited战斗快照（createExitedBattleSnapshot）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
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

/** 中文名：解析战斗finalization快照（resolveBattleFinalizationSnapshot）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
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

/** 中文名：创建final战斗快照（createFinalBattleSnapshot）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function createFinalBattleSnapshot(snapshot: GameSnapshot, forceTimeLimit: boolean): GameSnapshot {
  return resolveBattleFinalizationSnapshot(snapshot, forceTimeLimit).finalSnapshot;
}

/** 中文名：判断是否active战斗会话for本地玩家（isActiveBattleSessionForLocalPlayer）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
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

/** 中文名：判断是否active战斗会话compatiblewith队列状态（isActiveBattleSessionCompatibleWithQueueState）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
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
