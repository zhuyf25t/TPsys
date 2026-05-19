import type { Vec2, WeaponKind } from "../../objects/types";
import { getBattleDiagnosticsRoot, isBattleDiagnosticsEnabled } from "./battleDiagnosticsGate";

const MAX_LOCAL_FEEDBACK_SAMPLES = 240;
const LOCAL_MOTION_DISTANCE_EPSILON = 0.001;

export interface LocalMotionFeedbackDiagnosticSample {
  sequence: number;
  atMs: number;
  from: Vec2;
  to: Vec2;
  distance: number;
  movement?: Vec2;
  facing?: number;
}

export interface LocalMuzzleFeedbackDiagnosticSample {
  sequence: number;
  atMs: number;
  weaponKind: WeaponKind;
  position: Vec2;
  pointerWorld?: Vec2;
}

export interface LocalFeedbackDiagnosticChannel<TSample> {
  count: number;
  firstAtMs: number | null;
  lastAtMs: number | null;
  sampleCount: number;
  sampleWindowSize: number;
  recentSamples: TSample[];
  lastSample: TSample | null;
}

export interface LocalFeedbackDiagnosticsSnapshot {
  motion: LocalFeedbackDiagnosticChannel<LocalMotionFeedbackDiagnosticSample>;
  muzzle: LocalFeedbackDiagnosticChannel<LocalMuzzleFeedbackDiagnosticSample>;
}

export interface LocalMotionFeedbackDiagnosticsRecordInput {
  from: Vec2;
  to: Vec2;
  movement?: Vec2;
  facing?: number;
}

export interface LocalMuzzleFeedbackDiagnosticsRecordInput {
  weaponKind: WeaponKind;
  position: Vec2;
  pointerWorld?: Vec2;
}

interface SlayDemoBattleDiagnosticsRoot {
  localFeedback?: LocalFeedbackDiagnosticsSnapshot;
  [key: string]: unknown;
}

let motionCount = 0;
let motionFirstAtMs: number | null = null;
let motionLastAtMs: number | null = null;
let muzzleCount = 0;
let muzzleFirstAtMs: number | null = null;
let muzzleLastAtMs: number | null = null;
const motionSamples: LocalMotionFeedbackDiagnosticSample[] = [];
const muzzleSamples: LocalMuzzleFeedbackDiagnosticSample[] = [];

publishLocalFeedbackDiagnostics();

/** 中文名：记录本地运动feedbackdiagnostics（recordLocalMotionFeedbackDiagnostics）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function recordLocalMotionFeedbackDiagnostics(input: LocalMotionFeedbackDiagnosticsRecordInput): void {
  if (!isBattleDiagnosticsEnabled()) {
    return;
  }

  const distance = distanceBetween(input.from, input.to);
  if (distance <= LOCAL_MOTION_DISTANCE_EPSILON) {
    return;
  }

  const atMs = nowMs();
  motionCount += 1;
  motionFirstAtMs = motionFirstAtMs ?? atMs;
  motionLastAtMs = atMs;

  const sample: LocalMotionFeedbackDiagnosticSample = {
    sequence: motionCount,
    atMs,
    from: cloneVec2(input.from),
    to: cloneVec2(input.to),
    distance
  };
  if (input.movement) {
    sample.movement = cloneVec2(input.movement);
  }
  if (input.facing !== undefined) {
    sample.facing = input.facing;
  }

  pushSample(motionSamples, sample);
  publishLocalFeedbackDiagnostics();
}

/** 中文名：记录本地muzzlefeedbackdiagnostics（recordLocalMuzzleFeedbackDiagnostics）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function recordLocalMuzzleFeedbackDiagnostics(input: LocalMuzzleFeedbackDiagnosticsRecordInput): void {
  if (!isBattleDiagnosticsEnabled()) {
    return;
  }

  const atMs = nowMs();
  muzzleCount += 1;
  muzzleFirstAtMs = muzzleFirstAtMs ?? atMs;
  muzzleLastAtMs = atMs;

  const sample: LocalMuzzleFeedbackDiagnosticSample = {
    sequence: muzzleCount,
    atMs,
    weaponKind: input.weaponKind,
    position: cloneVec2(input.position)
  };
  if (input.pointerWorld) {
    sample.pointerWorld = cloneVec2(input.pointerWorld);
  }

  pushSample(muzzleSamples, sample);
  publishLocalFeedbackDiagnostics();
}

function publishLocalFeedbackDiagnostics(): void {
  const diagnosticsRoot = getBattleDiagnosticsRoot<SlayDemoBattleDiagnosticsRoot>();
  if (!diagnosticsRoot) {
    return;
  }

  diagnosticsRoot.localFeedback = createSnapshot();
}

function createSnapshot(): LocalFeedbackDiagnosticsSnapshot {
  return {
    motion: createChannelSnapshot(motionCount, motionFirstAtMs, motionLastAtMs, motionSamples),
    muzzle: createChannelSnapshot(muzzleCount, muzzleFirstAtMs, muzzleLastAtMs, muzzleSamples)
  };
}

function createChannelSnapshot<TSample extends object>(
  count: number,
  firstAtMs: number | null,
  lastAtMs: number | null,
  samples: TSample[]
): LocalFeedbackDiagnosticChannel<TSample> {
  return {
    count,
    firstAtMs,
    lastAtMs,
    sampleCount: samples.length,
    sampleWindowSize: MAX_LOCAL_FEEDBACK_SAMPLES,
    recentSamples: samples.map((sample) => ({ ...sample })),
    lastSample: samples.length > 0 ? { ...samples[samples.length - 1] } : null
  };
}

function pushSample<TSample>(samples: TSample[], sample: TSample): void {
  samples.push(sample);
  if (samples.length > MAX_LOCAL_FEEDBACK_SAMPLES) {
    samples.splice(0, samples.length - MAX_LOCAL_FEEDBACK_SAMPLES);
  }
}

function distanceBetween(left: Vec2, right: Vec2): number {
  return Math.hypot(right.x - left.x, right.y - left.y);
}

function cloneVec2(vector: Vec2): Vec2 {
  return {
    x: vector.x,
    y: vector.y
  };
}

function nowMs(): number {
  if (typeof performance !== "undefined" && typeof performance.now === "function") {
    return performance.now();
  }

  return Date.now();
}
