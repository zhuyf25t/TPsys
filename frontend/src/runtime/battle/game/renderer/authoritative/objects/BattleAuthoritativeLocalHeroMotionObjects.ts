import type { BattleHeroViewState as Hero } from "../../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { BattlePlayerCommand as PlayerCommand } from "../../../../../../objects/battle/microservices/session/objects/command/BattlePlayerCommand";
import type { BattleGameSnapshot as GameSnapshot } from "../../../../../../objects/battle/microservices/session/objects/state/BattleGameSnapshot";
import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";
import type { MotionObstacleBounds } from "../../../../microservices/world/functions/BattleMotionRules";
import type { LocalHeroDisplayPoseStore } from "../../entities/BattleLocalHeroDisplay";

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
