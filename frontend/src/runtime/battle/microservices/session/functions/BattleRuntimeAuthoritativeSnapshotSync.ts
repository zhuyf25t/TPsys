import type { BattleGameEventState as GameEvent } from "../../../../../objects/battle/microservices/runtime/objects/event/BattleGameEventState";
import type { BattleItemPickupState as ItemPickup, BattleWeaponPickupState as WeaponPickup } from "../../../../../objects/battle/microservices/abilities/objects/pickup/BattlePickupState";
import type { BattleProjectileState as Projectile } from "../../../../../objects/battle/microservices/combat/objects/projectile/BattleProjectileState";
import type { ProjectileKind } from "../../../../../objects/battle/microservices/combat/objects/projectile/ProjectileKind";
import type { BattleStateSkillResponseDto as SkillState } from "../../../../../objects/battle/microservices/session/api/state/BattleStatePlayerResponseApiTypes";
import type { BattleSlowFieldState as SlowField } from "../../../../../objects/battle/microservices/abilities/objects/skill/BattleSlowFieldState";
import type { BattleWeaponState as WeaponState } from "../../../../../objects/battle/microservices/combat/objects/weapon/BattleWeaponState";
import type { BattleRuntimeAuthoritativeFrame } from "./BattleRuntimeAuthoritativeFrameBuilder";

type AuthoritativeProjectileFrame = BattleRuntimeAuthoritativeFrame["projectiles"][number];
type AuthoritativeSlowFieldFrame = BattleRuntimeAuthoritativeFrame["slowFields"][number];
type AuthoritativePickupFrame = BattleRuntimeAuthoritativeFrame["pickups"][number];
type AuthoritativeWeaponPickupFrame = AuthoritativePickupFrame & { kind: "Weapon"; weaponKind: WeaponPickup["weaponKind"] };
type AuthoritativeMedkitPickupFrame = AuthoritativePickupFrame & { kind: "Medkit" };
type AuthoritativeWeaponFrame = BattleRuntimeAuthoritativeFrame["heroes"][number]["weapons"][number];

export interface MergeBattleRuntimeAuthoritativeEventsInput {
  frame: BattleRuntimeAuthoritativeFrame;
  feedEventTtlMs: number;
  maxEvents: number;
}

export function syncBattleRuntimeAuthoritativeSlowFields(
  snapshotFields: SlowField[],
  authoritativeFields: readonly AuthoritativeSlowFieldFrame[]
): void {
  const existingFieldsById = indexSlowFieldsById(snapshotFields);
  let writeIndex = 0;

  for (const authoritativeField of authoritativeFields) {
    let snapshotField = existingFieldsById.get(authoritativeField.fieldId);
    if (snapshotField) {
      existingFieldsById.delete(authoritativeField.fieldId);
      applyAuthoritativeSlowField(snapshotField, authoritativeField);
    } else {
      snapshotField = createAuthoritativeSlowField(authoritativeField);
    }

    snapshotFields[writeIndex] = snapshotField;
    writeIndex += 1;
  }

  snapshotFields.length = writeIndex;
}

export function syncBattleRuntimeAuthoritativeWeaponPickups(
  snapshotPickups: WeaponPickup[],
  authoritativePickups: readonly AuthoritativePickupFrame[]
): void {
  const existingPickupsById = indexWeaponPickupsById(snapshotPickups);
  let writeIndex = 0;

  for (const authoritativePickup of authoritativePickups) {
    if (!isAuthoritativeWeaponPickup(authoritativePickup)) {
      continue;
    }

    let snapshotPickup = existingPickupsById.get(authoritativePickup.pickupId);
    if (snapshotPickup) {
      existingPickupsById.delete(authoritativePickup.pickupId);
      applyAuthoritativeWeaponPickup(snapshotPickup, authoritativePickup);
    } else {
      snapshotPickup = createAuthoritativeWeaponPickup(authoritativePickup);
    }

    snapshotPickups[writeIndex] = snapshotPickup;
    writeIndex += 1;
  }

  snapshotPickups.length = writeIndex;
}

