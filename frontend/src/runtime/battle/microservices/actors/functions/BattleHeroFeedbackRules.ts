import type { BattleHeroViewState as Hero } from "../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { BattleVector2 as Vec2 } from "../../../../../objects/battle/objects/core/BattleCoreScalars";

export interface BattleHeroFeedbackState {
  hp: number;
  alive: boolean;
  score: number;
  currentWeaponAmmoTotal: number | null;
  position: Vec2;
}

export type BattleHeroFeedbackTone = "neutral" | "success" | "warning" | "error";

export interface BattleHeroFloatingTextFeedbackPlan {
  kind: "floating-text";
  position: Vec2;
  text: string;
  tone: BattleHeroFeedbackTone;
}

export interface BattleHeroPulseFeedbackPlan {
  kind: "pulse";
  position: Vec2;
  radius: number;
  color: number;
}

export interface BattleHeroFlashFeedbackPlan {
  kind: "flash-hero";
  heroId: string;
  color: number;
}

export interface BattleHeroImpactSparkFeedbackPlan {
  kind: "impact-spark";
  position: Vec2;
  color: number;
}

export interface BattleHeroHitConfirmFeedbackPlan {
  kind: "hit-confirm";
  position: Vec2;
  color: number;
}

export interface BattleHeroCameraShakeFeedbackPlan {
  kind: "camera-shake";
  durationMs: number;
  intensity: number;
}

export type BattleHeroFeedbackPlan =
  | BattleHeroFloatingTextFeedbackPlan
  | BattleHeroPulseFeedbackPlan
  | BattleHeroFlashFeedbackPlan
  | BattleHeroImpactSparkFeedbackPlan
  | BattleHeroHitConfirmFeedbackPlan
  | BattleHeroCameraShakeFeedbackPlan;

export interface ResolveBattleHeroFeedbackPlansInput {
  hero: Hero;
  previous: BattleHeroFeedbackState;
  playerHeroId: string;
  sharedAuthoritativeRuntime: boolean;
  displayPosition: Vec2 | null;
}

const HERO_ELIMINATED_TEXT = "\u51fa\u5c40";
const HERO_SCORE_TEXT_PREFIX = "\u51fb\u8d25 +";
const HERO_AMMO_TEXT_PREFIX = "\u5f39\u836f +";

export function createBattleHeroFeedbackState(hero: Hero, position: Vec2): BattleHeroFeedbackState {
  return {
    hp: hero.hp,
    alive: hero.alive,
    score: hero.score,
    currentWeaponAmmoTotal: resolveCurrentWeaponAmmoTotal(hero),
    position: cloneVector(position)
  };
}

export function resolveBattleHeroFeedbackPlans(input: ResolveBattleHeroFeedbackPlansInput): BattleHeroFeedbackPlan[] {
  const plans: BattleHeroFeedbackPlan[] = [];

  if (input.sharedAuthoritativeRuntime) {
    plans.push(...resolveAuthoritativeHealthDeltaPlans(input));
    plans.push(...resolveAuthoritativeAmmoDeltaPlans(input));
  }

  if (input.previous.alive && !input.hero.alive) {
    plans.push({
      kind: "floating-text",
      position: cloneVector(input.previous.position),
      text: HERO_ELIMINATED_TEXT,
      tone: "error"
    });
    plans.push({
      kind: "pulse",
      position: cloneVector(input.previous.position),
      radius: 42,
      color: 0xff6b6b
    });

    if (input.hero.heroId === input.playerHeroId) {
      plans.push({
        kind: "camera-shake",
        durationMs: 140,
        intensity: 0.0024
      });
    }
  }

  if (!input.sharedAuthoritativeRuntime && !input.previous.alive && input.hero.alive) {
    plans.push({
      kind: "pulse",
      position: cloneVector(input.displayPosition ?? input.hero.position),
      radius: 42,
      color: 0xffb36f
    });
  }

  if (input.hero.heroId === input.playerHeroId && input.hero.score > input.previous.score) {
    plans.push({
      kind: "floating-text",
      position: cloneVector(input.displayPosition ?? input.hero.position),
      text: `${HERO_SCORE_TEXT_PREFIX}${input.hero.score - input.previous.score}`,
      tone: "success"
    });
  }

  return plans;
}

