import type Phaser from "phaser";
import type { Vec2 } from "../../objects/types";
import { getBattleDiagnosticsRoot, isBattleDiagnosticsEnabled } from "./battleDiagnosticsGate";

interface BattleVisionDiagnosticsRoot {
  vision?: BattleVisionDiagnosticsSnapshot;
  [key: string]: unknown;
}

interface BattleVisionDiagnosticsSnapshot {
  camera?: BattleVisionCameraDiagnostics;
  viewport?: BattleVisionViewportDiagnostics;
  lookAhead?: BattleVisionLookAheadDiagnostics;
  lastCameraAtMs?: number;
  lastLookAheadAtMs?: number;
}

interface BattleVisionCameraDiagnostics {
  width: number;
  height: number;
  zoom: number;
  roundPixels: boolean;
  scrollX: number;
  scrollY: number;
  centerX: number;
  centerY: number;
  worldView: {
    x: number;
    y: number;
    width: number;
    height: number;
  };
  screenPxPerWorldUnit: {
    x: number | null;
    y: number | null;
    average: number | null;
  };
  playerDisplayPosition?: Vec2;
}

interface BattleVisionViewportDiagnostics {
  windowInnerWidth: number | null;
  windowInnerHeight: number | null;
  devicePixelRatio: number | null;
  canvasClient: {
    width: number;
    height: number;
  } | null;
}

interface BattleVisionLookAheadDiagnostics {
  pointer: Vec2;
  rawPointer: Vec2;
  pointerReady: boolean;
  screenCenter: Vec2;
  desiredOffset: Vec2;
  actualOffset: Vec2;
  targetPosition: Vec2;
  playerPosition: Vec2;
  actualOffsetDistance: number;
  targetAheadDistance: number;
  constants: {
    ratio: number;
    max: Vec2;
    lerp: Vec2;
  };
}

export function recordBattleVisionCameraDiagnostics(input: {
  camera: Phaser.Cameras.Scene2D.Camera;
  playerDisplayPosition?: Vec2;
}): void {
  if (!isBattleVisionDiagnosticsEnabled()) {
    return;
  }

  const { camera, playerDisplayPosition } = input;
  const worldView = camera.worldView;
  const screenPxPerWorldUnitX = worldView.width > 0 ? camera.width / worldView.width : null;
  const screenPxPerWorldUnitY = worldView.height > 0 ? camera.height / worldView.height : null;
  const screenPxPerWorldUnitAverage =
    screenPxPerWorldUnitX !== null && screenPxPerWorldUnitY !== null
      ? (screenPxPerWorldUnitX + screenPxPerWorldUnitY) / 2
      : null;

  updateVisionDiagnostics({
    camera: {
      width: camera.width,
      height: camera.height,
      zoom: camera.zoom,
      roundPixels: camera.roundPixels,
      scrollX: camera.scrollX,
      scrollY: camera.scrollY,
      centerX: camera.centerX,
      centerY: camera.centerY,
      worldView: {
        x: worldView.x,
        y: worldView.y,
        width: worldView.width,
        height: worldView.height
      },
      screenPxPerWorldUnit: {
        x: screenPxPerWorldUnitX,
        y: screenPxPerWorldUnitY,
        average: screenPxPerWorldUnitAverage
      },
      ...(playerDisplayPosition ? { playerDisplayPosition: cloneVec2(playerDisplayPosition) } : {})
    },
    viewport: readViewportDiagnostics(),
    lastCameraAtMs: nowMs()
  });
}

export function recordBattleVisionLookAheadDiagnostics(input: {
  pointer: Vec2;
  rawPointer: Vec2;
  pointerReady: boolean;
  screenCenter: Vec2;
  desiredOffset: Vec2;
  actualOffset: Vec2;
  targetPosition: Vec2;
  playerPosition: Vec2;
  ratio: number;
  max: Vec2;
  lerp: Vec2;
}): void {
  if (!isBattleVisionDiagnosticsEnabled()) {
    return;
  }

  updateVisionDiagnostics({
    lookAhead: {
      pointer: cloneVec2(input.pointer),
      rawPointer: cloneVec2(input.rawPointer),
      pointerReady: input.pointerReady,
      screenCenter: cloneVec2(input.screenCenter),
      desiredOffset: cloneVec2(input.desiredOffset),
      actualOffset: cloneVec2(input.actualOffset),
      targetPosition: cloneVec2(input.targetPosition),
      playerPosition: cloneVec2(input.playerPosition),
      actualOffsetDistance: vectorLength(input.actualOffset),
      targetAheadDistance: distanceBetween(input.targetPosition, input.playerPosition),
      constants: {
        ratio: input.ratio,
        max: cloneVec2(input.max),
        lerp: cloneVec2(input.lerp)
      }
    },
    lastLookAheadAtMs: nowMs()
  });
}

export function isBattleVisionDiagnosticsEnabled(): boolean {
  return isBattleDiagnosticsEnabled();
}

function updateVisionDiagnostics(patch: Partial<BattleVisionDiagnosticsSnapshot>): void {
  const diagnosticsRoot = getBattleDiagnosticsRoot<BattleVisionDiagnosticsRoot>();
  if (!diagnosticsRoot) {
    return;
  }

  diagnosticsRoot.vision = {
    ...(diagnosticsRoot.vision ?? {}),
    ...patch
  };
}

function readViewportDiagnostics(): BattleVisionViewportDiagnostics {
  if (typeof window === "undefined") {
    return {
      windowInnerWidth: null,
      windowInnerHeight: null,
      devicePixelRatio: null,
      canvasClient: null
    };
  }

  const canvas = document.querySelector("canvas");
  const canvasRect = canvas ? canvas.getBoundingClientRect() : null;
  return {
    windowInnerWidth: window.innerWidth,
    windowInnerHeight: window.innerHeight,
    devicePixelRatio: window.devicePixelRatio,
    canvasClient: canvasRect
      ? {
          width: canvasRect.width,
          height: canvasRect.height
        }
      : null
  };
}

function cloneVec2(vector: Vec2): Vec2 {
  return {
    x: vector.x,
    y: vector.y
  };
}

function vectorLength(vector: Vec2): number {
  return Math.hypot(vector.x, vector.y);
}

function distanceBetween(left: Vec2, right: Vec2): number {
  return Math.hypot(right.x - left.x, right.y - left.y);
}

function nowMs(): number {
  if (typeof performance !== "undefined" && typeof performance.now === "function") {
    return performance.now();
  }

  return Date.now();
}
