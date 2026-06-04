import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";
import type {
  LocalFeedbackDiagnosticChannel,
  LocalMotionFeedbackDiagnosticSample,
  LocalMotionFeedbackDiagnosticsRecordInput,
  LocalMuzzleFeedbackDiagnosticSample,
  LocalMuzzleFeedbackDiagnosticsRecordInput
} from "../objects/LocalFeedbackDiagnosticsObjects";

const LOCAL_MOTION_DISTANCE_EPSILON = 0.001;

export function createLocalMotionFeedbackDiagnosticSample(
  input: LocalMotionFeedbackDiagnosticsRecordInput,
  sequence: number,
  atMs: number
): LocalMotionFeedbackDiagnosticSample | null {
  const distance = distanceBetweenLocalFeedbackVec2(input.from, input.to);
  if (distance <= LOCAL_MOTION_DISTANCE_EPSILON) {
    return null;
  }

  return {
    sequence,
    atMs,
    from: cloneLocalFeedbackVec2(input.from),
    to: cloneLocalFeedbackVec2(input.to),
    distance,
    ...(input.movement ? { movement: cloneLocalFeedbackVec2(input.movement) } : {}),
    ...(input.facing !== undefined ? { facing: input.facing } : {})
  };
}

export function createLocalMuzzleFeedbackDiagnosticSample(
  input: LocalMuzzleFeedbackDiagnosticsRecordInput,
  sequence: number,
  atMs: number
): LocalMuzzleFeedbackDiagnosticSample {
  return {
    sequence,
    atMs,
    weaponKind: input.weaponKind,
    position: cloneLocalFeedbackVec2(input.position),
    ...(input.pointerWorld ? { pointerWorld: cloneLocalFeedbackVec2(input.pointerWorld) } : {})
  };
}

export function createLocalFeedbackDiagnosticChannelSnapshot<TSample extends object>(
  count: number,
  firstAtMs: number | null,
  lastAtMs: number | null,
  samples: readonly TSample[],
  sampleWindowSize: number
): LocalFeedbackDiagnosticChannel<TSample> {
  return {
    count,
    firstAtMs,
    lastAtMs,
    sampleCount: samples.length,
    sampleWindowSize,
    recentSamples: samples.map((sample) => ({ ...sample })),
    lastSample: samples.length > 0 ? { ...samples[samples.length - 1] } : null
  };
}

function cloneLocalFeedbackVec2(vector: Vec2): Vec2 {
  return {
    x: vector.x,
    y: vector.y
  };
}

function distanceBetweenLocalFeedbackVec2(left: Vec2, right: Vec2): number {
  return Math.hypot(right.x - left.x, right.y - left.y);
}
