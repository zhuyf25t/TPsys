import type { GameSnapshot } from "../../../objects/types";
import { advanceCombatProjectiles, type CombatProjectileEffect } from "../../../runtime/local/combat/combatFrameController";
import { createObstacleCollisionAdapter } from "../../../runtime/local/geometry/sceneGeometry";
import type { ObstacleBounds } from "../arena/arenaBuilder";
import { getFreezeSpeedMultiplier } from "../../../runtime/local/skills/freezeFieldController";

export interface ProjectileFrameSceneBridgeOptions {
  getSnapshot(): GameSnapshot;
  getObstacleBounds(): readonly ObstacleBounds[];
  presentEffect(effect: CombatProjectileEffect): void;
}

export class ProjectileFrameSceneBridge {
  public constructor(private readonly options: ProjectileFrameSceneBridgeOptions) {}

  public updateProjectiles(deltaMs: number): void {
    const snapshot = this.options.getSnapshot();
    const obstacleCollision = createObstacleCollisionAdapter([...this.options.getObstacleBounds()]);
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
}
