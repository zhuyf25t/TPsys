import {
  normalizeBattleExtractionStateResponse,
  normalizeBattleGasZoneStateResponse,
  normalizeBattleLootCacheStateResponse
} from "../../extraction/functions/normalizeBattleExtractionStateResponse";
import type { AuthoritativeBattleState } from "../api/BattleAuthoritativeSessionClient";
import {
  normalizeRequiredArray,
  normalizeVectorPayload,
  readDroppedOptionalString,
  readNumber,
  readString
} from "./BattleAuthoritativeSessionNormalizerPrimitives";
import {
  normalizeAuthoritativeBattleEventState,
  normalizeAuthoritativeBattlePhase,
  normalizeAuthoritativeBattlePickupState,
  normalizeAuthoritativeBattleSlowFieldState
} from "./BattleAuthoritativeSessionStateEntityNormalizer";
import { normalizeAuthoritativeBattlePlayerState } from "./BattleAuthoritativeSessionPlayerStateNormalizer";
import {
  normalizeAuthoritativeBattleProjectileState,
  normalizeAuthoritativeBattleProjectileTerminalState
} from "./BattleAuthoritativeSessionProjectileStateNormalizer";

export function normalizeAuthoritativeBattleState(payload: unknown): AuthoritativeBattleState | null {
  if (!payload || typeof payload !== "object") {
    return null;
  }

  const value = payload as Partial<AuthoritativeBattleState> & Record<string, unknown>;
  const battleId = readString(value.battleId);
  const roomId = readString(value.roomId);
  const mapId = readString(value.mapId);
  const phase = normalizeAuthoritativeBattlePhase(value.phase);
  const serverTime = readNumber(value.serverTime);
  const startedAt = readNumber(value.startedAt);
  const durationMs = readNumber(value.durationMs);
  const elapsedMs = readNumber(value.elapsedMs);
  const endsAt = readNumber(value.endsAt);
  const worldSize = normalizeVectorPayload(value.worldSize);
  const tick = readNumber(value.tick);
  const resultReady = typeof value.resultReady === "boolean" ? value.resultReady : null;
  const replayReady = typeof value.replayReady === "boolean" ? value.replayReady : null;

  if (
    !battleId ||
    !roomId ||
    !mapId ||
    phase === null ||
    serverTime === null ||
    startedAt === null ||
    durationMs === null ||
    elapsedMs === null ||
    endsAt === null ||
    worldSize === null ||
    tick === null ||
    resultReady === null ||
    replayReady === null ||
    !Array.isArray(value.players) ||
    !Array.isArray(value.projectiles) ||
    !Array.isArray(value.projectileTerminals) ||
    !Array.isArray(value.slowFields) ||
    !Array.isArray(value.pickups) ||
    !Array.isArray(value.events)
  ) {
    return null;
  }

  const players = normalizeRequiredArray(value.players, normalizeAuthoritativeBattlePlayerState)?.sort(
    (left, right) => left.seat - right.seat
  );
  const projectiles = normalizeRequiredArray(value.projectiles, normalizeAuthoritativeBattleProjectileState);
  const projectileTerminals = normalizeRequiredArray(
    value.projectileTerminals,
    normalizeAuthoritativeBattleProjectileTerminalState
  );
  const slowFields = normalizeRequiredArray(value.slowFields, normalizeAuthoritativeBattleSlowFieldState);
  const pickups = normalizeRequiredArray(value.pickups, normalizeAuthoritativeBattlePickupState);
  const events = normalizeRequiredArray(value.events, normalizeAuthoritativeBattleEventState);
  const gasZone = Object.prototype.hasOwnProperty.call(value, "gasZone")
    ? normalizeBattleGasZoneStateResponse(value.gasZone)
    : null;
  const extraction = Object.prototype.hasOwnProperty.call(value, "extraction")
    ? normalizeBattleExtractionStateResponse(value.extraction)
    : null;
  const lootCaches = Array.isArray(value.lootCaches)
    ? normalizeRequiredArray(value.lootCaches, normalizeBattleLootCacheStateResponse)
    : [];

  if (
    players === null ||
    typeof players === "undefined" ||
    projectiles === null ||
    projectileTerminals === null ||
    slowFields === null ||
    pickups === null ||
    events === null ||
    gasZone === null && Object.prototype.hasOwnProperty.call(value, "gasZone") ||
    extraction === null && Object.prototype.hasOwnProperty.call(value, "extraction") ||
    lootCaches === null
  ) {
    return null;
  }

  const hasWinnerPlayerId = Object.prototype.hasOwnProperty.call(value, "winnerPlayerId");
  const hasWinnerHeroId = Object.prototype.hasOwnProperty.call(value, "winnerHeroId");
  const winnerPlayerId = readDroppedOptionalString(value.winnerPlayerId);
  const winnerHeroId = readDroppedOptionalString(value.winnerHeroId);

  if ((hasWinnerPlayerId && winnerPlayerId === null) || (hasWinnerHeroId && winnerHeroId === null)) {
    return null;
  }

  return {
    battleId,
    roomId,
    mapId,
    phase,
    serverTime,
    startedAt,
    durationMs: Math.max(1, Math.round(durationMs)),
    elapsedMs: Math.max(0, Math.round(elapsedMs)),
    endsAt,
    worldSize,
    tick,
    resultReady,
    replayReady,
    players,
    projectiles,
    projectileTerminals,
    slowFields,
    pickups,
    gasZone,
    extraction,
    lootCaches,
    events,
    ...(winnerPlayerId ? { winnerPlayerId } : {}),
    ...(winnerHeroId ? { winnerHeroId } : {})
  };
}
