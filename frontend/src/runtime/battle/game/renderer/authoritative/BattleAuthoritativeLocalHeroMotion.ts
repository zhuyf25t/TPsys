import { resolveBattleAuthoritativeLocalHeroDisplayMotionPlan } from "../../../local/movement/BattleAuthoritativeLocalHeroDisplayMotionRules";
import { recordLocalMotionFeedbackDiagnostics } from "../diagnostics/localFeedbackDiagnostics";
import type {
  ApplyAuthoritativeLocalHeroDisplayMotionInput,
  AuthoritativeLocalHeroDisplayMotionResult
} from "./objects/BattleAuthoritativeLocalHeroMotionObjects";

export type {
  ApplyAuthoritativeLocalHeroDisplayMotionInput,
  AuthoritativeLocalHeroDisplayMotionResult
} from "./objects/BattleAuthoritativeLocalHeroMotionObjects";

export function applyAuthoritativeLocalHeroDisplayMotion({
  snapshot,
  player,
  command,
  deltaMs,
  displayPoseStore,
  obstacleBounds,
  dashCooldownMsOverride,
  blinkCooldownMsOverride
}: ApplyAuthoritativeLocalHeroDisplayMotionInput): AuthoritativeLocalHeroDisplayMotionResult {
  const currentPosition = displayPoseStore.read().position;
  const plan = resolveBattleAuthoritativeLocalHeroDisplayMotionPlan({
    snapshot,
    player,
    command,
    deltaMs,
    currentPosition,
    obstacleBounds,
    dashCooldownMsOverride,
    blinkCooldownMsOverride
  });

  if (!plan.shouldWriteDisplayPose) {
    return {
      predictedDashDestination: plan.predictedDashDestination,
      predictedBlinkDestination: plan.predictedBlinkDestination
    };
  }

  displayPoseStore.write({
    position: plan.nextPosition,
    facing: plan.facing
  });

  if (plan.movementApplied) {
    recordLocalMotionFeedbackDiagnostics({
      from: currentPosition,
      to: plan.nextPosition,
      movement: plan.movement,
      facing: plan.facing
    });
  }

  return {
    predictedDashDestination: plan.predictedDashDestination,
    predictedBlinkDestination: plan.predictedBlinkDestination
  };
}
