import type { GameSnapshot, Hero, PlayerCommand, Vec2 } from "../../../domain/types";
import { BASE_MOVE_SPEED, SPRINT_MULTIPLIER } from "../../../game/constants";
import { findMotionDestination, type MotionObstacleBounds } from "../runtime-local/movement/motionController";
import { getFreezeSpeedMultiplier } from "../runtime-local/skills/freezeFieldController";
import {
  findDashSkillState,
  resolveAuthoritativeLocalHeroDashPrediction
} from "./authoritativeLocalHeroDashPrediction";
import {
  findBlinkSkillState,
  isAuthoritativeLocalHeroBlinkConfirm,
  resolveAuthoritativeLocalHeroBlinkPrediction
} from "./authoritativeLocalHeroBlinkPrediction";
import { recordLocalMotionFeedbackDiagnostics } from "./localFeedbackDiagnostics";
import type { LocalHeroDisplayPoseStore } from "./localHeroDisplayPose";

export interface ApplyAuthoritativeLocalHeroDisplayMotionInput {
  snapshot: GameSnapshot;
  player: Hero;
  command: PlayerCommand;
  deltaMs: number;
  displayPoseStore: LocalHeroDisplayPoseStore;
  obstacleBounds: readonly MotionObstacleBounds[];
  dashCooldownMsOverride?: number;
  blinkCooldownMsOverride?: number;
}

export interface AuthoritativeLocalHeroDisplayMotionResult {
  predictedDashDestination: Vec2 | null;
  predictedBlinkDestination: Vec2 | null;
}

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
  if (!player.alive) {
    return { predictedDashDestination: null, predictedBlinkDestination: null };
  }

  const facing = Math.atan2(command.aim.y, command.aim.x);
  const localDisplayPose = displayPoseStore.read();
  const currentPosition = localDisplayPose.position;
  let nextPosition = currentPosition;
  let predictedDashDestination: Vec2 | null = null;
  let predictedBlinkDestination: Vec2 | null = null;

  if (isAuthoritativeLocalHeroBlinkConfirm(command, player.preparedSkill)) {
    const blink = findBlinkSkillState(player.skills);
    const blinkPrediction =
      blink === null
        ? null
        : resolveAuthoritativeLocalHeroBlinkPrediction({
            player,
            position: nextPosition,
            target: command.pointerWorld,
            worldSize: snapshot.worldSize,
            obstacleBounds,
            blinkCooldownMs: blinkCooldownMsOverride ?? blink.cooldownMs,
            blinkActiveMs: blink.activeMs
          });

    if (blinkPrediction) {
      nextPosition = blinkPrediction.destination;
      predictedBlinkDestination = blinkPrediction.destination;
    }
  }

  if (command.castDash) {
    const dash = findDashSkillState(player.skills);
    const dashPrediction =
      dash === null
        ? null
        : resolveAuthoritativeLocalHeroDashPrediction({
            position: nextPosition,
            movement: command.movement,
            aim: command.aim,
            radius: player.radius,
            worldSize: snapshot.worldSize,
            obstacleBounds,
            dashCooldownMs: dashCooldownMsOverride ?? dash.cooldownMs,
            dashActiveMs: dash.activeMs,
            alive: player.alive
          });

    if (dashPrediction) {
      nextPosition = dashPrediction.destination;
      predictedDashDestination = dashPrediction.destination;
    }
  }

  const movement = normalizeVector(command.movement);
  if (movement.x === 0 && movement.y === 0) {
    displayPoseStore.write({
      position: nextPosition,
      facing
    });
    return { predictedDashDestination, predictedBlinkDestination };
  }

  const deltaSeconds = Math.max(0, deltaMs) / 1000;
  const sprintMultiplier = command.sprint && player.stamina > 0 ? SPRINT_MULTIPLIER : 1;
  const speedMultiplier = getFreezeSpeedMultiplier(nextPosition, snapshot.slowFields);
  nextPosition = findMotionDestination({
    position: nextPosition,
    direction: movement,
    distance: BASE_MOVE_SPEED * sprintMultiplier * speedMultiplier * deltaSeconds,
    radius: player.radius,
    worldSize: snapshot.worldSize,
    obstacleBounds
  }).destination;

  displayPoseStore.write({
    position: nextPosition,
    facing
  });
  recordLocalMotionFeedbackDiagnostics({
    from: currentPosition,
    to: nextPosition,
    movement,
    facing
  });
  return { predictedDashDestination, predictedBlinkDestination };
}

function normalizeVector(vector: Vec2): Vec2 {
  const length = Math.hypot(vector.x, vector.y);
  if (length <= 0.0001) {
    return { x: 0, y: 0 };
  }

  return {
    x: vector.x / length,
    y: vector.y / length
  };
}
