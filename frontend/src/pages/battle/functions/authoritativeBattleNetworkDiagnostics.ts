import type {
  AuthoritativeBattleCommand,
  AuthoritativeBattleCommandSubmitOutcome,
  AuthoritativeBattleState
} from "../../../runtime/battle/microservices/session/api/BattleAuthoritativeSessionClient";
import {
  getBattleDiagnosticsRoot,
  isBattleDiagnosticsEnabled
} from "../../../runtime/battle/game/renderer/diagnostics/battleDiagnosticsGate";

type AuthoritativeStateIngressSource = "channel" | "stream" | "poll";
type AuthoritativeCommandSubmitStatus = "accepted" | "http" | "network" | "parse";

interface AuthoritativeStateIngressDiagnosticsRecordInput {
  readonly source: AuthoritativeStateIngressSource;
  readonly state: AuthoritativeBattleState;
}

interface AuthoritativeCommandSubmitDiagnosticsRecordInput {
  readonly command: AuthoritativeBattleCommand;
  readonly submittedAtMs: number;
  readonly outcome: AuthoritativeBattleCommandSubmitOutcome;
  readonly deferredAtMs?: number | null;
  readonly inFlightCountBeforeSend?: number | null;
  readonly inFlightLimit?: number | null;
  readonly priorityInputPending?: boolean;
}

interface AuthoritativeCommandDeferDiagnosticsRecordInput {
  readonly atMs: number;
  readonly priorityInputPending: boolean;
  readonly inFlightCount: number;
  readonly inFlightLimit: number;
}

interface NumericSummary {
  readonly sampleCount: number;
  readonly valueCount: number;
  readonly avg: number | null;
  readonly max: number | null;
  readonly p95: number | null;
}

interface AuthoritativeStateIngressDiagnosticSample {
  readonly sequence: number;
  readonly atMs: number;
  readonly source: AuthoritativeStateIngressSource;
  readonly intervalMs: number | null;
  readonly tick: number;
  readonly tickDelta: number | null;
  readonly elapsedMs: number;
  readonly elapsedDeltaMs: number | null;
  readonly playerCount: number;
  readonly projectileCount: number;
}

interface AuthoritativeCommandSubmitDiagnosticSample {
  readonly sequence: number;
  readonly deferredAtMs: number | null;
  readonly submittedAtMs: number;
  readonly completedAtMs: number;
  readonly queueDelayMs: number | null;
  readonly latencyMs: number;
  readonly clientCommandSeq: number;
  readonly clientTick: number;
  readonly inFlightCountBeforeSend: number | null;
  readonly inFlightLimit: number | null;
  readonly priorityInputPending: boolean;
  readonly movementActive: boolean;
  readonly primaryHeld: boolean;
  readonly reloadPressed: boolean;
  readonly castDash: boolean;
  readonly castBlink: boolean;
  readonly castFreeze: boolean;
  readonly castCritical: boolean;
  readonly switchWeaponDirection: -1 | 0 | 1;
  readonly switchWeaponIndex: number | null;
  readonly status: AuthoritativeCommandSubmitStatus;
  readonly acceptedCommandSeq: number | null;
  readonly acceptedTick: number | null;
  readonly acceptedTickLag: number | null;
  readonly acceptedCommandSeqLag: number | null;
  readonly serverPath: "fresh" | "serialized" | null;
  readonly serverDurationMs: number | null;
  readonly serverLockWaitMs: number | null;
  readonly serverLockHeldMs: number | null;
  readonly serverAdvanceMs: number | null;
  readonly serverCommitRetryCount: number | null;
  readonly httpStatus: number | null;
  readonly errorCode: string | null;
}

interface AuthoritativeCommandDeferDiagnosticSample {
  readonly sequence: number;
  readonly atMs: number;
  readonly priorityInputPending: boolean;
  readonly inFlightCount: number;
  readonly inFlightLimit: number;
}

