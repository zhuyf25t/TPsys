import type { BattleHeroViewState as Hero } from "../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { SkillKind } from "../../../../../objects/battle/microservices/abilities/objects/skill/SkillKind";
import type { BattlePlayerCommand as PlayerCommand } from "../../../../../objects/battle/microservices/session/objects/command/BattlePlayerCommand";
import type { BattleVector2 as Vec2 } from "../../../../../objects/battle/objects/core/BattleCoreScalars";
import type { MotionObstacleBounds } from "../../world/functions/BattleMotionRules";
import { isBattleSharedAuthoritativeTargetValid } from "./BattleSkillTargetValidityRules";
import {
  PREPARED_TARGET_FEEDBACK_COMMAND_PRIORITY,
  getInstantSkillRuntimeProfile,
  getPreparedTargetSkillFeedbackRadius,
  isSkillCommandPressed,
  resolvePreparedTargetSkillCommand,
  type PreparedTargetSkillKind,
  type SkillFeedbackIntent
} from "./BattleSkillRuntimeProfiles";

interface TargetedSkillFeedbackRequest {
  kind: PreparedTargetSkillKind;
  intent: SkillFeedbackIntent;
  feedbackRadius: number;
}

interface BattleSkillFeedbackRejectionDraft {
  kind: "rejection";
  position: Vec2;
  radius: number;
}

export interface BattleDashSkillFeedbackPlan {
  kind: "dash";
  position: Vec2;
  direction: Vec2;
}

export interface BattleBlinkSkillTargetFeedbackPlan {
  kind: "blink-target";
  position: Vec2;
  intent: SkillFeedbackIntent;
  direction: Vec2;
}

export interface BattleFreezeSkillTargetFeedbackPlan {
  kind: "freeze-target";
  position: Vec2;
  intent: SkillFeedbackIntent;
}

export interface BattleSkillRejectionFeedbackPlan {
  kind: "rejection";
  position: Vec2;
  radius: number;
}

export type BattleSkillFeedbackPlan =
  | BattleDashSkillFeedbackPlan
  | BattleBlinkSkillTargetFeedbackPlan
  | BattleFreezeSkillTargetFeedbackPlan
  | BattleSkillRejectionFeedbackPlan;

export interface BattleSkillFeedbackResolution {
  suppressPrimaryFeedback: boolean;
  plans: BattleSkillFeedbackPlan[];
  nextSkillRejectFeedbackAtMs: number;
}

export interface ResolveBattleSkillFeedbackPlansInput {
  player: Hero;
  command: PlayerCommand;
  displayPosition: Vec2;
  worldSize: Vec2;
  obstacleBounds: readonly MotionObstacleBounds[];
  primaryPressStarted: boolean;
  nowMs: number;
  nextSkillRejectFeedbackAtMs: number;
}

export const BATTLE_SKILL_REJECT_FEEDBACK_MIN_MS = 160;

export function resolveBattleSkillFeedbackPlans(
  input: ResolveBattleSkillFeedbackPlansInput
): BattleSkillFeedbackResolution {
  const plans: BattleSkillFeedbackPlan[] = [];
  let nextSkillRejectFeedbackAtMs = input.nextSkillRejectFeedbackAtMs;
  const targetedSkillRequest = resolveBattleTargetedSkillFeedbackRequest(
    input.player,
    input.command,
    input.primaryPressStarted
  );

  if (isSkillCommandPressed(input.command, "Dash")) {
    if (canPresentBattleSkillFeedback(input.player, "Dash")) {
      plans.push({
        kind: "dash",
        position: input.displayPosition,
        direction: resolveBattleSkillAimDirection(input.command.aim)
      });
    } else {
      nextSkillRejectFeedbackAtMs = appendBattleSkillRejectionFeedback({
        plans,
        nowMs: input.nowMs,
        nextSkillRejectFeedbackAtMs,
        position: input.displayPosition,
        radius: getInstantSkillRuntimeProfile("Dash").feedback.rejectionRadius
      });
    }
  }

  if (targetedSkillRequest) {
    const targetedPlan = resolveBattleTargetedSkillFeedbackPlan({
      player: input.player,
      request: targetedSkillRequest,
      target: input.command.pointerWorld,
      displayPosition: input.displayPosition,
      worldSize: input.worldSize,
      obstacleBounds: input.obstacleBounds
    });

    if (targetedPlan.kind === "rejection") {
      nextSkillRejectFeedbackAtMs = appendBattleSkillRejectionFeedback({
        plans,
        nowMs: input.nowMs,
        nextSkillRejectFeedbackAtMs,
        position: targetedPlan.position,
        radius: targetedPlan.radius
      });
    } else {
      plans.push(targetedPlan);
    }
  }

  return {
    suppressPrimaryFeedback: targetedSkillRequest?.intent === "release",
    plans,
    nextSkillRejectFeedbackAtMs
  };
}

