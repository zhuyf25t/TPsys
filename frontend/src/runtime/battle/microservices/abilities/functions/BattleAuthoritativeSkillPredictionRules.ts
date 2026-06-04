import type { BattleHeroViewState as Hero } from "../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { BattlePreparedSkill as PreparedSkill } from "../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { BattlePlayerCommand as PlayerCommand } from "../../../../../objects/battle/microservices/session/objects/command/BattlePlayerCommand";
import type { BattleStateSkillResponseDto as SkillState } from "../../../../../objects/battle/microservices/session/api/state/BattleStatePlayerResponseApiTypes";
import type { BattleVector2 as Vec2 } from "../../../../../objects/battle/objects/core/BattleCoreScalars";
import { SKILL_DEFINITIONS } from "../../../../../objects/battle/microservices/abilities/objects/abilities/BattleAbilityRuleDefinitions";
import {
  findMotionDestination,
  isMotionTargetPointValid,
  type MotionObstacleBounds
} from "../../world/functions/BattleMotionRules";

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

export interface AuthoritativeLocalHeroDashPredictionInput {
  position: Vec2;
  movement: Vec2;
  aim: Vec2;
  radius: number;
  worldSize: Vec2;
  obstacleBounds: readonly MotionObstacleBounds[];
  dashCooldownMs: number;
  dashActiveMs?: number;
  alive: boolean;
}

export interface AuthoritativeLocalHeroDashPrediction {
  destination: Vec2;
  direction: Vec2;
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
    !isMotionTargetPointValid({
      target,
      radius: player.radius,
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

export function resolveAuthoritativeLocalHeroDashPrediction({
  position,
  movement,
  aim,
  radius,
  worldSize,
  obstacleBounds,
  dashCooldownMs,
  dashActiveMs = 0,
  alive
}: AuthoritativeLocalHeroDashPredictionInput): AuthoritativeLocalHeroDashPrediction | null {
  if (!alive || !isAuthoritativeLocalHeroDashReady(dashCooldownMs, dashActiveMs)) {
    return null;
  }

  const direction = resolveDashDirection(movement, aim);
  if (!isVectorActive(direction)) {
    return null;
  }

  return {
    destination: findMotionDestination({
      position,
      direction,
      distance: SKILL_DEFINITIONS.Dash.distance,
      radius,
      worldSize,
      obstacleBounds
    }).destination,
    direction
  };
}

export function findBlinkSkillState(skills: readonly SkillState[]): SkillState | null {
  return skills.find((skill) => skill.kind === "Blink") ?? null;
}

export function findDashSkillState(skills: readonly SkillState[]): SkillState | null {
  return skills.find((skill) => skill.kind === "Dash") ?? null;
}

export function isAuthoritativeLocalHeroBlinkReady(cooldownMs: number, activeMs = 0): boolean {
  return Number.isFinite(cooldownMs) && cooldownMs <= 0 && Number.isFinite(activeMs) && activeMs <= 0;
}

export function isAuthoritativeLocalHeroDashReady(cooldownMs: number, activeMs = 0): boolean {
  return Number.isFinite(cooldownMs) && cooldownMs <= 0 && Number.isFinite(activeMs) && activeMs <= 0;
}

export function getPredictedBlinkCooldownMs(): number {
  return SKILL_DEFINITIONS.Blink.cooldownMs;
}

export function getPredictedDashCooldownMs(): number {
  return SKILL_DEFINITIONS.Dash.cooldownMs;
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

function resolveDashDirection(movement: Vec2, aim: Vec2): Vec2 {
  return isVectorActive(movement) ? movement : aim;
}

function isVectorActive(vector: Vec2): boolean {
  return Math.hypot(vector.x, vector.y) > 0.0001;
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
