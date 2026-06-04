import type {
  AuthoritativeBattlePlayerState,
  AuthoritativeBattleSkillState,
  AuthoritativeBattleWeaponState
} from "../api/BattleAuthoritativeSessionClient";
import {
  normalizeAuthoritativeBattleSkillKind,
  normalizeAuthoritativeBattleWeaponKind,
  normalizeRequiredArray,
  normalizeVectorPayload,
  readNullableNumberField,
  readNumber,
  readOptionalNumber,
  readString
} from "./BattleAuthoritativeSessionNormalizerPrimitives";
export function normalizeAuthoritativeBattlePlayerState(payload: unknown): AuthoritativeBattlePlayerState | null {
  if (!payload || typeof payload !== "object") {
    return null;
  }

  const value = payload as Partial<AuthoritativeBattlePlayerState> & Record<string, unknown>;
  const playerId = readString(value.playerId);
  const heroId = readString(value.heroId);
  const handle = readString(value.handle);
  const displayName = readString(value.displayName);
  const seat = readNumber(value.seat);
  const position = normalizeVectorPayload(value.position);
  const aim = normalizeVectorPayload(value.aim);
  const facing = readNumber(value.facing);
  const movement = normalizeVectorPayload(value.movement);
  const sprint = typeof value.sprint === "boolean" ? value.sprint : null;
  const currentWeaponIndex = readNumber(value.currentWeaponIndex);
  const lastClientCommandSeq = readNumber(value.lastClientCommandSeq);
  const currentWeaponKind = normalizeAuthoritativeBattleWeaponKind(value.currentWeaponKind);
  const ammoInMagazine = readNumber(value.ammoInMagazine);
  const magazineSize = readNumber(value.magazineSize);
  const hasReserveAmmo = Object.prototype.hasOwnProperty.call(value, "reserveAmmo");
  const reserveAmmo = readOptionalNumber(value.reserveAmmo);
  const fireCooldownMs = readNumber(value.fireCooldownMs);
  const reloadRemainingMs = readNumber(value.reloadRemainingMs);
  const heat = readNumber(value.heat);
  const overheated = typeof value.overheated === "boolean" ? value.overheated : null;
  const overheatRemainingMs = readNumber(value.overheatRemainingMs);
  const weaponsPayload = Array.isArray(value.weapons) ? value.weapons : null;
  const skillsPayload = Array.isArray(value.skills) ? value.skills : null;
  const weapons = weaponsPayload === null ? null : normalizeRequiredArray(weaponsPayload, normalizeAuthoritativeBattleWeaponState);
  const hp = readNumber(value.hp);
  const maxHp = readNumber(value.maxHp);
  const stamina = readNumber(value.stamina);
  const maxStamina = readNumber(value.maxStamina);
  const score = readNumber(value.score);
  const kills = readNumber(value.kills);
  const skills = skillsPayload === null ? null : normalizeRequiredArray(skillsPayload, normalizeAuthoritativeBattleSkillState);
  const hasEliminatedAtMs = Object.prototype.hasOwnProperty.call(value, "eliminatedAtMs");
  const eliminatedAtMs = readNullableNumberField(value.eliminatedAtMs);
  const respawnMs = readNumber(value.respawnMs);

  if (
    !playerId ||
    !heroId ||
    !handle ||
    !displayName ||
    seat === null ||
    position === null ||
    aim === null ||
    facing === null ||
    movement === null ||
    sprint === null ||
    lastClientCommandSeq === null ||
    currentWeaponIndex === null ||
    currentWeaponKind === null ||
    ammoInMagazine === null ||
    magazineSize === null ||
    !hasReserveAmmo ||
    fireCooldownMs === null ||
    reloadRemainingMs === null ||
    heat === null ||
    overheated === null ||
    overheatRemainingMs === null ||
    hp === null ||
    maxHp === null ||
    stamina === null ||
    maxStamina === null ||
    score === null ||
    kills === null ||
    weaponsPayload === null ||
    skillsPayload === null ||
    weapons === null ||
    skills === null ||
    (hasEliminatedAtMs && typeof eliminatedAtMs === "undefined") ||
    respawnMs === null ||
    typeof value.isBot !== "boolean" ||
    typeof value.primaryHeld !== "boolean" ||
    typeof value.reloadPressed !== "boolean" ||
    typeof value.alive !== "boolean"
  ) {
    return null;
  }

  const safeMaxStamina = Math.max(1, maxStamina);

  return {
    playerId,
    heroId,
    handle,
    displayName,
    seat: Math.max(0, Math.trunc(seat)),
    isBot: value.isBot,
    position,
    aim,
    facing,
    movement,
    sprint,
    primaryHeld: value.primaryHeld,
    reloadPressed: value.reloadPressed,
    lastClientCommandSeq: Math.max(0, Math.trunc(lastClientCommandSeq)),
    currentWeaponIndex: Math.max(0, Math.trunc(currentWeaponIndex)),
    weapons,
    currentWeaponKind,
    ammoInMagazine: Math.max(0, Math.round(ammoInMagazine)),
    magazineSize: Math.max(0, Math.round(magazineSize)),
    reserveAmmo: reserveAmmo === null ? null : Math.max(0, Math.round(reserveAmmo)),
    fireCooldownMs: Math.max(0, Math.round(fireCooldownMs)),
    reloadRemainingMs: Math.max(0, Math.round(reloadRemainingMs)),
    heat: Math.max(0, heat),
    overheated,
    overheatRemainingMs: Math.max(0, Math.round(overheatRemainingMs)),
    hp: Math.max(0, hp),
    maxHp: Math.max(1, maxHp),
    stamina: Math.max(0, Math.min(stamina, safeMaxStamina)),
    maxStamina: safeMaxStamina,
    score: Math.max(0, Math.round(score)),
    kills: Math.max(0, Math.round(kills)),
    skills,
    alive: value.alive,
    eliminatedAtMs: typeof eliminatedAtMs === "undefined" || eliminatedAtMs === null
      ? null
      : Math.max(0, Math.round(eliminatedAtMs)),
    respawnMs: Math.max(0, Math.round(respawnMs))
  };
}

