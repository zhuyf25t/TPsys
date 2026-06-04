import type { BattleHeroViewState as Hero } from "../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { BattleProjectileState as Projectile } from "../../../../../objects/battle/microservices/combat/objects/projectile/BattleProjectileState";
import type { BattleGameEventState as GameEvent } from "../../../../../objects/battle/microservices/runtime/objects/event/BattleGameEventState";
import type { BattleVector2 as Vec2 } from "../../../../../objects/battle/objects/core/BattleCoreScalars";
import { advanceProjectileRuntime } from "./BattleProjectileRuntimeRules";
import {
  findHeroHitAlongPath as resolveHeroHitAlongPath,
  resolveProjectileHitAttempt,
  resolveRocketExplosionTargets
} from "./BattleProjectileTargetingRules";
import { resolveBattleCriticalDamage } from "./BattleCriticalDamageRules";

export type DamageProjectileKind = Projectile["kind"] | "rocket-explosion";
export type DamageNoDamageReason = "projectile-dead" | "invalid-target" | "self-hit" | "duplicate-hit";

export interface DamageApplicationInput {
  heroes: Hero[];
  target: Hero;
  ownerHeroId: string;
  projectileId: string;
  projectileKind: DamageProjectileKind;
  damage: number;
  elapsedMs: number;
}

export interface DamageApplicationResult {
  applied: boolean;
  reason: DamageNoDamageReason | null;
  hpBefore: number;
  hpAfter: number;
  killed: boolean;
  killerHeroId: string | null;
  killerDisplayName: string | null;
  targetDisplayName: string;
  event: Pick<GameEvent, "type" | "message"> | null;
}

export interface CombatFrameProjectileInput {
  projectiles: readonly Projectile[];
  deltaMs: number;
  elapsedMs: number;
  worldSize: Vec2;
  heroes: Hero[];
  getProjectileSpeedMultiplier?: (position: Vec2) => number;
  collidesWithObstacles: (position: Vec2, radius: number) => boolean;
}

export interface CombatProjectileTrailEffect {
  type: "rocket-trail";
  projectileId: string;
  position: Vec2;
}

export interface CombatProjectileNoDamageEffect {
  type: "no-damage";
  projectileId: string;
  reason: DamageNoDamageReason;
}

export interface CombatProjectileHitEffect {
  type: "hit";
  projectileId: string;
  projectileKind: DamageProjectileKind;
  ownerHeroId: string;
  targetHeroId: string;
  targetDisplayName: string;
  targetPosition: Vec2;
  origin?: Vec2;
  damage: number;
  hpBefore: number;
  hpAfter: number;
  killed: boolean;
  event: Pick<GameEvent, "type" | "message"> | null;
}

export interface CombatProjectileExplosionEffect {
  type: "rocket-explosion";
  projectileId: string;
  ownerHeroId: string;
  origin: Vec2;
  splashRadius: number;
}

export type CombatProjectileEffect =
  | CombatProjectileTrailEffect
  | CombatProjectileNoDamageEffect
  | CombatProjectileHitEffect
  | CombatProjectileExplosionEffect;

export interface CombatFrameProjectileResult {
  nextProjectiles: Projectile[];
  effects: CombatProjectileEffect[];
}

export function advanceCombatProjectiles(input: CombatFrameProjectileInput): CombatFrameProjectileResult {
  const outcomes = advanceProjectileRuntime({
    projectiles: [...input.projectiles],
    deltaMs: input.deltaMs,
    worldSize: input.worldSize,
    getSpeedMultiplier: input.getProjectileSpeedMultiplier,
    collidesWithObstacles: input.collidesWithObstacles,
    findHeroHitAlongPath: (start, end, radius, ownerHeroId) =>
      resolveHeroHitAlongPath({
        start,
        end,
        radius,
        ownerHeroId,
        heroes: input.heroes
      })
  });

  const effects: CombatProjectileEffect[] = [];
  const nextProjectiles: Projectile[] = [];

  outcomes.forEach((outcome) => {
    const projectile = outcome.projectile;

    if (outcome.emitRocketTrail && projectile.kind === "rocket") {
      effects.push({
        type: "rocket-trail",
        projectileId: projectile.projectileId,
        position: { x: projectile.position.x, y: projectile.position.y }
      });
    }

    if (outcome.route === "dead") {
      effects.push({
        type: "no-damage",
        projectileId: projectile.projectileId,
        reason: "projectile-dead"
      });
      return;
    }

    if (outcome.route === "rocket-explode") {
      const origin = {
        x: outcome.impactPosition?.x ?? projectile.position.x,
        y: outcome.impactPosition?.y ?? projectile.position.y
      };

      effects.push({
        type: "rocket-explosion",
        projectileId: projectile.projectileId,
        ownerHeroId: projectile.ownerHeroId,
        origin,
        splashRadius: projectile.splashRadius
      });

      applyRocketExplosion(origin, projectile, input.heroes, effects, input.elapsedMs);
      return;
    }

    if (outcome.route === "wall-hit") {
      projectile.alive = false;
      effects.push({
        type: "no-damage",
        projectileId: projectile.projectileId,
        reason: "invalid-target"
      });
      return;
    }

    if (outcome.route === "hero-hit" && outcome.hitHero) {
      const hitEffect = applyProjectileHit(
        projectile,
        outcome.hitHero,
        input.heroes,
        projectile.kind,
        true,
        input.elapsedMs
      );
      effects.push(hitEffect);
      return;
    }

    if (outcome.nextProjectile) {
      nextProjectiles.push(outcome.nextProjectile);
    }
  });

  return {
    nextProjectiles,
    effects
  };
}

