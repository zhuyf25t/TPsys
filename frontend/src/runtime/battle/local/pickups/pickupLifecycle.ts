import type { BattleItemPickupState as ItemPickup, BattleWeaponPickupState as WeaponPickup } from "../../../../objects/battle/microservices/abilities/objects/pickup/BattlePickupState";
import type { BattleVector2 as Vec2 } from "../../../../objects/battle/objects/core/BattleCoreScalars";
import { advancePickupRespawnLifecycle } from "../../microservices/abilities/functions/BattlePickupRules";
import {
  resolvePickupSpawnPoint,
  type BattlePickupSpawnBounds,
  type BattlePickupSpawnResolverContext
} from "../../microservices/world/functions/BattlePickupSpawnPointRules";
import { getItemPickupSpawnPoints, getWeaponPickupSpawnPoints } from "../../microservices/world/functions/BattleWorldInitialLayout";

interface PickupObstacleBoundsLike {
  position: Vec2;
  size: Vec2;
}

interface PickupOccludableLike {
  bounds: BattlePickupSpawnBounds;
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
  const resolverContext = createPickupSpawnResolverContext(input);
  advancePickupRespawnLifecycle({
    deltaMs: input.deltaMs,
    weaponPickups: input.weaponPickups,
    itemPickups: input.itemPickups,
    resolveWeaponRespawnPosition: (pickup) =>
      resolvePickupSpawnPoint({
        kind: "weapon",
        pickupId: pickup.pickupId,
        spawnPoints: getWeaponPickupSpawnPoints(),
        context: resolverContext,
        random: Math.random
      }),
    resolveItemRespawnPosition: (pickup) =>
      resolvePickupSpawnPoint({
        kind: "medkit",
        pickupId: pickup.pickupId,
        spawnPoints: getItemPickupSpawnPoints(),
        context: resolverContext,
        random: Math.random
      })
  });
}

function createPickupSpawnResolverContext(input: PickupLifecycleContextInput): BattlePickupSpawnResolverContext {
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
