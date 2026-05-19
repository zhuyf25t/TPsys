import type { GameSnapshot, Vec2 } from "../../../objects/types";
import { recordRemoteProjectileBirthDiagnostics } from "../remoteViewDiagnostics";
import {
  PROJECTILE_SPARK_COLORS,
  createRemoteGatlingProjectileBirthTracerOptions,
  resolveRemoteProjectileBirthFeedbackPosition,
  type ProjectileFeedbackState,
  type ProjectileTracerFeedbackOptions
} from "./projectileTerminalFeedbackPolicy";

export interface RemoteProjectileBirthFeedbackPresenterCallbacks {
  createImpactSpark(position: Vec2, color: number): void;
  createPulse(position: Vec2, radius: number, color: number): void;
  createProjectileTracer(options: ProjectileTracerFeedbackOptions): void;
}

export interface AuthoritativeRemoteProjectileBirthFeedbackPresentation {
  snapshot: GameSnapshot;
  previousProjectileStates: ReadonlyMap<string, ProjectileFeedbackState>;
  getHeroDisplayPosition(heroId: string): Vec2 | null;
  callbacks: RemoteProjectileBirthFeedbackPresenterCallbacks;
}

export function presentAuthoritativeRemoteProjectileBirthFeedback({
  snapshot,
  previousProjectileStates,
  callbacks
}: AuthoritativeRemoteProjectileBirthFeedbackPresentation): void {
  snapshot.projectiles.forEach((projectile) => {
    if (previousProjectileStates.has(projectile.projectileId) || projectile.ownerHeroId === snapshot.playerHeroId) {
      return;
    }

    const owner = snapshot.heroes.find((hero) => hero.heroId === projectile.ownerHeroId);
    const position = resolveRemoteProjectileBirthFeedbackPosition(projectile, owner);
    const color = PROJECTILE_SPARK_COLORS[projectile.kind];
    recordRemoteProjectileBirthDiagnostics({
      projectile,
      ownerDisplayName: owner?.displayName,
      position
    });
    if (projectile.kind === "gatling-bullet") {
      callbacks.createProjectileTracer(createRemoteGatlingProjectileBirthTracerOptions(projectile, position, color));
      return;
    }

    callbacks.createImpactSpark(position, color);

    if (projectile.kind === "rocket") {
      callbacks.createPulse(position, 16, color);
    }
  });
}
