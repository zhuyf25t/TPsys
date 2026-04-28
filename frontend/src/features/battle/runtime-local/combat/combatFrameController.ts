import type { GameEvent, Hero, Projectile, Vec2 } from "../../../../domain/types";
import { advanceProjectileRuntime } from "../projectiles/projectileController";
import {
  findHeroHitAlongPath as resolveHeroHitAlongPath,
  resolveProjectileHitAttempt,
  resolveRocketExplosionTargets
} from "../projectiles/hitResolver";
import { applyDamageToHero, type DamageNoDamageReason, type DamageProjectileKind } from "../projectiles/damageResolver";

export interface CombatFrameProjectileInput {
  projectiles: readonly Projectile[];
  deltaMs: number;
  elapsedMs: number;
  worldSize: Vec2;
  heroes: Hero[];
  getProjectileSpeedMultiplier?: (position: Vec2) => number;
  collidesWithObstacles: (position: Vec2, radius: number) => boolean;
}

export interface CombatFrameRespawnInput {
  heroes: Hero[];
  deltaMs: number;
  playerHeroId: string;
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

export interface CombatRespawnEffect {
  type: "respawn";
  heroId: string;
  displayName: string;
  spawn: Vec2;
  isPlayerHero: boolean;
  resetSceneTransitionState: boolean;
  event: Pick<GameEvent, "type" | "message">;
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

export function advanceCombatRespawns(_input: CombatFrameRespawnInput): CombatRespawnEffect[] {
  return [];
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
    damage: projectile.damage,
    hpBefore: damageResult.hpBefore,
    hpAfter: damageResult.hpAfter,
    killed: damageResult.killed,
    event: damageResult.event
  };
}
