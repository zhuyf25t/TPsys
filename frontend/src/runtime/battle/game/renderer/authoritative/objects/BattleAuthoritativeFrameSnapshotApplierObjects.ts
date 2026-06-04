import type { BattleGameSnapshot as GameSnapshot } from "../../../../../../objects/battle/microservices/session/objects/state/BattleGameSnapshot";
import type { BattleRuntimeAuthoritativeFrame } from "../../../../microservices/session/functions/BattleRuntimeAuthoritativeFrameBuilder";
import type {
  BattleRuntimeAuthoritativeLocalPlayerCorrectionTarget,
  BattleRuntimeAuthoritativeLocalPlayerReplayContext
} from "../../../../microservices/session/functions/BattleRuntimeAuthoritativeHeroSnapshotSync";

export type LocalPlayerAuthoritativeCorrectionTarget =
  BattleRuntimeAuthoritativeLocalPlayerCorrectionTarget;

export type LocalPlayerAuthoritativeReplayContext =
  BattleRuntimeAuthoritativeLocalPlayerReplayContext;

export interface ApplyAuthoritativeFrameToSnapshotInput {
  snapshot: GameSnapshot;
  frame: BattleRuntimeAuthoritativeFrame;
  receivedAtMs?: number;
  localPlayerMovementActive?: boolean;
  localPlayerReplay?: LocalPlayerAuthoritativeReplayContext;
  applyLocalPlayerAuthoritativeCorrection?(target: LocalPlayerAuthoritativeCorrectionTarget): void;
}
