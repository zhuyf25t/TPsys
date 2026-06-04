import type {
  BattleItemPickupState as ItemPickup,
  BattleWeaponPickupState as WeaponPickup
} from "../../../../../objects/battle/microservices/abilities/objects/pickup/BattlePickupState";
import type { PickupSpawnPoint } from "../../../../../objects/battle/microservices/world/objects/world/PickupSpawnPoint";
import type { BattleVector2 as Vec2 } from "../../../../../objects/battle/objects/core/BattleCoreScalars";

export interface BattlePickupSpawnBounds {
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface BattlePickupSpawnResolverContext {
  worldSize: Vec2;
  obstacleBounds: readonly BattlePickupSpawnBounds[];
  occludableBounds: readonly BattlePickupSpawnBounds[];
  weaponPickups: readonly WeaponPickup[];
  itemPickups: readonly ItemPickup[];
}

export interface BattlePickupSpawnPointResolutionInput {
  kind: PickupSpawnPoint["kind"];
  pickupId: string;
  spawnPoints: readonly PickupSpawnPoint[];
  context: BattlePickupSpawnResolverContext;
  random: () => number;
}

const EDGE_PADDING = 48;
const CLEARANCE_RADIUS = 24;
const PICKUP_SPACING = 24;
const OCCLUDABLE_PADDING = 48;

export function resolvePickupSpawnPoint(input: BattlePickupSpawnPointResolutionInput): Vec2 {
  const shuffled = shuffleSpawnPoints(input.spawnPoints, input.random);
  const selected = shuffled.find((point) =>
    isPickupSpawnPointAvailable(point, input.kind, input.pickupId, input.context)
  );
  const fallback =
    input.spawnPoints.find((point) => isPickupSpawnPointValid(point.position, input.context)) ?? input.spawnPoints[0];
  const resolved = selected ?? fallback;
  if (!resolved) {
    throw new Error(`Missing pickup spawn point for ${input.pickupId}`);
  }

  return {
    x: resolved.position.x,
    y: resolved.position.y
  };
}

export function isPickupSpawnPointAvailable(
  point: PickupSpawnPoint,
  kind: PickupSpawnPoint["kind"],
  pickupId: string,
  context: BattlePickupSpawnResolverContext
): boolean {
  if (point.occupied || !isPickupSpawnPointValid(point.position, context)) {
    return false;
  }

  if (kind === "weapon") {
    return !context.weaponPickups.some(
      (pickup) =>
        pickup.pickupId !== pickupId &&
        pickup.available &&
        distanceBetween(pickup.position, point.position) < PICKUP_SPACING
    );
  }

  return !context.itemPickups.some(
    (pickup) =>
      pickup.pickupId !== pickupId && pickup.available && distanceBetween(pickup.position, point.position) < PICKUP_SPACING
  );
}

export function isPickupSpawnPointValid(position: Vec2, context: BattlePickupSpawnResolverContext): boolean {
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

function shuffleSpawnPoints<T>(items: readonly T[], random: () => number): T[] {
  const shuffled = [...items];
  for (let index = shuffled.length - 1; index > 0; index -= 1) {
    const swapIndex = Math.floor(random() * (index + 1));
    [shuffled[index], shuffled[swapIndex]] = [shuffled[swapIndex], shuffled[index]];
  }
  return shuffled;
}

function distanceBetween(left: Vec2, right: Vec2): number {
  return Math.hypot(left.x - right.x, left.y - right.y);
}

function intersectsCircleRect(position: Vec2, radius: number, rect: BattlePickupSpawnBounds): boolean {
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

function containsPoint(rect: BattlePickupSpawnBounds, point: Vec2): boolean {
  const left = rect.x;
  const right = rect.x + rect.width;
  const top = rect.y;
  const bottom = rect.y + rect.height;
  return point.x >= left && point.x <= right && point.y >= top && point.y <= bottom;
}

function clamp(value: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, value));
}