export function syncBattleRuntimeAuthoritativeItemPickups(
  snapshotPickups: ItemPickup[],
  authoritativePickups: readonly AuthoritativePickupFrame[]
): void {
  const existingPickupsById = indexItemPickupsById(snapshotPickups);
  let writeIndex = 0;

  for (const authoritativePickup of authoritativePickups) {
    if (!isAuthoritativeMedkitPickup(authoritativePickup)) {
      continue;
    }

    let snapshotPickup = existingPickupsById.get(authoritativePickup.pickupId);
    if (snapshotPickup) {
      existingPickupsById.delete(authoritativePickup.pickupId);
      applyAuthoritativeItemPickup(snapshotPickup, authoritativePickup);
    } else {
      snapshotPickup = createAuthoritativeItemPickup(authoritativePickup);
    }

    snapshotPickups[writeIndex] = snapshotPickup;
    writeIndex += 1;
  }

  snapshotPickups.length = writeIndex;
}

export function syncBattleRuntimeAuthoritativeProjectiles(
  snapshotProjectiles: Projectile[],
  authoritativeProjectiles: readonly AuthoritativeProjectileFrame[]
): void {
  const existingProjectilesById = indexProjectilesById(snapshotProjectiles);
  let writeIndex = 0;

  for (const authoritativeProjectile of authoritativeProjectiles) {
    let snapshotProjectile = existingProjectilesById.get(authoritativeProjectile.projectileId);
    if (snapshotProjectile) {
      existingProjectilesById.delete(authoritativeProjectile.projectileId);
      applyAuthoritativeProjectile(snapshotProjectile, authoritativeProjectile);
    } else {
      snapshotProjectile = createAuthoritativeProjectile(authoritativeProjectile);
    }

    snapshotProjectiles[writeIndex] = snapshotProjectile;
    writeIndex += 1;
  }

  snapshotProjectiles.length = writeIndex;
}

export function syncBattleRuntimeAuthoritativeWeaponStates(
  snapshotWeapons: WeaponState[],
  authoritativeWeapons: readonly AuthoritativeWeaponFrame[]
): void {
  let writeIndex = 0;

  for (const authoritativeWeapon of authoritativeWeapons) {
    const snapshotWeapon = snapshotWeapons[writeIndex];
    if (snapshotWeapon) {
      applyAuthoritativeWeaponState(snapshotWeapon, authoritativeWeapon);
    } else {
      snapshotWeapons[writeIndex] = createAuthoritativeWeaponState(authoritativeWeapon);
    }
    writeIndex += 1;
  }

  snapshotWeapons.length = writeIndex;
}

export function syncBattleRuntimeAuthoritativeSkillStates(
  snapshotSkills: SkillState[],
  authoritativeSkills: readonly SkillState[]
): void {
  let writeIndex = 0;

  for (const authoritativeSkill of authoritativeSkills) {
    const snapshotSkill = snapshotSkills[writeIndex];
    if (snapshotSkill) {
      snapshotSkill.kind = authoritativeSkill.kind;
      snapshotSkill.cooldownMs = authoritativeSkill.cooldownMs;
      snapshotSkill.activeMs = authoritativeSkill.activeMs;
    } else {
      snapshotSkills[writeIndex] = {
        kind: authoritativeSkill.kind,
        cooldownMs: authoritativeSkill.cooldownMs,
        activeMs: authoritativeSkill.activeMs
      };
    }
    writeIndex += 1;
  }

  snapshotSkills.length = writeIndex;
}

export function mergeBattleRuntimeAuthoritativeEvents({
  frame,
  feedEventTtlMs,
  maxEvents
}: MergeBattleRuntimeAuthoritativeEventsInput): GameEvent[] {
  const authoritativeEvents = frame.events
    .map((event): GameEvent | null => {
      const ageMs = Math.max(0, frame.elapsedMs - event.elapsedMs);
      const ttlMs = feedEventTtlMs - ageMs;
      if (ttlMs <= 0) {
        return null;
      }

      return {
        eventId: event.eventId,
        type: event.type,
        message: event.message,
        ttlMs
      };
    })
    .filter((event): event is GameEvent => event !== null);

  return authoritativeEvents.slice(-Math.max(0, Math.trunc(maxEvents)));
}

export function clampBattleRuntimeAuthoritativeElapsedMs(elapsedMs: number, durationMs: number): number {
  const safeDurationMs = Number.isFinite(durationMs) ? Math.max(1, durationMs) : 1;
  const safeElapsedMs = Number.isFinite(elapsedMs) ? elapsedMs : 0;
  return Math.max(0, Math.min(safeElapsedMs, safeDurationMs));
}

export function clampBattleRuntimeAuthoritativeWeaponIndex(currentWeaponIndex: number, weaponCount: number): number {
  if (weaponCount <= 0 || !Number.isFinite(currentWeaponIndex)) {
    return 0;
  }

  return Math.max(0, Math.min(Math.trunc(currentWeaponIndex), weaponCount - 1));
}

