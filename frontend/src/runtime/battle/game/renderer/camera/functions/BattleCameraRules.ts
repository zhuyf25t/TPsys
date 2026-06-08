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
const BATTLE_CAMERA_OFFSET_LERP = { x: 0.16, y: 0.16 };
const BATTLE_CAMERA_FOCUS_HALF_LIFE_MS = 82;
const BATTLE_CAMERA_OFFSET_HALF_LIFE_MS = 70;
const BATTLE_CAMERA_FOCUS_DEADZONE = 2.5;
const BATTLE_CAMERA_FOCUS_SNAP_DISTANCE = 520;

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
  cameraOffset,
  cameraFocus,
  deltaMs
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
  const offsetAlpha = smoothingAlpha(deltaMs, BATTLE_CAMERA_OFFSET_HALF_LIFE_MS);
  const nextOffset = {
    x: linear(cameraOffset.x, desiredOffset.x, offsetAlpha),
    y: linear(cameraOffset.y, desiredOffset.y, offsetAlpha)
  };
  const nextFocus = resolveBattleCameraFocus({
    cameraFocus,
    playerPosition,
    deltaMs
  });

  return {
    screenCenter,
    resolvedPointer,
    desiredOffset,
    nextOffset,
    nextFocus,
    targetPosition: {
      x: nextFocus.x + nextOffset.x,
      y: nextFocus.y + nextOffset.y
    },
    ratio: BATTLE_CAMERA_POINTER_LOOK_AHEAD_RATIO,
    max: BATTLE_CAMERA_POINTER_LOOK_AHEAD_MAX,
    lerp: BATTLE_CAMERA_OFFSET_LERP
  };
}

function resolveBattleCameraFocus({
  cameraFocus,
  playerPosition,
  deltaMs
}: {
  cameraFocus: Vec2;
  playerPosition: Vec2;
  deltaMs: number;
}): Vec2 {
  if (!isFinitePosition(cameraFocus) || !isFinitePosition(playerPosition)) {
    return clonePosition(playerPosition);
  }

  const deltaX = playerPosition.x - cameraFocus.x;
  const deltaY = playerPosition.y - cameraFocus.y;
  const distance = Math.hypot(deltaX, deltaY);
  if (!Number.isFinite(distance) || distance >= BATTLE_CAMERA_FOCUS_SNAP_DISTANCE) {
    return clonePosition(playerPosition);
  }

  if (distance <= BATTLE_CAMERA_FOCUS_DEADZONE) {
    return clonePosition(cameraFocus);
  }

  const alpha = smoothingAlpha(deltaMs, BATTLE_CAMERA_FOCUS_HALF_LIFE_MS);
  return {
    x: linear(cameraFocus.x, playerPosition.x, alpha),
    y: linear(cameraFocus.y, playerPosition.y, alpha)
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

function smoothingAlpha(deltaMs: number, halfLifeMs: number): number {
  const safeDeltaMs = Math.max(0, Number.isFinite(deltaMs) ? deltaMs : 0);
  const safeHalfLifeMs = Math.max(1, Number.isFinite(halfLifeMs) ? halfLifeMs : 1);
  if (safeDeltaMs <= 0) {
    return 1 - Math.pow(0.5, 16.6667 / safeHalfLifeMs);
  }
  return clamp(1 - Math.pow(0.5, safeDeltaMs / safeHalfLifeMs), 0, 1);
}

function isFinitePosition(position: Vec2): boolean {
  return Number.isFinite(position.x) && Number.isFinite(position.y);
}

function clonePosition(position: Vec2): Vec2 {
  return { x: position.x, y: position.y };
}
