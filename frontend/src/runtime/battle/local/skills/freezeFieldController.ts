import type { SlowField, Vec2 } from "../../../../objects/battle/types";

export const FREEZE_SPEED_MULTIPLIER = 0.5;

export interface AppendFreezeFieldInput {
  fields: readonly SlowField[];
  sequence: number;
  ownerHeroId: string;
  position: Vec2;
  radius: number;
  durationMs: number;
}

export interface AppendFreezeFieldResult {
  nextFields: SlowField[];
  nextSequence: number;
}

export interface AdvanceFreezeFieldsInput {
  fields: readonly SlowField[];
  deltaMs: number;
}

/** 中文名：推进freezefields（advanceFreezeFields）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function advanceFreezeFields(input: AdvanceFreezeFieldsInput): SlowField[] {
  return input.fields
    .map((field) => ({
      ...field,
      ttlMs: field.ttlMs - input.deltaMs
    }))
    .filter((field) => field.ttlMs > 0);
}

/** 中文名：appendfreezefield（appendFreezeField）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function appendFreezeField(input: AppendFreezeFieldInput): AppendFreezeFieldResult {
  return {
    nextFields: [
      ...input.fields,
      {
        fieldId: `freeze-${input.sequence}`,
        ownerHeroId: input.ownerHeroId,
        position: { x: input.position.x, y: input.position.y },
        radius: input.radius,
        ttlMs: input.durationMs,
        durationMs: input.durationMs
      }
    ],
    nextSequence: input.sequence + 1
  };
}

/** 中文名：获取freezespeedmultiplier（getFreezeSpeedMultiplier）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function getFreezeSpeedMultiplier(position: Vec2, fields: readonly SlowField[]): number {
  return isInsideAnyFreezeField(position, fields) ? FREEZE_SPEED_MULTIPLIER : 1;
}

/** 中文名：判断是否insideanyfreezefield（isInsideAnyFreezeField）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function isInsideAnyFreezeField(position: Vec2, fields: readonly SlowField[]): boolean {
  return fields.some((field) => {
    const dx = position.x - field.position.x;
    const dy = position.y - field.position.y;
    return dx * dx + dy * dy <= field.radius * field.radius;
  });
}

/** 中文名：判断是否freeze目标inrange（isFreezeTargetInRange）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function isFreezeTargetInRange(origin: Vec2, target: Vec2, range: number): boolean {
  return Math.hypot(target.x - origin.x, target.y - origin.y) <= range;
}