function resolveBattleTargetedSkillFeedbackRequest(
  player: Hero,
  command: PlayerCommand,
  primaryPressStarted: boolean
): TargetedSkillFeedbackRequest | null {
  if (primaryPressStarted) {
    const releaseKind = resolveBattleTargetedSkillReleaseKind(player, command);
    if (releaseKind) {
      return {
        kind: releaseKind,
        intent: "release",
        feedbackRadius: getPreparedTargetSkillFeedbackRadius(releaseKind, "release")
      };
    }
  }

  const prepareKind = resolvePreparedTargetSkillCommand(command, PREPARED_TARGET_FEEDBACK_COMMAND_PRIORITY);
  if (!prepareKind) {
    return null;
  }

  return {
    kind: prepareKind,
    intent: "prepare",
    feedbackRadius: getPreparedTargetSkillFeedbackRadius(prepareKind, "prepare")
  };
}

function resolveBattleTargetedSkillFeedbackPlan(input: {
  player: Hero;
  request: TargetedSkillFeedbackRequest;
  target: Vec2;
  displayPosition: Vec2;
  worldSize: Vec2;
  obstacleBounds: readonly MotionObstacleBounds[];
}): Exclude<BattleSkillFeedbackPlan, BattleSkillRejectionFeedbackPlan> | BattleSkillFeedbackRejectionDraft {
  if (!canPresentBattleSkillFeedback(input.player, input.request.kind)) {
    return {
      kind: "rejection",
      position: input.displayPosition,
      radius: input.request.feedbackRadius
    };
  }

  const targetValid = isBattleSharedAuthoritativeTargetValid({
    player: input.player,
    preparedSkill: input.request.kind,
    target: input.target,
    worldSize: input.worldSize,
    obstacleBounds: input.obstacleBounds
  });
  if (!targetValid) {
    return {
      kind: "rejection",
      position: input.target,
      radius: input.request.feedbackRadius
    };
  }

  if (input.request.kind === "Blink") {
    return {
      kind: "blink-target",
      position: input.target,
      intent: input.request.intent,
      direction: resolveBattleSkillDirectionBetween(input.displayPosition, input.target)
    };
  }

  return {
    kind: "freeze-target",
    position: input.target,
    intent: input.request.intent
  };
}

function appendBattleSkillRejectionFeedback(input: {
  plans: BattleSkillFeedbackPlan[];
  nowMs: number;
  nextSkillRejectFeedbackAtMs: number;
  position: Vec2;
  radius: number;
}): number {
  if (input.nowMs < input.nextSkillRejectFeedbackAtMs) {
    return input.nextSkillRejectFeedbackAtMs;
  }

  const nextSkillRejectFeedbackAtMs = input.nowMs + BATTLE_SKILL_REJECT_FEEDBACK_MIN_MS;
  input.plans.push({
    kind: "rejection",
    position: input.position,
    radius: input.radius
  });
  return nextSkillRejectFeedbackAtMs;
}

function resolveBattleTargetedSkillReleaseKind(player: Hero, command: PlayerCommand): PreparedTargetSkillKind | null {
  const toggledKind = resolvePreparedTargetSkillCommand(command, PREPARED_TARGET_FEEDBACK_COMMAND_PRIORITY);
  if (toggledKind) {
    return toggledKind;
  }

  return player.preparedSkill;
}

function canPresentBattleSkillFeedback(player: Hero, kind: SkillKind): boolean {
  const skill = player.skills.find((entry) => entry.kind === kind);
  return skill !== undefined && skill.cooldownMs <= 0;
}

function resolveBattleSkillDirectionBetween(from: Vec2, to: Vec2): Vec2 {
  return resolveBattleSkillAimDirection({
    x: to.x - from.x,
    y: to.y - from.y
  });
}

function resolveBattleSkillAimDirection(aim: Vec2): Vec2 {
  const length = Math.hypot(aim.x, aim.y);
  if (length <= 0.0001) {
    return { x: 1, y: 0 };
  }

  return {
    x: aim.x / length,
    y: aim.y / length
  };
}
