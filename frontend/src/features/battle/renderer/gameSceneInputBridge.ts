import type Phaser from "phaser";
import type { GameSnapshot, Hero, PlayerCommand, PreparedSkill, SkillKind, Vec2 } from "../../../domain/types";
import type { ControlKeys } from "../input/controlKeys";
import { readPhaserPlayerCommand } from "../input/phaserPlayerCommandReader";
import type { ObstacleBounds } from "./arena/arenaBuilder";
import { suppressInvalidAuthoritativePreparedConfirm } from "./authoritativeFrameSceneBridge";

export interface ReadGameScenePlayerCommandInput {
  input: Phaser.Input.InputPlugin;
  controls: ControlKeys;
  playerPosition: Vec2;
  pointerJustPressed: boolean;
  secondaryJustPressed: boolean;
  pendingWeaponSwitchDirection: -1 | 0 | 1;
  sharedAuthoritativeRuntime: boolean;
  player: Hero;
  preparedSkill: PreparedSkill;
  worldSize: GameSnapshot["worldSize"];
  obstacleBounds: readonly ObstacleBounds[];
}

export function readGameScenePlayerCommand({
  input,
  controls,
  playerPosition,
  pointerJustPressed,
  secondaryJustPressed,
  pendingWeaponSwitchDirection,
  sharedAuthoritativeRuntime,
  player,
  preparedSkill,
  worldSize,
  obstacleBounds
}: ReadGameScenePlayerCommandInput): PlayerCommand {
  const command = readPhaserPlayerCommand({
    input,
    controls,
    playerPosition,
    pointerJustPressed,
    secondaryJustPressed,
    pendingWeaponSwitchDirection
  });

  if (!sharedAuthoritativeRuntime) {
    return command;
  }

  return suppressInvalidAuthoritativePreparedConfirm({
    command: suppressUnreadyAuthoritativePreparedToggle(command, player),
    player,
    preparedSkill,
    worldSize,
    obstacleBounds
  });
}

function suppressUnreadyAuthoritativePreparedToggle(command: PlayerCommand, player: Hero): PlayerCommand {
  const toggleBlink = command.toggleBlink && isAuthoritativeSkillReady(player, "Blink");
  const toggleFreeze = command.toggleFreeze && isAuthoritativeSkillReady(player, "Freeze");

  if (toggleBlink === command.toggleBlink && toggleFreeze === command.toggleFreeze) {
    return command;
  }

  return {
    ...command,
    toggleBlink,
    toggleFreeze
  };
}

function isAuthoritativeSkillReady(player: Hero, kind: SkillKind): boolean {
  const skill = player.skills.find((entry) => entry.kind === kind);
  return skill !== undefined && skill.cooldownMs <= 0 && skill.activeMs <= 0;
}
