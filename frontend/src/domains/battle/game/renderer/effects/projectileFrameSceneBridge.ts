import type { GameSnapshot, Vec2 } from "../../../objects/types";
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
  private obstacleBoundsRef: readonly ObstacleBounds[] | null = null;
  private obstacleCollision: ((position: Vec2, radius: number) => boolean) | null = null;

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

  private getObstacleCollision(): (position: Vec2, radius: number) => boolean {
    const obstacleBounds = this.options.getObstacleBounds();
    if (this.obstacleCollision === null || this.obstacleBoundsRef !== obstacleBounds) {
      this.obstacleBoundsRef = obstacleBounds;
      this.obstacleCollision = createObstacleCollisionAdapter(obstacleBounds);
    }

    return this.obstacleCollision;
  }
}
