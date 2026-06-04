import type { BattleProjectileState as Projectile } from "../../../../../objects/battle/microservices/combat/objects/projectile/BattleProjectileState";
import type { BattleVector2 as Vec2 } from "../../../../../objects/battle/objects/core/BattleCoreScalars";
import type { BattleHeroViewState as Hero } from "../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";

export type ProjectileHitRejectReason = "projectile-dead" | "invalid-target" | "self-hit" | "duplicate-hit";
export const PROJECTILE_SHOOTER_ADVANTAGE_RADIUS = 6;

export interface ProjectileHitAttemptInput {
  projectile: Projectile;
  target: Hero;
}

export interface ProjectileHitAttemptResult {
  shouldApplyDamage: boolean;
  reason: ProjectileHitRejectReason | null;
}

export interface RocketExplosionTargetsInput {
  origin: Vec2;
  splashRadius: number;
  ownerHeroId: string;
  heroes: readonly Hero[];
}

export interface HeroPathHitInput {
  start: Vec2;
  end: Vec2;
  radius: number;
  ownerHeroId: string;
  heroes: readonly Hero[];
}

export function resolveProjectileHitAttempt(input: ProjectileHitAttemptInput): ProjectileHitAttemptResult {
  if (!input.projectile.alive) {
    return { shouldApplyDamage: false, reason: "projectile-dead" };
  }

  if (!input.target.alive) {
    return { shouldApplyDamage: false, reason: "invalid-target" };
  }

  if (input.target.heroId === input.projectile.ownerHeroId) {
    return { shouldApplyDamage: false, reason: "self-hit" };
  }

  if (input.projectile.hitTargets.includes(input.target.heroId)) {
    return { shouldApplyDamage: false, reason: "duplicate-hit" };
  }

  return { shouldApplyDamage: true, reason: null };
}

export function resolveRocketExplosionTargets(input: RocketExplosionTargetsInput): Hero[] {
  return input.heroes.filter((hero) => {
    if (!hero.alive || hero.heroId === input.ownerHeroId) {
      return false;
    }

    const distance = Math.hypot(hero.position.x - input.origin.x, hero.position.y - input.origin.y);
    return distance <= input.splashRadius + hero.radius;
  });
}

export function findHeroHitAlongPath(input: HeroPathHitInput): Hero | null {
  let closestHero: Hero | null = null;
  let closestT = Number.POSITIVE_INFINITY;

  for (const hero of input.heroes) {
    if (!hero.alive || hero.heroId === input.ownerHeroId) {
      continue;
    }

    const t = getSegmentHitTime(input.start, input.end, hero.position);
    const closestPoint = {
      x: input.start.x + (input.end.x - input.start.x) * t,
      y: input.start.y + (input.end.y - input.start.y) * t
    };
    const distance = Math.hypot(closestPoint.x - hero.position.x, closestPoint.y - hero.position.y);
    if (distance <= input.radius + hero.radius + PROJECTILE_SHOOTER_ADVANTAGE_RADIUS && t < closestT) {
      closestHero = hero;
      closestT = t;
    }
  }

  return closestHero;
}

export function getSegmentHitTime(start: Vec2, end: Vec2, point: Vec2): number {
  const dx = end.x - start.x;
  const dy = end.y - start.y;
  const lengthSq = dx * dx + dy * dy;
  if (lengthSq <= 0.0001) {
    return 0;
  }

  return Math.max(0, Math.min(1, ((point.x - start.x) * dx + (point.y - start.y) * dy) / lengthSq));
}
