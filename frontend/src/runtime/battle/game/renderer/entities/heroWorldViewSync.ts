import {
  syncHeroHealthVisuals,
  syncHeroReadabilityVisuals
} from "./heroReadabilityView";
import { syncLocalHeroMotionStreaks } from "./localHeroMotionStreakView";
import { resolveHeroWorldViewFrameLayoutPlan } from "./functions/HeroWorldViewFrameLayoutRules";
import { resolveHeroActionProgressPlan } from "./functions/WorldViewFactoryRules";
import type { SyncHeroWorldViewFrameInput } from "./objects/HeroWorldViewSyncObjects";

export function syncHeroWorldViewFrame({
  view,
  hero,
  displayState,
  isPlayer,
  snapshot,
  deltaMs,
  weaponSwitchRemainingMs,
  weaponSwitchTotalMs
}: SyncHeroWorldViewFrameInput): void {
  const displayPosition = displayState.position;
  const actionProgress = resolveHeroActionProgressPlan({
    isPlayer,
    weapon: hero.weapons[hero.currentWeaponIndex],
    weaponSwitchRemainingMs,
    weaponSwitchTotalMs
  });
  const layoutPlan = resolveHeroWorldViewFrameLayoutPlan({ displayPosition, actionProgress });

  view.sprite.setPosition(layoutPlan.spritePosition.x, layoutPlan.spritePosition.y);
  view.sprite.setRotation(displayState.facing);
  syncLocalHeroMotionStreaks(view.localMotionStreaks, layoutPlan.spritePosition, deltaMs);
  syncHeroReadabilityVisuals(view, hero, layoutPlan.spritePosition, displayState.facing, isPlayer, snapshot.slowFields);
  if (view.nameLabel.text !== hero.displayName) {
    view.nameLabel.setText(hero.displayName);
  }
  view.nameLabel.setPosition(layoutPlan.nameLabelPosition.x, layoutPlan.nameLabelPosition.y);
  view.healthBackground.setPosition(layoutPlan.healthBackgroundPosition.x, layoutPlan.healthBackgroundPosition.y);
  view.healthFill.setPosition(layoutPlan.healthFillPosition.x, layoutPlan.healthFillPosition.y);
  syncHeroHealthVisuals(view, hero, snapshot.elapsedMs);

  if (layoutPlan.actionBar.visible) {
    view.actionBackground.setVisible(layoutPlan.actionBar.visibility.background);
    view.actionFill.setVisible(layoutPlan.actionBar.visibility.fill);
    view.actionBackground.setPosition(layoutPlan.actionBar.backgroundPosition.x, layoutPlan.actionBar.backgroundPosition.y);
    view.actionFill.setPosition(layoutPlan.actionBar.fillPosition.x, layoutPlan.actionBar.fillPosition.y);
    view.actionFill.displayWidth = layoutPlan.actionBar.fillWidth;
  }

  view.marker?.setPosition(layoutPlan.markerPosition.x, layoutPlan.markerPosition.y);
}
