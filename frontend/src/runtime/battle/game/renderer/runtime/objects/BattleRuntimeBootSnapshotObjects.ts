import type { BattleInitialParticipantsConfig } from "../../../../../../objects/battle/microservices/session/objects/state/BattleInitialParticipants";
import type { BattleGameSnapshot as GameSnapshot } from "../../../../../../objects/battle/microservices/session/objects/state/BattleGameSnapshot";
import type { AuthoritativeBattleState } from "../../../../microservices/session/api/BattleAuthoritativeSessionClient";

export interface CreateBattleRuntimeBootSnapshotInput {
  initialSnapshot: GameSnapshot | null;
  initialParticipants?: BattleInitialParticipantsConfig;
  initialAuthoritativeState: AuthoritativeBattleState | null;
  localAuthoritativePlayerId: string;
}
