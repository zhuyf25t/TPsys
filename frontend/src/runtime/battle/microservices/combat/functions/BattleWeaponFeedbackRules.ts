import type { BattleHeroViewState as Hero } from "../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import { WEAPON_DEFINITIONS } from "../../../../../objects/battle/microservices/combat/objects/combat/BattleCombatRuleDefinitions";
import type { BattleWeaponState as WeaponState } from "../../../../../objects/battle/microservices/combat/objects/weapon/BattleWeaponState";
import type { WeaponKind } from "../../../../../objects/battle/microservices/combat/objects/weapon/WeaponKind";
import type { BattlePlayerCommand as PlayerCommand } from "../../../../../objects/battle/microservices/session/objects/command/BattlePlayerCommand";
import type { BattleVector2 as Vec2 } from "../../../../../objects/battle/objects/core/BattleCoreScalars";
import { resolveProjectileBirthPosition } from "./BattleProjectileFactoryRules";
import { getWeaponRuntimeProfile } from "./BattleWeaponRuntimeProfiles";

export interface BattleWeaponMuzzleFeedbackTracer {
  start: Vec2;
  direction: Vec2;
  length: number;
  color: number;
  thickness: number;
  durationMs: number;
  alpha?: number;
  ghostScale?: number;
  glintAlphaScale?: number;
  underglowAlphaScale?: number;
  coreAlphaScale?: number;
  ghostAlphaScale?: number;
}

export interface BattleWeaponMuzzleFeedbackStyle {
  color: number;
  radius: number;
  sparks: number;
  tracer: Omit<BattleWeaponMuzzleFeedbackTracer, "start" | "direction" | "color">;
  reticlePulse?: {
    radius: number;
    color: number;
  };
}

export interface BattleWeaponPrimaryFeedbackPlan {
  weaponKind: WeaponKind;
  direction: Vec2;
  pointerWorld: Vec2;
  muzzle: {
    position: Vec2;
    color: number;
    radius: number;
    sparks: number;
  };
  tracer: BattleWeaponMuzzleFeedbackTracer;
  reticlePulse?: {
    position: Vec2;
    radius: number;
    color: number;
  };
  nextPrimaryFeedbackAtMs: number;
}

export interface BattleWeaponReloadIntentFeedbackPlan {
  floatingText: {
    text: string;
    tone: "neutral";
  };
  nextReloadIntentFeedbackAtMs: number;
}

export interface ResolveBattleWeaponPrimaryFeedbackInput {
  player: Hero;
  command: PlayerCommand;
  suppressForTargetedRelease: boolean;
  nowMs: number;
  nextPrimaryFeedbackAtMs: number;
}

export interface ResolveBattleWeaponReloadIntentFeedbackInput {
  player: Hero;
  command: Pick<PlayerCommand, "reloadPressed">;
  nowMs: number;
  nextReloadIntentFeedbackAtMs: number;
}

export const BATTLE_WEAPON_PRIMARY_FEEDBACK_MIN_MS = 120;
export const BATTLE_WEAPON_RELOAD_INTENT_FEEDBACK_MIN_MS = 520;

const BATTLE_RELOAD_INTENT_FEEDBACK_TEXT = "\u93b9\u3220\u810a\u7487\u950b\u7730";
const PISTOL_SHORT_MUZZLE_TRACER: BattleWeaponMuzzleFeedbackStyle["tracer"] = {
  length: 22,
  thickness: 2,
  durationMs: 54,
  alpha: 0.2,
  ghostScale: 0.22,
  glintAlphaScale: 0,
  underglowAlphaScale: 0,
  coreAlphaScale: 0.64,
  ghostAlphaScale: 0
};

export const BATTLE_WEAPON_MUZZLE_FEEDBACK_STYLES: Readonly<Record<WeaponKind, BattleWeaponMuzzleFeedbackStyle>> = {
  Pistol: {
    color: 0xfff0c6,
    radius: 6,
    sparks: 1,
    tracer: PISTOL_SHORT_MUZZLE_TRACER,
    reticlePulse: { radius: 8, color: 0xfff0c6 }
  },
  RocketLauncher: {
    color: 0xffb36f,
    radius: 18,
    sparks: 5,
    tracer: { length: 104, thickness: 7, durationMs: 145, alpha: 0.52, ghostScale: 1.45 },
    reticlePulse: { radius: 12, color: 0xffb36f }
  },
  Gatling: {
    color: 0xffd86d,
    radius: 8,
    sparks: 2,
    tracer: {
      length: 72,
      thickness: 2,
      durationMs: 68,
      alpha: 0.36,
      ghostScale: 0.5,
      glintAlphaScale: 0,
      underglowAlphaScale: 0,
      coreAlphaScale: 0.46,
      ghostAlphaScale: 0
    }
  },
  Shotgun: {
    color: 0xffefb7,
    radius: 20,
    sparks: 7,
    tracer: { length: 104, thickness: 8, durationMs: 126, alpha: 0.48, ghostScale: 1.25 },
    reticlePulse: { radius: 10, color: 0xffefb7 }
  }
};

