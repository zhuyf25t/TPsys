import type { BattleGasZoneState } from "../../../../../objects/battle/microservices/extraction/objects/extraction/BattleExtractionDefinitions";

export interface AdvanceBattleSharedAuthoritativeGasZoneInput {
  gasZone: BattleGasZoneState | null;
  elapsedMs: number;
  deltaMs: number;
}

export function advanceBattleSharedAuthoritativeGasZone(
  input: AdvanceBattleSharedAuthoritativeGasZoneInput
): BattleGasZoneState | null {
  const zone = input.gasZone;
  const deltaMs = Math.max(0, input.deltaMs);
  if (!zone || deltaMs <= 0 || zone.phase === "final") {
    return zone;
  }

  const nextElapsed = Math.max(0, input.elapsedMs);
  const previousElapsed = Math.max(0, nextElapsed - deltaMs);
  const startsAtMs = Math.max(0, zone.startsAtMs);
  const endsAtMs = Math.max(startsAtMs + 1, zone.endsAtMs);
  if (nextElapsed < startsAtMs) {
    return { ...zone, phase: "waiting" };
  }

  const activeFrom = Math.max(previousElapsed, startsAtMs);
  const activeTo = Math.min(nextElapsed, endsAtMs);
  const activeDeltaMs = Math.max(0, activeTo - activeFrom);
  const advanced = activeDeltaMs > 0
    ? advanceActiveGasZone(zone, activeDeltaMs, activeFrom, startsAtMs, endsAtMs)
    : zone;

  return {
    ...advanced,
    phase: advanced.nextRadius <= 0 && nextElapsed >= endsAtMs ? "final" : "advancing"
  };
}

function advanceActiveGasZone(
  zone: BattleGasZoneState,
  activeDeltaMs: number,
  activeFrom: number,
  startsAtMs: number,
  endsAtMs: number
): BattleGasZoneState {
  const remainingMs = Math.max(1, endsAtMs - activeFrom);
  const progress = Math.max(0, Math.min(1, activeDeltaMs / remainingMs));
  return {
    ...zone,
    radius: Math.max(0, zone.radius + (zone.nextRadius - zone.radius) * progress),
    progressMs: Math.max(0, Math.min(endsAtMs - startsAtMs, activeFrom + activeDeltaMs - startsAtMs))
  };
}
