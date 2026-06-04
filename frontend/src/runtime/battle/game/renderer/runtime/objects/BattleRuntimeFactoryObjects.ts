import type { BattlePreparedSkill as PreparedSkill } from "../../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { BattlePlayerCommand as PlayerCommand } from "../../../../../../objects/battle/microservices/session/objects/command/BattlePlayerCommand";
import type { BattleInitialParticipantsConfig } from "../../../../../../objects/battle/microservices/session/objects/state/BattleInitialParticipants";
import type { BattleGameSnapshot as GameSnapshot } from "../../../../../../objects/battle/microservices/session/objects/state/BattleGameSnapshot";
import type { AuthoritativeBattleState } from "../../../../microservices/session/api/BattleAuthoritativeSessionClient";
import type { AuthoritativeLocalHeroReplayCommandEntry } from "../../authoritative/BattleAuthoritativeLocalHeroReplay";

export interface BattleRuntimeHandle {
  destroy: () => void;
  readSnapshot: () => GameSnapshot | null;
  readPlayerCommand: () => PlayerCommand | null;
  captureThumbnail: () => string | null;
  setAuthoritativePreparedSkill: (preparedSkill: PreparedSkill) => void;
  applyAuthoritativeState: (
    state: AuthoritativeBattleState,
    localPlayerId: string,
    commandHistory?: readonly AuthoritativeLocalHeroReplayCommandEntry[]
  ) => boolean;
}

export interface CreateBattleRuntimeOptions {
  mountNode: HTMLElement;
  hudRoot: HTMLElement;
  initialSnapshot?: GameSnapshot | null;
  initialParticipants?: BattleInitialParticipantsConfig;
  initialAuthoritativeState?: AuthoritativeBattleState | null;
  localAuthoritativePlayerId?: string;
  sharedAuthoritativeRuntime?: boolean;
  mapId?: string;
}