interface AuthoritativeBattleNetworkDiagnosticsSnapshot {
  readonly stateIngress: {
    readonly count: number;
    readonly channelCount: number;
    readonly streamCount: number;
    readonly pollCount: number;
    readonly firstAtMs: number | null;
    readonly lastAtMs: number | null;
    readonly intervalSummary: NumericSummary;
    readonly tickDeltaSummary: NumericSummary;
    readonly elapsedDeltaSummary: NumericSummary;
    readonly sampleCount: number;
    readonly sampleWindowSize: number;
    readonly recentSamples: AuthoritativeStateIngressDiagnosticSample[];
    readonly lastSample: AuthoritativeStateIngressDiagnosticSample | null;
  };
  readonly commandSubmit: {
    readonly count: number;
    readonly acceptedCount: number;
    readonly failedCount: number;
    readonly firstSubmittedAtMs: number | null;
    readonly lastCompletedAtMs: number | null;
    readonly latencySummary: NumericSummary;
    readonly sampleCount: number;
    readonly sampleWindowSize: number;
    readonly recentSamples: AuthoritativeCommandSubmitDiagnosticSample[];
    readonly lastSample: AuthoritativeCommandSubmitDiagnosticSample | null;
  };
  readonly commandDefer: {
    readonly count: number;
    readonly priorityCount: number;
    readonly firstAtMs: number | null;
    readonly lastAtMs: number | null;
    readonly sampleCount: number;
    readonly sampleWindowSize: number;
    readonly recentSamples: AuthoritativeCommandDeferDiagnosticSample[];
    readonly lastSample: AuthoritativeCommandDeferDiagnosticSample | null;
  };
}

interface SlayDemoAuthoritativeBattleNetworkDiagnosticsRoot {
  authoritativeNetwork?: AuthoritativeBattleNetworkDiagnosticsSnapshot;
  [key: string]: unknown;
}

const MAX_STATE_INGRESS_SAMPLES = 360;
const MAX_COMMAND_SUBMIT_SAMPLES = 240;
const MAX_COMMAND_DEFER_SAMPLES = 240;

let stateIngressCount = 0;
let channelStateIngressCount = 0;
let streamStateIngressCount = 0;
let pollStateIngressCount = 0;
let stateIngressFirstAtMs: number | null = null;
let stateIngressLastAtMs: number | null = null;
let previousStateTick: number | null = null;
let previousStateElapsedMs: number | null = null;
const stateIngressSamples: AuthoritativeStateIngressDiagnosticSample[] = [];

let commandSubmitCount = 0;
let acceptedCommandSubmitCount = 0;
let failedCommandSubmitCount = 0;
let commandSubmitFirstSubmittedAtMs: number | null = null;
let commandSubmitLastCompletedAtMs: number | null = null;
const commandSubmitSamples: AuthoritativeCommandSubmitDiagnosticSample[] = [];

let commandDeferCount = 0;
let priorityCommandDeferCount = 0;
let commandDeferFirstAtMs: number | null = null;
let commandDeferLastAtMs: number | null = null;
const commandDeferSamples: AuthoritativeCommandDeferDiagnosticSample[] = [];

export function resolveBattleNetworkDiagnosticsNowMs(): number {
  if (typeof performance !== "undefined" && typeof performance.now === "function") {
    return performance.now();
  }

  return Date.now();
}

export function recordAuthoritativeStateIngressDiagnostics({
  source,
  state
}: AuthoritativeStateIngressDiagnosticsRecordInput): void {
  if (!isBattleDiagnosticsEnabled()) {
    return;
  }

  const atMs = resolveBattleNetworkDiagnosticsNowMs();
  const intervalMs = stateIngressLastAtMs === null ? null : Math.max(0, atMs - stateIngressLastAtMs);
  const tick = Math.max(0, Math.trunc(state.tick));
  const tickDelta = previousStateTick === null ? null : Math.max(0, tick - previousStateTick);
  const elapsedMs = Math.max(0, Math.round(state.elapsedMs));
  const elapsedDeltaMs =
    previousStateElapsedMs === null ? null : Math.max(0, elapsedMs - previousStateElapsedMs);

  stateIngressCount += 1;
  if (source === "channel") {
    channelStateIngressCount += 1;
  } else if (source === "stream") {
    streamStateIngressCount += 1;
  } else {
    pollStateIngressCount += 1;
  }
  stateIngressFirstAtMs = stateIngressFirstAtMs ?? atMs;
  stateIngressLastAtMs = atMs;
  previousStateTick = tick;
  previousStateElapsedMs = elapsedMs;

  pushSample(
    stateIngressSamples,
    {
      sequence: stateIngressCount,
      atMs,
      source,
      intervalMs,
      tick,
      tickDelta,
      elapsedMs,
      elapsedDeltaMs,
      playerCount: state.players.length,
      projectileCount: state.projectiles.length
    },
    MAX_STATE_INGRESS_SAMPLES
  );

  publishAuthoritativeBattleNetworkDiagnostics();
}

