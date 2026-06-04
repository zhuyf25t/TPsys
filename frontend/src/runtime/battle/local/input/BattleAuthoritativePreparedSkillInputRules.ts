import type { BattleHeroViewState as Hero } from "../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { BattlePreparedSkill as PreparedSkill } from "../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { BattlePlayerCommand as PlayerCommand } from "../../../../objects/battle/microservices/session/objects/command/BattlePlayerCommand";
import type { BattleVector2 as Vec2 } from "../../../../objects/battle/objects/core/BattleCoreScalars";
import type { SceneGeometryObstacleBounds } from "../geometry/sceneGeometry";
import { isBattleSharedAuthoritativeTargetValid } from "../../microservices/abilities/functions/BattleSkillTargetValidityRules";

export interface SuppressInvalidAuthoritativePreparedConfirmInput {
  command: PlayerCommand;
  player: Hero;
  preparedSkill: PreparedSkill;
  worldSize: Vec2;
  obstacleBounds: readonly SceneGeometryObstacleBounds[];
}

export function suppressInvalidAuthoritativePreparedConfirm({
  command,
  player,
  preparedSkill,
  worldSize,
  obstacleBounds
}: SuppressInvalidAuthoritativePreparedConfirmInput): PlayerCommand {
  if (!command.primaryJustPressed || preparedSkill === null) {
    return command;
  }

  if (
    isBattleSharedAuthoritativeTargetValid({
      player,
      preparedSkill,
      target: command.pointerWorld,
      worldSize,
      obstacleBounds
    })
  ) {
    return command;
  }

  return {
    ...command,
    primaryJustPressed: false
  };
}
