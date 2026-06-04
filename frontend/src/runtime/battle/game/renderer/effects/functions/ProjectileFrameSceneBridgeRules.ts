import type { ShouldRefreshProjectileFrameObstacleCollisionInput } from "../objects/ProjectileFrameSceneBridgeObjects";

export function shouldRefreshProjectileFrameObstacleCollision({
  cachedObstacleBounds,
  nextObstacleBounds,
  cachedObstacleCollision
}: ShouldRefreshProjectileFrameObstacleCollisionInput): boolean {
  return cachedObstacleCollision === null || cachedObstacleBounds !== nextObstacleBounds;
}