function isAuthoritativeWeaponPickup(
  pickup: BattleRuntimeAuthoritativeFrame["pickups"][number]
): pickup is AuthoritativeWeaponPickupFrame {
  return pickup.kind === "Weapon" && isAuthoritativeWeaponKind(pickup.weaponKind);
}

function isAuthoritativeMedkitPickup(
  pickup: BattleRuntimeAuthoritativeFrame["pickups"][number]
): pickup is AuthoritativeMedkitPickupFrame {
  return pickup.kind === "Medkit";
}

function indexSlowFieldsById(fields: readonly SlowField[]): Map<string, SlowField> {
  const fieldsById = new Map<string, SlowField>();
  for (let index = 0; index < fields.length; index += 1) {
    const field = fields[index];
    fieldsById.set(field.fieldId, field);
  }
  return fieldsById;
}

function createAuthoritativeSlowField(field: AuthoritativeSlowFieldFrame): SlowField {
  const snapshotField: SlowField = {
    fieldId: field.fieldId,
    ownerHeroId: field.ownerHeroId,
    position: { x: 0, y: 0 },
    radius: 0,
    ttlMs: 0,
    durationMs: 0
  };
  applyAuthoritativeSlowField(snapshotField, field);
  return snapshotField;
}

function applyAuthoritativeSlowField(snapshotField: SlowField, field: AuthoritativeSlowFieldFrame): void {
  snapshotField.fieldId = field.fieldId;
  snapshotField.ownerHeroId = field.ownerHeroId;
  snapshotField.position.x = field.position.x;
  snapshotField.position.y = field.position.y;
  snapshotField.radius = Math.max(0, field.radius);
  snapshotField.ttlMs = Math.max(0, Math.round(field.ttlMs));
  snapshotField.durationMs = Math.max(0, Math.round(field.durationMs));
}

function indexWeaponPickupsById(pickups: readonly WeaponPickup[]): Map<string, WeaponPickup> {
  const pickupsById = new Map<string, WeaponPickup>();
  for (let index = 0; index < pickups.length; index += 1) {
    const pickup = pickups[index];
    pickupsById.set(pickup.pickupId, pickup);
  }
  return pickupsById;
}

function createAuthoritativeWeaponPickup(pickup: AuthoritativeWeaponPickupFrame): WeaponPickup {
  const snapshotPickup: WeaponPickup = {
    pickupId: pickup.pickupId,
    weaponKind: pickup.weaponKind,
    position: { x: 0, y: 0 },
    available: pickup.available,
    respawnMs: 0
  };
  applyAuthoritativeWeaponPickup(snapshotPickup, pickup);
  return snapshotPickup;
}

function applyAuthoritativeWeaponPickup(snapshotPickup: WeaponPickup, pickup: AuthoritativeWeaponPickupFrame): void {
  snapshotPickup.pickupId = pickup.pickupId;
  snapshotPickup.weaponKind = pickup.weaponKind;
  snapshotPickup.position.x = pickup.position.x;
  snapshotPickup.position.y = pickup.position.y;
  snapshotPickup.available = pickup.available;
  snapshotPickup.respawnMs = Math.max(0, Math.round(pickup.respawnMs));
}

function indexItemPickupsById(pickups: readonly ItemPickup[]): Map<string, ItemPickup> {
  const pickupsById = new Map<string, ItemPickup>();
  for (let index = 0; index < pickups.length; index += 1) {
    const pickup = pickups[index];
    pickupsById.set(pickup.pickupId, pickup);
  }
  return pickupsById;
}

function createAuthoritativeItemPickup(pickup: AuthoritativeMedkitPickupFrame): ItemPickup {
  const snapshotPickup: ItemPickup = {
    pickupId: pickup.pickupId,
    kind: pickup.kind,
    position: { x: 0, y: 0 },
    available: pickup.available,
    respawnMs: 0
  };
  applyAuthoritativeItemPickup(snapshotPickup, pickup);
  return snapshotPickup;
}

function applyAuthoritativeItemPickup(snapshotPickup: ItemPickup, pickup: AuthoritativeMedkitPickupFrame): void {
  snapshotPickup.pickupId = pickup.pickupId;
  snapshotPickup.kind = pickup.kind;
  snapshotPickup.position.x = pickup.position.x;
  snapshotPickup.position.y = pickup.position.y;
  snapshotPickup.available = pickup.available;
  snapshotPickup.respawnMs = Math.max(0, Math.round(pickup.respawnMs));
}

