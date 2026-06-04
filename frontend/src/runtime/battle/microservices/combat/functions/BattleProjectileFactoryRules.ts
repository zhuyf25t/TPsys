import type { BattleHeroViewState as Hero } from "../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { BattleProjectileState as Projectile } from "../../../../../objects/battle/microservices/combat/objects/projectile/BattleProjectileState";
import type { BattleWeaponRuleDefinition as WeaponDefinition } from "../../../../../objects/battle/microservices/combat/objects/combat/BattleCombatRuleDefinitions";
import type { BattleVector2 as Vec2 } from "../../../../../objects/battle/objects/core/BattleCoreScalars";

export const AUTHORITATIVE_PROJECTILE_BIRTH_CLEARANCE = 4;

export interface BattleWeaponProjectileAnglePlanInput {
  aimAngle: number;
  weaponDefinition: Pick<WeaponDefinition, "projectileCount" | "spreadRadians">;
}

export interface ProjectileBirthPositionInput {
  ownerPosition: Vec2;
  direction: Vec2;
  ownerRadius: number;
  projectileRadius: number;
}

export interface BattleProjectileSpawnInput {
  projectileSequence: number;
  player: Pick<Hero, "heroId" | "team" | "position" | "radius">;
  definition: WeaponDefinition;
  angle: number;
}

export function buildWeaponProjectileAnglePlan(input: BattleWeaponProjectileAnglePlanInput): number[] {
  const projectileCount = Math.max(1, Math.floor(input.weaponDefinition.projectileCount));
  if (projectileCount <= 1 || input.weaponDefinition.spreadRadians === 0) {
    return [input.aimAngle];
  }

  return Array.from({ length: projectileCount }, (_, projectileIndex) => {
    const offset = (projectileIndex / (projectileCount - 1) - 0.5) * input.weaponDefinition.spreadRadians;
    return input.aimAngle + offset;
  });
}

export function resolveProjectileBirthForwardDistance(ownerRadius: number, projectileRadius: number): number {
  return ownerRadius + projectileRadius + AUTHORITATIVE_PROJECTILE_BIRTH_CLEARANCE;
}

export function resolveProjectileBirthPosition(input: ProjectileBirthPositionInput): Vec2 {
  const forwardDistance = resolveProjectileBirthForwardDistance(input.ownerRadius, input.projectileRadius);
  return {
    x: input.ownerPosition.x + input.direction.x * forwardDistance,
    y: input.ownerPosition.y + input.direction.y * forwardDistance
  };
}

export function createBattleProjectileSpawn(input: BattleProjectileSpawnInput): Projectile {
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
