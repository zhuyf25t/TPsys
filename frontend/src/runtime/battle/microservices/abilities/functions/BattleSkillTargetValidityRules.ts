import type { BattleHeroViewState as Hero } from "../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { BattlePreparedSkill as PreparedSkill } from "../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { BattleVector2 as Vec2 } from "../../../../../objects/battle/objects/core/BattleCoreScalars";
import {
  isInsideWorld,
  isMotionTargetPointValid,
  type MotionObstacleBounds
} from "../../world/functions/BattleMotionRules";
import { isFreezeTargetInRange } from "./BattleSlowFieldRuntimeRules";
import { getPreparedTargetSkillRuntimeProfile } from "./BattleSkillRuntimeProfiles";

export interface BattleSharedAuthoritativeTargetValidityInput {
  player: Hero;
  preparedSkill: Exclude<PreparedSkill, null>;
  target: Vec2;
  worldSize: Vec2;
  obstacleBounds: readonly MotionObstacleBounds[];
}

export function isBattleSharedAuthoritativeTargetValid(
  input: BattleSharedAuthoritativeTargetValidityInput
): boolean {
  const profile = getPreparedTargetSkillRuntimeProfile(input.preparedSkill);

  switch (profile.kind) {
    case "Blink":
      return (
        isBattleSkillTargetInRange(input.player.position, input.target, profile.target.range) &&
        isMotionTargetPointValid({
          target: input.target,
          radius: input.player.radius,
          worldSize: input.worldSize,
          obstacleBounds: input.obstacleBounds
        })
      );
    case "Freeze":
      return (
        isFreezeTargetInRange(input.player.position, input.target, profile.target.range) &&
        isInsideWorld(input.target, 0, input.worldSize)
      );
  }
}

function isBattleSkillTargetInRange(origin: Vec2, target: Vec2, range: number): boolean {
  return Math.hypot(target.x - origin.x, target.y - origin.y) <= range;
}
