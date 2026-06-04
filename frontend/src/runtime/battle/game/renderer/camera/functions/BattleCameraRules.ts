import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";
import type {
  BattleCameraConfigurationPlan,
  BattleCameraPointerSample,
  BattleCameraResolvedPointer,
  BattleCameraTargetUpdatePlan,
  ResolveBattleCameraTargetUpdatePlanInput
} from "../objects/BattleCameraObjects";

const BATTLE_CAMERA_DEADZONE = { width: 0, height: 0 };
const BATTLE_CAMERA_ZOOM = 1.4;
const BATTLE_CAMERA_BACKGROUND_COLOR = "#57a6d9";
const BATTLE_CAMERA_ROUND_PIXELS = false;
const BATTLE_CAMERA_POINTER_LOOK_AHEAD_RATIO = 0.38;
const BATTLE_CAMERA_POINTER_LOOK_AHEAD_MAX = { x: 260, y: 260 };
const BATTLE_CAMERA_OFFSET_LERP = { x: 1, y: 1 };

export function resolveBattleCameraConfigurationPlan(worldSize: Vec2, globalPadding: number): BattleCameraConfigurationPlan {
  return {
    bounds: {
      x: -globalPadding,
      y: -globalPadding,
      width: worldSize.x + globalPadding * 2,
      height: worldSize.y + globalPadding * 2
    },
    zoom: BATTLE_CAMERA_ZOOM,
    roundPixels: BATTLE_CAMERA_ROUND_PIXELS,
    backgroundColor: BATTLE_CAMERA_BACKGROUND_COLOR,
    deadzone: BATTLE_CAMERA_DEADZONE
  };
}

export function resolveBattleCameraTargetUpdatePlan({
  pointer,
  scaleSize,
  playerPosition,
  cameraOffset
}: ResolveBattleCameraTargetUpdatePlanInput): BattleCameraTargetUpdatePlan {
  const screenCenter = {
    x: scaleSize.width / 2,
    y: scaleSize.height / 2
  };
  const resolvedPointer = resolveBattleCameraPointer(pointer, screenCenter);
  const desiredOffset = {
    x: clamp(
      (resolvedPointer.position.x - screenCenter.x) * BATTLE_CAMERA_POINTER_LOOK_AHEAD_RATIO,
      -BATTLE_CAMERA_POINTER_LOOK_AHEAD_MAX.x,
      BATTLE_CAMERA_POINTER_LOOK_AHEAD_MAX.x
    ),
    y: clamp(
      (resolvedPointer.position.y - screenCenter.y) * BATTLE_CAMERA_POINTER_LOOK_AHEAD_RATIO,
      -BATTLE_CAMERA_POINTER_LOOK_AHEAD_MAX.y,
      BATTLE_CAMERA_POINTER_LOOK_AHEAD_MAX.y
    )
  };
  const nextOffset = {
    x: linear(cameraOffset.x, desiredOffset.x, BATTLE_CAMERA_OFFSET_LERP.x),
    y: linear(cameraOffset.y, desiredOffset.y, BATTLE_CAMERA_OFFSET_LERP.y)
  };

  return {
    screenCenter,
    resolvedPointer,
    desiredOffset,
    nextOffset,
    targetPosition: {
      x: playerPosition.x + nextOffset.x,
      y: playerPosition.y + nextOffset.y
    },
    ratio: BATTLE_CAMERA_POINTER_LOOK_AHEAD_RATIO,
    max: BATTLE_CAMERA_POINTER_LOOK_AHEAD_MAX,
    lerp: BATTLE_CAMERA_OFFSET_LERP
  };
}

function resolveBattleCameraPointer(pointer: BattleCameraPointerSample, screenCenter: Vec2): BattleCameraResolvedPointer {
  const pointerHasEvent = pointer.hasEvent || pointer.moveTime > 0 || pointer.downTime > 0 || pointer.upTime > 0;
  if (!pointerHasEvent || !Number.isFinite(pointer.position.x) || !Number.isFinite(pointer.position.y)) {
    return {
      position: screenCenter,
      ready: false
    };
  }

  return {
    position: pointer.position,
    ready: true
  };
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max);
}

function linear(start: number, end: number, amount: number): number {
  return start + (end - start) * amount;
}
