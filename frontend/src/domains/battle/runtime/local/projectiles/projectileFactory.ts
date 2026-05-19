import type { Hero, Projectile } from "../../../objects/types";
import { resolveProjectileBirthPosition } from "../../../game/projectileBirth";
import type { WeaponDefinition } from "../../../game/weapons";

export interface ProjectileFactoryInput {
  projectileSequence: number;
  player: Pick<Hero, "heroId" | "team" | "position" | "radius">;
  definition: WeaponDefinition;
  angle: number;
}

/** 中文名：创建投射物spawn（createProjectileSpawn）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function createProjectileSpawn(input: ProjectileFactoryInput): Projectile {
  const direction = {
    x: Math.cos(input.angle),
    y: Math.sin(input.angle)
  };
  const position = resolveProjectileBirthPosition({
    ownerPosition: input.player.position,
    direction,
    ownerRadius: input.player.radius,
    projectileRadius: input.definition.projectileRadius
  });

  return {
    projectileId: `projectile-${input.projectileSequence}`,
    kind: input.definition.projectileKind,
    ownerHeroId: input.player.heroId,
    team: input.player.team,
    position,
    velocity: {
      x: direction.x * input.definition.projectileSpeedPerSecond,
      y: direction.y * input.definition.projectileSpeedPerSecond
    },
    facing: input.angle,
    radius: input.definition.projectileRadius,
    damage: input.definition.projectileDamage,
    ttlMs: input.definition.projectileLifetimeMs,
    maxLifetimeMs: input.definition.projectileLifetimeMs,
    splashRadius: input.definition.splashRadius,
    alive: true,
    hitTargets: []
  };
}