export function recordAuthoritativeCommandSubmitDiagnostics({
  command,
  submittedAtMs,
  outcome,
  deferredAtMs = null,
  inFlightCountBeforeSend = null,
  inFlightLimit = null,
  priorityInputPending = false
}: AuthoritativeCommandSubmitDiagnosticsRecordInput): void {
  if (!isBattleDiagnosticsEnabled()) {
    return;
  }

  const completedAtMs = resolveBattleNetworkDiagnosticsNowMs();
  const normalizedDeferredAtMs =
    typeof deferredAtMs === "number" && Number.isFinite(deferredAtMs) ? Math.max(0, deferredAtMs) : null;
  const queueDelayMs =
    normalizedDeferredAtMs === null ? null : Math.max(0, submittedAtMs - normalizedDeferredAtMs);
  const latencyMs = Math.max(0, completedAtMs - submittedAtMs);
  const status = resolveCommandSubmitStatus(outcome);
  const serverDiagnostics = outcome.ok ? outcome.accepted.serverDiagnostics ?? null : null;

  commandSubmitCount += 1;
  if (outcome.ok) {
    acceptedCommandSubmitCount += 1;
  } else {
    failedCommandSubmitCount += 1;
  }
  commandSubmitFirstSubmittedAtMs = commandSubmitFirstSubmittedAtMs ?? submittedAtMs;
  commandSubmitLastCompletedAtMs = completedAtMs;

  pushSample(
    commandSubmitSamples,
    {
      sequence: commandSubmitCount,
      deferredAtMs: normalizedDeferredAtMs,
      submittedAtMs,
      completedAtMs,
      queueDelayMs,
      latencyMs,
      clientCommandSeq: Math.max(0, Math.trunc(command.clientCommandSeq)),
      clientTick: Math.max(0, Math.trunc(command.clientTick)),
      inFlightCountBeforeSend:
        typeof inFlightCountBeforeSend === "number" && Number.isFinite(inFlightCountBeforeSend)
          ? Math.max(0, Math.trunc(inFlightCountBeforeSend))
          : null,
      inFlightLimit:
        typeof inFlightLimit === "number" && Number.isFinite(inFlightLimit)
          ? Math.max(0, Math.trunc(inFlightLimit))
          : null,
      priorityInputPending,
      movementActive: Math.hypot(command.movement.x, command.movement.y) > 0.0001,
      primaryHeld: command.primaryHeld,
      reloadPressed: command.reloadPressed,
      castDash: command.castDash,
      castBlink: command.castBlink,
      castFreeze: command.castFreeze,
      castCritical: command.castCritical,
      switchWeaponDirection: command.switchWeaponDirection,
      switchWeaponIndex: command.switchWeaponIndex,
      status,
      acceptedCommandSeq: outcome.ok ? Math.max(0, Math.trunc(outcome.accepted.acceptedCommandSeq)) : null,
      acceptedTick: outcome.ok ? Math.max(0, Math.trunc(outcome.accepted.acceptedTick)) : null,
      acceptedTickLag:
        serverDiagnostics !== null
          ? serverDiagnostics.acceptedTickLag
          : outcome.ok
            ? outcome.accepted.acceptedTick - command.clientTick
            : null,
      acceptedCommandSeqLag:
        serverDiagnostics !== null
          ? serverDiagnostics.acceptedCommandSeqLag
          : outcome.ok
            ? outcome.accepted.acceptedCommandSeq - command.clientCommandSeq
            : null,
      serverPath: serverDiagnostics?.path ?? null,
      serverDurationMs: serverDiagnostics?.durationMs ?? null,
      serverLockWaitMs: serverDiagnostics?.lockWaitMs ?? null,
      serverLockHeldMs: serverDiagnostics?.lockHeldMs ?? null,
      serverAdvanceMs: serverDiagnostics?.advanceMs ?? null,
      serverCommitRetryCount: serverDiagnostics?.commitRetryCount ?? null,
      httpStatus: !outcome.ok && outcome.kind === "http" ? outcome.status : null,
      errorCode: !outcome.ok && outcome.kind === "http" ? outcome.errorCode ?? null : null
    },
    MAX_COMMAND_SUBMIT_SAMPLES
  );

  publishAuthoritativeBattleNetworkDiagnostics();
}

export function recordAuthoritativeCommandDeferDiagnostics({
  atMs,
  priorityInputPending,
  inFlightCount,
  inFlightLimit
}: AuthoritativeCommandDeferDiagnosticsRecordInput): void {
  if (!isBattleDiagnosticsEnabled()) {
    return;
  }

  const normalizedAtMs = Math.max(0, atMs);
  commandDeferCount += 1;
  if (priorityInputPending) {
    priorityCommandDeferCount += 1;
  }
  commandDeferFirstAtMs = commandDeferFirstAtMs ?? normalizedAtMs;
  commandDeferLastAtMs = normalizedAtMs;

  pushSample(
    commandDeferSamples,
    {
      sequence: commandDeferCount,
      atMs: normalizedAtMs,
      priorityInputPending,
      inFlightCount: Math.max(0, Math.trunc(inFlightCount)),
      inFlightLimit: Math.max(0, Math.trunc(inFlightLimit))
    },
    MAX_COMMAND_DEFER_SAMPLES
  );
}

