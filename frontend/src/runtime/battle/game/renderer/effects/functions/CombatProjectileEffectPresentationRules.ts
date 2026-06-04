import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";
import type {
  CombatProjectileEffectPresentationAction,
  CombatProjectileEffectPresentationPlan,
  ResolveCombatProjectileEffectPresentationPlanInput
} from "../objects/CombatProjectileEffectPresenterObjects";

const ROCKET_TRAIL_COLOR = 0xffb36f;
const ROCKET_TRAIL_PULSE_RADIUS = 10;
const ROCKET_EXPLOSION_SHOCKWAVE_START_RADIUS = 28;
const ROCKET_EXPLOSION_SHOCKWAVE_COLOR = 0xffb677;
const ROCKET_EXPLOSION_SHOCKWAVE_DURATION_MS = 250;
const ROCKET_EXPLOSION_SPARK_COLOR = 0xffd57a;
const ROCKET_EXPLOSION_CAMERA_SHAKE_DURATION_MS = 110;
const ROCKET_EXPLOSION_CAMERA_SHAKE_INTENSITY = 0.0022;
const PROJECTILE_HIT_TEXT_COLOR = "#ff9a9a";
const PROJECTILE_HIT_SPARK_COLOR = 0xffe2ba;
const PROJECTILE_HIT_FLASH_COLOR = 0xffffff;
const ROCKET_KNOCKBACK_STRENGTH = 110;
const NORMALIZE_EPSILON = 0.0001;

export function resolveCombatProjectileEffectPresentationPlan({
  effect,
  snapshot
}: ResolveCombatProjectileEffectPresentationPlanInput): CombatProjectileEffectPresentationPlan {
  switch (effect.type) {
    case "rocket-trail":
      return {
        actions: [
          { kind: "pulse", position: effect.position, radius: ROCKET_TRAIL_PULSE_RADIUS, color: ROCKET_TRAIL_COLOR },
          { kind: "impactSpark", position: effect.position, color: ROCKET_TRAIL_COLOR }
        ]
      };
    case "no-damage":
      return { actions: [] };
    case "rocket-explosion":
      return {
        actions: [
          {
            kind: "shockwave",
            position: effect.origin,
            startRadius: ROCKET_EXPLOSION_SHOCKWAVE_START_RADIUS,
            endRadius: effect.splashRadius,
            color: ROCKET_EXPLOSION_SHOCKWAVE_COLOR,
            durationMs: ROCKET_EXPLOSION_SHOCKWAVE_DURATION_MS
          },
          { kind: "impactSpark", position: effect.origin, color: ROCKET_EXPLOSION_SPARK_COLOR },
          {
            kind: "shakeCamera",
            durationMs: ROCKET_EXPLOSION_CAMERA_SHAKE_DURATION_MS,
            intensity: ROCKET_EXPLOSION_CAMERA_SHAKE_INTENSITY
          }
        ]
      };
    case "hit":
      return { actions: resolveHitPresentationActions(effect, snapshot) };
  }
}

function resolveHitPresentationActions(
  effect: Extract<ResolveCombatProjectileEffectPresentationPlanInput["effect"], { type: "hit" }>,
  snapshot: ResolveCombatProjectileEffectPresentationPlanInput["snapshot"]
): CombatProjectileEffectPresentationAction[] {
  const actions: CombatProjectileEffectPresentationAction[] = [
    { kind: "floatingText", position: effect.targetPosition, text: `-${effect.damage}`, color: PROJECTILE_HIT_TEXT_COLOR },
    { kind: "impactSpark", position: effect.targetPosition, color: PROJECTILE_HIT_SPARK_COLOR },
    { kind: "flashHero", heroId: effect.targetHeroId, color: PROJECTILE_HIT_FLASH_COLOR }
  ];

  if (effect.event) {
    actions.push({
      kind: "pushEvent",
      eventType: effect.event.type,
      message: effect.event.message
    });
  }

  if (effect.killed && effect.targetHeroId === snapshot.playerHeroId) {
    actions.push({ kind: "stopPlayerMotion" }, { kind: "setPlayerActorDisabled" });
  }

  const knockback = resolveRocketExplosionKnockbackAction(effect, snapshot);
  if (knockback) {
    actions.push(knockback);
  }

  return actions;
}

function resolveRocketExplosionKnockbackAction(
  effect: Extract<ResolveCombatProjectileEffectPresentationPlanInput["effect"], { type: "hit" }>,
  snapshot: ResolveCombatProjectileEffectPresentationPlanInput["snapshot"]
): CombatProjectileEffectPresentationAction | null {
  if (effect.projectileKind !== "rocket-explosion" || !effect.origin || effect.targetHeroId === effect.ownerHeroId) {
    return null;
  }

  const target = snapshot.heroes.find((hero) => hero.heroId === effect.targetHeroId);
  if (!target || !target.alive) {
    return null;
  }

  return {
    kind: "knockback",
    heroId: target.heroId,
    direction: normalizeVector({
      x: target.position.x - effect.origin.x,
      y: target.position.y - effect.origin.y
    }),
    strength: ROCKET_KNOCKBACK_STRENGTH
  };
}

function normalizeVector(vector: Vec2): Vec2 {
  const length = Math.hypot(vector.x, vector.y);
  if (length <= NORMALIZE_EPSILON) {
    return { x: 0, y: 0 };
  }

  return {
    x: vector.x / length,
    y: vector.y / length
  };
}
