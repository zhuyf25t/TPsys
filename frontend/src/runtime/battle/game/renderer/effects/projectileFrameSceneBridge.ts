import { advanceCombatProjectiles } from "../../../microservices/combat/functions/BattleProjectileImpactRules";
import { createObstacleCollisionAdapter } from "../../../local/geometry/sceneGeometry";
import { getFreezeSpeedMultiplier } from "../../../microservices/abilities/functions/BattleSlowFieldRuntimeRules";
import { shouldRefreshProjectileFrameObstacleCollision } from "./functions/ProjectileFrameSceneBridgeRules";
import type {
  ProjectileFrameObstacleBounds,
  ProjectileFrameObstacleCollision,
  ProjectileFrameSceneBridgeOptions
} from "./objects/ProjectileFrameSceneBridgeObjects";

export class ProjectileFrameSceneBridge {
  private obstacleBoundsRef: ProjectileFrameObstacleBounds | null = null;
  private obstacleCollision: ProjectileFrameObstacleCollision | null = null;

  public constructor(private readonly options: ProjectileFrameSceneBridgeOptions) {}

  public updateProjectiles(deltaMs: number): void {
    const snapshot = this.options.getSnapshot();
    const obstacleCollision = this.getObstacleCollision();
    const result = advanceCombatProjectiles({
      projectiles: snapshot.projectiles,
      deltaMs,
      elapsedMs: snapshot.elapsedMs,
      worldSize: snapshot.worldSize,
      heroes: snapshot.heroes,
      getProjectileSpeedMultiplier: (position) => getFreezeSpeedMultiplier(position, snapshot.slowFields),
      collidesWithObstacles: obstacleCollision
    });

    result.effects.forEach((effect) => this.options.presentEffect(effect));
    snapshot.projectiles = result.nextProjectiles;
  }

  private getObstacleCollision(): ProjectileFrameObstacleCollision {
    const obstacleBounds = this.options.getObstacleBounds();
    const cachedObstacleCollision = this.obstacleCollision;
    const shouldRefresh = shouldRefreshProjectileFrameObstacleCollision({
      cachedObstacleBounds: this.obstacleBoundsRef,
      nextObstacleBounds: obstacleBounds,
      cachedObstacleCollision
    });
    if (!shouldRefresh && cachedObstacleCollision) {
      return cachedObstacleCollision;
    }

    this.obstacleBoundsRef = obstacleBounds;
    const obstacleCollision = createObstacleCollisionAdapter(obstacleBounds);
    this.obstacleCollision = obstacleCollision;
    return obstacleCollision;
  }
}