function normalizeAuthoritativeBattleWeaponState(payload: unknown): AuthoritativeBattleWeaponState | null {
  if (!payload || typeof payload !== "object") {
    return null;
  }

  const value = payload as Partial<AuthoritativeBattleWeaponState> & Record<string, unknown>;
  const weaponKind = normalizeAuthoritativeBattleWeaponKind(value.weaponKind);
  const ammoInMagazine = readNumber(value.ammoInMagazine);
  const magazineSize = readNumber(value.magazineSize);
  const hasReserveAmmo = Object.prototype.hasOwnProperty.call(value, "reserveAmmo");
  const reserveAmmo = readOptionalNumber(value.reserveAmmo);
  const fireCooldownMs = readNumber(value.fireCooldownMs);
  const reloadRemainingMs = readNumber(value.reloadRemainingMs);
  const heat = readNumber(value.heat);
  const overheated = typeof value.overheated === "boolean" ? value.overheated : null;
  const overheatRemainingMs = readNumber(value.overheatRemainingMs);

  if (
    weaponKind === null ||
    ammoInMagazine === null ||
    magazineSize === null ||
    !hasReserveAmmo ||
    fireCooldownMs === null ||
    reloadRemainingMs === null ||
    heat === null ||
    overheated === null ||
    overheatRemainingMs === null
  ) {
    return null;
  }

  return {
    weaponKind,
    ammoInMagazine: Math.max(0, Math.round(ammoInMagazine)),
    magazineSize: Math.max(0, Math.round(magazineSize)),
    reserveAmmo: reserveAmmo === null ? null : Math.max(0, Math.round(reserveAmmo)),
    fireCooldownMs: Math.max(0, Math.round(fireCooldownMs)),
    reloadRemainingMs: Math.max(0, Math.round(reloadRemainingMs)),
    heat: Math.max(0, heat),
    overheated,
    overheatRemainingMs: Math.max(0, Math.round(overheatRemainingMs))
  };
}

function normalizeAuthoritativeBattleSkillState(payload: unknown): AuthoritativeBattleSkillState | null {
  if (!payload || typeof payload !== "object") {
    return null;
  }

  const value = payload as Partial<AuthoritativeBattleSkillState> & Record<string, unknown>;
  const cooldownMs = readNumber(value.cooldownMs);
  const activeMs = readNumber(value.activeMs);
  const kind = normalizeAuthoritativeBattleSkillKind(value.kind);
  if (kind === null || cooldownMs === null || activeMs === null) {
    return null;
  }

  return {
    kind,
    cooldownMs: Math.max(0, Math.round(cooldownMs)),
    activeMs: Math.max(0, Math.round(activeMs))
  };
}


