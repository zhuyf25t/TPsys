import type Phaser from "phaser";
import type { GameSnapshot, Hero, Vec2 } from "../../objects/types";
import { BattleFeedbackSceneBridge } from "./effects/battleFeedbackSceneBridge";
import { SharedAuthoritativeLocalFeedbackSceneBridge } from "./effects/sharedAuthoritativeLocalFeedbackSceneBridge";
import type { SceneVfxController } from "./effects/sceneVfxController";
import type { LocalHeroDisplay } from "./localHeroDisplayPose";
import type { ObstacleBounds } from "./arena/arenaBuilder";
import {
  getHeroDisplayPosition,
  getProjectileDisplayPosition,
  type WorldViewState
} from "./entities/worldViewFactory";

/** 中文名：创建gamescene战斗feedbackbridge（createGameSceneBattleFeedbackBridge）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function createGameSceneBattleFeedbackBridge({
  getSnapshot,
  getWorldViews,
  flashHero,
  vfx,
  camera
}: {
  getSnapshot: () => GameSnapshot;
  getWorldViews: () => WorldViewState;
  flashHero: (heroId: string, color: number) => void;
  vfx: SceneVfxController;
  camera: Phaser.Cameras.Scene2D.Camera;
}): BattleFeedbackSceneBridge {
  return new BattleFeedbackSceneBridge({
    getSnapshot,
    getHeroDisplayPosition: (heroId) => getHeroDisplayPosition(getWorldViews(), heroId),
    getProjectileDisplayPosition: (projectileId) => getProjectileDisplayPosition(getWorldViews(), projectileId),
    flashHero,
    showFloatingText: (position, text, tone) => vfx.showFloatingText(position, text, tone),
    createPulse: (position, radius, color) => vfx.createPulse(position, radius, color),
    createImpactSpark: (position, color) => vfx.createImpactSpark(position, color),
    createProjectileDissipate: (position, color) => vfx.createProjectileDissipate(position, color),
    createHitConfirm: (position, color) => vfx.createHitConfirm(position, color),
    createShockwave: (position, startRadius, endRadius, color, duration) =>
      vfx.createShockwave(position, startRadius, endRadius, color, duration),
    createProjectileTracer: (options) => vfx.createProjectileTracer(options),
    shakeCamera: (duration, intensity) => camera.shake(duration, intensity)
  });
}

/** 中文名：创建gamescene共享authoritative本地feedbackbridge（createGameSceneSharedAuthoritativeLocalFeedbackBridge）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function createGameSceneSharedAuthoritativeLocalFeedbackBridge({
  getPlayerHero,
  localHeroDisplay,
  getWorldSize,
  getObstacleBounds,
  getNowMs,
  vfx
}: {
  getPlayerHero: () => Hero;
  localHeroDisplay: LocalHeroDisplay;
  getWorldSize: () => Vec2;
  getObstacleBounds: () => readonly ObstacleBounds[];
  getNowMs: () => number;
  vfx: SceneVfxController;
}): SharedAuthoritativeLocalFeedbackSceneBridge {
  return new SharedAuthoritativeLocalFeedbackSceneBridge({
    getPlayerHero,
    localHeroDisplay,
    getWorldSize,
    getObstacleBounds,
    getNowMs,
    createMuzzleBurst: (position, color, radius, sparks, direction) =>
      vfx.createMuzzleBurst(position, color, radius, sparks, direction),
    createPulse: (position, radius, color) => vfx.createPulse(position, radius, color),
    createProjectileTracer: (options) => vfx.createProjectileTracer(options),
    createBlinkSkillTargetFeedback: (position, intent, direction) =>
      vfx.createBlinkSkillTargetFeedback(position, intent, direction),
    createFreezeSkillTargetFeedback: (position, intent) => vfx.createFreezeSkillTargetFeedback(position, intent),
    createDashSkillFeedback: (position, direction) => vfx.createDashSkillFeedback(position, direction),
    createSkillRejectionFeedback: (position, radius) => vfx.createSkillRejectionFeedback(position, radius),
    showFloatingText: (position, text, tone) => vfx.showFloatingText(position, text, tone)
  });
}
