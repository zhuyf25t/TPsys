import type { BattleHeroViewState as Hero } from "../../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { BattlePlayerCommand as PlayerCommand } from "../../../../../../objects/battle/microservices/session/objects/command/BattlePlayerCommand";
import type { BattleGameSnapshot as GameSnapshot } from "../../../../../../objects/battle/microservices/session/objects/state/BattleGameSnapshot";
import type { AuthoritativeLocalHeroReplayCommandEntry } from "../../../../microservices/session/functions/BattleAuthoritativeLocalHeroReplayProjection";
import type { BattleRuntimeAuthoritativeFrame } from "../../../../microservices/session/functions/BattleRuntimeAuthoritativeFrameBuilder";
import type { MotionObstacleBounds } from "../../../../microservices/world/functions/BattleMotionRules";

export type GameSceneAuthoritativeFrame = BattleRuntimeAuthoritativeFrame;

export interface GameSceneAuthoritativeFrameOptions {
  localCommandHistory?: readonly AuthoritativeLocalHeroReplayCommandEntry[];
  localLastClientCommandSeq?: number;
  nowMs?: number;
}

export interface ApplyAuthoritativeFrameSceneBridgeInput {
  snapshot: GameSnapshot;
  frame: BattleRuntimeAuthoritativeFrame;
  localPlayerMovementActive: boolean;
  obstacleBounds: readonly MotionObstacleBounds[];
  options?: GameSceneAuthoritativeFrameOptions;
}

export interface UpdateAuthoritativeLocalDisplayMotionInput {
  snapshot: GameSnapshot;
  player: Hero;
  command: PlayerCommand;
  deltaMs: number;
  obstacleBounds: readonly MotionObstacleBounds[];
  localPlayerMovementActive: boolean;
}
