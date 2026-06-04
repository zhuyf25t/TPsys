import type { BattleGameSnapshot as GameSnapshot } from "../../../../../../objects/battle/microservices/session/objects/state/BattleGameSnapshot";
import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";
import type { CombatProjectileEffect } from "../../../../microservices/combat/functions/BattleProjectileImpactRules";
import type { ObstacleBounds } from "../../arena/objects/ArenaBuilderObjects";

export type ProjectileFrameObstacleBounds = readonly ObstacleBounds[];
export type ProjectileFrameObstacleCollision = (position: Vec2, radius: number) => boolean;

export interface ProjectileFrameSceneBridgeOptions {
  getSnapshot(): GameSnapshot;
  getObstacleBounds(): ProjectileFrameObstacleBounds;
  presentEffect(effect: CombatProjectileEffect): void;
}

export interface ShouldRefreshProjectileFrameObstacleCollisionInput {
  cachedObstacleBounds: ProjectileFrameObstacleBounds | null;
  nextObstacleBounds: ProjectileFrameObstacleBounds;
  cachedObstacleCollision: ProjectileFrameObstacleCollision | null;
}
