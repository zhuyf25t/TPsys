import type {
  BattleExtractionResponseDto,
  BattleGasZoneResponseDto,
  BattleLootCacheResponseDto
} from "../../../../../objects/battle/microservices/extraction/api/state/BattleExtractionStateResponseApiTypes";

export function normalizeBattleGasZoneStateResponse(payload: unknown): BattleGasZoneResponseDto | null {
  if (!payload || typeof payload !== "object") {
    return null;
  }

  const value = payload as Partial<BattleGasZoneResponseDto> & Record<string, unknown>;
  const phase = value.phase === "waiting" || value.phase === "advancing" || value.phase === "final" ? value.phase : null;
  const center = normalizeVectorPayload(value.center);
  const radius = readNumber(value.radius);
  const nextRadius = readNumber(value.nextRadius);
  const damagePerSecond = readNumber(value.damagePerSecond);
  const stageIndex = readNumber(value.stageIndex);
  const progressMs = readNumber(value.progressMs);
  const startsAtMs = readNumber(value.startsAtMs);
  const endsAtMs = readNumber(value.endsAtMs);

  if (
    phase === null ||
    center === null ||
    radius === null ||
    nextRadius === null ||
    damagePerSecond === null ||
    stageIndex === null ||
    progressMs === null ||
    startsAtMs === null ||
    endsAtMs === null
  ) {
    return null;
  }

  return {
    phase,
    center,
    radius: Math.max(0, radius),
    nextRadius: Math.max(0, nextRadius),
    damagePerSecond: Math.max(0, damagePerSecond),
    stageIndex: Math.max(0, Math.trunc(stageIndex)),
    progressMs: Math.max(0, Math.round(progressMs)),
    startsAtMs: Math.max(0, Math.round(startsAtMs)),
    endsAtMs: Math.max(0, Math.round(endsAtMs))
  };
}

export function normalizeBattleExtractionStateResponse(payload: unknown): BattleExtractionResponseDto | null {
  if (!payload || typeof payload !== "object") {
    return null;
  }

  const value = payload as Partial<BattleExtractionResponseDto> & Record<string, unknown>;
  const zones = Array.isArray(value.zones) ? normalizeRequiredArray(value.zones, normalizeBattleExtractionZone) : null;
  const status = normalizeBattleExtractionStatus(value.status);
  return zones && status ? { zones, status } : null;
}

export function normalizeBattleLootCacheStateResponse(payload: unknown): BattleLootCacheResponseDto | null {
  if (!payload || typeof payload !== "object") {
    return null;
  }

  const value = payload as Partial<BattleLootCacheResponseDto> & Record<string, unknown>;
  const cacheId = readString(value.cacheId);
  const position = normalizeVectorPayload(value.position);
  const radius = readNumber(value.radius);
  const searchDurationMs = readNumber(value.searchDurationMs);
  const scoreValue = readNumber(value.scoreValue);
  const status = normalizeBattleLootCacheStatus(value.status);
  if (!cacheId || position === null || radius === null || searchDurationMs === null || scoreValue === null || status === null) {
    return null;
  }

  return {
    cacheId,
    position,
    radius: Math.max(0, radius),
    searchDurationMs: Math.max(1, Math.round(searchDurationMs)),
    scoreValue: Math.max(0, Math.round(scoreValue)),
    status
  };
}

function normalizeBattleExtractionZone(
  payload: unknown
): BattleExtractionResponseDto["zones"][number] | null {
  if (!payload || typeof payload !== "object") {
    return null;
  }

  const value = payload as BattleExtractionResponseDto["zones"][number] & Record<string, unknown>;
  const zoneId = readString(value.zoneId);
  const position = normalizeVectorPayload(value.position);
  const radius = readNumber(value.radius);
  const availableFromMs = readNumber(value.availableFromMs);
  const channelDurationMs = readNumber(value.channelDurationMs);
  if (!zoneId || position === null || radius === null || availableFromMs === null || channelDurationMs === null) {
    return null;
  }

  return {
    zoneId,
    position,
    radius: Math.max(0, radius),
    availableFromMs: Math.max(0, Math.round(availableFromMs)),
    channelDurationMs: Math.max(1, Math.round(channelDurationMs))
  };
}

function normalizeBattleExtractionStatus(
  payload: unknown
): BattleExtractionResponseDto["status"] | null {
  if (!payload || typeof payload !== "object") {
    return null;
  }

  const value = payload as BattleExtractionResponseDto["status"] & Record<string, unknown>;
  if (value.status === "inactive" || value.status === "available") {
    return { status: value.status };
  }

  const playerId = readString(value.playerId);
  const heroId = readString(value.heroId);
  const zoneId = readString(value.zoneId);
  if (!playerId || !heroId || !zoneId) {
    return null;
  }

  if (value.status === "extracting") {
    const progressMs = readNumber(value.progressMs);
    return progressMs === null
      ? null
      : { status: "extracting", playerId, heroId, zoneId, progressMs: Math.max(0, Math.round(progressMs)) };
  }

  if (value.status === "extracted") {
    const atElapsedMs = readNumber(value.atElapsedMs);
    return atElapsedMs === null
      ? null
      : { status: "extracted", playerId, heroId, zoneId, atElapsedMs: Math.max(0, Math.round(atElapsedMs)) };
  }

  if (value.status === "interrupted") {
    const atElapsedMs = readNumber(value.atElapsedMs);
    const reason = value.reason === "left_zone" || value.reason === "eliminated" ? value.reason : null;
    return atElapsedMs === null || reason === null
      ? null
      : { status: "interrupted", playerId, heroId, zoneId, reason, atElapsedMs: Math.max(0, Math.round(atElapsedMs)) };
  }

  return null;
}

function normalizeBattleLootCacheStatus(
  payload: unknown
): BattleLootCacheResponseDto["status"] | null {
  if (!payload || typeof payload !== "object") {
    return null;
  }

  const value = payload as BattleLootCacheResponseDto["status"] & Record<string, unknown>;
  if (value.status === "available") {
    return { status: "available" };
  }

  const playerId = readString(value.playerId);
  const heroId = readString(value.heroId);
  if (!playerId || !heroId) {
    return null;
  }

  if (value.status === "searching") {
    const progressMs = readNumber(value.progressMs);
    return progressMs === null
      ? null
      : { status: "searching", playerId, heroId, progressMs: Math.max(0, Math.round(progressMs)) };
  }

  if (value.status === "searched") {
    const atElapsedMs = readNumber(value.atElapsedMs);
    return atElapsedMs === null
      ? null
      : { status: "searched", playerId, heroId, atElapsedMs: Math.max(0, Math.round(atElapsedMs)) };
  }

  return null;
}

function normalizeVectorPayload(payload: unknown): { x: number; y: number } | null {
  if (!payload || typeof payload !== "object") {
    return null;
  }

  const value = payload as Record<string, unknown>;
  const x = readNumber(value.x);
  const y = readNumber(value.y);
  if (x === null || y === null) {
    return null;
  }

  return { x, y };
}

function normalizeRequiredArray<T>(
  values: unknown[],
  normalize: (value: unknown) => T | null
): T[] | null {
  const normalized: T[] = [];
  for (const value of values) {
    const item = normalize(value);
    if (item === null) {
      return null;
    }

    normalized.push(item);
  }

  return normalized;
}

function readString(value: unknown): string | null {
  return typeof value === "string" && value.trim() ? value.trim() : null;
}

function readNumber(value: unknown): number | null {
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}
