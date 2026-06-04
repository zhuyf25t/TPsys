import type { BattleGameSnapshot as GameSnapshot } from "../../../objects/battle/microservices/session/objects/state/BattleGameSnapshot";
import type { BattleReplayFrameState as ReplayFrame } from "../../../objects/battle/microservices/projections/objects/replay/BattleReplayFrameState";
import { appendBotOnlyBattleClosureReplayFrames } from "../../../runtime/battle/microservices/projections/functions/BattleFinalizationReplayRules";
import type { BotOnlyBattleClosure } from "../../../runtime/battle/microservices/projections/functions/BattleBotOnlyClosureReplayRules";
import type { ActiveBattleSession } from "../objects/BattlePageState";
import {
  createExitedBattleSnapshot,
  resolveBattleFinalizationSnapshot
} from "./battlePageRuntimeHelpers";
import { resolveRecoveredBattleReplayFrames } from "./battleRuntimeReplayFrames";

interface LocalRuntimeFinalizationPlanInput {
  readonly snapshot: GameSnapshot;
  readonly forceCurrentSnapshot: boolean;
  readonly durationExpired: boolean;
  readonly replayFrames: ReplayFrame[];
}

export interface LocalRuntimeFinalizationPlan {
  readonly finalSnapshot: GameSnapshot;
  readonly botOnlyClosure: BotOnlyBattleClosure | null;
  readonly replayFrames: ReplayFrame[];
}

export interface CompletedSessionRecoveryPlan {
  readonly finalSnapshot: GameSnapshot;
  readonly botOnlyClosure: BotOnlyBattleClosure | null;
  readonly replayFrames: ReplayFrame[];
  readonly allowBotOnlyClosure: boolean;
  readonly syncBackend: boolean;
}

export function resolveLocalRuntimeFinalizationPlan({
  snapshot,
  forceCurrentSnapshot,
  durationExpired,
  replayFrames
}: LocalRuntimeFinalizationPlanInput): LocalRuntimeFinalizationPlan {
  const exitResolvedSnapshot =
    forceCurrentSnapshot && !durationExpired ? createExitedBattleSnapshot(snapshot) : snapshot;
  const { finalSnapshot, botOnlyClosure } = resolveBattleFinalizationSnapshot(exitResolvedSnapshot, durationExpired);
  return {
    finalSnapshot,
    botOnlyClosure,
    replayFrames: botOnlyClosure ? appendBotOnlyBattleClosureReplayFrames(replayFrames, botOnlyClosure) : [...replayFrames]
  };
}

export function resolveCompletedSessionRecoveryPlan(session: ActiveBattleSession): CompletedSessionRecoveryPlan {
  const isSharedAuthoritativeSession = session.sharedAuthoritativeRuntime === true;
  const recoveredSnapshot = isSharedAuthoritativeSession ? session.snapshot : createExitedBattleSnapshot(session.snapshot);
  const { finalSnapshot, botOnlyClosure } = isSharedAuthoritativeSession
    ? { finalSnapshot: recoveredSnapshot, botOnlyClosure: null }
    : resolveBattleFinalizationSnapshot(recoveredSnapshot, false);
  const replayFrames = isSharedAuthoritativeSession
    ? [...session.replayFrames]
    : botOnlyClosure
      ? appendBotOnlyBattleClosureReplayFrames(session.replayFrames, botOnlyClosure)
      : resolveRecoveredBattleReplayFrames(session.replayFrames, finalSnapshot);

  return {
    finalSnapshot,
    botOnlyClosure,
    replayFrames,
    allowBotOnlyClosure: !isSharedAuthoritativeSession,
    syncBackend: !isSharedAuthoritativeSession
  };
}
