import type { BattlePlayerCommand as PlayerCommand } from "../../../../../objects/battle/microservices/session/objects/command/BattlePlayerCommand";
import { suppressInvalidAuthoritativePreparedConfirm } from "../../../local/input/BattleAuthoritativePreparedSkillInputRules";
import { readPhaserPlayerCommand } from "../../../local/input/phaserPlayerCommandReader";
import { suppressUnreadyAuthoritativePreparedToggle } from "./functions/BattleGameSceneInputBridgeRules";
import type { ReadGameScenePlayerCommandInput } from "./objects/BattleGameSceneInputBridgeObjects";

export function readGameScenePlayerCommand({
  input,
  controls,
  playerPosition,
  pointerJustPressed,
  secondaryJustPressed,
  pendingWeaponSwitchDirection,
  pendingWeaponSwitchIndex,
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
    pendingWeaponSwitchDirection,
    pendingWeaponSwitchIndex
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
