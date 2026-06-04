import type { CreateGameSceneSharedAuthoritativeLocalFeedbackBridgeInput } from "../objects/GameSceneSharedAuthoritativeLocalFeedbackBridgeFactoryObjects";
import { SharedAuthoritativeLocalFeedbackSceneBridge } from "../sharedAuthoritativeLocalFeedbackSceneBridge";

export function createGameSceneSharedAuthoritativeLocalFeedbackBridge({
  getPlayerHero,
  localHeroDisplay,
  getWorldSize,
  getObstacleBounds,
  getNowMs,
  vfx
}: CreateGameSceneSharedAuthoritativeLocalFeedbackBridgeInput): SharedAuthoritativeLocalFeedbackSceneBridge {
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
