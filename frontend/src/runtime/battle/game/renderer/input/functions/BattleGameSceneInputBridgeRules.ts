import type { SkillKind } from "../../../../../../objects/battle/microservices/abilities/objects/skill/SkillKind";
import type { BattleHeroViewState as Hero } from "../../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { BattlePlayerCommand as PlayerCommand } from "../../../../../../objects/battle/microservices/session/objects/command/BattlePlayerCommand";

export function suppressUnreadyAuthoritativePreparedToggle(command: PlayerCommand, player: Hero): PlayerCommand {
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
