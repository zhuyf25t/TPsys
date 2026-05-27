import type { SkillState, Vec2 } from "../../../../objects/battle/types";
import { SKILL_DEFINITIONS } from "../skills";
import { findDashDestination } from "../../local/geometry/sceneGeometry";
import type { MotionObstacleBounds } from "../../local/movement/motionController";

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

/** 中文名：解析authoritative本地英雄dashprediction（resolveAuthoritativeLocalHeroDashPrediction）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
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

/** 中文名：查找dash技能状态（findDashSkillState）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function findDashSkillState(skills: readonly SkillState[]): SkillState | null {
  return skills.find((skill) => skill.kind === "Dash") ?? null;
}

/** 中文名：判断是否authoritative本地英雄dashready（isAuthoritativeLocalHeroDashReady）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function isAuthoritativeLocalHeroDashReady(cooldownMs: number, activeMs = 0): boolean {
  return Number.isFinite(cooldownMs) && cooldownMs <= 0 && Number.isFinite(activeMs) && activeMs <= 0;
}

/** 中文名：获取predicteddashcooldownms（getPredictedDashCooldownMs）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function getPredictedDashCooldownMs(): number {
  return SKILL_DEFINITIONS.Dash.cooldownMs;
}

function resolveDashDirection(movement: Vec2, aim: Vec2): Vec2 {
  return isVectorActive(movement) ? movement : aim;
}

function isVectorActive(vector: Vec2): boolean {
  return Math.hypot(vector.x, vector.y) > 0.0001;
}
