import type { SkillState, Vec2 } from "../../objects/types";
import { SKILL_DEFINITIONS } from "../skills";
import { findDashDestination } from "../../runtime/local/geometry/sceneGeometry";
import type { MotionObstacleBounds } from "../../runtime/local/movement/motionController";

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
    destination: findDashDestination({
      position,
      direction,
      distance: SKILL_DEFINITIONS.Dash.distance,
      radius,
      worldSize,
      obstacleBounds
    }),
    direction
  };
}

export function findDashSkillState(skills: readonly SkillState[]): SkillState | null {
  return skills.find((skill) => skill.kind === "Dash") ?? null;
}

export function isAuthoritativeLocalHeroDashReady(cooldownMs: number, activeMs = 0): boolean {
  return Number.isFinite(cooldownMs) && cooldownMs <= 0 && Number.isFinite(activeMs) && activeMs <= 0;
}

export function getPredictedDashCooldownMs(): number {
  return SKILL_DEFINITIONS.Dash.cooldownMs;
}

function resolveDashDirection(movement: Vec2, aim: Vec2): Vec2 {
  return isVectorActive(movement) ? movement : aim;
}

function isVectorActive(vector: Vec2): boolean {
  return Math.hypot(vector.x, vector.y) > 0.0001;
}
