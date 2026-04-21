import type { ItemPickup, PickupSpawnPoint, Vec2, WeaponPickup } from "../../../../domain/types";

export interface RectLike {
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface PickupSpawnResolverContext {
  worldSize: Vec2;
  obstacleBounds: readonly RectLike[];
  occludableBounds: readonly RectLike[];
  weaponPickups: readonly WeaponPickup[];
  itemPickups: readonly ItemPickup[];
}

const EDGE_PADDING = 48;
const CLEARANCE_RADIUS = 24;
const PICKUP_SPACING = 24;
const OCCLUDABLE_PADDING = 48;

export function resolvePickupSpawnPoint(
  kind: PickupSpawnPoint["kind"],
  pickupId: string,
  spawnPoints: readonly PickupSpawnPoint[],
  context: PickupSpawnResolverContext,
  randomFn: () => number = Math.random
): Vec2 {
  const shuffled = shuffleSpawnPoints(spawnPoints, randomFn);
  const selected = shuffled.find((point) => isPickupSpawnPointAvailable(point, kind, pickupId, context));
  const fallback = spawnPoints.find((point) => isPickupSpawnPointValid(point.position, context)) ?? spawnPoints[0];
  const resolved = selected ?? fallback;

  return {
    x: resolved.position.x,
    y: resolved.position.y
  };
}

export function isPickupSpawnPointAvailable(
  point: PickupSpawnPoint,
  kind: PickupSpawnPoint["kind"],
  pickupId: string,
  context: PickupSpawnResolverContext
): boolean {
  if (point.occupied || !isPickupSpawnPointValid(point.position, context)) {
    return false;
  }

  if (kind === "weapon") {
    return !context.weaponPickups.some(
      (pickup) =>
        pickup.weaponId !== pickupId &&
        pickup.available &&
        distanceBetween(pickup.position, point.position) < PICKUP_SPACING
    );
  }

  return !context.itemPickups.some(
    (pickup) =>
      pickup.pickupId !== pickupId && pickup.available && distanceBetween(pickup.position, point.position) < PICKUP_SPACING
  );
}

export function isPickupSpawnPointValid(position: Vec2, context: PickupSpawnResolverContext): boolean {
  if (
    position.x < EDGE_PADDING ||
    position.x > context.worldSize.x - EDGE_PADDING ||
    position.y < EDGE_PADDING ||
    position.y > context.worldSize.y - EDGE_PADDING
  ) {
    return false;
  }

  if (context.obstacleBounds.some((obstacle) => intersectsCircleRect(position, CLEARANCE_RADIUS, obstacle))) {
    return false;
  }

  return !context.occludableBounds.some((bounds) => {
    const expanded = {
      x: bounds.x - OCCLUDABLE_PADDING,
      y: bounds.y - OCCLUDABLE_PADDING,
      width: bounds.width + OCCLUDABLE_PADDING * 2,
      height: bounds.height + OCCLUDABLE_PADDING * 2
    };
    return containsPoint(expanded, position);
  });
}

function shuffleSpawnPoints<T>(items: readonly T[], randomFn: () => number): T[] {
  const shuffled = [...items];
  for (let index = shuffled.length - 1; index > 0; index -= 1) {
    const swapIndex = Math.floor(randomFn() * (index + 1));
    [shuffled[index], shuffled[swapIndex]] = [shuffled[swapIndex], shuffled[index]];
  }
  return shuffled;
}

function distanceBetween(left: Vec2, right: Vec2): number {
  return Math.hypot(left.x - right.x, left.y - right.y);
}

function intersectsCircleRect(position: Vec2, radius: number, rect: RectLike): boolean {
  const left = rect.x - rect.width / 2;
  const right = rect.x + rect.width / 2;
  const top = rect.y - rect.height / 2;
  const bottom = rect.y + rect.height / 2;
  const closestX = clamp(position.x, left, right);
  const closestY = clamp(position.y, top, bottom);
  const dx = position.x - closestX;
  const dy = position.y - closestY;
  return dx * dx + dy * dy <= radius * radius;
}

function containsPoint(rect: RectLike, point: Vec2): boolean {
  const left = rect.x;
  const right = rect.x + rect.width;
  const top = rect.y;
  const bottom = rect.y + rect.height;
  return point.x >= left && point.x <= right && point.y >= top && point.y <= bottom;
}

function clamp(value: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, value));
}
