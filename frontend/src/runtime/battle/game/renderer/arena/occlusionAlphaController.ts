import Phaser from "phaser";
import { resolveOcclusionAlphaPlans, resolveOcclusionProbePlan } from "./functions/OcclusionAlphaRules";
import type { OcclusionAlphaInput, OcclusionProbePlan, OcclusionProbeShape } from "./objects/OcclusionAlphaObjects";

export type { OcclusionAlphaInput } from "./objects/OcclusionAlphaObjects";

/** 中文名：更新occludablealpha（updateOccludableAlpha）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function updateOccludableAlpha(input: OcclusionAlphaInput): void {
  const probe = createOcclusionProbe(resolveOcclusionProbePlan(input));
  resolveOcclusionAlphaPlans({
    ...input,
    probe,
    intersectsProbe: intersectsOcclusionProbe,
    lerpAlpha: lerpOcclusionAlpha
  }).forEach((plan) => {
    plan.occludable.sprite.setAlpha(plan.alpha);
  });
}

function createOcclusionProbe(plan: OcclusionProbePlan): OcclusionProbeShape {
  return new Phaser.Geom.Rectangle(plan.x, plan.y, plan.width, plan.height);
}

function intersectsOcclusionProbe(bounds: OcclusionProbeShape, probe: OcclusionProbeShape): boolean {
  return Phaser.Geom.Intersects.RectangleToRectangle(bounds, probe);
}

function lerpOcclusionAlpha(current: number, target: number, amount: number): number {
  return Phaser.Math.Linear(current, target, amount);
}
