import type { GameSnapshot } from "../../../objects/types";
import { WORLD_SIZE } from "../../../game/constants";
import {
  createInitialHeroes,
  createInitialItemPickups,
  createInitialWeaponPickups,
  type InitialHeroConfig
} from "../../../game/spawn";
import { getBotProfileById } from "../../../../bots/runtime/registry/botRegistry";

export interface InitialBattleParticipantSeat {
  seat: number;
  playerId?: string;
  heroId: string;
  handle: string;
  displayName: string;
  joinedAt: number;
  isBot: boolean;
  spawnPointIndex: number;
  rating?: number;
  avatar?: string;
  skin?: string;
}

export interface InitialBattleParticipantsConfig {
  localPlayerHandle: string;
  localPlayerId?: string;
  queuedHandles: string[];
  capacity: number;
  seats?: InitialBattleParticipantSeat[];
}

/** 中文名：创建initial战斗快照（createInitialBattleSnapshot）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function createInitialBattleSnapshot(participants?: InitialBattleParticipantsConfig, worldSize = WORLD_SIZE): GameSnapshot {
  const seatAssignments = normalizeSeatAssignments(participants?.seats);
  const heroes = createInitialHeroes(
    seatAssignments.length > 0
      ? seatAssignments.map<InitialHeroConfig>((seat) => ({
          heroId: seat.heroId,
          displayName: seat.displayName,
          skin: seat.skin,
          spawnPointIndex: seat.spawnPointIndex
        }))
      : undefined
  );
  if (seatAssignments.length === 0) {
    applyInitialParticipants(heroes, participants);
  }

  return {
    heroes,
    projectiles: [],
    slowFields: [],
    weaponPickups: createInitialWeaponPickups(),
    itemPickups: createInitialItemPickups(),
    events: [],
    worldSize: { x: worldSize.x, y: worldSize.y },
    elapsedMs: 0,
    playerHeroId: resolvePlayerHeroId(participants, seatAssignments)
  };
}

function applyInitialParticipants(
  heroes: GameSnapshot["heroes"],
  participants: InitialBattleParticipantsConfig | undefined
): void {
  const botSlots = heroes.filter((hero) => hero.heroId !== "player-1");
  botSlots.forEach((hero) => {
    const botProfile = getBotProfileById(hero.heroId);
    if (botProfile) {
      hero.displayName = botProfile.displayName;
    }
  });

  if (!participants) {
    return;
  }

  const localPlayerHandle = normalizeHandle(participants.localPlayerHandle);
  const localPlayer = heroes.find((hero) => hero.heroId === "player-1");
  if (localPlayerHandle && localPlayer) {
    localPlayer.displayName = localPlayerHandle;
  }

  const localKey = localPlayerHandle.toLowerCase();
  const seenHandles = new Set(localKey ? [localKey] : []);
  const queuedHumanHandles = participants.queuedHandles
    .map(normalizeHandle)
    .filter((handle) => {
      const key = handle.toLowerCase();
      if (!handle || seenHandles.has(key)) {
        return false;
      }

      seenHandles.add(key);
      return true;
    });

  const humanCapacity = Math.max(0, Math.min(participants.capacity, heroes.length) - 1);
  queuedHumanHandles.slice(0, Math.min(humanCapacity, botSlots.length)).forEach((handle, index) => {
    const slot = botSlots[index];
    if (slot) {
      slot.displayName = handle;
    }
  });
}

function normalizeHandle(handle: string): string {
  return handle.trim();
}

function normalizeSeatAssignments(
  seats: InitialBattleParticipantsConfig["seats"]
): InitialBattleParticipantSeat[] {
  if (!seats?.length) {
    return [];
  }

  return [...seats].sort((left, right) => left.seat - right.seat);
}

function resolvePlayerHeroId(
  participants: InitialBattleParticipantsConfig | undefined,
  seats: InitialBattleParticipantSeat[]
): string {
  if (seats.length === 0) {
    return "player-1";
  }

  const localHandle = normalizeHandle(participants?.localPlayerHandle ?? "").toLowerCase();
  const localPlayerId = normalizeHandle(participants?.localPlayerId ?? "");
  if (localPlayerId) {
    return seats.find((seat) => normalizeHandle(seat.playerId ?? "") === localPlayerId)?.heroId ?? "player-1";
  }

  if (!localHandle) {
    return "player-1";
  }

  return (
    seats.find((seat) => normalizeHandle(seat.handle).toLowerCase() === localHandle)?.heroId ??
    "player-1"
  );
}