function resolveAuthoritativeHealthDeltaPlans(
  input: ResolveBattleHeroFeedbackPlansInput
): BattleHeroFeedbackPlan[] {
  if (!input.hero.alive && !input.previous.alive) {
    return [];
  }

  const hpDelta = input.hero.hp - input.previous.hp;
  const feedbackPosition = cloneVector(
    input.displayPosition ?? (input.previous.alive && !input.hero.alive ? input.previous.position : input.hero.position)
  );

  if (hpDelta < 0) {
    const damage = Math.round(Math.abs(hpDelta));
    const isPlayerDamage = input.hero.heroId === input.playerHeroId;
    const plans: BattleHeroFeedbackPlan[] = [
      { kind: "flash-hero", heroId: input.hero.heroId, color: 0xffffff },
      { kind: "impact-spark", position: feedbackPosition, color: 0xffe2ba },
      { kind: "hit-confirm", position: feedbackPosition, color: isPlayerDamage ? 0xff6b6b : 0xfff0c6 },
      { kind: "floating-text", position: feedbackPosition, text: `-${damage}`, tone: "error" }
    ];

    if (isPlayerDamage && input.previous.alive && input.hero.alive) {
      const shakeScale = Math.min(1, damage / Math.max(1, input.hero.maxHp * 0.35));
      plans.push({ kind: "pulse", position: feedbackPosition, radius: 28, color: 0xff5c5c });
      plans.push({
        kind: "camera-shake",
        durationMs: 70 + Math.round(shakeScale * 40),
        intensity: 0.0012 + shakeScale * 0.0008
      });
    }

    return plans;
  }

  if (hpDelta > 0 && input.hero.alive) {
    return [
      {
        kind: "floating-text",
        position: feedbackPosition,
        text: `+${Math.round(hpDelta)}`,
        tone: "success"
      },
      {
        kind: "pulse",
        position: feedbackPosition,
        radius: 36,
        color: 0x7dff9d
      }
    ];
  }

  return [];
}

function resolveAuthoritativeAmmoDeltaPlans(
  input: ResolveBattleHeroFeedbackPlansInput
): BattleHeroFeedbackPlan[] {
  if (!input.previous.alive || !input.hero.alive) {
    return [];
  }

  const currentWeaponAmmoTotal = resolveCurrentWeaponAmmoTotal(input.hero);
  if (currentWeaponAmmoTotal === null || input.previous.currentWeaponAmmoTotal === null) {
    return [];
  }

  const ammoDelta = currentWeaponAmmoTotal - input.previous.currentWeaponAmmoTotal;
  if (ammoDelta <= 0) {
    return [];
  }

  const feedbackPosition = cloneVector(input.displayPosition ?? input.hero.position);
  return [
    {
      kind: "floating-text",
      position: feedbackPosition,
      text: `${HERO_AMMO_TEXT_PREFIX}${ammoDelta}`,
      tone: "success"
    },
    {
      kind: "pulse",
      position: feedbackPosition,
      radius: 30,
      color: 0xffd86d
    }
  ];
}

function resolveCurrentWeaponAmmoTotal(hero: Hero): number | null {
  const weapon = hero.weapons[hero.currentWeaponIndex];
  if (!weapon) {
    return null;
  }

  return toSafeAmmoCount(weapon.ammoInMagazine) + toSafeAmmoCount(weapon.reserveAmmo);
}

function toSafeAmmoCount(value: number | null | undefined): number {
  return typeof value === "number" && Number.isFinite(value) ? Math.max(0, Math.round(value)) : 0;
}

function cloneVector(vector: Vec2): Vec2 {
  return { x: vector.x, y: vector.y };
}
