import type { Hero, Vec2 } from "../../../objects/types";
import type { SceneGeometryObstacleBounds } from "../geometry/sceneGeometry";
import { isMotionTargetPointValid } from "./motionController";

export interface BlinkTargetValidityInput {
  player: Hero;
  target: Vec2;
  worldSize: Vec2;
  obstacleBounds: readonly SceneGeometryObstacleBounds[];
}

/** 中文名：判断是否blink目标valid（isBlinkTargetValid）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function isBlinkTargetValid(input: BlinkTargetValidityInput): boolean {
  return isMotionTargetPointValid({
    target: input.target,
    radius: input.player.radius,
    worldSize: input.worldSize,
    obstacleBounds: input.obstacleBounds
  });
}
