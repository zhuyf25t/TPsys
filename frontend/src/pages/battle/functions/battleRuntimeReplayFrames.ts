import type { BattleGameSnapshot as GameSnapshot } from "../../../objects/battle/microservices/session/objects/state/BattleGameSnapshot";
import {
  buildReplayFrame,
  shouldCaptureReplayFrame
} from "../../../runtime/battle/microservices/projections/functions/BattleReplayFrameRecorder";
import type { BattleReplayFrameState as ReplayFrame } from "../../../objects/battle/microservices/projections/objects/replay/BattleReplayFrameState";
import { appendBotOnlyBattleClosureReplayFrames } from "../../../runtime/battle/microservices/projections/functions/BattleFinalizationReplayRules";
import { resolveBattleFinalizationSnapshot } from "./battlePageRuntimeHelpers";

interface BattleReplayFrameRecordInput {
  readonly replayFrames: readonly ReplayFrame[];
  readonly lastReplaySampleElapsed: number | null;
  readonly snapshot: GameSnapshot;
  readonly force?: boolean;
}

interface BattleReplayFrameRecordResult {
  readonly replayFrames: ReplayFrame[];
  readonly lastReplaySampleFrame: ReplayFrame;
  readonly lastReplaySampleElapsed: number;
}

export function recordBattleReplayFrame({
  replayFrames,
  lastReplaySampleElapsed,
  snapshot,
  force = false
}: BattleReplayFrameRecordInput): BattleReplayFrameRecordResult | null {
  if (!force && !shouldCaptureReplayFrame(lastReplaySampleElapsed, snapshot.elapsedMs)) {
    return null;
  }

  const nextFrame = buildReplayFrame(snapshot);
  const nextReplayFrames = [...replayFrames];
  const lastFrameIndex = nextReplayFrames.length - 1;
  if (lastFrameIndex >= 0 && nextReplayFrames[lastFrameIndex].elapsedMs === nextFrame.elapsedMs) {
    nextReplayFrames[lastFrameIndex] = nextFrame;
  } else {
    nextReplayFrames.push(nextFrame);
  }

  return {
    replayFrames: nextReplayFrames,
    lastReplaySampleFrame: nextFrame,
    lastReplaySampleElapsed: snapshot.elapsedMs
  };
}

export function resolveRecoveredBattleReplayFrames(frames: ReplayFrame[], snapshot: GameSnapshot): ReplayFrame[] {
  const { botOnlyClosure } = resolveBattleFinalizationSnapshot(snapshot, false);
  return botOnlyClosure ? appendBotOnlyBattleClosureReplayFrames(frames, botOnlyClosure) : [...frames];
}
