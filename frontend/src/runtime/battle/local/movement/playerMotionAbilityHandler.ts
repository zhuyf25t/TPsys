import type { BattleVector2 as Vec2 } from "../../../../objects/battle/objects/core/BattleCoreScalars";
import type { BattleHeroViewState as Hero } from "../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { BattlePlayerCommand as PlayerCommand } from "../../../../objects/battle/microservices/session/objects/command/BattlePlayerCommand";
import { JUMP_COOLDOWN_MS, JUMP_DISTANCE } from "../../game/objects/BattleGameConstants";
import {
  activateBattleSkillState,
  getSkillState,
  isBattleSkillReady,
  resolveBattlePreparedSkillToggle,
  SKILL_DEFINITIONS,
  updateBattleSkillState
} from "../../microservices/abilities/functions/BattleSkillStateRules";
import { findDashDestination, type SceneGeometryObstacleBounds } from "../geometry/sceneGeometry";
import { isFreezeTargetInRange } from "../../microservices/abilities/functions/BattleSlowFieldRuntimeRules";
import {
  PREPARED_TARGET_APPLY_COMMAND_ORDER,
  isSkillCommandPressed,
  type PreparedTargetSkillKind
} from "../../microservices/abilities/functions/BattleSkillRuntimeProfiles";
import { consumeBattleSkillStamina } from "../../microservices/abilities/functions/BattleSkillStaminaRules";
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

/** Apply skill inputs. */
export function applySkillInputs(input: ApplySkillInputs): void {
  const blink = getSkillState(input.player, "Blink");
  const dash = getSkillState(input.player, "Dash");
  const freeze = getSkillState(input.player, "Freeze");
  const critical = getSkillState(input.player, "Critical");

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

  if (isSkillCommandPressed(input.command, "Dash") && isBattleSkillReady(dash)) {
    const definition = SKILL_DEFINITIONS.Dash;
    const direction = input.command.movement.x === 0 && input.command.movement.y === 0 ? input.command.aim : input.command.movement;
    if (direction.x !== 0 || direction.y !== 0) {
      const destination = findDashDestination({
        position: input.player.position,
        direction,
        distance: definition.distance,
        radius: input.player.radius,
        worldSize: input.worldSize,
        obstacleBounds: input.obstacleBounds
      });
      const stamina = consumeBattleSkillStamina(input.player, "Dash");
      if (!stamina.ok) {
        input.callbacks.showFloatingText(input.player.position, stamina.message ?? "体力不足", "warning");
        return;
      }
      input.player.skills = updateBattleSkillState(
        input.player.skills,
        "Dash",
        activateBattleSkillState(dash, definition)
      );
      input.callbacks.createPulse(input.player.position, 18, 0xb8d8ff);
      input.callbacks.startPlayerMotion(destination, 140, "dash");
    }
  }

  if (input.player.preparedSkill === "Blink" && input.command.primaryJustPressed) {
    const canBlink =
      isBattleSkillReady(blink) &&
      isBlinkTargetValid({
        player: input.player,
        target: input.command.pointerWorld,
        worldSize: input.worldSize,
        obstacleBounds: input.obstacleBounds
      });
    if (canBlink) {
      const definition = SKILL_DEFINITIONS.Blink;
      input.player.skills = updateBattleSkillState(
        input.player.skills,
        "Blink",
        activateBattleSkillState(blink, definition)
      );
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
      input.callbacks.createFloatingText(input.command.pointerWorld, "\u76ee\u6807\u65e0\u6548", "#ff8c8c");
    }
  }

  if (input.player.preparedSkill === "Freeze" && input.command.primaryJustPressed) {
    const definition = SKILL_DEFINITIONS.Freeze;
    const canFreeze =
      isBattleSkillReady(freeze) &&
      isFreezeTargetInRange(input.player.position, input.command.pointerWorld, definition.range);

    if (canFreeze) {
      const stamina = consumeBattleSkillStamina(input.player, "Freeze");
      if (!stamina.ok) {
        input.callbacks.showFloatingText(input.player.position, stamina.message ?? "体力不足", "warning");
        return;
      }
      input.player.skills = updateBattleSkillState(
        input.player.skills,
        "Freeze",
        activateBattleSkillState(freeze, definition)
      );
      input.player.preparedSkill = null;
      input.callbacks.addFreezeField(input.player.heroId, input.command.pointerWorld, definition.radius, definition.durationMs);
      input.callbacks.createPulse(input.command.pointerWorld, definition.radius, 0x86f4ff);
    } else {
      input.callbacks.createFloatingText(input.command.pointerWorld, "\u51b0\u96fe\u76ee\u6807\u65e0\u6548", "#8beeff");
    }
  }

  if (isSkillCommandPressed(input.command, "Critical") && isBattleSkillReady(critical)) {
    const stamina = consumeBattleSkillStamina(input.player, "Critical");
    if (!stamina.ok) {
      input.callbacks.showFloatingText(input.player.position, stamina.message ?? "体力不足", "warning");
      return;
    }
    const definition = SKILL_DEFINITIONS.Critical;
    input.player.skills = updateBattleSkillState(
      input.player.skills,
      "Critical",
      activateBattleSkillState(critical, definition)
    );
    input.callbacks.createPulse(input.player.position, 42, 0xffd166);
    input.callbacks.showFloatingText(input.player.position, "暴击启动", "success");
  }
}

function togglePreparedSkill(player: Hero, kind: PreparedTargetSkillKind): void {
  player.preparedSkill = resolveBattlePreparedSkillToggle(player.preparedSkill, kind);
}

/** Apply jump action. */
export function applyJumpAction(input: ApplyJumpActionInputs): void {
  if (!input.player.alive || !input.command.secondaryJustPressed || input.isPlayerMotionActive) {
    return;
  }

  if (input.player.jumpCooldownMs > 0) {
    input.callbacks.showFloatingText(input.player.position, "\u8df3\u8dc3\u51b7\u5374\u4e2d", "neutral");
    return;
  }

  if (input.command.movement.x === 0 && input.command.movement.y === 0) {
    input.callbacks.showFloatingText(input.player.position, "\u6ca1\u6709\u79fb\u52a8\u65b9\u5411", "neutral");
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
    input.callbacks.showFloatingText(input.player.position, "\u76ee\u6807\u53d7\u963b", "warning");
    return;
  }

  input.player.jumpCooldownMs = JUMP_COOLDOWN_MS;
  input.callbacks.startPlayerMotion(destination, 190, "jump");
}
