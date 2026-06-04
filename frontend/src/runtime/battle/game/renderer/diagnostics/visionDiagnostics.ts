import { getBattleDiagnosticsRoot, isBattleDiagnosticsEnabled } from "./battleDiagnosticsGate";
import {
  cloneBattleVisionVec2,
  createBattleVisionLookAheadDiagnostics,
  resolveBattleVisionScreenPxPerWorldUnit
} from "./functions/VisionDiagnosticsRules";
import type {
  BattleVisionCameraDiagnosticsRecordInput,
  BattleVisionDiagnosticsRoot,
  BattleVisionDiagnosticsSnapshot,
  BattleVisionLookAheadDiagnosticsRecordInput,
  BattleVisionViewportDiagnostics
} from "./objects/VisionDiagnosticsObjects";

export type {
  BattleVisionCameraDiagnostics,
  BattleVisionCameraDiagnosticsRecordInput,
  BattleVisionDiagnosticsSnapshot,
  BattleVisionLookAheadDiagnostics,
  BattleVisionLookAheadDiagnosticsRecordInput,
  BattleVisionScreenPxPerWorldUnit,
  BattleVisionViewportDiagnostics
} from "./objects/VisionDiagnosticsObjects";

export function recordBattleVisionCameraDiagnostics(input: BattleVisionCameraDiagnosticsRecordInput): void {
  if (!isBattleVisionDiagnosticsEnabled()) {
    return;
  }

  const { camera, playerDisplayPosition } = input;
  const worldView = camera.worldView;

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
      screenPxPerWorldUnit: resolveBattleVisionScreenPxPerWorldUnit({
        cameraWidth: camera.width,
        cameraHeight: camera.height,
        worldViewWidth: worldView.width,
        worldViewHeight: worldView.height
      }),
      ...(playerDisplayPosition ? { playerDisplayPosition: cloneBattleVisionVec2(playerDisplayPosition) } : {})
    },
    viewport: readViewportDiagnostics(),
    lastCameraAtMs: nowMs()
  });
}

export function recordBattleVisionLookAheadDiagnostics(input: BattleVisionLookAheadDiagnosticsRecordInput): void {
  if (!isBattleVisionDiagnosticsEnabled()) {
    return;
  }

  updateVisionDiagnostics({
    lookAhead: createBattleVisionLookAheadDiagnostics(input),
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

function nowMs(): number {
  if (typeof performance !== "undefined" && typeof performance.now === "function") {
    return performance.now();
  }

  return Date.now();
}
