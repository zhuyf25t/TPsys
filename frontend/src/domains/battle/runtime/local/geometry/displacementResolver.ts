import type { Vec2 } from "../../../objects/types";
import { findDashDestination, normalizeVector, type SceneGeometryObstacleBounds } from "./sceneGeometry";

export interface ResolveDisplacementInput {
  position: Vec2;
  radius: number;
  direction: Vec2;
  strength: number;
  worldSize: Vec2;
  obstacleBounds: readonly SceneGeometryObstacleBounds[];
}

function resolveDisplacementDestination(
  position: Vec2,
  radius: number,
  direction: Vec2,
  distance: number,
  worldSize: Vec2,
  obstacleBounds: readonly SceneGeometryObstacleBounds[]
): Vec2 | null {
  const normalized = normalizeVector(direction);
  if (normalized.x === 0 && normalized.y === 0) {
    return null;
  }

  return findDashDestination({
    position,
    direction: normalized,
    distance,
    radius,
    worldSize,
    obstacleBounds
  });
}

/** 中文名：解析recoildestination（resolveRecoilDestination）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function resolveRecoilDestination(input: ResolveDisplacementInput): Vec2 | null {
  const recoilDistance = Math.min(24, input.strength * 0.18);
  return resolveDisplacementDestination(
    input.position,
    input.radius,
    { x: -input.direction.x, y: -input.direction.y },
    recoilDistance,
    input.worldSize,
    input.obstacleBounds
  );
}

/** 中文名：解析knockbackdestination（resolveKnockbackDestination）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function resolveKnockbackDestination(input: ResolveDisplacementInput): Vec2 | null {
  const knockbackDistance = Math.min(28, input.strength * 0.14);
  return resolveDisplacementDestination(
    input.position,
    input.radius,
    input.direction,
    knockbackDistance,
    input.worldSize,
    input.obstacleBounds
  );
}
