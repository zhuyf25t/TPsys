import type {
  AuthoritativeBattleSkillKind,
  AuthoritativeBattleVector,
  AuthoritativeBattleWeaponKind
} from "../api/BattleAuthoritativeSessionClient";

export function normalizeAuthoritativeBattleSkillKind(payload: unknown): AuthoritativeBattleSkillKind | null {
  return payload === "Blink" || payload === "Dash" || payload === "Freeze" || payload === "Critical" ? payload : null;
}

export function normalizeAuthoritativeBattleWeaponKind(payload: unknown): AuthoritativeBattleWeaponKind | null {
  return payload === "Pistol" ||
    payload === "RocketLauncher" ||
    payload === "Gatling" ||
    payload === "Shotgun"
    ? payload
    : null;
}

export function normalizeVectorPayload(payload: unknown): AuthoritativeBattleVector | null {
  if (!payload || typeof payload !== "object") {
    return null;
  }

  const value = payload as Partial<AuthoritativeBattleVector> & Record<string, unknown>;
  const x = readNumber(value.x);
  const y = readNumber(value.y);
  if (x === null || y === null) {
    return null;
  }

  return { x, y };
}

export function normalizeVector(vector: AuthoritativeBattleVector): AuthoritativeBattleVector {
  const x = Number.isFinite(vector.x) ? vector.x : 0;
  const y = Number.isFinite(vector.y) ? vector.y : 0;
  return { x, y };
}

export function normalizeWorldPoint(point: AuthoritativeBattleVector): AuthoritativeBattleVector {
  return normalizeVector(point);
}

export function normalizeAim(aim: AuthoritativeBattleVector): AuthoritativeBattleVector {
  const normalized = normalizeVector(aim);
  const length = Math.hypot(normalized.x, normalized.y);
  if (length <= 0.0001) {
    return { x: 1, y: 0 };
  }

  return {
    x: normalized.x / length,
    y: normalized.y / length
  };
}

export function normalizeSwitchDirection(direction: number): -1 | 0 | 1 {
  if (direction < 0) {
    return -1;
  }

  if (direction > 0) {
    return 1;
  }

  return 0;
}

export function normalizeSwitchWeaponIndex(index: number | null): number | null {
  return index === null || !Number.isFinite(index) ? null : Math.max(0, Math.trunc(index));
}

export function normalizeRequiredArray<T>(
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

export function readString(value: unknown): string | null {
  return typeof value === "string" && value.trim() ? value.trim() : null;
}

export function readDroppedOptionalString(value: unknown): string | null {
  return typeof value === "string" && value.trim() ? value.trim() : null;
}

export function readNullableStringField(value: unknown): string | null | undefined {
  if (value === null) {
    return null;
  }

  return typeof value === "string" && value.trim() ? value.trim() : undefined;
}

export function readNumber(value: unknown): number | null {
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

export function readOptionalNumber(value: unknown): number | null {
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

export function readNullableNumberField(value: unknown): number | null | undefined {
  if (value === null) {
    return null;
  }

  return typeof value === "number" && Number.isFinite(value) ? value : undefined;
}

export function normalizeNullableNonNegativeInteger(value: number | null): number | null {
  return value === null ? null : Math.max(0, Math.round(value));
}
