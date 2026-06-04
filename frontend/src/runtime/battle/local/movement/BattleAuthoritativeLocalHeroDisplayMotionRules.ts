import type { BattleGameSnapshot as GameSnapshot } from "../../../../objects/battle/microservices/session/objects/state/BattleGameSnapshot";
import type { BattleVector2 as Vec2 } from "../../../../objects/battle/objects/core/BattleCoreScalars";
import type { BattleHeroViewState as Hero } from "../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { BattlePlayerCommand as PlayerCommand } from "../../../../objects/battle/microservices/session/objects/command/BattlePlayerCommand";
import { BASE_MOVE_SPEED, SPRINT_MULTIPLIER } from "../../game/objects/BattleGameConstants";
import { getFreezeSpeedMultiplier } from "../../microservices/abilities/functions/BattleSlowFieldRuntimeRules";
import {
  findBlinkSkillState,
  findDashSkillState,
  isAuthoritativeLocalHeroBlinkConfirm,
  resolveAuthoritativeLocalHeroBlinkPrediction,
  resolveAuthoritativeLocalHeroDashPrediction
} from "../../microservices/abilities/functions/BattleAuthoritativeSkillPredictionRules";
import { findMotionDestination, type MotionObstacleBounds } from "../../microservices/world/functions/BattleMotionRules";

export interface ResolveBattleAuthoritativeLocalHeroDisplayMotionInput {
  snapshot: GameSnapshot;
  player: Hero;
  command: PlayerCommand;
  deltaMs: number;
  currentPosition: Vec2;
  obstacleBounds: readonly MotionObstacleBounds[];
  dashCooldownMsOverride?: number;
  blinkCooldownMsOverride?: number;
}

export interface BattleAuthoritativeLocalHeroDisplayMotionPlan {
  shouldWriteDisplayPose: boolean;
  nextPosition: Vec2;
  facing: number;
  movement: Vec2;
  movementApplied: boolean;
  predictedDashDestination: Vec2 | null;
  predictedBlinkDestination: Vec2 | null;
}

export function resolveBattleAuthoritativeLocalHeroDisplayMotionPlan({
  snapshot,
  player,
  command,
  deltaMs,
  currentPosition,
  obstacleBounds,
  dashCooldownMsOverride,
  blinkCooldownMsOverride
}: ResolveBattleAuthoritativeLocalHeroDisplayMotionInput): BattleAuthoritativeLocalHeroDisplayMotionPlan {
  const facing = Math.atan2(command.aim.y, command.aim.x);
  if (!player.alive) {
    return {
      shouldWriteDisplayPose: false,
      nextPosition: currentPosition,
      facing,
      movement: { x: 0, y: 0 },
      movementApplied: false,
      predictedDashDestination: null,
      predictedBlinkDestination: null
    };
  }

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
    return {
      shouldWriteDisplayPose: true,
      nextPosition,
      facing,
      movement,
      movementApplied: false,
      predictedDashDestination,
      predictedBlinkDestination
    };
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

  return {
    shouldWriteDisplayPose: true,
    nextPosition,
    facing,
    movement,
    movementApplied: true,
    predictedDashDestination,
    predictedBlinkDestination
  };
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
