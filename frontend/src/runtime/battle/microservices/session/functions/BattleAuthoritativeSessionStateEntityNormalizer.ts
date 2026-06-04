import type {
  AuthoritativeBattleEventParticipant,
  AuthoritativeBattleEventState,
  AuthoritativeBattlePhase,
  AuthoritativeBattlePickupKind,
  AuthoritativeBattlePickupState,
  AuthoritativeBattleSlowFieldState
} from "../api/BattleAuthoritativeSessionClient";
import {
  normalizeAuthoritativeBattleWeaponKind,
  normalizeVectorPayload,
  readNumber,
  readString
} from "./BattleAuthoritativeSessionNormalizerPrimitives";

export function normalizeAuthoritativeBattlePickupState(payload: unknown): AuthoritativeBattlePickupState | null {
  if (!payload || typeof payload !== "object") {
    return null;
  }

  const value = payload as Partial<AuthoritativeBattlePickupState> & Record<string, unknown>;
  const pickupId = readString(value.pickupId);
  const kind = normalizeAuthoritativeBattlePickupKind(value.kind);
  const hasWeaponKind = Object.prototype.hasOwnProperty.call(value, "weaponKind");
  const weaponKind = normalizeAuthoritativeBattleWeaponKind(value.weaponKind);
  const position = normalizeVectorPayload(value.position);
  const respawnMs = readNumber(value.respawnMs);

  if (
    !pickupId ||
    kind === null ||
    position === null ||
    typeof value.available !== "boolean" ||
    respawnMs === null ||
    (kind === "Weapon" && weaponKind === null) ||
    (kind !== "Weapon" && hasWeaponKind)
  ) {
    return null;
  }

  if (kind === "Weapon" && weaponKind !== null) {
    return {
      pickupId,
      kind,
      weaponKind,
      position,
      available: value.available,
      respawnMs: Math.max(0, Math.round(respawnMs))
    };
  }

  return {
    pickupId,
    kind,
    position,
    available: value.available,
    respawnMs: Math.max(0, Math.round(respawnMs))
  };
}

function normalizeAuthoritativeBattlePickupKind(payload: unknown): AuthoritativeBattlePickupKind | null {
  return payload === "Medkit" || payload === "Weapon" ? payload : null;
}

export function normalizeAuthoritativeBattlePhase(payload: unknown): AuthoritativeBattlePhase | null {
  return payload === "waiting" || payload === "active" || payload === "finished" ? payload : null;
}

export function normalizeAuthoritativeBattleSlowFieldState(payload: unknown): AuthoritativeBattleSlowFieldState | null {
  if (!payload || typeof payload !== "object") {
    return null;
  }

  const value = payload as Partial<AuthoritativeBattleSlowFieldState> & Record<string, unknown>;
  const fieldId = readString(value.fieldId);
  const ownerPlayerId = readString(value.ownerPlayerId);
  const ownerHeroId = readString(value.ownerHeroId);
  const position = normalizeVectorPayload(value.position);
  const radius = readNumber(value.radius);
  const ttlMs = readNumber(value.ttlMs);
  const durationMs = readNumber(value.durationMs);

  if (
    !fieldId ||
    !ownerPlayerId ||
    !ownerHeroId ||
    position === null ||
    radius === null ||
    ttlMs === null ||
    durationMs === null
  ) {
    return null;
  }

  return {
    fieldId,
    ownerPlayerId,
    ownerHeroId,
    position,
    radius: Math.max(0, radius),
    ttlMs: Math.max(0, Math.round(ttlMs)),
    durationMs: Math.max(0, Math.round(durationMs))
  };
}

export function normalizeAuthoritativeBattleEventState(payload: unknown): AuthoritativeBattleEventState | null {
  if (!payload || typeof payload !== "object") {
    return null;
  }

  const value = payload as Partial<AuthoritativeBattleEventState> & Record<string, unknown>;
  const eventId = readString(value.eventId);
  const type = readString(value.type);
  const kind = readString(value.kind);
  const elapsedMs = readNumber(value.elapsedMs);
  const message = readString(value.message);
  const source = normalizeAuthoritativeBattleEventParticipant(value.source);
  const target = normalizeAuthoritativeBattleEventParticipant(value.target);

  if (
    !eventId ||
    !isAuthoritativeBattleEventKind(type) ||
    !isAuthoritativeBattleEventKind(kind) ||
    type !== kind ||
    elapsedMs === null ||
    !message ||
    source === null ||
    target === null
  ) {
    return null;
  }

  return {
    eventId,
    type,
    kind,
    elapsedMs: Math.max(0, Math.round(elapsedMs)),
    message,
    source,
    target
  };
}

function isAuthoritativeBattleEventKind(kind: string | null): kind is AuthoritativeBattleEventState["kind"] {
  return kind === "kill" || kind === "heal" || kind === "pickup" || kind === "respawn";
}

function normalizeAuthoritativeBattleEventParticipant(payload: unknown): AuthoritativeBattleEventParticipant | null {
  if (!payload || typeof payload !== "object") {
    return null;
  }

  const value = payload as Partial<AuthoritativeBattleEventParticipant> & Record<string, unknown>;
  const playerId = readString(value.playerId);
  const heroId = readString(value.heroId);
  const displayName = readString(value.displayName);

  if (!playerId || !heroId || !displayName) {
    return null;
  }

  return { playerId, heroId, displayName };
}
