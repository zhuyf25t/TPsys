import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";
import type {
  RemoteHeroViewDiagnosticSample,
  RemoteHeroViewMetricSummary,
  RemoteProjectileBirthDiagnosticSample,
  RemoteProjectileTerminalDiagnosticSample
} from "../objects/RemoteViewDiagnosticsObjects";

export function summarizeRemoteHeroMetric(
  samples: readonly RemoteHeroViewDiagnosticSample[],
  selectValue: (sample: RemoteHeroViewDiagnosticSample) => number | null
): RemoteHeroViewMetricSummary {
  const values: number[] = [];
  let nullCount = 0;

  samples.forEach((sample) => {
    const value = selectValue(sample);
    if (typeof value === "number" && Number.isFinite(value)) {
      values.push(value);
    } else {
      nullCount += 1;
    }
  });

  if (values.length === 0) {
    return {
      sampleCount: samples.length,
      valueCount: 0,
      nullCount,
      avg: null,
      max: null,
      p95: null,
      p99: null
    };
  }

  const sortedValues = [...values].sort((left, right) => left - right);
  const total = values.reduce((sum, value) => sum + value, 0);

  return {
    sampleCount: samples.length,
    valueCount: values.length,
    nullCount,
    avg: total / values.length,
    max: sortedValues[sortedValues.length - 1],
    p95: percentile(sortedValues, 0.95),
    p99: percentile(sortedValues, 0.99)
  };
}

export function cloneRemoteHeroSample(sample: RemoteHeroViewDiagnosticSample): RemoteHeroViewDiagnosticSample {
  return {
    ...sample,
    displayPosition: cloneRemoteViewDiagnosticsVec2(sample.displayPosition),
    targetPosition: cloneNullableRemoteViewDiagnosticsVec2(sample.targetPosition)
  };
}

export function cloneRemoteProjectileBirthSample(
  sample: RemoteProjectileBirthDiagnosticSample
): RemoteProjectileBirthDiagnosticSample {
  return {
    ...sample,
    position: cloneRemoteViewDiagnosticsVec2(sample.position)
  };
}

export function cloneRemoteProjectileTerminalSample(
  sample: RemoteProjectileTerminalDiagnosticSample
): RemoteProjectileTerminalDiagnosticSample {
  return {
    ...sample,
    terminalPosition: cloneNullableRemoteViewDiagnosticsVec2(sample.terminalPosition),
    displayPosition: cloneRemoteViewDiagnosticsVec2(sample.displayPosition),
    authoritativePosition: cloneRemoteViewDiagnosticsVec2(sample.authoritativePosition)
  };
}

export function isFiniteRemoteViewDiagnosticsVec2(position: Vec2): boolean {
  return Number.isFinite(position.x) && Number.isFinite(position.y);
}

export function cloneRemoteViewDiagnosticsVec2(position: Vec2): Vec2 {
  return {
    x: position.x,
    y: position.y
  };
}

export function cloneNullableRemoteViewDiagnosticsVec2(position: Vec2 | null): Vec2 | null {
  return position ? cloneRemoteViewDiagnosticsVec2(position) : null;
}

export function distanceBetweenRemoteViewDiagnosticsVec2(left: Vec2, right: Vec2): number {
  return Math.hypot(right.x - left.x, right.y - left.y);
}

export function toFiniteRemoteViewDiagnosticsNumberOrNull(value: number | null | undefined): number | null {
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

export function normalizeRemoteViewDiagnosticsOptionalString(value: string | null | undefined): string | null {
  return typeof value === "string" && value.trim() ? value.trim() : null;
}

function percentile(sortedValues: readonly number[], percentileValue: number): number | null {
  if (sortedValues.length === 0) {
    return null;
  }

  const clampedPercentile = Math.min(1, Math.max(0, percentileValue));
  const index = Math.min(sortedValues.length - 1, Math.ceil(sortedValues.length * clampedPercentile) - 1);
  return sortedValues[index];
}
