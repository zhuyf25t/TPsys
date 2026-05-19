import type { Hero, Projectile, Vec2 } from "../../../objects/types";

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

/** 中文名：解析投射物hitattempt（resolveProjectileHitAttempt）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
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

/** 中文名：解析rocketexplosiontargets（resolveRocketExplosionTargets）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function resolveRocketExplosionTargets(input: RocketExplosionTargetsInput): Hero[] {
  return input.heroes.filter((hero) => {
    if (!hero.alive || hero.heroId === input.ownerHeroId) {
      return false;
    }

    const distance = Math.hypot(hero.position.x - input.origin.x, hero.position.y - input.origin.y);
    return distance <= input.splashRadius + hero.radius;
  });
}

/** 中文名：查找英雄hitalongpath（findHeroHitAlongPath）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
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

/** 中文名：获取segmenthit时间（getSegmentHitTime）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function getSegmentHitTime(start: Vec2, end: Vec2, point: Vec2): number {
  const dx = end.x - start.x;
  const dy = end.y - start.y;
  const lengthSq = dx * dx + dy * dy;
  if (lengthSq <= 0.0001) {
    return 0;
  }

  return Math.max(0, Math.min(1, ((point.x - start.x) * dx + (point.y - start.y) * dy) / lengthSq));
}
