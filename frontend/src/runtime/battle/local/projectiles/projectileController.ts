import type { Hero, Projectile, Vec2 } from "../../../../objects/battle/types";
import { PROJECTILE_SHOOTER_ADVANTAGE_RADIUS } from "./hitResolver";

export type ProjectileRuntimeRoute = "dead" | "expired" | "wall-hit" | "hero-hit" | "rocket-explode" | "keep";

export interface ProjectileRuntimeOutcome {
  projectile: Projectile;
  nextProjectile: Projectile | null;
  route: ProjectileRuntimeRoute;
  emitRocketTrail: boolean;
  impactPosition: Vec2 | null;
  hitHero: Hero | null;
}

export interface ProjectileRuntimeContext {
  projectiles: Projectile[];
  deltaMs: number;
  worldSize: Vec2;
  getSpeedMultiplier?: (position: Vec2) => number;
  collidesWithObstacles: (position: Vec2, radius: number) => boolean;
  findHeroHitAlongPath: (start: Vec2, end: Vec2, radius: number, ownerHeroId: string) => Hero | null;
}

/** 中文名：推进投射物runtime（advanceProjectileRuntime）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function advanceProjectileRuntime(context: ProjectileRuntimeContext): ProjectileRuntimeOutcome[] {
  const deltaSeconds = context.deltaMs / 1000;
  const outcomes: ProjectileRuntimeOutcome[] = [];

  for (const projectile of context.projectiles) {
    const speedMultiplier = context.getSpeedMultiplier?.(projectile.position) ?? 1;
    outcomes.push(advanceProjectile(projectile, deltaSeconds * speedMultiplier, context));
  }

  return outcomes;
}

function advanceProjectile(
  projectile: Projectile,
  deltaSeconds: number,
  context: ProjectileRuntimeContext
): ProjectileRuntimeOutcome {
  if (!projectile.alive) {
    return {
      projectile,
      nextProjectile: null,
      route: "dead",
      emitRocketTrail: false,
      impactPosition: null,
      hitHero: null
    };
  }

  const emitRocketTrail = projectile.kind === "rocket" && projectile.ttlMs % 70 < context.deltaMs;
  const nextPosition = {
    x: projectile.position.x + projectile.velocity.x * deltaSeconds,
    y: projectile.position.y + projectile.velocity.y * deltaSeconds
  };
  const nextLifetime = projectile.ttlMs - context.deltaMs;

  if (nextLifetime <= 0 || !isInsideWorld(nextPosition, projectile.radius, context.worldSize)) {
    return projectile.kind === "rocket"
      ? buildTerminalOutcome(projectile, emitRocketTrail, "rocket-explode", nextPosition)
      : buildTerminalOutcome(projectile, emitRocketTrail, "expired", nextPosition);
  }

  const hitWall = context.collidesWithObstacles(nextPosition, projectile.radius);
  const hitHero = context.findHeroHitAlongPath(projectile.position, nextPosition, projectile.radius, projectile.ownerHeroId);

  if (projectile.kind === "rocket" && hitHero !== null) {
    return buildTerminalOutcome(
      projectile,
      emitRocketTrail,
      "rocket-explode",
      resolveHeroImpactPosition(
        projectile.position,
        nextPosition,
        hitHero.position,
        projectile.radius + hitHero.radius + PROJECTILE_SHOOTER_ADVANTAGE_RADIUS
      ),
      hitHero
    );
  }

  if (projectile.kind === "rocket" && hitWall) {
    return buildTerminalOutcome(projectile, emitRocketTrail, "rocket-explode", nextPosition);
  }

  if (hitWall) {
    return buildTerminalOutcome(projectile, emitRocketTrail, "wall-hit", nextPosition);
  }

  if (hitHero) {
    return buildTerminalOutcome(projectile, emitRocketTrail, "hero-hit", nextPosition, hitHero);
  }

  return {
    projectile,
    nextProjectile: {
      ...projectile,
      position: nextPosition,
      ttlMs: nextLifetime
    },
    route: "keep",
    emitRocketTrail,
    impactPosition: nextPosition,
    hitHero: null
  };
}

function buildTerminalOutcome(
  projectile: Projectile,
  emitRocketTrail: boolean,
  route: Exclude<ProjectileRuntimeRoute, "keep">,
  impactPosition: Vec2,
  hitHero: Hero | null = null
): ProjectileRuntimeOutcome {
  return {
    projectile,
    nextProjectile: null,
    route,
    emitRocketTrail,
    impactPosition,
    hitHero
  };
}

function isInsideWorld(position: Vec2, radius: number, worldSize: Vec2): boolean {
  return (
    position.x >= radius &&
    position.x <= worldSize.x - radius &&
    position.y >= radius &&
    position.y <= worldSize.y - radius
  );
}

function resolveHeroImpactPosition(start: Vec2, end: Vec2, heroPosition: Vec2, hitRadius: number): Vec2 {
  const dx = end.x - start.x;
  const dy = end.y - start.y;
  const lengthSq = dx * dx + dy * dy;
  if (lengthSq <= 0.0001) {
    return { x: start.x, y: start.y };
  }

  const offsetX = start.x - heroPosition.x;
  const offsetY = start.y - heroPosition.y;
  const b = 2 * (offsetX * dx + offsetY * dy);
  const c = offsetX * offsetX + offsetY * offsetY - hitRadius * hitRadius;
  const discriminant = b * b - 4 * lengthSq * c;
  const projectedT = Math.max(0, Math.min(1, ((heroPosition.x - start.x) * dx + (heroPosition.y - start.y) * dy) / lengthSq));
  const hitT =
    discriminant >= 0
      ? Math.max(0, Math.min(1, (-b - Math.sqrt(discriminant)) / (2 * lengthSq)))
      : projectedT;

  return {
    x: start.x + dx * hitT,
    y: start.y + dy * hitT
  };
}
