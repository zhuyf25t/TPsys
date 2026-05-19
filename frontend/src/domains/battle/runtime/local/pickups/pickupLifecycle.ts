import type { ItemPickup, Vec2, WeaponPickup } from "../../../objects/types";
import { ITEM_PICKUP_SPAWN_POINTS, WEAPON_PICKUP_SPAWN_POINTS } from "../../../game/spawn";
import { resolvePickupSpawnPoint, type PickupSpawnResolverContext, type RectLike } from "./pickupSpawnResolver";

interface PickupObstacleBoundsLike {
  position: Vec2;
  size: Vec2;
}

interface PickupOccludableLike {
  bounds: RectLike;
}

export interface PickupLifecycleContextInput {
  worldSize: Vec2;
  obstacleBounds: readonly PickupObstacleBoundsLike[];
  occludables: readonly PickupOccludableLike[];
  weaponPickups: readonly WeaponPickup[];
  itemPickups: readonly ItemPickup[];
}

export interface AdvancePickupLifecycleInput extends PickupLifecycleContextInput {
  deltaMs: number;
}

export function advancePickupLifecycle(input: AdvancePickupLifecycleInput): void {
  advanceWeaponPickups(input);
  advanceItemPickups(input);
}

export function findNearbyPickup(
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

export function findNearbyItemPickup(
  position: Vec2,
  pickups: readonly ItemPickup[],
  radius: number
): ItemPickup | null {
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

function advanceWeaponPickups(input: AdvancePickupLifecycleInput): void {
  input.weaponPickups.forEach((pickup) => {
    if (pickup.available || pickup.respawnMs <= 0) {
      return;
    }

    pickup.respawnMs = Math.max(0, pickup.respawnMs - input.deltaMs);
    if (pickup.respawnMs === 0) {
      pickup.position = resolvePickupSpawnPoint("weapon", pickup.weaponId, WEAPON_PICKUP_SPAWN_POINTS, createPickupSpawnResolverContext(input));
      pickup.available = true;
    }
  });
}

function advanceItemPickups(input: AdvancePickupLifecycleInput): void {
  input.itemPickups.forEach((pickup) => {
    if (pickup.available || pickup.respawnMs <= 0) {
      return;
    }

    pickup.respawnMs = Math.max(0, pickup.respawnMs - input.deltaMs);
    if (pickup.respawnMs === 0) {
      pickup.position = resolvePickupSpawnPoint("medkit", pickup.pickupId, ITEM_PICKUP_SPAWN_POINTS, createPickupSpawnResolverContext(input));
      pickup.available = true;
    }
  });
}

function createPickupSpawnResolverContext(input: PickupLifecycleContextInput): PickupSpawnResolverContext {
  return {
    worldSize: input.worldSize,
    obstacleBounds: input.obstacleBounds.map((obstacle) => ({
      x: obstacle.position.x,
      y: obstacle.position.y,
      width: obstacle.size.x,
      height: obstacle.size.y
    })),
    occludableBounds: input.occludables.map((occludable) => ({
      x: occludable.bounds.x,
      y: occludable.bounds.y,
      width: occludable.bounds.width,
      height: occludable.bounds.height
    })),
    weaponPickups: input.weaponPickups,
    itemPickups: input.itemPickups
  };
}

function distanceBetween(left: Vec2, right: Vec2): number {
  return Math.hypot(left.x - right.x, left.y - right.y);
}
