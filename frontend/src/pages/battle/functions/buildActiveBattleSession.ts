import type { BattleGameSnapshot as GameSnapshot } from "../../../objects/battle/microservices/session/objects/state/BattleGameSnapshot";
import type { BattleReplayFrameState as ReplayFrame } from "../../../objects/battle/microservices/projections/objects/replay/BattleReplayFrameState";
import type { ActiveBattleSession, ActiveBattleSessionOwner } from "../objects/BattlePageState";

interface BuildActiveBattleSessionInput {
  readonly owner: ActiveBattleSessionOwner;
  readonly sessionEpoch: string | null;
  readonly battleId: string;
  readonly mapId: string;
  readonly sharedAuthoritativeRuntime: boolean;
  readonly localAuthoritativePlayerId: string;
  readonly localAuthoritativeTicketId: string;
  readonly savedAt: number;
  readonly snapshot: GameSnapshot;
  readonly replayFrames: ReplayFrame[];
  readonly lastReplaySampleElapsed: number | null;
}

export function buildActiveBattleSession({
  owner,
  sessionEpoch,
  battleId,
  mapId,
  sharedAuthoritativeRuntime,
  localAuthoritativePlayerId,
  localAuthoritativeTicketId,
  savedAt,
  snapshot,
  replayFrames,
  lastReplaySampleElapsed
}: BuildActiveBattleSessionInput): ActiveBattleSession {
  return {
    version: 1,
    owner,
    ...(sessionEpoch ? { sessionEpoch } : {}),
    battleId,
    mapId,
    ...(sharedAuthoritativeRuntime ? { sharedAuthoritativeRuntime: true } : {}),
    ...(sharedAuthoritativeRuntime && localAuthoritativePlayerId ? { localAuthoritativePlayerId } : {}),
    ...(sharedAuthoritativeRuntime && localAuthoritativeTicketId ? { localAuthoritativeTicketId } : {}),
    savedAt,
    snapshot,
    replayFrames,
    lastReplaySampleElapsed
  };
}
