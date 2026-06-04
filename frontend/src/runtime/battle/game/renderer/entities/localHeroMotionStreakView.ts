import Phaser from "phaser";
import type { BattleVector2 as Vec2 } from "../../../../../objects/battle/objects/core/BattleCoreScalars";
import {
  isFiniteVec2,
  resolveLocalHeroMotionStreakCreationPlans,
  resolveLocalHeroMotionStreakHiddenPlan,
  resolveLocalHeroMotionStreakRenderPlan,
  resolveLocalHeroMotionStreakUpdate
} from "./functions/LocalHeroMotionStreakRules";
import type {
  LocalHeroMotionStreakCreationPlan,
  LocalHeroMotionStreakRenderPlan,
  LocalHeroMotionStreakView
} from "./objects/LocalHeroMotionStreakObjects";

export type { LocalHeroMotionStreakView } from "./objects/LocalHeroMotionStreakObjects";

/** 中文名：创建本地英雄运动streakview（createLocalHeroMotionStreakView）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function createLocalHeroMotionStreakView(scene: Phaser.Scene, position: Vec2): LocalHeroMotionStreakView {
  const streaks = resolveLocalHeroMotionStreakCreationPlans({ position }).map((plan) =>
    createLocalHeroMotionStreakRectangle(scene, plan)
  );

  return {
    streaks,
    lastPosition: null,
    lastAngle: 0,
    intensity: 0
  };
}

/** 中文名：sync本地英雄运动streaks（syncLocalHeroMotionStreaks）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function syncLocalHeroMotionStreaks(
  view: LocalHeroMotionStreakView | null,
  displayPosition: Vec2,
  deltaMs: number
): void {
  if (!view || !isFiniteVec2(displayPosition)) {
    return;
  }

  const update = resolveLocalHeroMotionStreakUpdate({
    previousPosition: view.lastPosition,
    displayPosition,
    deltaMs,
    previousAngle: view.lastAngle,
    previousIntensity: view.intensity
  });
  view.lastPosition = update.lastPosition;
  view.lastAngle = update.angle;
  view.intensity = update.intensity;

  if (!update.visible) {
    hideLocalHeroMotionStreaks(view, false);
    return;
  }

  view.streaks.forEach((streak, index) => {
    const plan = resolveLocalHeroMotionStreakRenderPlan({
      displayPosition,
      angle: update.angle,
      intensity: update.intensity,
      index
    });
    syncLocalHeroMotionStreakRectangle(streak, plan);
  });
}

/** 中文名：hide本地英雄运动streaks（hideLocalHeroMotionStreaks）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function hideLocalHeroMotionStreaks(view: LocalHeroMotionStreakView | null, resetPosition: boolean): void {
  if (!view) {
    return;
  }

  view.intensity = 0;
  if (resetPosition) {
    view.lastPosition = null;
  }
  const hiddenPlan = resolveLocalHeroMotionStreakHiddenPlan();
  view.streaks.forEach((streak) => {
    streak.setVisible(hiddenPlan.visible);
    streak.setFillStyle(hiddenPlan.fillColor, hiddenPlan.fillAlpha);
  });
}

function createLocalHeroMotionStreakRectangle(
  scene: Phaser.Scene,
  plan: LocalHeroMotionStreakCreationPlan
): Phaser.GameObjects.Rectangle {
  return scene.add
    .rectangle(plan.position.x, plan.position.y, plan.width, plan.height, plan.fillColor, plan.fillAlpha)
    .setOrigin(plan.origin.x, plan.origin.y)
    .setDepth(plan.depth)
    .setVisible(plan.visible);
}

function syncLocalHeroMotionStreakRectangle(
  streak: Phaser.GameObjects.Rectangle,
  plan: LocalHeroMotionStreakRenderPlan
): void {
  streak.setVisible(plan.visible);
  streak.setPosition(plan.position.x, plan.position.y);
  streak.setRotation(plan.rotation);
  streak.setDisplaySize(plan.width, plan.height);
  streak.setFillStyle(plan.fillColor, plan.alpha);
}
