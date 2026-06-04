import {
  getPreparedTargetSkillRuntimeProfile,
  isPreparedTargetSkillKind
} from "../../../../microservices/abilities/functions/BattleSkillRuntimeProfiles";
import type {
  PreparedSkillIndicatorPlan,
  PreparedSkillIndicatorTargetValidityInput,
  PreparedSkillIndicatorVisualMutationPlan,
  PreparedSkillIndicatorViewSyncContext
} from "../objects/PreparedSkillIndicatorObjects";

const PREPARED_SKILL_VALID_COLOR = 0x69ff9f;
const PREPARED_SKILL_INVALID_COLOR = 0xff6b6b;
const PREPARED_SKILL_RANGE_FILL_ALPHA = 0.05;
const PREPARED_SKILL_TARGET_FILL_ALPHA = 0.16;
const PREPARED_SKILL_STROKE_WIDTH = 2;
const PREPARED_SKILL_STROKE_ALPHA = 0.88;

export function resolvePreparedSkillIndicatorPlan({
  snapshot,
  pointerWorld,
  isBlinkTargetValid,
  isPreparedTargetValid,
  sharedAuthoritativeRuntime = false,
  localHeroDisplayOverride
}: Omit<PreparedSkillIndicatorViewSyncContext, "worldViews">): PreparedSkillIndicatorPlan {
  const player = snapshot.heroes.find((hero) => hero.heroId === snapshot.playerHeroId);
  const preparedSkill = player?.preparedSkill ?? null;

  if (!player || !player.alive || !isPreparedTargetSkillKind(preparedSkill)) {
    return { visible: false };
  }

  const displayPosition = sharedAuthoritativeRuntime ? player.position : localHeroDisplayOverride?.position ?? player.position;
  const profile = getPreparedTargetSkillRuntimeProfile(preparedSkill);
  const skill = player.skills.find((entry) => entry.kind === preparedSkill) ?? null;
  const valid = Boolean(
    skill &&
      skill.cooldownMs <= 0 &&
      isPreparedIndicatorTargetValid({
        player,
        preparedSkill,
        target: pointerWorld,
        displayPosition,
        localHeroDisplayOverride,
        isBlinkTargetValid,
        isPreparedTargetValid
      })
  );
  const color = valid ? PREPARED_SKILL_VALID_COLOR : PREPARED_SKILL_INVALID_COLOR;

  return {
    visible: true,
    range: {
      position: displayPosition,
      radius: profile.target.range,
      color,
      fillAlpha: PREPARED_SKILL_RANGE_FILL_ALPHA,
      strokeWidth: PREPARED_SKILL_STROKE_WIDTH,
      strokeAlpha: PREPARED_SKILL_STROKE_ALPHA
    },
    target: {
      position: pointerWorld,
      radius: profile.target.indicatorRadius,
      color,
      fillAlpha: PREPARED_SKILL_TARGET_FILL_ALPHA,
      strokeWidth: PREPARED_SKILL_STROKE_WIDTH,
      strokeAlpha: PREPARED_SKILL_STROKE_ALPHA
    }
  };
}

export function resolvePreparedSkillIndicatorVisualMutationPlan(
  plan: PreparedSkillIndicatorPlan
): PreparedSkillIndicatorVisualMutationPlan {
  if (!plan.visible) {
    return {
      rangeIndicator: { visible: false },
      targetIndicator: { visible: false }
    };
  }

  return {
    rangeIndicator: {
      visible: true,
      ...plan.range
    },
    targetIndicator: {
      visible: true,
      ...plan.target
    }
  };
}

function isPreparedIndicatorTargetValid({
  player,
  preparedSkill,
  target,
  displayPosition,
  localHeroDisplayOverride,
  isBlinkTargetValid,
  isPreparedTargetValid
}: PreparedSkillIndicatorTargetValidityInput): boolean {
  if (isPreparedTargetValid) {
    return isPreparedTargetValid(player, preparedSkill, target);
  }

  switch (preparedSkill) {
    case "Blink":
      return isBlinkIndicatorTargetValid({
        player,
        target,
        localHeroDisplayOverride,
        isBlinkTargetValid
      });
    case "Freeze":
      return isFreezeIndicatorTargetValid({ target, displayPosition });
  }
}

function isFreezeIndicatorTargetValid({
  target,
  displayPosition
}: Pick<PreparedSkillIndicatorTargetValidityInput, "target" | "displayPosition">): boolean {
  const profile = getPreparedTargetSkillRuntimeProfile("Freeze");
  return distanceBetween(displayPosition, target) <= profile.target.range;
}

function isBlinkIndicatorTargetValid({
  player,
  target,
  localHeroDisplayOverride,
  isBlinkTargetValid
}: Pick<PreparedSkillIndicatorTargetValidityInput, "player" | "target" | "localHeroDisplayOverride" | "isBlinkTargetValid">): boolean {
  return isBlinkTargetValid(
    localHeroDisplayOverride
      ? { ...player, position: localHeroDisplayOverride.position, facing: localHeroDisplayOverride.facing }
      : player,
    target
  );
}

function distanceBetween(
  left: PreparedSkillIndicatorTargetValidityInput["displayPosition"],
  right: PreparedSkillIndicatorTargetValidityInput["target"]
): number {
  return Math.hypot(left.x - right.x, left.y - right.y);
}
