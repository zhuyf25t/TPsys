import type { BattleHeroViewState as Hero } from "../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type {
  BattleInitialHeroConfig,
  BattleInitialParticipantSeat,
  BattleInitialParticipantsConfig
} from "../../../../../objects/battle/microservices/session/objects/state/BattleInitialParticipants";
import type { BattleGameSnapshot as GameSnapshot } from "../../../../../objects/battle/microservices/session/objects/state/BattleGameSnapshot";
import type { BattleVector2 as Vec2 } from "../../../../../objects/battle/objects/core/BattleCoreScalars";

export interface BattleInitialSnapshotStateInput {
  heroes: GameSnapshot["heroes"];
  weaponPickups: GameSnapshot["weaponPickups"];
  itemPickups: GameSnapshot["itemPickups"];
  gasZone: GameSnapshot["gasZone"];
  extraction: GameSnapshot["extraction"];
  lootCaches: GameSnapshot["lootCaches"];
  worldSize: Vec2;
  playerHeroId: string;
}

export function normalizeBattleInitialSeatAssignments(
  seats: readonly BattleInitialParticipantSeat[] | undefined
): BattleInitialParticipantSeat[] {
  if (!seats?.length) {
    return [];
  }

  return [...seats].sort((left, right) => left.seat - right.seat);
}

export function buildBattleInitialSeatHeroConfigs(
  seats: readonly BattleInitialParticipantSeat[]
): BattleInitialHeroConfig[] {
  return seats.map((seat) => ({
    heroId: seat.heroId,
    displayName: seat.displayName,
    skin: seat.skin,
    spawnPointIndex: seat.spawnPointIndex
  }));
}

export function applyBattleInitialParticipantsToHeroes(
  heroes: readonly Hero[],
  participants: BattleInitialParticipantsConfig | undefined,
  botDisplayNameByHeroId: ReadonlyMap<string, string>
): Hero[] {
  const botSlots = heroes.filter((hero) => hero.heroId !== "player-1");
  const displayNameByHeroId = new Map<string, string>();
  botSlots.forEach((hero) => {
    const botDisplayName = normalizeHandle(botDisplayNameByHeroId.get(hero.heroId) ?? "");
    if (botDisplayName) {
      displayNameByHeroId.set(hero.heroId, botDisplayName);
    }
  });

  if (participants) {
    const localPlayerHandle = normalizeHandle(participants.localPlayerHandle);
    if (localPlayerHandle && heroes.some((hero) => hero.heroId === "player-1")) {
      displayNameByHeroId.set("player-1", localPlayerHandle);
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
        displayNameByHeroId.set(slot.heroId, handle);
      }
    });
  }

  return heroes.map((hero) => {
    const displayName = displayNameByHeroId.get(hero.heroId);
    return displayName === undefined ? hero : { ...hero, displayName };
  });
}

export function resolveBattleInitialPlayerHeroId(
  participants: BattleInitialParticipantsConfig | undefined,
  seats: readonly BattleInitialParticipantSeat[]
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

  return seats.find((seat) => normalizeHandle(seat.handle).toLowerCase() === localHandle)?.heroId ?? "player-1";
}

export function createBattleInitialSnapshotState({
  heroes,
  weaponPickups,
  itemPickups,
  gasZone,
  extraction,
  lootCaches,
  worldSize,
  playerHeroId
}: BattleInitialSnapshotStateInput): GameSnapshot {
  return {
    heroes,
    projectiles: [],
    slowFields: [],
    weaponPickups,
    itemPickups,
    gasZone,
    extraction,
    lootCaches,
    events: [],
    worldSize: { x: worldSize.x, y: worldSize.y },
    elapsedMs: 0,
    playerHeroId
  };
}

function normalizeHandle(handle: string): string {
  return handle.trim();
}
