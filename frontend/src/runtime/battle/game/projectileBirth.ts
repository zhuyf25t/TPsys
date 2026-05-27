import type { Vec2 } from "../../../objects/battle/types";

export const AUTHORITATIVE_PROJECTILE_BIRTH_CLEARANCE = 4;

export interface ProjectileBirthPositionInput {
  ownerPosition: Vec2;
  direction: Vec2;
  ownerRadius: number;
  projectileRadius: number;
}

/** 中文名：解析投射物birthforwarddistance（resolveProjectileBirthForwardDistance）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function resolveProjectileBirthForwardDistance(ownerRadius: number, projectileRadius: number): number {
  return ownerRadius + projectileRadius + AUTHORITATIVE_PROJECTILE_BIRTH_CLEARANCE;
}

/** 中文名：解析投射物birthposition（resolveProjectileBirthPosition）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function resolveProjectileBirthPosition(input: ProjectileBirthPositionInput): Vec2 {
  const forwardDistance = resolveProjectileBirthForwardDistance(input.ownerRadius, input.projectileRadius);
  return {
    x: input.ownerPosition.x + input.direction.x * forwardDistance,
    y: input.ownerPosition.y + input.direction.y * forwardDistance
  };
}
