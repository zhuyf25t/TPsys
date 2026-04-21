import type { Hero, Projectile } from "../../../../domain/types";
import type { WeaponDefinition } from "../../../../game/weapons";

export interface ProjectileFactoryInput {
  projectileSequence: number;
  player: Pick<Hero, "heroId" | "team" | "position" | "radius">;
  definition: WeaponDefinition;
  angle: number;
}

export function createProjectileSpawn(input: ProjectileFactoryInput): Projectile {
  const direction = {
    x: Math.cos(input.angle),
    y: Math.sin(input.angle)
  };
  const offset = input.player.radius + (input.definition.projectileKind === "rocket" ? 22 : 16);

  return {
    projectileId: `projectile-${input.projectileSequence}`,
    kind: input.definition.projectileKind,
    ownerHeroId: input.player.heroId,
    team: input.player.team,
    position: {
      x: input.player.position.x + direction.x * offset,
      y: input.player.position.y + direction.y * offset
    },
    velocity: {
      x: direction.x * input.definition.speed,
      y: direction.y * input.definition.speed
    },
    facing: input.angle,
    radius: input.definition.radius,
    damage: input.definition.damage,
    ttlMs: input.definition.lifetimeMs,
    maxLifetimeMs: input.definition.lifetimeMs,
    splashRadius: input.definition.splashRadius,
    alive: true,
    hitTargets: []
  };
}
