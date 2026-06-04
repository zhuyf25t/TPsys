import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";

export interface LocalHeroDisplayPose {
  position: Vec2;
  facing: number;
}

export interface LocalHeroDisplayPoseReader {
  read(): LocalHeroDisplayPose;
}

export interface LocalHeroDisplayPoseStore extends LocalHeroDisplayPoseReader {
  write(displayPose: LocalHeroDisplayPose): void;
}

export interface LocalHeroDisplayPositionStore extends LocalHeroDisplayPoseStore {
  writePosition(position: Vec2): void;
}

export interface LocalHeroDisplayActor {
  x: number;
  y: number;
  rotation: number;
  setPosition(x: number, y: number): unknown;
  setRotation(rotation: number): unknown;
}
