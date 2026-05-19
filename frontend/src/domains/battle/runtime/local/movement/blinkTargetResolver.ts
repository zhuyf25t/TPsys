import type { Hero, Vec2 } from "../../../objects/types";
import type { SceneGeometryObstacleBounds } from "../geometry/sceneGeometry";
import { isMotionTargetPointValid } from "./motionController";

export interface BlinkTargetValidityInput {
  player: Hero;
  target: Vec2;
  worldSize: Vec2;
  obstacleBounds: readonly SceneGeometryObstacleBounds[];
}

export function isBlinkTargetValid(input: BlinkTargetValidityInput): boolean {
  return isMotionTargetPointValid({
    target: input.target,
    radius: input.player.radius,
    worldSize: input.worldSize,
    obstacleBounds: input.obstacleBounds
  });
}