function indexProjectilesById(projectiles: readonly Projectile[]): Map<string, Projectile> {
  const projectilesById = new Map<string, Projectile>();
  for (let index = 0; index < projectiles.length; index += 1) {
    const projectile = projectiles[index];
    projectilesById.set(projectile.projectileId, projectile);
  }
  return projectilesById;
}

function createAuthoritativeProjectile(projectile: AuthoritativeProjectileFrame): Projectile {
  const snapshotProjectile: Projectile = {
    projectileId: projectile.projectileId,
    kind: "pistol-bullet",
    ownerHeroId: projectile.ownerHeroId,
    team: "FreeForAll",
    position: { x: 0, y: 0 },
    velocity: { x: 0, y: 0 },
    facing: 0,
    radius: 0,
    damage: 0,
    ttlMs: 0,
    maxLifetimeMs: 0,
    splashRadius: 0,
    alive: true,
    hitTargets: []
  };
  applyAuthoritativeProjectile(snapshotProjectile, projectile);
  return snapshotProjectile;
}

function applyAuthoritativeProjectile(snapshotProjectile: Projectile, projectile: AuthoritativeProjectileFrame): void {
  snapshotProjectile.projectileId = projectile.projectileId;
  snapshotProjectile.kind = normalizeAuthoritativeProjectileKind(projectile.kind);
  snapshotProjectile.ownerHeroId = projectile.ownerHeroId;
  snapshotProjectile.team = "FreeForAll";
  snapshotProjectile.position.x = projectile.position.x;
  snapshotProjectile.position.y = projectile.position.y;
  snapshotProjectile.velocity.x = projectile.velocity.x;
  snapshotProjectile.velocity.y = projectile.velocity.y;
  snapshotProjectile.facing = projectile.facing;
  snapshotProjectile.radius = projectile.radius;
  snapshotProjectile.damage = projectile.damage;
  snapshotProjectile.ttlMs = projectile.ttlMs;
  snapshotProjectile.maxLifetimeMs = projectile.maxLifetimeMs;
  snapshotProjectile.splashRadius = projectile.splashRadius;
  snapshotProjectile.alive = true;
  snapshotProjectile.hitTargets.length = 0;
}

function createAuthoritativeWeaponState(weapon: AuthoritativeWeaponFrame): WeaponState {
  const weaponState: WeaponState = {
    weaponKind: weapon.weaponKind,
    ammoInMagazine: 0,
    magazineSize: 0,
    reserveAmmo: null,
    fireCooldownMs: 0,
    reloadRemainingMs: 0,
    heat: 0,
    overheated: false,
    overheatRemainingMs: 0
  };
  applyAuthoritativeWeaponState(weaponState, weapon);
  return weaponState;
}

function applyAuthoritativeWeaponState(weaponState: WeaponState, weapon: AuthoritativeWeaponFrame): void {
  const magazineSize = Math.max(0, Math.round(weapon.magazineSize));
  const ammoInMagazine = Math.max(0, Math.min(Math.round(weapon.ammoInMagazine), magazineSize));
  const reserveAmmo = weapon.reserveAmmo === null ? null : Math.max(0, Math.round(weapon.reserveAmmo));

  weaponState.weaponKind = weapon.weaponKind;
  weaponState.ammoInMagazine = ammoInMagazine;
  weaponState.magazineSize = magazineSize;
  weaponState.reserveAmmo = reserveAmmo;
  weaponState.fireCooldownMs = Math.max(0, Math.round(weapon.fireCooldownMs));
  weaponState.reloadRemainingMs = Math.max(0, Math.round(weapon.reloadRemainingMs));
  weaponState.heat = Math.max(0, weapon.heat);
  weaponState.overheated = weapon.overheated;
  weaponState.overheatRemainingMs = Math.max(0, Math.round(weapon.overheatRemainingMs));
}

function normalizeAuthoritativeProjectileKind(kind: string): ProjectileKind {
  switch (kind) {
    case "rocket":
    case "gatling-bullet":
    case "shotgun-pellet":
    case "pistol-bullet":
      return kind;
    default:
      return "pistol-bullet";
  }
}

function isAuthoritativeWeaponKind(weaponKind: unknown): weaponKind is WeaponPickup["weaponKind"] {
  return weaponKind === "Pistol" ||
    weaponKind === "RocketLauncher" ||
    weaponKind === "Gatling" ||
    weaponKind === "Shotgun";
}
