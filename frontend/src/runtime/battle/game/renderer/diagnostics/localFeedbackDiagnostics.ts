import { getBattleDiagnosticsRoot, isBattleDiagnosticsEnabled } from "./battleDiagnosticsGate";
import {
  createLocalFeedbackDiagnosticChannelSnapshot,
  createLocalMotionFeedbackDiagnosticSample,
  createLocalMuzzleFeedbackDiagnosticSample
} from "./functions/LocalFeedbackDiagnosticsRules";
import type {
  LocalFeedbackDiagnosticsSnapshot,
  LocalMotionFeedbackDiagnosticSample,
  LocalMotionFeedbackDiagnosticsRecordInput,
  LocalMuzzleFeedbackDiagnosticSample,
  LocalMuzzleFeedbackDiagnosticsRecordInput,
  SlayDemoLocalFeedbackDiagnosticsRoot
} from "./objects/LocalFeedbackDiagnosticsObjects";

export type {
  LocalFeedbackDiagnosticChannel,
  LocalFeedbackDiagnosticsSnapshot,
  LocalMotionFeedbackDiagnosticSample,
  LocalMotionFeedbackDiagnosticsRecordInput,
  LocalMuzzleFeedbackDiagnosticSample,
  LocalMuzzleFeedbackDiagnosticsRecordInput
} from "./objects/LocalFeedbackDiagnosticsObjects";

const MAX_LOCAL_FEEDBACK_SAMPLES = 240;

let motionCount = 0;
let motionFirstAtMs: number | null = null;
let motionLastAtMs: number | null = null;
let muzzleCount = 0;
let muzzleFirstAtMs: number | null = null;
let muzzleLastAtMs: number | null = null;
const motionSamples: LocalMotionFeedbackDiagnosticSample[] = [];
const muzzleSamples: LocalMuzzleFeedbackDiagnosticSample[] = [];

publishLocalFeedbackDiagnostics();

export function recordLocalMotionFeedbackDiagnostics(input: LocalMotionFeedbackDiagnosticsRecordInput): void {
  if (!isBattleDiagnosticsEnabled()) {
    return;
  }

  const atMs = nowMs();
  const nextSequence = motionCount + 1;
  const sample = createLocalMotionFeedbackDiagnosticSample(input, nextSequence, atMs);
  if (!sample) {
    return;
  }

  motionCount = nextSequence;
  motionFirstAtMs = motionFirstAtMs ?? atMs;
  motionLastAtMs = atMs;
  pushSample(motionSamples, sample);
  publishLocalFeedbackDiagnostics();
}

export function recordLocalMuzzleFeedbackDiagnostics(input: LocalMuzzleFeedbackDiagnosticsRecordInput): void {
  if (!isBattleDiagnosticsEnabled()) {
    return;
  }

  const atMs = nowMs();
  muzzleCount += 1;
  muzzleFirstAtMs = muzzleFirstAtMs ?? atMs;
  muzzleLastAtMs = atMs;
  pushSample(muzzleSamples, createLocalMuzzleFeedbackDiagnosticSample(input, muzzleCount, atMs));
  publishLocalFeedbackDiagnostics();
}

function publishLocalFeedbackDiagnostics(): void {
  const diagnosticsRoot = getBattleDiagnosticsRoot<SlayDemoLocalFeedbackDiagnosticsRoot>();
  if (!diagnosticsRoot) {
    return;
  }

  diagnosticsRoot.localFeedback = createSnapshot();
}

function createSnapshot(): LocalFeedbackDiagnosticsSnapshot {
  return {
    motion: createLocalFeedbackDiagnosticChannelSnapshot(
      motionCount,
      motionFirstAtMs,
      motionLastAtMs,
      motionSamples,
      MAX_LOCAL_FEEDBACK_SAMPLES
    ),
    muzzle: createLocalFeedbackDiagnosticChannelSnapshot(
      muzzleCount,
      muzzleFirstAtMs,
      muzzleLastAtMs,
      muzzleSamples,
      MAX_LOCAL_FEEDBACK_SAMPLES
    )
  };
}

function pushSample<TSample>(samples: TSample[], sample: TSample): void {
  samples.push(sample);
  if (samples.length > MAX_LOCAL_FEEDBACK_SAMPLES) {
    samples.splice(0, samples.length - MAX_LOCAL_FEEDBACK_SAMPLES);
  }
}

function nowMs(): number {
  if (typeof performance !== "undefined" && typeof performance.now === "function") {
    return performance.now();
  }

  return Date.now();
}
