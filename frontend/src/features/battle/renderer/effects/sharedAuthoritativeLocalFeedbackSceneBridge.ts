import type { Hero, PlayerCommand, SkillKind, Vec2, WeaponKind, WeaponState } from "../../../../domain/types";
import { SKILL_DEFINITIONS } from "../../../../game/skills";
import { WEAPON_DEFINITIONS } from "../../../../game/weapons";
import type { SceneGeometryObstacleBounds } from "../../runtime-local/geometry/sceneGeometry";
import { recordLocalMuzzleFeedbackDiagnostics } from "../localFeedbackDiagnostics";
import type { LocalHeroDisplayPoseReader } from "../localHeroDisplayPose";
import { isSharedAuthoritativeTargetValid } from "./sharedAuthoritativeTargetValidity";

interface MuzzleFeedbackStyle {
  color: number;
  radius: number;
  sparks: number;
  tracer: {
    length: number;
    thickness: number;
    durationMs: number;
    alpha?: number;
    ghostScale?: number;
    glintAlphaScale?: number;
    underglowAlphaScale?: number;
    coreAlphaScale?: number;
    ghostAlphaScale?: number;
  };
  reticlePulse?: {
    radius: number;
    color: number;
  };
}

type SkillFeedbackIntent = "prepare" | "release";
type TargetedFeedbackSkillKind = "Blink" | "Freeze";

interface TargetedSkillFeedbackRequest {
  kind: TargetedFeedbackSkillKind;
  intent: SkillFeedbackIntent;
  feedbackRadius: number;
}

