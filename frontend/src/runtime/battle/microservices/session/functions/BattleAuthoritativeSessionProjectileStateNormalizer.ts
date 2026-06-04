import type {
  AuthoritativeBattleProjectileKind,
  AuthoritativeBattleProjectileState,
  AuthoritativeBattleProjectileTerminalReason,
  AuthoritativeBattleProjectileTerminalState
} from "../api/BattleAuthoritativeSessionClient";
import {
  normalizeNullableNonNegativeInteger,
  normalizeVectorPayload,
  readNullableNumberField,
  readNullableStringField,
  readNumber,
  readString
} from "./BattleAuthoritativeSessionNormalizerPrimitives";
export function normalizeAuthoritativeBattleProjectileState(payload: unknown): AuthoritativeBattleProjectileState | null {
  if (!payload || typeof payload !== "object") {
    return null;
  }

  const value = payload as Partial<AuthoritativeBattleProjectileState> & Record<string, unknown>;
  const projectileId = readString(value.projectileId);
  const ownerHeroId = readString(value.ownerHeroId);
  const kind = normalizeAuthoritativeBattleProjectileKind(value.kind);
  const position = normalizeVectorPayload(value.position);
  const velocity = normalizeVectorPayload(value.velocity);
  const facing = readNumber(value.facing);
  const radius = readNumber(value.radius);
  const damage = readNumber(value.damage);
  const ttlMs = readNumber(value.ttlMs);
  const maxLifetimeMs = readNumber(value.maxLifetimeMs);
  const splashRadius = readNumber(value.splashRadius);

  if (
    !projectileId ||
    !ownerHeroId ||
    kind === null ||
    position === null ||
    velocity === null ||
    facing === null ||
    radius === null ||
    damage === null ||
    ttlMs === null ||
    maxLifetimeMs === null ||
    splashRadius === null
  ) {
    return null;
  }

  return {
    projectileId,
    ownerHeroId,
    kind,
    position,
    velocity,
    facing,
    radius: Math.max(0, radius),
    damage: Math.max(0, Math.round(damage)),
    ttlMs: Math.max(0, Math.round(ttlMs)),
    maxLifetimeMs: Math.max(0, Math.round(maxLifetimeMs)),
    splashRadius: Math.max(0, splashRadius)
  };
}

export function normalizeAuthoritativeBattleProjectileTerminalState(
  payload: unknown
): AuthoritativeBattleProjectileTerminalState | null {
  if (!payload || typeof payload !== "object") {
    return null;
  }

  const value = payload as Partial<AuthoritativeBattleProjectileTerminalState> & Record<string, unknown>;
  const projectileId = readString(value.projectileId);
  const kind = normalizeAuthoritativeBattleProjectileKind(value.kind);
  const ownerPlayerId = readString(value.ownerPlayerId);
  const ownerHeroId = readString(value.ownerHeroId);
  const reason = normalizeAuthoritativeBattleProjectileTerminalReason(value.reason);
  const start = normalizeVectorPayload(value.start);
  const end = normalizeVectorPayload(value.end);
  const terminalPosition = normalizeVectorPayload(value.terminalPosition);
  const ttlBefore = readNumber(value.ttlBefore);
  const ttlAfter = readNumber(value.ttlAfter);
  const elapsedMs = readNumber(value.elapsedMs);
  const hasTargetPlayerId = Object.prototype.hasOwnProperty.call(value, "targetPlayerId");
  const hasTargetHeroId = Object.prototype.hasOwnProperty.call(value, "targetHeroId");
  const hasHpBefore = Object.prototype.hasOwnProperty.call(value, "hpBefore");
  const hasHpAfter = Object.prototype.hasOwnProperty.call(value, "hpAfter");
  const hasDamage = Object.prototype.hasOwnProperty.call(value, "damage");
  const targetPlayerId = readNullableStringField(value.targetPlayerId);
  const targetHeroId = readNullableStringField(value.targetHeroId);
  const hpBefore = readNullableNumberField(value.hpBefore);
  const hpAfter = readNullableNumberField(value.hpAfter);
  const damageValue = readNullableNumberField(value.damage);

  if (
    !projectileId ||
    kind === null ||
    !ownerPlayerId ||
    !ownerHeroId ||
    reason === null ||
    start === null ||
    end === null ||
    terminalPosition === null ||
    ttlBefore === null ||
    ttlAfter === null ||
    elapsedMs === null ||
    !hasTargetPlayerId ||
    !hasTargetHeroId ||
    !hasHpBefore ||
    !hasHpAfter ||
    !hasDamage ||
    typeof targetPlayerId === "undefined" ||
    typeof targetHeroId === "undefined" ||
    typeof hpBefore === "undefined" ||
    typeof hpAfter === "undefined" ||
    typeof damageValue === "undefined"
  ) {
    return null;
  }

  return {
    projectileId,
    kind,
    ownerPlayerId,
    ownerHeroId,
    reason,
    start,
    end,
    terminalPosition,
    ttlBefore: Math.max(0, Math.round(ttlBefore)),
    ttlAfter: Math.max(0, Math.round(ttlAfter)),
    elapsedMs: Math.max(0, Math.round(elapsedMs)),
    targetPlayerId,
    targetHeroId,
    hpBefore: normalizeNullableNonNegativeInteger(hpBefore),
    hpAfter: normalizeNullableNonNegativeInteger(hpAfter),
    damage: normalizeNullableNonNegativeInteger(damageValue)
  };
}

function normalizeAuthoritativeBattleProjectileKind(payload: unknown): AuthoritativeBattleProjectileKind | null {
  return payload === "pistol-bullet" ||
    payload === "rocket" ||
    payload === "gatling-bullet" ||
    payload === "shotgun-pellet"
    ? payload
    : null;
}

function normalizeAuthoritativeBattleProjectileTerminalReason(
  payload: unknown
): AuthoritativeBattleProjectileTerminalReason | null {
  return payload === "hit" || payload === "ttl" || payload === "obstacle" || payload === "world" ? payload : null;
}
