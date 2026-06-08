import type Phaser from "phaser";
import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";

export interface ConfigureBattleCameraInput {
  camera: Phaser.Cameras.Scene2D.Camera;
  worldSize: Vec2;
  globalPadding: number;
}

export interface UpdateBattleCameraTargetInput {
  pointer: Phaser.Input.Pointer;
  scaleSize: Phaser.Structs.Size;
  playerPosition: Vec2;
  cameraTarget: Phaser.GameObjects.Zone;
  cameraOffset: Vec2;
  cameraFocus: Vec2;
  deltaMs: number;
}

export interface BattleCameraBoundsPlan {
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface BattleCameraDeadzonePlan {
  width: number;
  height: number;
}

export interface BattleCameraConfigurationPlan {
  bounds: BattleCameraBoundsPlan;
  zoom: number;
  roundPixels: boolean;
  backgroundColor: string;
  deadzone: BattleCameraDeadzonePlan;
}

export interface BattleCameraPointerSample {
  position: Vec2;
  hasEvent: boolean;
  moveTime: number;
  downTime: number;
  upTime: number;
}

export interface BattleCameraScaleSize {
  width: number;
  height: number;
}

export interface BattleCameraResolvedPointer {
  position: Vec2;
  ready: boolean;
}

export interface ResolveBattleCameraTargetUpdatePlanInput {
  pointer: BattleCameraPointerSample;
  scaleSize: BattleCameraScaleSize;
  playerPosition: Vec2;
  cameraOffset: Vec2;
  cameraFocus: Vec2;
  deltaMs: number;
}

export interface BattleCameraTargetUpdatePlan {
  screenCenter: Vec2;
  resolvedPointer: BattleCameraResolvedPointer;
  desiredOffset: Vec2;
  nextOffset: Vec2;
  nextFocus: Vec2;
  targetPosition: Vec2;
  ratio: number;
  max: Vec2;
  lerp: Vec2;
}