export interface LocalProjectileTracerFeedback {
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

export interface SharedAuthoritativeLocalFeedbackSceneBridgeOptions {
  getPlayerHero(): Hero;
  localHeroDisplay: LocalHeroDisplayPoseReader;
  getWorldSize(): Vec2;
  getObstacleBounds(): readonly SceneGeometryObstacleBounds[];
  getNowMs(): number;
  createMuzzleBurst(position: Vec2, color: number, radius: number, sparks: number, direction?: Vec2): void;
  createPulse(position: Vec2, radius: number, color: number): void;
  createProjectileTracer(options: LocalProjectileTracerFeedback): void;
  createBlinkSkillTargetFeedback(position: Vec2, intent: SkillFeedbackIntent, direction?: Vec2): void;
  createFreezeSkillTargetFeedback(position: Vec2, intent: SkillFeedbackIntent): void;
  createDashSkillFeedback(position: Vec2, direction: Vec2): void;
  createSkillRejectionFeedback(position: Vec2, radius: number): void;
  showFloatingText(position: Vec2, text: string, tone: "neutral" | "success" | "warning" | "error"): void;
}

const PRIMARY_FEEDBACK_MIN_MS = 120;
const SKILL_REJECT_FEEDBACK_MIN_MS = 160;
const RELOAD_INTENT_FEEDBACK_MIN_MS = 520;
const AUTHORITATIVE_PROJECTILE_BIRTH_CLEARANCE = 4;
const BLINK_PREPARE_FEEDBACK_RADIUS = 24;
const BLINK_RELEASE_FEEDBACK_RADIUS = 28;
const FREEZE_PREPARE_FEEDBACK_RADIUS = SKILL_DEFINITIONS.Freeze.radius * 0.2;
const FREEZE_RELEASE_FEEDBACK_RADIUS = SKILL_DEFINITIONS.Freeze.radius;
const PISTOL_SHORT_MUZZLE_TRACER: MuzzleFeedbackStyle["tracer"] = {
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

// Former pistol values are archived in docs as `piercing-rail-tracer-long`
// for a future rail/piercing weapon style: length 148, thickness 3,
// duration 118ms, alpha 0.62, ghostScale 1.9, with the default side glint.

const MUZZLE_FEEDBACK_STYLES: Record<WeaponKind, MuzzleFeedbackStyle> = {
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

export class SharedAuthoritativeLocalFeedbackSceneBridge {
  private nextPrimaryFeedbackAtMs = 0;
  private nextSkillRejectFeedbackAtMs = 0;
  private nextReloadIntentFeedbackAtMs = 0;
  private primaryHeldLastFrame = false;

  public constructor(private readonly options: SharedAuthoritativeLocalFeedbackSceneBridgeOptions) {}

  public update(command: PlayerCommand): void {
    const primaryPressStarted = command.primaryJustPressed || (command.primaryHeld && !this.primaryHeldLastFrame);
    this.primaryHeldLastFrame = command.primaryHeld;

    const player = this.options.getPlayerHero();
    if (!player.alive) {
      return;
    }

    const displayPose = this.options.localHeroDisplay.read();
    const targetedSkillRequest = resolveTargetedSkillFeedbackRequest(player, command, primaryPressStarted);
    this.presentReloadIntentFeedback(player, command, displayPose.position);
    this.presentPrimaryFeedback(player, command, displayPose.position, targetedSkillRequest?.intent === "release");
    this.presentSkillFeedback(player, command, displayPose.position, targetedSkillRequest);
  }

  private presentReloadIntentFeedback(player: Hero, command: PlayerCommand, displayPosition: Vec2): void {
    if (!command.reloadPressed) {
      return;
    }

    const weapon = player.weapons[player.currentWeaponIndex];
    if (!weapon || !canRequestReloadFeedback(weapon)) {
      return;
    }

    const nowMs = this.options.getNowMs();
    if (nowMs < this.nextReloadIntentFeedbackAtMs) {
      return;
    }

    this.nextReloadIntentFeedbackAtMs = nowMs + RELOAD_INTENT_FEEDBACK_MIN_MS;
    this.options.showFloatingText(displayPosition, "换弹请求", "neutral");
  }

  private presentPrimaryFeedback(
    player: Hero,
    command: PlayerCommand,
    displayPosition: Vec2,
    suppressForTargetedRelease: boolean
  ): void {
    if (!command.primaryHeld || player.preparedSkill !== null || suppressForTargetedRelease) {
      return;
    }

    const weapon = player.weapons[player.currentWeaponIndex];
    if (!weapon) {
      return;
    }

    if (!canPresentPrimaryFeedback(weapon)) {
      return;
    }

    const nowMs = this.options.getNowMs();
    if (nowMs < this.nextPrimaryFeedbackAtMs) {
      return;
    }

    this.nextPrimaryFeedbackAtMs = nowMs + getPrimaryFeedbackIntervalMs(weapon.weaponKind);

    const direction = resolveAimDirection(command.aim);
    const style = MUZZLE_FEEDBACK_STYLES[weapon.weaponKind];
    const muzzleForwardDistance = resolveMuzzleForwardDistance(player, weapon.weaponKind);
    // Pistol feedback stays display-anchored for immediate feel; its tracer is short/subtle so it does not compete with the authoritative projectile path.
    const muzzlePosition = {
      x: displayPosition.x + direction.x * muzzleForwardDistance,
      y: displayPosition.y + direction.y * muzzleForwardDistance
    };

    this.options.createMuzzleBurst(muzzlePosition, style.color, style.radius, style.sparks, direction);
    this.options.createProjectileTracer({
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
    });
    if (style.reticlePulse) {
      this.options.createPulse(command.pointerWorld, style.reticlePulse.radius, style.reticlePulse.color);
    }
    recordLocalMuzzleFeedbackDiagnostics({
      weaponKind: weapon.weaponKind,
      position: muzzlePosition,
      pointerWorld: command.pointerWorld
    });
  }

  private presentSkillFeedback(
    player: Hero,
    command: PlayerCommand,
    displayPosition: Vec2,
    targetedRequest: TargetedSkillFeedbackRequest | null
  ): void {
    if (command.castDash) {
      if (canPresentSkillFeedback(player, "Dash")) {
        this.options.createDashSkillFeedback(displayPosition, resolveAimDirection(command.aim));
      } else {
        this.presentSkillRejectionFeedback(displayPosition, 22);
      }
    }

    if (targetedRequest) {
      this.presentTargetedSkillFeedback(
        player,
        targetedRequest.kind,
        command.pointerWorld,
        displayPosition,
        targetedRequest.feedbackRadius,
        targetedRequest.intent
      );
    }
  }

  private presentTargetedSkillFeedback(
    player: Hero,
    kind: TargetedFeedbackSkillKind,
    target: Vec2,
    displayPosition: Vec2,
    successRadius: number,
    intent: SkillFeedbackIntent
  ): void {
    if (!canPresentSkillFeedback(player, kind)) {
      this.presentSkillRejectionFeedback(displayPosition, successRadius);
      return;
    }

    const targetValid = isSharedAuthoritativeTargetValid({
      player,
      preparedSkill: kind,
      target,
      worldSize: this.options.getWorldSize(),
      obstacleBounds: this.options.getObstacleBounds()
    });
    if (!targetValid) {
      this.presentSkillRejectionFeedback(target, successRadius);
      return;
    }

    if (kind === "Blink") {
      this.options.createBlinkSkillTargetFeedback(target, intent, resolveDirectionBetween(displayPosition, target));
      return;
    }

    this.options.createFreezeSkillTargetFeedback(target, intent);
  }

  private presentSkillRejectionFeedback(position: Vec2, radius: number): void {
    const nowMs = this.options.getNowMs();
    if (nowMs < this.nextSkillRejectFeedbackAtMs) {
      return;
    }

    this.nextSkillRejectFeedbackAtMs = nowMs + SKILL_REJECT_FEEDBACK_MIN_MS;
    this.options.createSkillRejectionFeedback(position, radius);
  }
}

function getPrimaryFeedbackIntervalMs(weaponKind: WeaponKind): number {
  const cooldownMs = WEAPON_DEFINITIONS[weaponKind].cooldownMs;
  return Math.max(PRIMARY_FEEDBACK_MIN_MS, cooldownMs);
}

function resolveMuzzleForwardDistance(player: Hero, weaponKind: WeaponKind): number {
  // Mirrors authoritative projectile birth: hero radius + projectile radius + 4px clearance.
  return player.radius + WEAPON_DEFINITIONS[weaponKind].radius + AUTHORITATIVE_PROJECTILE_BIRTH_CLEARANCE;
}

function canPresentPrimaryFeedback(weapon: WeaponState): boolean {
  if (weapon.reloadRemaining > 0 || weapon.overheated || weapon.overheatRemaining > 0) {
    return false;
  }

  const definition = WEAPON_DEFINITIONS[weapon.weaponKind];
  if (!definition.usesHeat && weapon.ammoInMagazine <= 0) {
    return false;
  }

  return true;
}

function canRequestReloadFeedback(weapon: WeaponState): boolean {
  return (
    weapon.weaponKind !== "Gatling" &&
    weapon.reloadRemaining <= 0 &&
    weapon.ammoInMagazine < weapon.magazineSize &&
    weapon.reserveAmmo !== null &&
    weapon.reserveAmmo > 0
  );
}

function canPresentSkillFeedback(player: Hero, kind: SkillKind): boolean {
  const skill = player.skills.find((entry) => entry.kind === kind);
  return skill !== undefined && skill.cooldownMs <= 0;
}

function resolveTargetedSkillFeedbackRequest(
  player: Hero,
  command: PlayerCommand,
  primaryPressStarted: boolean
): TargetedSkillFeedbackRequest | null {
  if (primaryPressStarted) {
    const releaseKind = resolveTargetedSkillReleaseKind(player, command);
    if (releaseKind) {
      return {
        kind: releaseKind,
        intent: "release",
        feedbackRadius: getTargetedSkillFeedbackRadius(releaseKind, "release")
      };
    }
  }

  const prepareKind = resolveToggledTargetedSkill(command);
  if (!prepareKind) {
    return null;
  }

  return {
    kind: prepareKind,
    intent: "prepare",
    feedbackRadius: getTargetedSkillFeedbackRadius(prepareKind, "prepare")
  };
}

function resolveTargetedSkillReleaseKind(player: Hero, command: PlayerCommand): TargetedFeedbackSkillKind | null {
  const toggledKind = resolveToggledTargetedSkill(command);
  if (toggledKind) {
    return toggledKind;
  }

  return player.preparedSkill;
}

function resolveToggledTargetedSkill(command: PlayerCommand): TargetedFeedbackSkillKind | null {
  if (command.toggleFreeze) {
    return "Freeze";
  }
  if (command.toggleBlink) {
    return "Blink";
  }

  return null;
}

function getTargetedSkillFeedbackRadius(
  kind: TargetedFeedbackSkillKind,
  intent: SkillFeedbackIntent
): number {
  if (kind === "Blink") {
    return intent === "release" ? BLINK_RELEASE_FEEDBACK_RADIUS : BLINK_PREPARE_FEEDBACK_RADIUS;
  }

  return intent === "release" ? FREEZE_RELEASE_FEEDBACK_RADIUS : FREEZE_PREPARE_FEEDBACK_RADIUS;
}

function resolveAimDirection(aim: Vec2): Vec2 {
  const length = Math.hypot(aim.x, aim.y);
  if (length <= 0.0001) {
    return { x: 1, y: 0 };
  }

  return {
    x: aim.x / length,
    y: aim.y / length
  };
}

function resolveDirectionBetween(from: Vec2, to: Vec2): Vec2 {
  return resolveAimDirection({
    x: to.x - from.x,
    y: to.y - from.y
  });
}
