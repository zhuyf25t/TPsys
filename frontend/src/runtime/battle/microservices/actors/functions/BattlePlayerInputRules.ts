import type { BattleHeroViewState as Hero } from "../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { BattlePlayerCommand as PlayerCommand } from "../../../../../objects/battle/microservices/session/objects/command/BattlePlayerCommand";

export function resolveBattlePlayerCommandAfterSkillInput(
  command: PlayerCommand,
  preparedSkillBeforeSkillInputs: Hero["preparedSkill"]
): PlayerCommand {
  return shouldSuppressBattlePrimaryFireForSkill(command, preparedSkillBeforeSkillInputs)
    ? suppressBattlePrimaryFire(command)
    : command;
}

export function shouldSuppressBattlePrimaryFireForSkill(
  command: PlayerCommand,
  preparedSkillBeforeSkillInputs: Hero["preparedSkill"]
): boolean {
  return (
    preparedSkillBeforeSkillInputs !== null ||
    command.castDash ||
    command.toggleBlink ||
    command.toggleFreeze
  );
}

function suppressBattlePrimaryFire(command: PlayerCommand): PlayerCommand {
  if (!command.primaryHeld && !command.primaryJustPressed) {
    return command;
  }

  return {
    ...command,
    primaryHeld: false,
    primaryJustPressed: false
  };
}