function publishAuthoritativeBattleNetworkDiagnostics(): void {
  const diagnosticsRoot = getBattleDiagnosticsRoot<SlayDemoAuthoritativeBattleNetworkDiagnosticsRoot>();
  if (!diagnosticsRoot) {
    return;
  }

  diagnosticsRoot.authoritativeNetwork = createSnapshot();
}

function createSnapshot(): AuthoritativeBattleNetworkDiagnosticsSnapshot {
  return {
    stateIngress: {
      count: stateIngressCount,
      channelCount: channelStateIngressCount,
      streamCount: streamStateIngressCount,
      pollCount: pollStateIngressCount,
      firstAtMs: stateIngressFirstAtMs,
      lastAtMs: stateIngressLastAtMs,
      intervalSummary: summarizeNumericSamples(stateIngressSamples, (sample) => sample.intervalMs),
      tickDeltaSummary: summarizeNumericSamples(stateIngressSamples, (sample) => sample.tickDelta),
      elapsedDeltaSummary: summarizeNumericSamples(stateIngressSamples, (sample) => sample.elapsedDeltaMs),
      sampleCount: stateIngressSamples.length,
      sampleWindowSize: MAX_STATE_INGRESS_SAMPLES,
      recentSamples: stateIngressSamples.map((sample) => ({ ...sample })),
      lastSample: lastSample(stateIngressSamples)
    },
    commandSubmit: {
      count: commandSubmitCount,
      acceptedCount: acceptedCommandSubmitCount,
      failedCount: failedCommandSubmitCount,
      firstSubmittedAtMs: commandSubmitFirstSubmittedAtMs,
      lastCompletedAtMs: commandSubmitLastCompletedAtMs,
      latencySummary: summarizeNumericSamples(commandSubmitSamples, (sample) => sample.latencyMs),
      sampleCount: commandSubmitSamples.length,
      sampleWindowSize: MAX_COMMAND_SUBMIT_SAMPLES,
      recentSamples: commandSubmitSamples.map((sample) => ({ ...sample })),
      lastSample: lastSample(commandSubmitSamples)
    },
    commandDefer: {
      count: commandDeferCount,
      priorityCount: priorityCommandDeferCount,
      firstAtMs: commandDeferFirstAtMs,
      lastAtMs: commandDeferLastAtMs,
      sampleCount: commandDeferSamples.length,
      sampleWindowSize: MAX_COMMAND_DEFER_SAMPLES,
      recentSamples: commandDeferSamples.map((sample) => ({ ...sample })),
      lastSample: lastSample(commandDeferSamples)
    }
  };
}

function resolveCommandSubmitStatus(
  outcome: AuthoritativeBattleCommandSubmitOutcome
): AuthoritativeCommandSubmitStatus {
  return outcome.ok ? "accepted" : outcome.kind;
}

function summarizeNumericSamples<TSample>(
  samples: readonly TSample[],
  selectValue: (sample: TSample) => number | null
): NumericSummary {
  const values = samples
    .map(selectValue)
    .filter((value): value is number => typeof value === "number" && Number.isFinite(value));

  if (values.length === 0) {
    return {
      sampleCount: samples.length,
      valueCount: 0,
      avg: null,
      max: null,
      p95: null
    };
  }

  const sortedValues = [...values].sort((left, right) => left - right);
  return {
    sampleCount: samples.length,
    valueCount: values.length,
    avg: values.reduce((sum, value) => sum + value, 0) / values.length,
    max: sortedValues[sortedValues.length - 1],
    p95: percentile(sortedValues, 0.95)
  };
}

function percentile(sortedValues: readonly number[], percentileValue: number): number | null {
  if (sortedValues.length === 0) {
    return null;
  }

  const clampedPercentile = Math.min(1, Math.max(0, percentileValue));
  const index = Math.min(sortedValues.length - 1, Math.ceil(sortedValues.length * clampedPercentile) - 1);
  return sortedValues[index];
}

function pushSample<TSample>(samples: TSample[], sample: TSample, maxSamples: number): void {
  samples.push(sample);
  if (samples.length > maxSamples) {
    samples.splice(0, samples.length - maxSamples);
  }
}

function lastSample<TSample>(samples: readonly TSample[]): TSample | null {
  return samples.length > 0 ? { ...samples[samples.length - 1] } : null;
}
