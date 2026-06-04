import type { BattleSlowFieldState as SlowField } from "../../../../../objects/battle/microservices/abilities/objects/skill/BattleSlowFieldState";
import type Phaser from "phaser";
import {
  resolveSlowFieldViewCreationPlan,
  resolveSlowFieldViewReleasePlan,
  resolveSlowFieldViewVisualPlan
} from "./functions/SlowFieldPresentationRules";
import type {
  SlowFieldCircleCreationPlan,
  SlowFieldCircleReleasePlan,
  SlowFieldCircleVisualPlan,
  SlowFieldView,
  SlowFieldViewReleasePlan
} from "./objects/SlowFieldViewObjects";

export function createSlowFieldView(scene: Phaser.Scene, field: SlowField): SlowFieldView {
  const plan = resolveSlowFieldViewCreationPlan({ field });

  return {
    fill: createSlowFieldCircle(scene, plan.fill),
    rim: createSlowFieldCircle(scene, plan.rim)
  };
}

export function releaseSlowFieldView(view: SlowFieldView): void {
  applySlowFieldViewReleasePlan(view, resolveSlowFieldViewReleasePlan());
}

export function syncSlowFieldViewVisuals(view: SlowFieldView, field: SlowField): void {
  const plan = resolveSlowFieldViewVisualPlan({ field });
  syncSlowFieldCircle(view.fill, plan.fill);
  syncSlowFieldCircle(view.rim, plan.rim);
}

function createSlowFieldCircle(
  scene: Phaser.Scene,
  plan: SlowFieldCircleCreationPlan
): Phaser.GameObjects.Arc {
  const circle = scene.add
    .circle(plan.position.x, plan.position.y, plan.radius, plan.fillColor, plan.fillAlpha)
    .setDepth(plan.depth);

  if (plan.stroke) {
    circle.setStrokeStyle(plan.stroke.width, plan.stroke.color, plan.stroke.alpha);
  }

  return circle;
}

function syncSlowFieldCircle(circle: Phaser.GameObjects.Arc, plan: SlowFieldCircleVisualPlan): void {
  circle.setPosition(plan.position.x, plan.position.y);
  circle.setRadius(plan.radius);

  if (plan.fill) {
    circle.setFillStyle(plan.fill.color, plan.fill.alpha);
  }
  if (plan.stroke) {
    circle.setStrokeStyle(plan.stroke.width, plan.stroke.color, plan.stroke.alpha);
  }
}

function applySlowFieldViewReleasePlan(view: SlowFieldView, plan: SlowFieldViewReleasePlan): void {
  applySlowFieldCircleReleasePlan(view.fill, plan.fill);
  applySlowFieldCircleReleasePlan(view.rim, plan.rim);
}

function applySlowFieldCircleReleasePlan(
  circle: Phaser.GameObjects.Arc,
  plan: SlowFieldCircleReleasePlan
): void {
  if (plan.destroy) {
    circle.destroy();
  }
}
