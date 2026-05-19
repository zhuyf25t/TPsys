import type { Hero, PlayerCommand, PreparedSkill, SkillState, Vec2 } from "../../objects/types";
import { SKILL_DEFINITIONS } from "../skills";
import type { MotionObstacleBounds } from "../../runtime/local/movement/motionController";
import { isBlinkTargetValid } from "../../runtime/local/movement/blinkTargetResolver";

export interface AuthoritativeLocalHeroBlinkPredictionInput {
  player: Hero;
  position: Vec2;
  target: Vec2 | null | undefined;
  worldSize: Vec2;
  obstacleBounds: readonly MotionObstacleBounds[];
  blinkCooldownMs?: number;
  blinkActiveMs?: number;
}

export interface AuthoritativeLocalHeroBlinkPrediction {
  destination: Vec2;
}

export function resolveAuthoritativeLocalHeroBlinkPrediction({
  player,
  position,
  target,
  worldSize,
  obstacleBounds,
  blinkCooldownMs,
  blinkActiveMs
}: AuthoritativeLocalHeroBlinkPredictionInput): AuthoritativeLocalHeroBlinkPrediction | null {
  const blink = findBlinkSkillState(player.skills);
  const cooldownMs = blinkCooldownMs ?? blink?.cooldownMs;
  const activeMs = blinkActiveMs ?? blink?.activeMs ?? 0;

  if (
    blink === null ||
    !player.alive ||
    cooldownMs === undefined ||
    !isAuthoritativeLocalHeroBlinkReady(cooldownMs, activeMs) ||
    !isFinitePosition(position) ||
    !isFinitePosition(target) ||
    !isFinitePosition(worldSize) ||
    !Number.isFinite(player.radius) ||
    player.radius <= 0
  ) {
    return null;
  }

  if (distanceBetween(position, target) > SKILL_DEFINITIONS.Blink.range) {
    return null;
  }

  if (
    !isBlinkTargetValid({
      player,
      target,
      worldSize,
      obstacleBounds
    })
  ) {
    return null;
  }

  return {
    destination: clonePosition(target)
  };
}

export function findBlinkSkillState(skills: readonly SkillState[]): SkillState | null {
  return skills.find((skill) => skill.kind === "Blink") ?? null;
}

export function isAuthoritativeLocalHeroBlinkReady(cooldownMs: number, activeMs = 0): boolean {
  return Number.isFinite(cooldownMs) && cooldownMs <= 0 && Number.isFinite(activeMs) && activeMs <= 0;
}

export function getPredictedBlinkCooldownMs(): number {
  return SKILL_DEFINITIONS.Blink.cooldownMs;
}

export function isAuthoritativeLocalHeroBlinkConfirm(command: PlayerCommand, preparedSkill: PreparedSkill): boolean {
  if (!command.primaryJustPressed) {
    return false;
  }

  if (command.toggleFreeze) {
    return false;
  }

  if (command.toggleBlink) {
    return true;
  }

  return preparedSkill === "Blink";
}

function isFinitePosition(position: Vec2 | null | undefined): position is Vec2 {
  return position !== null && position !== undefined && Number.isFinite(position.x) && Number.isFinite(position.y);
}

function clonePosition(position: Vec2): Vec2 {
  return { x: position.x, y: position.y };
}

function distanceBetween(left: Vec2, right: Vec2): number {
  return Math.hypot(right.x - left.x, right.y - left.y);
}
