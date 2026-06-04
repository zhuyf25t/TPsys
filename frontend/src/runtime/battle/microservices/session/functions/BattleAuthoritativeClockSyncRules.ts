import type { BattleRuntimeAuthoritativeFrame } from "./BattleRuntimeAuthoritativeFrameBuilder";

export interface BattleAuthoritativeClockAnchor {
  readonly elapsedMs: number;
  readonly receivedAtMs: number;
  readonly durationMs: number;
}

export interface CreateBattleAuthoritativeClockAnchorInput {
  readonly frame: Pick<BattleRuntimeAuthoritativeFrame, "elapsedMs" | "durationMs" | "serverTime">;
  readonly receivedAtMs: number;
}

export interface ResolveBattleAuthoritativeClockElapsedInput {
  readonly anchor: BattleAuthoritativeClockAnchor | null;
  readonly fallbackElapsedMs: number;
  readonly nowMs: number;
}

export function createBattleAuthoritativeClockAnchor({
  frame,
  receivedAtMs
}: CreateBattleAuthoritativeClockAnchorInput): BattleAuthoritativeClockAnchor {
  const durationMs = normalizeDurationMs(frame.durationMs);
  const transitMs = Math.max(0, normalizeMillis(receivedAtMs) - normalizeMillis(frame.serverTime));
  return {
    elapsedMs: clampElapsedMs(normalizeMillis(frame.elapsedMs) + transitMs, durationMs),
    receivedAtMs: normalizeMillis(receivedAtMs),
    durationMs
  };
}

export function resolveBattleAuthoritativeClockElapsedMs({
  anchor,
  fallbackElapsedMs,
  nowMs
}: ResolveBattleAuthoritativeClockElapsedInput): number {
  if (!anchor) {
    return Math.max(0, normalizeMillis(fallbackElapsedMs));
  }

  const elapsedSinceAnchorMs = Math.max(0, normalizeMillis(nowMs) - anchor.receivedAtMs);
  return clampElapsedMs(anchor.elapsedMs + elapsedSinceAnchorMs, anchor.durationMs);
}

export function clampElapsedMs(elapsedMs: number, durationMs: number): number {
  return Math.max(0, Math.min(normalizeMillis(elapsedMs), normalizeDurationMs(durationMs)));
}

function normalizeDurationMs(value: number): number {
  return Math.max(1, normalizeMillis(value));
}

function normalizeMillis(value: number): number {
  return Number.isFinite(value) ? Math.max(0, Math.round(value)) : 0;
}
