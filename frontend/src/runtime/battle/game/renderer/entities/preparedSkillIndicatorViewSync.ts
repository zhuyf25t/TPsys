import Phaser from "phaser";
import type { GameSnapshot, Hero, PreparedSkill, Vec2 } from "../../../../../objects/battle/types";
import {
  getPreparedTargetSkillRuntimeProfile,
  isPreparedTargetSkillKind,
  type PreparedTargetSkillKind
} from "../../../local/skills/skillRuntimeProfiles";

export interface PreparedSkillIndicatorViewState {
  rangeIndicator: Phaser.GameObjects.Arc;
  targetIndicator: Phaser.GameObjects.Arc;
}

interface PreparedSkillIndicatorDisplayOverride {
  position: Vec2;
  facing: number;
}

interface PreparedSkillIndicatorViewSyncContext {
  snapshot: GameSnapshot;
  worldViews: PreparedSkillIndicatorViewState;
  pointerWorld: Vec2;
  isBlinkTargetValid: (player: Hero, target: Vec2) => boolean;
  isPreparedTargetValid?: (player: Hero, preparedSkill: Exclude<PreparedSkill, null>, target: Vec2) => boolean;
  sharedAuthoritativeRuntime?: boolean;
  localHeroDisplayOverride?: PreparedSkillIndicatorDisplayOverride;
}

/** 中文名：syncprepared技能indicatorviews（syncPreparedSkillIndicatorViews）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function syncPreparedSkillIndicatorViews({
  snapshot,
  worldViews,
  pointerWorld,
  isBlinkTargetValid,
  isPreparedTargetValid,
  sharedAuthoritativeRuntime = false,
  localHeroDisplayOverride
}: PreparedSkillIndicatorViewSyncContext): void {
  const player = snapshot.heroes.find((hero) => hero.heroId === snapshot.playerHeroId);
  const preparedSkill = player?.preparedSkill ?? null;

  if (!player || !player.alive || !isPreparedTargetSkillKind(preparedSkill)) {
    worldViews.rangeIndicator.setVisible(false);
    worldViews.targetIndicator.setVisible(false);
    return;
  }

  const displayPosition = sharedAuthoritativeRuntime ? player.position : localHeroDisplayOverride?.position ?? player.position;
  const profile = getPreparedTargetSkillRuntimeProfile(preparedSkill);
  const skill = player.skills.find((entry) => entry.kind === preparedSkill) ?? null;
  const valid = Boolean(
    skill &&
      skill.cooldownMs <= 0 &&
      isPreparedIndicatorTargetValid(
        player,
        preparedSkill,
        pointerWorld,
        displayPosition,
        localHeroDisplayOverride,
        isBlinkTargetValid,
        isPreparedTargetValid
      )
  );
  const color = valid ? 0x69ff9f : 0xff6b6b;

  worldViews.rangeIndicator.setVisible(true);
  worldViews.rangeIndicator.setPosition(displayPosition.x, displayPosition.y);
  worldViews.rangeIndicator.setRadius(profile.target.range);
  worldViews.rangeIndicator.setFillStyle(color, 0.05);
  worldViews.rangeIndicator.setStrokeStyle(2, color, 0.88);

  worldViews.targetIndicator.setVisible(true);
  worldViews.targetIndicator.setPosition(pointerWorld.x, pointerWorld.y);
  worldViews.targetIndicator.setRadius(profile.target.indicatorRadius);
  worldViews.targetIndicator.setFillStyle(color, 0.16);
  worldViews.targetIndicator.setStrokeStyle(2, color, 0.88);
}

function isPreparedIndicatorTargetValid(
  player: Hero,
  preparedSkill: PreparedTargetSkillKind,
  target: Vec2,
  displayPosition: Vec2,
  localHeroDisplayOverride: PreparedSkillIndicatorDisplayOverride | undefined,
  isBlinkTargetValid: PreparedSkillIndicatorViewSyncContext["isBlinkTargetValid"],
  isPreparedTargetValid: PreparedSkillIndicatorViewSyncContext["isPreparedTargetValid"]
): boolean {
  if (isPreparedTargetValid) {
    return isPreparedTargetValid(player, preparedSkill, target);
  }

  switch (preparedSkill) {
    case "Blink":
      return isBlinkIndicatorTargetValid(player, target, localHeroDisplayOverride, isBlinkTargetValid);
    case "Freeze":
      return isFreezeIndicatorTargetValid(target, displayPosition);
  }
}

function isFreezeIndicatorTargetValid(
  target: Vec2,
  displayPosition: Vec2
): boolean {
  const profile = getPreparedTargetSkillRuntimeProfile("Freeze");
  return Phaser.Math.Distance.Between(displayPosition.x, displayPosition.y, target.x, target.y) <= profile.target.range;
}

function isBlinkIndicatorTargetValid(
  player: Hero,
  target: Vec2,
  localHeroDisplayOverride: PreparedSkillIndicatorDisplayOverride | undefined,
  isBlinkTargetValid: PreparedSkillIndicatorViewSyncContext["isBlinkTargetValid"]
): boolean {
  return isBlinkTargetValid(
    localHeroDisplayOverride
      ? { ...player, position: localHeroDisplayOverride.position, facing: localHeroDisplayOverride.facing }
      : player,
    target
  );
}
