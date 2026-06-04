import type { BattleSlowFieldState as SlowField } from "../../../../../objects/battle/microservices/abilities/objects/skill/BattleSlowFieldState";
import type { BattleVector2 as Vec2 } from "../../../../../objects/battle/objects/core/BattleCoreScalars";

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

export function advanceFreezeFields(input: AdvanceFreezeFieldsInput): SlowField[] {
  return input.fields
    .map((field) => ({
      ...field,
      ttlMs: field.ttlMs - input.deltaMs
    }))
    .filter((field) => field.ttlMs > 0);
}

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

export function getFreezeSpeedMultiplier(position: Vec2, fields: readonly SlowField[]): number {
  return isInsideAnyFreezeField(position, fields) ? FREEZE_SPEED_MULTIPLIER : 1;
}

export function isInsideAnyFreezeField(position: Vec2, fields: readonly SlowField[]): boolean {
  return fields.some((field) => {
    const dx = position.x - field.position.x;
    const dy = position.y - field.position.y;
    return dx * dx + dy * dy <= field.radius * field.radius;
  });
}

export function isFreezeTargetInRange(origin: Vec2, target: Vec2, range: number): boolean {
  return Math.hypot(target.x - origin.x, target.y - origin.y) <= range;
}
