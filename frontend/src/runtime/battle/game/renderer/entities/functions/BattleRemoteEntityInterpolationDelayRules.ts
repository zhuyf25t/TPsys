export interface BattleRemoteEntityInterpolationSampleTiming {
  readonly receivedAtMs: number;
}

export interface ResolveBattleRemoteEntityAdaptiveInterpolationDelayInput {
  readonly samples: readonly BattleRemoteEntityInterpolationSampleTiming[];
  readonly renderNowMs: number;
  readonly baseDelayMs: number;
  readonly maxDelayMs: number;
  readonly jitterPaddingMs: number;
  readonly latestSamplePaddingMs: number;
}

export function resolveBattleRemoteEntityAdaptiveInterpolationDelayMs({
  samples,
  renderNowMs,
  baseDelayMs,
  maxDelayMs,
  jitterPaddingMs,
  latestSamplePaddingMs
}: ResolveBattleRemoteEntityAdaptiveInterpolationDelayInput): number {
  const safeBaseDelayMs = Math.max(0, normalizeFiniteNumber(baseDelayMs, 0));
  const safeMaxDelayMs = Math.max(safeBaseDelayMs, normalizeFiniteNumber(maxDelayMs, safeBaseDelayMs));
  let delayMs = safeBaseDelayMs;

  const intervals = collectSampleIntervals(samples);
  if (intervals.length > 0) {
    const averageIntervalMs = average(intervals);
    const percentileIntervalMs = percentile(intervals, 0.75);
    delayMs = Math.max(
      delayMs,
      averageIntervalMs + jitterPaddingMs,
      percentileIntervalMs + jitterPaddingMs
    );
  }

  const latestSample = samples[samples.length - 1] ?? null;
  if (latestSample && Number.isFinite(renderNowMs) && Number.isFinite(latestSample.receivedAtMs)) {
    const latestSampleAgeMs = renderNowMs - latestSample.receivedAtMs;
    if (latestSampleAgeMs > 0) {
      delayMs = Math.max(delayMs, latestSampleAgeMs + latestSamplePaddingMs);
    }
  }

  delayMs = clamp(delayMs, safeBaseDelayMs, safeMaxDelayMs);

  const historySpanMs = resolveHistorySpanMs(samples);
  if (historySpanMs !== null && historySpanMs > safeBaseDelayMs + 1) {
    delayMs = Math.min(delayMs, historySpanMs - 0.5);
  }

  return Math.round(clamp(delayMs, safeBaseDelayMs, safeMaxDelayMs));
}

function collectSampleIntervals(samples: readonly BattleRemoteEntityInterpolationSampleTiming[]): number[] {
  const intervals: number[] = [];
  for (let index = 1; index < samples.length; index += 1) {
    const previous = samples[index - 1];
    const current = samples[index];
    const intervalMs = current.receivedAtMs - previous.receivedAtMs;
    if (Number.isFinite(intervalMs) && intervalMs > 0.001) {
      intervals.push(intervalMs);
    }
  }
  return intervals;
}

function resolveHistorySpanMs(samples: readonly BattleRemoteEntityInterpolationSampleTiming[]): number | null {
  if (samples.length < 2) {
    return null;
  }

  const first = samples[0];
  const last = samples[samples.length - 1];
  const spanMs = last.receivedAtMs - first.receivedAtMs;
  return Number.isFinite(spanMs) && spanMs > 0 ? spanMs : null;
}

function average(values: readonly number[]): number {
  if (values.length === 0) {
    return 0;
  }

  return values.reduce((sum, value) => sum + value, 0) / values.length;
}

function percentile(values: readonly number[], ratio: number): number {
  if (values.length === 0) {
    return 0;
  }

  const sorted = [...values].sort((left, right) => left - right);
  const safeRatio = clamp(ratio, 0, 1);
  const index = Math.min(sorted.length - 1, Math.floor((sorted.length - 1) * safeRatio));
  return sorted[index];
}

function normalizeFiniteNumber(value: number, fallback: number): number {
  return Number.isFinite(value) ? value : fallback;
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}
