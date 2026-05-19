import type {
  GameEvent,
  GameSnapshot,
  Hero,
  ItemPickup,
  Projectile,
  ProjectileKind,
  SkillState,
  SlowField,
  Vec2,
  WeaponPickup,
  WeaponState
} from "../../objects/types";
import { FEED_EVENT_TTL_MS } from "../constants";
import type { BattleRuntimeAuthoritativeFrame } from "./authoritativeBattleStateBridge";
import {
  resolveAuthoritativeLocalHeroReplayTarget,
  type AuthoritativeLocalHeroReplayProjection,
  type AuthoritativeLocalHeroReplayCommandEntry,
  type AuthoritativeLocalHeroPendingBlinkPrediction,
  type AuthoritativeLocalHeroPendingDashPrediction
} from "./authoritativeLocalHeroReplay";
import type { MotionObstacleBounds } from "../../runtime/local/movement/motionController";
import { findDashSkillState } from "./authoritativeLocalHeroDashPrediction";
import { findBlinkSkillState } from "./authoritativeLocalHeroBlinkPrediction";

const MAX_AUTHORITATIVE_EVENTS = 12;

type AuthoritativeProjectileFrame = BattleRuntimeAuthoritativeFrame["projectiles"][number];
type AuthoritativeSlowFieldFrame = BattleRuntimeAuthoritativeFrame["slowFields"][number];
type AuthoritativePickupFrame = BattleRuntimeAuthoritativeFrame["pickups"][number];
type AuthoritativeWeaponPickupFrame = AuthoritativePickupFrame & { kind: "Weapon"; weaponKind: WeaponPickup["weaponKind"] };
type AuthoritativeMedkitPickupFrame = AuthoritativePickupFrame & { kind: "Medkit" };
type AuthoritativeWeaponFrame = BattleRuntimeAuthoritativeFrame["heroes"][number]["weapons"][number];

export interface LocalPlayerAuthoritativeCorrectionTarget {
  authoritativePosition: Vec2;
  localMovementActive?: boolean;
  forceHardSnap?: boolean;
}

export interface ApplyAuthoritativeFrameToSnapshotInput {
  snapshot: GameSnapshot;
  frame: BattleRuntimeAuthoritativeFrame;
  localPlayerMovementActive?: boolean;
  localPlayerReplay?: LocalPlayerAuthoritativeReplayContext;
  applyLocalPlayerAuthoritativeCorrection?(target: LocalPlayerAuthoritativeCorrectionTarget): void;
}

export interface LocalPlayerAuthoritativeReplayContext {
  commandHistory: readonly AuthoritativeLocalHeroReplayCommandEntry[];
  lastClientCommandSeq: number;
  nowMs: number;
  obstacleBounds: readonly MotionObstacleBounds[];
  pendingBlinkPrediction?: AuthoritativeLocalHeroPendingBlinkPrediction | null;
  pendingDashPrediction?: AuthoritativeLocalHeroPendingDashPrediction | null;
  blinkCooldownMsOverride?: number;
  dashCooldownMsOverride?: number;
}

