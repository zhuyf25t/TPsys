import type { BattleSlowFieldState as SlowField } from "../../../../../../objects/battle/microservices/abilities/objects/skill/BattleSlowFieldState";
import type { BattleHeroViewState as Hero } from "../../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";
import type {
  AuthoritativeLocalHeroPendingBlinkPrediction,
  AuthoritativeLocalHeroPendingDashPrediction,
  AuthoritativeLocalHeroReplayCommandEntry
} from "../../../../microservices/session/functions/BattleAuthoritativeLocalHeroReplayProjection";
import type { MotionObstacleBounds } from "../../../../microservices/world/functions/BattleMotionRules";

export interface ResolveAuthoritativeLocalHeroReplayTargetInput {
  authoritativePosition: Vec2;
  worldSize: Vec2;
  obstacleBounds: readonly MotionObstacleBounds[];
  radius: number;
  player: Hero;
  stamina: number;
  maxStamina?: number;
  blinkCooldownMs?: number;
  blinkActiveMs?: number;
  dashCooldownMs?: number;
  dashActiveMs?: number;
  slowFields: readonly SlowField[];
  commandHistory: readonly AuthoritativeLocalHeroReplayCommandEntry[];
  lastClientCommandSeq: number;
  nowMs: number;
  pendingBlinkPrediction?: AuthoritativeLocalHeroPendingBlinkPrediction | null;
  pendingDashPrediction?: AuthoritativeLocalHeroPendingDashPrediction | null;
}
