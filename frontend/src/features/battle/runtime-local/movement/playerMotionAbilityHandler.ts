import type { Hero, PlayerCommand, Vec2 } from "../../../../domain/types";
import { JUMP_COOLDOWN_MS, JUMP_DISTANCE } from "../../../../game/constants";
import { SKILL_DEFINITIONS, getSkillState } from "../../../../game/skills";
import { findDashDestination, type SceneGeometryObstacleBounds } from "../geometry/sceneGeometry";
import { isFreezeTargetInRange } from "../skills/freezeFieldController";
import {
  PREPARED_TARGET_APPLY_COMMAND_ORDER,
  isSkillCommandPressed,
  type PreparedTargetSkillKind
} from "../skills/skillRuntimeProfiles";
import { isBlinkTargetValid } from "./blinkTargetResolver";

type FloatingTone = "neutral" | "warning" | "error" | "success";

export interface PlayerMotionAbilityCallbacks {
  startPlayerMotion(destination: Vec2, durationMs: number, motionType: "jump" | "dash" | "blink"): void;
  createPulse(position: Vec2, radius: number, color: number): void;
  createAfterimage(
    position: Vec2,
    rotation: number,
    scale: number,
    textureKey: string,
    tint: number,
    alpha: number
  ): void;
  createFloatingText(position: Vec2, text: string, color: string): void;
  showFloatingText(position: Vec2, text: string, tone: FloatingTone): void;
  addFreezeField(ownerHeroId: string, position: Vec2, radius: number, durationMs: number): void;
}

export interface ApplySkillInputs {
  player: Hero;
  command: PlayerCommand;
  worldSize: Vec2;
  obstacleBounds: readonly SceneGeometryObstacleBounds[];
  isPlayerMotionActive: boolean;
  playerBaseScale: number;
  playerTextureKey: string;
  callbacks: PlayerMotionAbilityCallbacks;
}

export interface ApplyJumpActionInputs {
  player: Hero;
  command: PlayerCommand;
  lastMoveDirection: Vec2;
  worldSize: Vec2;
  obstacleBounds: readonly SceneGeometryObstacleBounds[];
  isPlayerMotionActive: boolean;
  callbacks: Pick<PlayerMotionAbilityCallbacks, "startPlayerMotion" | "showFloatingText">;
}

export function applySkillInputs(input: ApplySkillInputs): void {
  const blink = getSkillState(input.player, "Blink");
  const dash = getSkillState(input.player, "Dash");
  const freeze = getSkillState(input.player, "Freeze");

  if (!input.player.alive) {
    input.player.preparedSkill = null;
    return;
  }

  if (input.isPlayerMotionActive) {
    return;
  }

  for (const kind of PREPARED_TARGET_APPLY_COMMAND_ORDER) {
    if (isSkillCommandPressed(input.command, kind)) {
      togglePreparedSkill(input.player, kind);
    }
  }

  if (isSkillCommandPressed(input.command, "Dash") && dash.cooldownMs <= 0) {
    const direction = input.command.movement.x === 0 && input.command.movement.y === 0 ? input.command.aim : input.command.movement;
    if (direction.x !== 0 || direction.y !== 0) {
      const destination = findDashDestination({
        position: input.player.position,
        direction,
        distance: SKILL_DEFINITIONS.Dash.distance,
        radius: input.player.radius,
        worldSize: input.worldSize,
        obstacleBounds: input.obstacleBounds
      });
      dash.cooldownMs = SKILL_DEFINITIONS.Dash.cooldownMs;
      dash.activeMs = 220;
      input.callbacks.createPulse(input.player.position, 18, 0xb8d8ff);
      input.callbacks.startPlayerMotion(destination, 140, "dash");
    }
  }

  if (input.player.preparedSkill === "Blink" && input.command.primaryJustPressed) {
    const canBlink =
      blink.cooldownMs <= 0 &&
      isBlinkTargetValid({
        player: input.player,
        target: input.command.pointerWorld,
        worldSize: input.worldSize,
        obstacleBounds: input.obstacleBounds
      });
    if (canBlink) {
      blink.cooldownMs = SKILL_DEFINITIONS.Blink.cooldownMs;
      blink.activeMs = 240;
      input.player.preparedSkill = null;
      input.callbacks.createAfterimage(
        input.player.position,
        input.player.facing,
        input.playerBaseScale,
        input.playerTextureKey,
        0x86dfff,
        0.28
      );
      input.callbacks.createPulse(input.player.position, 32, 0x86dfff);
      input.callbacks.startPlayerMotion(input.command.pointerWorld, 90, "blink");
    } else {
      input.callbacks.createFloatingText(input.command.pointerWorld, "目标无效", "#ff8c8c");
    }
  }

  if (input.player.preparedSkill === "Freeze" && input.command.primaryJustPressed) {
    const definition = SKILL_DEFINITIONS.Freeze;
    const canFreeze =
      freeze.cooldownMs <= 0 &&
      isFreezeTargetInRange(input.player.position, input.command.pointerWorld, definition.range);

    if (canFreeze) {
      freeze.cooldownMs = definition.cooldownMs;
      freeze.activeMs = definition.durationMs;
      input.player.preparedSkill = null;
      input.callbacks.addFreezeField(input.player.heroId, input.command.pointerWorld, definition.radius, definition.durationMs);
      input.callbacks.createPulse(input.command.pointerWorld, definition.radius, 0x86f4ff);
    } else {
      input.callbacks.createFloatingText(input.command.pointerWorld, "冰雾目标无效", "#8beeff");
    }
  }
}

function togglePreparedSkill(player: Hero, kind: PreparedTargetSkillKind): void {
  player.preparedSkill = player.preparedSkill === kind ? null : kind;
}

export function applyJumpAction(input: ApplyJumpActionInputs): void {
  if (!input.player.alive || !input.command.secondaryJustPressed || input.isPlayerMotionActive) {
    return;
  }

  if (input.player.jumpCooldownMs > 0) {
    input.callbacks.showFloatingText(input.player.position, "跳跃冷却中", "neutral");
    return;
  }

  if (input.command.movement.x === 0 && input.command.movement.y === 0) {
    input.callbacks.showFloatingText(input.player.position, "没有移动方向", "neutral");
    return;
  }

  const destination = findDashDestination({
    position: input.player.position,
    direction: input.lastMoveDirection,
    distance: JUMP_DISTANCE,
    radius: input.player.radius,
    worldSize: input.worldSize,
    obstacleBounds: input.obstacleBounds
  });
  const travelDistance = Math.hypot(destination.x - input.player.position.x, destination.y - input.player.position.y);
  if (travelDistance <= 4) {
    input.callbacks.showFloatingText(input.player.position, "目标受阻", "warning");
    return;
  }

  input.player.jumpCooldownMs = JUMP_COOLDOWN_MS;
  input.callbacks.startPlayerMotion(destination, 190, "jump");
}
