import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";
import type {
  BattleVisionLookAheadDiagnostics,
  BattleVisionLookAheadDiagnosticsRecordInput,
  BattleVisionScreenPxPerWorldUnit
} from "../objects/VisionDiagnosticsObjects";

export function resolveBattleVisionScreenPxPerWorldUnit(input: {
  cameraWidth: number;
  cameraHeight: number;
  worldViewWidth: number;
  worldViewHeight: number;
}): BattleVisionScreenPxPerWorldUnit {
  const x = input.worldViewWidth > 0 ? input.cameraWidth / input.worldViewWidth : null;
  const y = input.worldViewHeight > 0 ? input.cameraHeight / input.worldViewHeight : null;
  return {
    x,
    y,
    average: x !== null && y !== null ? (x + y) / 2 : null
  };
}

export function createBattleVisionLookAheadDiagnostics(
  input: BattleVisionLookAheadDiagnosticsRecordInput
): BattleVisionLookAheadDiagnostics {
  return {
    pointer: cloneBattleVisionVec2(input.pointer),
    rawPointer: cloneBattleVisionVec2(input.rawPointer),
    pointerReady: input.pointerReady,
    screenCenter: cloneBattleVisionVec2(input.screenCenter),
    desiredOffset: cloneBattleVisionVec2(input.desiredOffset),
    actualOffset: cloneBattleVisionVec2(input.actualOffset),
    targetPosition: cloneBattleVisionVec2(input.targetPosition),
    playerPosition: cloneBattleVisionVec2(input.playerPosition),
    actualOffsetDistance: battleVisionVectorLength(input.actualOffset),
    targetAheadDistance: distanceBetweenBattleVisionVec2(input.targetPosition, input.playerPosition),
    constants: {
      ratio: input.ratio,
      max: cloneBattleVisionVec2(input.max),
      lerp: cloneBattleVisionVec2(input.lerp)
    }
  };
}

export function cloneBattleVisionVec2(vector: Vec2): Vec2 {
  return {
    x: vector.x,
    y: vector.y
  };
}

function battleVisionVectorLength(vector: Vec2): number {
  return Math.hypot(vector.x, vector.y);
}

function distanceBetweenBattleVisionVec2(left: Vec2, right: Vec2): number {
  return Math.hypot(right.x - left.x, right.y - left.y);
}
