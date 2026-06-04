import type {
  BattleItemPickupState as ItemPickup,
  BattleWeaponPickupState as WeaponPickup
} from "../../../../../objects/battle/microservices/abilities/objects/pickup/BattlePickupState";
import type { BattleHeroViewState as Hero } from "../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { BattleVector2 as Vec2 } from "../../../../../objects/battle/objects/core/BattleCoreScalars";
import { createWeaponState, findWeaponIndex, refillWeaponState } from "../../combat/functions/BattleWeaponInventoryRules";

export const BATTLE_PICKUP_RESPAWN_MS = 10000;
export const BATTLE_MEDKIT_HEAL_HP = 25;

export interface AutomaticPickupRulesInput {
  player: Hero;
  weaponPickups: readonly WeaponPickup[];
  itemPickups: readonly ItemPickup[];
  autoPickupRadius: number;
}

export interface AutomaticWeaponPickupResult {
  pickup: WeaponPickup;
  weaponKind: WeaponPickup["weaponKind"];
  action: "equip" | "refill";
}

export interface AutomaticItemPickupResult {
  pickup: ItemPickup;
  kind: ItemPickup["kind"];
  wasFullHp: boolean;
}

export interface PickupRespawnLifecycleInput {
  deltaMs: number;
  weaponPickups: readonly WeaponPickup[];
  itemPickups: readonly ItemPickup[];
  resolveWeaponRespawnPosition(pickup: WeaponPickup): Vec2;
  resolveItemRespawnPosition(pickup: ItemPickup): Vec2;
}

export function applyAutomaticWeaponPickup(input: AutomaticPickupRulesInput): AutomaticWeaponPickupResult | null {
  if (!input.player.alive) {
    return null;
  }

  const pickup = findNearbyWeaponPickup(input.player.position, input.weaponPickups, input.autoPickupRadius);
  if (!pickup) {
    return null;
  }

  const existingIndex = findWeaponIndex(input.player.weapons, pickup.weaponKind);
  if (existingIndex >= 0) {
    input.player.weapons[existingIndex] = refillWeaponState(input.player.weapons[existingIndex]);
  } else {
    input.player.weapons.push(createWeaponState(pickup.weaponKind));
  }

  pickup.available = false;
  pickup.respawnMs = BATTLE_PICKUP_RESPAWN_MS;

  return {
    pickup,
    weaponKind: pickup.weaponKind,
    action: existingIndex >= 0 ? "refill" : "equip"
  };
}

export function applyAutomaticItemPickup(input: AutomaticPickupRulesInput): AutomaticItemPickupResult | null {
  if (!input.player.alive) {
    return null;
  }

  const pickup = findNearbyItemPickup(input.player.position, input.itemPickups, input.autoPickupRadius);
  if (!pickup) {
    return null;
  }

  if (pickup.kind !== "Medkit") {
    return null;
  }

  const wasFullHp = input.player.hp >= input.player.maxHp;
  input.player.hp = Math.min(input.player.maxHp, input.player.hp + BATTLE_MEDKIT_HEAL_HP);
  pickup.available = false;
  pickup.respawnMs = BATTLE_PICKUP_RESPAWN_MS;

  return {
    pickup,
    kind: pickup.kind,
    wasFullHp
  };
}

export function advancePickupRespawnLifecycle(input: PickupRespawnLifecycleInput): void {
  advanceWeaponPickupRespawns(input);
  advanceItemPickupRespawns(input);
}

export function findNearbyWeaponPickup(
  position: Vec2,
  pickups: readonly WeaponPickup[],
  radius: number
): WeaponPickup | null {
  let closest: WeaponPickup | null = null;
  let closestDistance = radius;

  pickups.forEach((pickup) => {
    if (!pickup.available) {
      return;
    }

    const distance = distanceBetween(position, pickup.position);
    if (distance <= closestDistance) {
      closest = pickup;
      closestDistance = distance;
    }
  });

  return closest;
}

export function findNearbyItemPickup(position: Vec2, pickups: readonly ItemPickup[], radius: number): ItemPickup | null {
  let closest: ItemPickup | null = null;
  let closestDistance = radius;

  pickups.forEach((pickup) => {
    if (!pickup.available) {
      return;
    }

    const distance = distanceBetween(position, pickup.position);
    if (distance <= closestDistance) {
      closest = pickup;
      closestDistance = distance;
    }
  });

  return closest;
}

function advanceWeaponPickupRespawns(input: PickupRespawnLifecycleInput): void {
  input.weaponPickups.forEach((pickup) => {
    if (pickup.available || pickup.respawnMs <= 0) {
      return;
    }

    pickup.respawnMs = Math.max(0, pickup.respawnMs - input.deltaMs);
    if (pickup.respawnMs === 0) {
      pickup.position = input.resolveWeaponRespawnPosition(pickup);
      pickup.available = true;
    }
  });
}

function advanceItemPickupRespawns(input: PickupRespawnLifecycleInput): void {
  input.itemPickups.forEach((pickup) => {
    if (pickup.available || pickup.respawnMs <= 0) {
      return;
    }

    pickup.respawnMs = Math.max(0, pickup.respawnMs - input.deltaMs);
    if (pickup.respawnMs === 0) {
      pickup.position = input.resolveItemRespawnPosition(pickup);
      pickup.available = true;
    }
  });
}

function distanceBetween(left: { x: number; y: number }, right: { x: number; y: number }): number {
  return Math.hypot(left.x - right.x, left.y - right.y);
}
