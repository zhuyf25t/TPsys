import type { ItemPickup, Vec2, WeaponPickup } from "../../../../objects/battle/types";
import { getItemPickupSpawnPoints, getWeaponPickupSpawnPoints } from "../../game/assets/battleContentCatalog";
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

/** 中文名：推进拾取物生命周期（advancePickupLifecycle）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function advancePickupLifecycle(input: AdvancePickupLifecycleInput): void {
  advanceWeaponPickups(input);
  advanceItemPickups(input);
}

/** 中文名：查找nearby拾取物（findNearbyPickup）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
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

/** 中文名：查找nearbyitem拾取物（findNearbyItemPickup）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
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
      pickup.position = resolvePickupSpawnPoint("weapon", pickup.weaponId, getWeaponPickupSpawnPoints(), createPickupSpawnResolverContext(input));
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
      pickup.position = resolvePickupSpawnPoint("medkit", pickup.pickupId, getItemPickupSpawnPoints(), createPickupSpawnResolverContext(input));
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