export function resolveBattleWeaponPrimaryFeedback(
  input: ResolveBattleWeaponPrimaryFeedbackInput
): BattleWeaponPrimaryFeedbackPlan | null {
  if (input.player.preparedSkill !== null || input.suppressForTargetedRelease) {
    return null;
  }

  const weapon = resolveCurrentWeapon(input.player);
  if (!weapon || !canPresentBattleWeaponPrimaryFeedback(weapon)) {
    return null;
  }

  if (!hasBattleWeaponPrimaryFeedbackIntent(input.command, weapon.weaponKind)) {
    return null;
  }

  if (input.nowMs < input.nextPrimaryFeedbackAtMs) {
    return null;
  }

  const direction = resolveBattleWeaponAimDirection(input.command.aim);
  const style = BATTLE_WEAPON_MUZZLE_FEEDBACK_STYLES[weapon.weaponKind];
  const muzzlePosition = resolveProjectileBirthPosition({
    ownerPosition: input.player.position,
    direction,
    ownerRadius: input.player.radius,
    projectileRadius: WEAPON_DEFINITIONS[weapon.weaponKind].projectileRadius
  });

  return {
    weaponKind: weapon.weaponKind,
    direction,
    pointerWorld: input.command.pointerWorld,
    muzzle: {
      position: muzzlePosition,
      color: style.color,
      radius: style.radius,
      sparks: style.sparks
    },
    tracer: {
      start: muzzlePosition,
      direction,
      length: style.tracer.length,
      color: style.color,
      thickness: style.tracer.thickness,
      durationMs: style.tracer.durationMs,
      alpha: style.tracer.alpha,
      ghostScale: style.tracer.ghostScale,
      glintAlphaScale: style.tracer.glintAlphaScale,
      underglowAlphaScale: style.tracer.underglowAlphaScale,
      coreAlphaScale: style.tracer.coreAlphaScale,
      ghostAlphaScale: style.tracer.ghostAlphaScale
    },
    ...(style.reticlePulse === undefined
      ? {}
      : {
          reticlePulse: {
            position: input.command.pointerWorld,
            radius: style.reticlePulse.radius,
            color: style.reticlePulse.color
          }
        }),
    nextPrimaryFeedbackAtMs: input.nowMs + getBattleWeaponPrimaryFeedbackIntervalMs(weapon.weaponKind)
  };
}

export function resolveBattleWeaponReloadIntentFeedback(
  input: ResolveBattleWeaponReloadIntentFeedbackInput
): BattleWeaponReloadIntentFeedbackPlan | null {
  if (!input.command.reloadPressed) {
    return null;
  }

  const weapon = resolveCurrentWeapon(input.player);
  if (!weapon || !canRequestBattleWeaponReloadFeedback(weapon)) {
    return null;
  }

  if (input.nowMs < input.nextReloadIntentFeedbackAtMs) {
    return null;
  }

  return {
    floatingText: {
      text: BATTLE_RELOAD_INTENT_FEEDBACK_TEXT,
      tone: "neutral"
    },
    nextReloadIntentFeedbackAtMs: input.nowMs + BATTLE_WEAPON_RELOAD_INTENT_FEEDBACK_MIN_MS
  };
}

export function getBattleWeaponPrimaryFeedbackIntervalMs(weaponKind: WeaponKind): number {
  const cooldownMs = WEAPON_DEFINITIONS[weaponKind].cooldownMs;
  return Math.max(BATTLE_WEAPON_PRIMARY_FEEDBACK_MIN_MS, cooldownMs);
}

function resolveCurrentWeapon(player: Hero): WeaponState | undefined {
  return player.weapons[player.currentWeaponIndex];
}

function canPresentBattleWeaponPrimaryFeedback(weapon: WeaponState): boolean {
  if (weapon.reloadRemainingMs > 0 || weapon.overheated || weapon.overheatRemainingMs > 0) {
    return false;
  }

  const definition = WEAPON_DEFINITIONS[weapon.weaponKind];
  if (!definition.usesHeat && weapon.ammoInMagazine <= 0) {
    return false;
  }

  return true;
}

function canRequestBattleWeaponReloadFeedback(weapon: WeaponState): boolean {
  return (
    weapon.weaponKind !== "Gatling" &&
    weapon.reloadRemainingMs <= 0 &&
    weapon.ammoInMagazine < weapon.magazineSize &&
    weapon.reserveAmmo !== null &&
    weapon.reserveAmmo > 0
  );
}

function hasBattleWeaponPrimaryFeedbackIntent(command: PlayerCommand, weaponKind: WeaponKind): boolean {
  const triggerMode = getWeaponRuntimeProfile(weaponKind).triggerMode;
  return triggerMode === "held" ? command.primaryHeld : command.primaryHeld || command.primaryJustPressed;
}

function resolveBattleWeaponAimDirection(aim: Vec2): Vec2 {
  const length = Math.hypot(aim.x, aim.y);
  if (length <= 0.0001) {
    return { x: 1, y: 0 };
  }

  return {
    x: aim.x / length,
    y: aim.y / length
  };
}