/** 中文名：应用authoritative帧转为快照（applyAuthoritativeFrameToSnapshot）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function applyAuthoritativeFrameToSnapshot({
  snapshot,
  frame,
  localPlayerMovementActive,
  localPlayerReplay,
  applyLocalPlayerAuthoritativeCorrection
}: ApplyAuthoritativeFrameToSnapshotInput): void {
  snapshot.elapsedMs = clampAuthoritativeElapsedMs(frame.elapsedMs, frame.durationMs);
  snapshot.worldSize = { x: frame.worldSize.x, y: frame.worldSize.y };
  syncAuthoritativeSlowFields(snapshot.slowFields, frame.slowFields);
  syncAuthoritativeWeaponPickups(snapshot.weaponPickups, frame.pickups);
  syncAuthoritativeItemPickups(snapshot.itemPickups, frame.pickups);
  syncAuthoritativeProjectiles(snapshot.projectiles, frame.projectiles);
  snapshot.events = mergeAuthoritativeEvents(frame);

  frame.heroes.forEach((authoritativeHero) => {
    const hero = snapshot.heroes.find((entry) => entry.heroId === authoritativeHero.heroId);
    if (!hero) {
      return;
    }

    const previousPosition = hero.position;
    const previousAlive = hero.alive;
    hero.displayName = authoritativeHero.displayName;
    hero.facing = authoritativeHero.facing;
    syncAuthoritativeWeaponStates(hero.weapons, authoritativeHero.weapons);
    hero.currentWeaponIndex = clampAuthoritativeWeaponIndex(
      authoritativeHero.currentWeaponIndex,
      hero.weapons.length
    );
    syncAuthoritativeSkillStates(hero.skills, authoritativeHero.skills);
    hero.preparedSkill = null;
    hero.alive = authoritativeHero.alive;
    hero.respawnMs = Math.max(0, Math.round(authoritativeHero.respawnMs));
    hero.lifeState = authoritativeHero.alive ? "alive" : hero.respawnMs > 0 ? "respawning" : "dead";
    hero.maxHp = Math.max(1, authoritativeHero.maxHp);
    hero.hp = Math.max(0, Math.min(authoritativeHero.hp, hero.maxHp));
    hero.score = Math.max(0, authoritativeHero.score);
    hero.maxStamina = Math.max(1, authoritativeHero.maxStamina);
    hero.stamina = Math.max(0, Math.min(authoritativeHero.stamina, hero.maxStamina));
    hero.jumpCooldownMs = 0;
    hero.eliminatedAtMs = authoritativeHero.alive ? null : authoritativeHero.eliminatedAtMs;

    const authoritativePosition = { x: authoritativeHero.position.x, y: authoritativeHero.position.y };
    hero.position = authoritativePosition;

    if (hero.heroId === snapshot.playerHeroId) {
      const forceHardSnap = previousAlive !== authoritativeHero.alive || !authoritativeHero.alive;
      const replayProjection = forceHardSnap
        ? createAuthoritativeOnlyReplayProjection(authoritativePosition, hero.stamina)
        : resolveLocalPlayerReplayProjection({
            authoritativePosition,
            snapshot,
            heroRadius: hero.radius,
            authoritativeStamina: hero.stamina,
            authoritativeMaxStamina: hero.maxStamina,
            localHero: hero,
            authoritativeBlinkSkill: findBlinkSkillState(hero.skills),
            authoritativeDashSkill: findDashSkillState(hero.skills),
            blinkCooldownMsOverride: localPlayerReplay?.blinkCooldownMsOverride,
            dashCooldownMsOverride: localPlayerReplay?.dashCooldownMsOverride,
            localPlayerReplay
          });
      if (replayProjection.hasPredictedStamina) {
        hero.stamina = Math.max(0, Math.min(replayProjection.stamina, hero.maxStamina));
      }

      applyLocalPlayerAuthoritativeCorrection?.({
        authoritativePosition: replayProjection.position,
        localMovementActive: localPlayerMovementActive,
        forceHardSnap
      });
    }

    hero.velocity = {
      x: authoritativeHero.alive ? hero.position.x - previousPosition.x : 0,
      y: authoritativeHero.alive ? hero.position.y - previousPosition.y : 0
    };
  });
}

function resolveLocalPlayerReplayProjection({
  authoritativePosition,
  snapshot,
  heroRadius,
  authoritativeStamina,
  authoritativeMaxStamina,
  localHero,
  authoritativeBlinkSkill,
  authoritativeDashSkill,
  blinkCooldownMsOverride,
  dashCooldownMsOverride,
  localPlayerReplay
}: {
  authoritativePosition: Vec2;
  snapshot: GameSnapshot;
  heroRadius: number;
  authoritativeStamina: number;
  authoritativeMaxStamina: number;
  localHero: Hero;
  authoritativeBlinkSkill: SkillState | null;
  authoritativeDashSkill: SkillState | null;
  blinkCooldownMsOverride?: number;
  dashCooldownMsOverride?: number;
  localPlayerReplay?: LocalPlayerAuthoritativeReplayContext;
}): AuthoritativeLocalHeroReplayProjection {
  if (!localPlayerReplay) {
    return createAuthoritativeOnlyReplayProjection(authoritativePosition, authoritativeStamina);
  }

  return resolveAuthoritativeLocalHeroReplayTarget({
    authoritativePosition,
    worldSize: snapshot.worldSize,
    obstacleBounds: localPlayerReplay.obstacleBounds,
    radius: heroRadius,
    player: localHero,
    stamina: authoritativeStamina,
    maxStamina: authoritativeMaxStamina,
    blinkCooldownMs: resolveReplayBlinkCooldownMs(authoritativeBlinkSkill, blinkCooldownMsOverride),
    blinkActiveMs: authoritativeBlinkSkill?.activeMs,
    dashCooldownMs: resolveReplayDashCooldownMs(authoritativeDashSkill, dashCooldownMsOverride),
    dashActiveMs: authoritativeDashSkill?.activeMs,
    slowFields: snapshot.slowFields,
    commandHistory: localPlayerReplay.commandHistory,
    lastClientCommandSeq: localPlayerReplay.lastClientCommandSeq,
    nowMs: localPlayerReplay.nowMs,
    pendingBlinkPrediction: localPlayerReplay.pendingBlinkPrediction ?? null,
    pendingDashPrediction: localPlayerReplay.pendingDashPrediction ?? null
  });
}

function resolveReplayBlinkCooldownMs(
  authoritativeBlinkSkill: SkillState | null,
  blinkCooldownMsOverride: number | undefined
): number | undefined {
  if (!Number.isFinite(blinkCooldownMsOverride)) {
    return authoritativeBlinkSkill?.cooldownMs;
  }

  return Math.max(authoritativeBlinkSkill?.cooldownMs ?? 0, blinkCooldownMsOverride ?? 0);
}

function resolveReplayDashCooldownMs(
  authoritativeDashSkill: SkillState | null,
  dashCooldownMsOverride: number | undefined
): number | undefined {
  if (!Number.isFinite(dashCooldownMsOverride)) {
    return authoritativeDashSkill?.cooldownMs;
  }

  return Math.max(authoritativeDashSkill?.cooldownMs ?? 0, dashCooldownMsOverride ?? 0);
}

function createAuthoritativeOnlyReplayProjection(
  authoritativePosition: Vec2,
  authoritativeStamina: number
): AuthoritativeLocalHeroReplayProjection {
  return {
    position: authoritativePosition,
    stamina: authoritativeStamina,
    hasPredictedStamina: false
  };
}

function isAuthoritativeWeaponPickup(
  pickup: BattleRuntimeAuthoritativeFrame["pickups"][number]
): pickup is AuthoritativeWeaponPickupFrame {
  return pickup.kind === "Weapon" && isAuthoritativeWeaponKind(pickup.weaponKind);
}

function isAuthoritativeMedkitPickup(
  pickup: BattleRuntimeAuthoritativeFrame["pickups"][number]
): pickup is BattleRuntimeAuthoritativeFrame["pickups"][number] & { kind: "Medkit" } {
  return pickup.kind === "Medkit";
}

function syncAuthoritativeSlowFields(
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

function syncAuthoritativeWeaponPickups(
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

function indexWeaponPickupsById(pickups: readonly WeaponPickup[]): Map<string, WeaponPickup> {
  const pickupsById = new Map<string, WeaponPickup>();
  for (let index = 0; index < pickups.length; index += 1) {
    const pickup = pickups[index];
    pickupsById.set(pickup.weaponId, pickup);
  }
  return pickupsById;
}

function createAuthoritativeWeaponPickup(pickup: AuthoritativeWeaponPickupFrame): WeaponPickup {
  const snapshotPickup: WeaponPickup = {
    weaponId: pickup.pickupId,
    weaponKind: pickup.weaponKind,
    position: { x: 0, y: 0 },
    available: pickup.available,
    respawnMs: 0
  };
  applyAuthoritativeWeaponPickup(snapshotPickup, pickup);
  return snapshotPickup;
}

function applyAuthoritativeWeaponPickup(snapshotPickup: WeaponPickup, pickup: AuthoritativeWeaponPickupFrame): void {
  snapshotPickup.weaponId = pickup.pickupId;
  snapshotPickup.weaponKind = pickup.weaponKind;
  snapshotPickup.position.x = pickup.position.x;
  snapshotPickup.position.y = pickup.position.y;
  snapshotPickup.available = pickup.available;
  snapshotPickup.respawnMs = Math.max(0, Math.round(pickup.respawnMs));
}

function syncAuthoritativeItemPickups(
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

function syncAuthoritativeProjectiles(
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

function syncAuthoritativeWeaponStates(
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

function createAuthoritativeWeaponState(weapon: AuthoritativeWeaponFrame): WeaponState {
  const weaponState: WeaponState = {
    weaponKind: weapon.weaponKind,
    ammoInMagazine: 0,
    magazineSize: 0,
    reserveAmmo: null,
    cooldownRemaining: 0,
    reloadRemaining: 0,
    heat: 0,
    overheated: false,
    overheatRemaining: 0
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
  weaponState.cooldownRemaining = Math.max(0, Math.round(weapon.fireCooldownMs));
  weaponState.reloadRemaining = Math.max(0, Math.round(weapon.reloadRemainingMs));
  weaponState.heat = Math.max(0, weapon.heat);
  weaponState.overheated = weapon.overheated;
  weaponState.overheatRemaining = Math.max(0, Math.round(weapon.overheatRemainingMs));
}

function clampAuthoritativeWeaponIndex(currentWeaponIndex: number, weaponCount: number): number {
  if (weaponCount <= 0 || !Number.isFinite(currentWeaponIndex)) {
    return 0;
  }

  return Math.max(0, Math.min(Math.trunc(currentWeaponIndex), weaponCount - 1));
}

function syncAuthoritativeSkillStates(
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

function mergeAuthoritativeEvents(frame: BattleRuntimeAuthoritativeFrame): GameEvent[] {
  const authoritativeEvents = frame.events
    .map((event): GameEvent | null => {
      const ageMs = Math.max(0, frame.elapsedMs - event.elapsedMs);
      const ttlMs = FEED_EVENT_TTL_MS - ageMs;
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

  return authoritativeEvents.slice(-MAX_AUTHORITATIVE_EVENTS);
}

function clampAuthoritativeElapsedMs(elapsedMs: number, durationMs: number): number {
  const safeDurationMs = Number.isFinite(durationMs) ? Math.max(1, durationMs) : 1;
  const safeElapsedMs = Number.isFinite(elapsedMs) ? elapsedMs : 0;
  return Math.max(0, Math.min(safeElapsedMs, safeDurationMs));
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
