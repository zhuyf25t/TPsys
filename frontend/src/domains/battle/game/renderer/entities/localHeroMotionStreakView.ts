import Phaser from "phaser";
import type { Vec2 } from "../../../objects/types";

const LOCAL_HERO_MOTION_STREAK_COUNT = 3;
const LOCAL_HERO_MOTION_STREAK_DEPTH = 31;
const LOCAL_HERO_MOTION_MIN_SPEED = 70;
const LOCAL_HERO_MOTION_MAX_SPEED = 470;
const LOCAL_HERO_MOTION_DECAY = 0.34;
const LOCAL_HERO_MOTION_TINT = 0x8fe8ff;

export interface LocalHeroMotionStreakView {
  streaks: Phaser.GameObjects.Rectangle[];
  lastPosition: Vec2 | null;
  lastAngle: number;
  intensity: number;
}

/** 中文名：创建本地英雄运动streakview（createLocalHeroMotionStreakView）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function createLocalHeroMotionStreakView(scene: Phaser.Scene, position: Vec2): LocalHeroMotionStreakView {
  const streaks = Array.from({ length: LOCAL_HERO_MOTION_STREAK_COUNT }, (_unused, index) =>
    scene.add
      .rectangle(position.x, position.y, 18 + index * 8, 3, LOCAL_HERO_MOTION_TINT, 0)
      .setOrigin(1, 0.5)
      .setDepth(LOCAL_HERO_MOTION_STREAK_DEPTH)
      .setVisible(false)
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

  const lastPosition = view.lastPosition;
  view.lastPosition = { x: displayPosition.x, y: displayPosition.y };

  if (!lastPosition || !isFiniteVec2(lastPosition)) {
    hideLocalHeroMotionStreaks(view, false);
    return;
  }

  const dx = displayPosition.x - lastPosition.x;
  const dy = displayPosition.y - lastPosition.y;
  const frameDistance = Math.hypot(dx, dy);
  const safeDeltaMs = Number.isFinite(deltaMs) ? Math.max(1, deltaMs) : 16.67;
  const speed = frameDistance * 1000 / safeDeltaMs;
  const speedIntensity = Phaser.Math.Clamp(
    (speed - LOCAL_HERO_MOTION_MIN_SPEED) / (LOCAL_HERO_MOTION_MAX_SPEED - LOCAL_HERO_MOTION_MIN_SPEED),
    0,
    1
  );

  if (speedIntensity > 0 && frameDistance > 0.05) {
    view.intensity = speedIntensity;
    view.lastAngle = Math.atan2(dy, dx);
  } else {
    view.intensity *= LOCAL_HERO_MOTION_DECAY;
  }

  if (view.intensity <= 0.04) {
    hideLocalHeroMotionStreaks(view, false);
    return;
  }

  const angle = view.lastAngle;
  const directionX = Math.cos(angle);
  const directionY = Math.sin(angle);
  view.streaks.forEach((streak, index) => {
    const falloff = 1 - index * 0.22;
    const offset = 14 + index * 11 + view.intensity * 10;
    const sideOffset = (index - 1) * 4;
    const alpha = 0.16 * view.intensity * falloff;
    streak.setVisible(true);
    streak.setPosition(
      displayPosition.x - directionX * offset - directionY * sideOffset,
      displayPosition.y - directionY * offset + directionX * sideOffset
    );
    streak.setRotation(angle);
    streak.setDisplaySize(20 + index * 9 + view.intensity * 14, 2 + view.intensity * 2);
    streak.setFillStyle(LOCAL_HERO_MOTION_TINT, alpha);
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
  view.streaks.forEach((streak) => {
    streak.setVisible(false);
    streak.setFillStyle(LOCAL_HERO_MOTION_TINT, 0);
  });
}

function isFiniteVec2(position: Vec2): boolean {
  return Number.isFinite(position.x) && Number.isFinite(position.y);
}
