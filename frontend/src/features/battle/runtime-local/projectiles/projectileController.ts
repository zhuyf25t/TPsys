import type { Hero, Projectile, Vec2 } from "../../../../domain/types";

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

  if (projectile.kind === "rocket" && (hitWall || hitHero !== null)) {
    return buildTerminalOutcome(projectile, emitRocketTrail, "rocket-explode", nextPosition, hitHero);
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
