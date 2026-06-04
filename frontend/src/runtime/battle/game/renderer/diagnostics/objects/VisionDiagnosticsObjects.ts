import type Phaser from "phaser";
import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";

export interface BattleVisionDiagnosticsRoot {
  vision?: BattleVisionDiagnosticsSnapshot;
  [key: string]: unknown;
}

export interface BattleVisionDiagnosticsSnapshot {
  camera?: BattleVisionCameraDiagnostics;
  viewport?: BattleVisionViewportDiagnostics;
  lookAhead?: BattleVisionLookAheadDiagnostics;
  lastCameraAtMs?: number;
  lastLookAheadAtMs?: number;
}

export interface BattleVisionCameraDiagnostics {
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
  screenPxPerWorldUnit: BattleVisionScreenPxPerWorldUnit;
  playerDisplayPosition?: Vec2;
}

export interface BattleVisionScreenPxPerWorldUnit {
  x: number | null;
  y: number | null;
  average: number | null;
}

export interface BattleVisionViewportDiagnostics {
  windowInnerWidth: number | null;
  windowInnerHeight: number | null;
  devicePixelRatio: number | null;
  canvasClient: {
    width: number;
    height: number;
  } | null;
}

export interface BattleVisionLookAheadDiagnostics {
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

export interface BattleVisionCameraDiagnosticsRecordInput {
  camera: Phaser.Cameras.Scene2D.Camera;
  playerDisplayPosition?: Vec2;
}

export interface BattleVisionLookAheadDiagnosticsRecordInput {
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
}
