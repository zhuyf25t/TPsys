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

const LOCAL_AUTHORITATIVE_DISPLAY_MAX_DISTANCE_FROM_AUTHORITATIVE = 48;

export function applyAuthoritativeLocalHeroDisplayMotion({
  snapshot,
  player,
  command,
  deltaMs,
  displayPoseStore,
  obstacleBounds,
  dashCooldownMsOverride,
  blinkCooldownMsOverride,
  maxDisplayDistanceFromAuthoritative = LOCAL_AUTHORITATIVE_DISPLAY_MAX_DISTANCE_FROM_AUTHORITATIVE
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
    blinkCooldownMsOverride,
    maxDisplayDistanceFromAuthoritative
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