export function applyDamageToHero(input: DamageApplicationInput): DamageApplicationResult {
  if (!input.target.alive) {
    return buildNoDamageResult("invalid-target", input.target.displayName);
  }

  const hpBefore = input.target.hp;
  const owner = input.heroes.find((hero) => hero.heroId === input.ownerHeroId);
  const damage = resolveBattleCriticalDamage(input.damage, owner);
  const hpAfter = Math.max(0, input.target.hp - damage);
  if (hpAfter === hpBefore) {
    return buildNoDamageResult("invalid-target", input.target.displayName);
  }

  input.target.hp = hpAfter;

  if (hpAfter > 0) {
    return {
      applied: true,
      reason: null,
      hpBefore,
      hpAfter,
      killed: false,
      killerHeroId: null,
      killerDisplayName: null,
      targetDisplayName: input.target.displayName,
      event: null
    };
  }

  input.target.alive = false;
  input.target.lifeState = "dead";
  input.target.respawnMs = 0;
  input.target.velocity = { x: 0, y: 0 };
  input.target.eliminatedAtMs = input.elapsedMs;

  const killer = owner;
  if (killer && killer.heroId !== input.target.heroId) {
    killer.score += 1;
  }

  return {
    applied: true,
    reason: null,
    hpBefore,
    hpAfter,
    killed: true,
    killerHeroId: killer && killer.heroId !== input.target.heroId ? killer.heroId : null,
    killerDisplayName: killer && killer.heroId !== input.target.heroId ? killer.displayName : null,
    targetDisplayName: input.target.displayName,
    event:
      killer && killer.heroId !== input.target.heroId
        ? {
            type: "kill",
            message: `${killer.displayName} \u51fb\u8d25 ${input.target.displayName}`
          }
        : null
  };
}

function applyRocketExplosion(
  origin: Vec2,
  projectile: Projectile,
  heroes: Hero[],
  effects: CombatProjectileEffect[],
  elapsedMs: number
): void {
  const targets = resolveRocketExplosionTargets({
    origin,
    splashRadius: projectile.splashRadius,
    ownerHeroId: projectile.ownerHeroId,
    heroes
  });

  targets.forEach((hero) => {
    effects.push(applyProjectileHit(projectile, hero, heroes, "rocket-explosion", false, elapsedMs, origin));
  });

  projectile.alive = false;
}

function applyProjectileHit(
  projectile: Projectile,
  target: Hero,
  heroes: Hero[],
  projectileKind: DamageProjectileKind,
  destroyOnHit: boolean,
  elapsedMs: number,
  origin?: Vec2
): CombatProjectileHitEffect | CombatProjectileNoDamageEffect {
  const hitAttempt = resolveProjectileHitAttempt({ projectile, target });
  if (!hitAttempt.shouldApplyDamage) {
    return {
      type: "no-damage",
      projectileId: projectile.projectileId,
      reason: hitAttempt.reason ?? "invalid-target"
    };
  }

  projectile.hitTargets.push(target.heroId);
  const damageResult = applyDamageToHero({
    heroes,
    target,
    ownerHeroId: projectile.ownerHeroId,
    projectileId: projectile.projectileId,
    projectileKind,
    damage: projectile.damage,
    elapsedMs
  });

  if (!damageResult.applied) {
    return {
      type: "no-damage",
      projectileId: projectile.projectileId,
      reason: damageResult.reason ?? "invalid-target"
    };
  }

  if (destroyOnHit) {
    projectile.alive = false;
  }

  return {
    type: "hit",
    projectileId: projectile.projectileId,
    projectileKind,
    ownerHeroId: projectile.ownerHeroId,
    targetHeroId: target.heroId,
    targetDisplayName: target.displayName,
    targetPosition: { x: target.position.x, y: target.position.y },
    origin,
    damage: damageResult.hpBefore - damageResult.hpAfter,
    hpBefore: damageResult.hpBefore,
    hpAfter: damageResult.hpAfter,
    killed: damageResult.killed,
    event: damageResult.event
  };
}

function buildNoDamageResult(reason: DamageNoDamageReason, targetDisplayName: string): DamageApplicationResult {
  return {
    applied: false,
    reason,
    hpBefore: 0,
    hpAfter: 0,
    killed: false,
    killerHeroId: null,
    killerDisplayName: null,
    targetDisplayName,
    event: null
  };
}
