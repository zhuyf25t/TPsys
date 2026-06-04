import { getHeroDisplayPosition, getProjectileDisplayPosition } from "../../entities/worldViewFactory";
import { BattleFeedbackSceneBridge } from "../battleFeedbackSceneBridge";
import type { CreateGameSceneBattleFeedbackBridgeInput } from "../objects/GameSceneBattleFeedbackBridgeFactoryObjects";

export function createGameSceneBattleFeedbackBridge({
  getSnapshot,
  getWorldViews,
  flashHero,
  vfx,
  camera
}: CreateGameSceneBattleFeedbackBridgeInput): BattleFeedbackSceneBridge {
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
