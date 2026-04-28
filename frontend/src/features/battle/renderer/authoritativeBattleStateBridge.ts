import type { GameSnapshot, Hero, SkillState, Vec2, WeaponKind } from "../../../domain/types";
import type { AuthoritativeBattleState } from "../adapters/authoritativeBattleClient";

export interface BattleRuntimeAuthoritativeHeroFrame {
  heroId: string;
  displayName: string;
  position: Vec2;
  facing: number;
  currentWeaponIndex: number;
  weapons: BattleRuntimeAuthoritativeWeaponFrame[];
  currentWeaponKind: WeaponKind;
  ammoInMagazine: number;
  magazineSize: number;
  reserveAmmo: number | null;
  fireCooldownMs: number;
  reloadRemainingMs: number;
  hp: number;
  maxHp: number;
  stamina: number;
  maxStamina: number;
  score: number;
  kills: number;
  skills: SkillState[];
  alive: boolean;
  eliminatedAtMs: number | null;
  respawnMs: number;
}

export interface BattleRuntimeAuthoritativeWeaponFrame {
  weaponKind: WeaponKind;
  ammoInMagazine: number;
  magazineSize: number;
  reserveAmmo: number | null;
  fireCooldownMs: number;
  reloadRemainingMs: number;
}

export interface BattleRuntimeAuthoritativeProjectileFrame {
  projectileId: string;
  ownerHeroId: string;
  kind: string;
  position: Vec2;
  velocity: Vec2;
  facing: number;
  radius: number;
  damage: number;
  ttlMs: number;
  maxLifetimeMs: number;
  splashRadius: number;
}

export interface BattleRuntimeAuthoritativeProjectileTerminalFrame {
  projectileId: string;
  kind: string;
  ownerPlayerId: string;
  ownerHeroId: string;
  reason: string;
  start: Vec2;
  end: Vec2;
  terminalPosition: Vec2;
  ttlBefore: number;
  ttlAfter: number;
  elapsedMs: number;
  targetPlayerId: string | null;
  targetHeroId: string | null;
  hpBefore: number | null;
  hpAfter: number | null;
  damage: number | null;
}

export interface BattleRuntimeAuthoritativeSlowFieldFrame {
  fieldId: string;
  ownerPlayerId: string;
  ownerHeroId: string;
  position: Vec2;
  radius: number;
  ttlMs: number;
  durationMs: number;
}

export interface BattleRuntimeAuthoritativePickupFrame {
  pickupId: string;
  kind: "Medkit" | "Weapon";
  weaponKind?: WeaponKind;
  position: Vec2;
  available: boolean;
  respawnMs: number;
}

export interface BattleRuntimeAuthoritativeEventParticipantFrame {
  playerId: string;
  heroId: string;
  displayName: string;
}

export interface BattleRuntimeAuthoritativeEventFrame {
  eventId: string;
  type: "kill" | "heal" | "pickup" | "respawn";
  kind: "kill" | "heal" | "pickup" | "respawn";
  elapsedMs: number;
  message: string;
  source: BattleRuntimeAuthoritativeEventParticipantFrame;
  target: BattleRuntimeAuthoritativeEventParticipantFrame;
}

export interface BattleRuntimeAuthoritativeFrame {
  battleId: string;
  phase: string;
  tick: number;
  serverTime: number;
  startedAt: number;
  durationMs: number;
  elapsedMs: number;
  endsAt: number;
  worldSize: Vec2;
  heroes: BattleRuntimeAuthoritativeHeroFrame[];
  projectiles: BattleRuntimeAuthoritativeProjectileFrame[];
  projectileTerminals: BattleRuntimeAuthoritativeProjectileTerminalFrame[];
  slowFields: BattleRuntimeAuthoritativeSlowFieldFrame[];
  pickups: BattleRuntimeAuthoritativePickupFrame[];
  events: BattleRuntimeAuthoritativeEventFrame[];
  remoteAuthoritativeHeroIds: string[];
}

export function buildBattleRuntimeAuthoritativeFrame(
  snapshot: GameSnapshot,
  state: AuthoritativeBattleState,
  localPlayerId: string,
  stableSeatHeroIds: string[]
): BattleRuntimeAuthoritativeFrame | null {
  const heroes = [...snapshot.heroes];
  if (heroes.length === 0 || state.players.length === 0) {
    return null;
  }

  const assignedHeroIds = new Set<string>();
  const normalizedLocalPlayerId = normalizeHandle(localPlayerId);
  const heroesById = new Map(heroes.map((hero) => [hero.heroId, hero] as const));
  const heroesByLabel = new Map<string, Hero[]>();
  heroes.forEach((hero) => {
    const labels = [hero.displayName, hero.heroId];
    labels.forEach((label) => {
      const key = normalizeHandle(label);
      if (!key) {
        return;
      }

      const group = heroesByLabel.get(key);
      if (group) {
        group.push(hero);
      } else {
        heroesByLabel.set(key, [hero]);
      }
    });
  });

  const heroFrames: BattleRuntimeAuthoritativeHeroFrame[] = [];

  state.players.forEach((player, index) => {
    const matchedHero = resolveMatchedHero({
      player,
      index,
      snapshot,
      normalizedLocalPlayerId,
      assignedHeroIds,
      heroesById,
      heroesByLabel,
      stableSeatHeroIds
    });
    if (!matchedHero) {
      return;
    }

    assignedHeroIds.add(matchedHero.heroId);
    const respawnMs = Math.max(0, Math.round(player.respawnMs));
    const alive = player.alive && player.hp > 0;
    const weapons = player.weapons.map((weapon) => ({
      weaponKind: weapon.weaponKind,
      ammoInMagazine: weapon.ammoInMagazine,
      magazineSize: weapon.magazineSize,
      reserveAmmo: weapon.reserveAmmo,
      fireCooldownMs: Math.max(0, Math.round(weapon.fireCooldownMs)),
      reloadRemainingMs: Math.max(0, Math.round(weapon.reloadRemainingMs))
    }));
    heroFrames.push({
      heroId: matchedHero.heroId,
      displayName: player.displayName,
      position: { x: player.position.x, y: player.position.y },
      facing: Number.isFinite(player.facing) ? player.facing : resolveFacing(player.aim, matchedHero.facing),
      currentWeaponIndex: clampWeaponIndex(player.currentWeaponIndex, weapons.length),
      weapons,
      currentWeaponKind: player.currentWeaponKind,
      ammoInMagazine: player.ammoInMagazine,
      magazineSize: player.magazineSize,
      reserveAmmo: player.reserveAmmo,
      fireCooldownMs: Math.max(0, Math.round(player.fireCooldownMs)),
      reloadRemainingMs: Math.max(0, Math.round(player.reloadRemainingMs)),
      hp: clampHp(player.hp, player.maxHp),
      maxHp: Math.max(1, Math.round(player.maxHp)),
      stamina: clampStamina(player.stamina, player.maxStamina),
      maxStamina: Math.max(1, player.maxStamina),
      score: Math.max(0, Math.round(player.score)),
      kills: Math.max(0, Math.round(player.kills)),
      skills: player.skills.map((skill) => ({
        kind: skill.kind,
        cooldownMs: Math.max(0, Math.round(skill.cooldownMs)),
        activeMs: Math.max(0, Math.round(skill.activeMs))
      })),
      alive,
      eliminatedAtMs: alive ? null : player.eliminatedAtMs,
      respawnMs
    });
  });

  if (heroFrames.length === 0) {
    return null;
  }

  return {
    battleId: state.battleId,
    phase: state.phase,
    tick: state.tick,
    serverTime: state.serverTime,
    startedAt: state.startedAt,
    durationMs: state.durationMs,
    elapsedMs: state.elapsedMs,
    endsAt: state.endsAt,
    worldSize: { x: state.worldSize.x, y: state.worldSize.y },
    heroes: heroFrames,
    projectiles: state.projectiles.map((projectile) => ({
      projectileId: projectile.projectileId,
      ownerHeroId: projectile.ownerHeroId,
      kind: projectile.kind,
      position: { x: projectile.position.x, y: projectile.position.y },
      velocity: { x: projectile.velocity.x, y: projectile.velocity.y },
      facing: projectile.facing,
      radius: projectile.radius,
      damage: projectile.damage,
      ttlMs: projectile.ttlMs,
      maxLifetimeMs: projectile.maxLifetimeMs,
      splashRadius: projectile.splashRadius
    })),
    projectileTerminals: state.projectileTerminals.map((terminal) => ({
      projectileId: terminal.projectileId,
      kind: terminal.kind,
      ownerPlayerId: terminal.ownerPlayerId,
      ownerHeroId: terminal.ownerHeroId,
      reason: terminal.reason,
      start: { x: terminal.start.x, y: terminal.start.y },
      end: { x: terminal.end.x, y: terminal.end.y },
      terminalPosition: { x: terminal.terminalPosition.x, y: terminal.terminalPosition.y },
      ttlBefore: terminal.ttlBefore,
      ttlAfter: terminal.ttlAfter,
      elapsedMs: terminal.elapsedMs,
      targetPlayerId: terminal.targetPlayerId,
      targetHeroId: terminal.targetHeroId,
      hpBefore: terminal.hpBefore,
      hpAfter: terminal.hpAfter,
      damage: terminal.damage
    })),
    slowFields: state.slowFields.map((field) => ({
      fieldId: field.fieldId,
      ownerPlayerId: field.ownerPlayerId,
      ownerHeroId: field.ownerHeroId,
      position: { x: field.position.x, y: field.position.y },
      radius: field.radius,
      ttlMs: field.ttlMs,
      durationMs: field.durationMs
    })),
    pickups: state.pickups.map((pickup) => ({
      pickupId: pickup.pickupId,
      kind: pickup.kind,
      ...(pickup.kind === "Weapon" ? { weaponKind: pickup.weaponKind } : {}),
      position: { x: pickup.position.x, y: pickup.position.y },
      available: pickup.available,
      respawnMs: pickup.respawnMs
    })),
    events: state.events.map((event) => ({
      eventId: event.eventId,
      type: event.type,
      kind: event.kind,
      elapsedMs: event.elapsedMs,
      message: event.message,
      source: {
        playerId: event.source.playerId,
        heroId: event.source.heroId,
        displayName: event.source.displayName
      },
      target: {
        playerId: event.target.playerId,
        heroId: event.target.heroId,
        displayName: event.target.displayName
      }
    })),
    remoteAuthoritativeHeroIds: heroFrames
      .map((hero) => hero.heroId)
      .filter((heroId) => heroId !== snapshot.playerHeroId)
  };
}

interface ResolveMatchedHeroInput {
  player: AuthoritativeBattleState["players"][number];
  index: number;
  snapshot: GameSnapshot;
  normalizedLocalPlayerId: string;
  assignedHeroIds: Set<string>;
  heroesById: Map<string, Hero>;
  heroesByLabel: Map<string, Hero[]>;
  stableSeatHeroIds: string[];
}

function resolveMatchedHero(input: ResolveMatchedHeroInput): Hero | null {
  const {
    player,
    index,
    snapshot,
    normalizedLocalPlayerId,
    assignedHeroIds,
    heroesById,
    heroesByLabel,
    stableSeatHeroIds
  } = input;
  const normalizedPlayerHandle = normalizeHandle(player.handle);
  const normalizedDisplayName = normalizeHandle(player.displayName);
  const normalizedPlayerId = normalizeHandle(player.playerId);

  const directHero = heroesById.get(player.heroId);
  if (directHero && !assignedHeroIds.has(directHero.heroId)) {
    return directHero;
  }

  if (normalizedPlayerId && normalizedPlayerId === normalizedLocalPlayerId) {
    const localHero = heroesById.get(snapshot.playerHeroId) ?? null;
    if (localHero && !assignedHeroIds.has(localHero.heroId)) {
      return localHero;
    }
  }

  const labelMatches = [
    ...(heroesByLabel.get(normalizedPlayerHandle) ?? []),
    ...(heroesByLabel.get(normalizedDisplayName) ?? [])
  ];
  const unassignedLabelMatch = labelMatches.find((hero) => !assignedHeroIds.has(hero.heroId));
  if (unassignedLabelMatch) {
    return unassignedLabelMatch;
  }

  const seatHeroId = stableSeatHeroIds[index] ?? null;
  const seatHero = seatHeroId ? heroesById.get(seatHeroId) ?? null : null;
  if (seatHero && !assignedHeroIds.has(seatHero.heroId)) {
    return seatHero;
  }

  return null;
}

function resolveFacing(aim: Vec2, fallback: number): number {
  const length = Math.hypot(aim.x, aim.y);
  if (length <= 0.0001) {
    return fallback;
  }

  return Math.atan2(aim.y, aim.x);
}

function clampWeaponIndex(index: number, weaponCount: number): number {
  if (weaponCount <= 0) {
    return 0;
  }

  return Math.max(0, Math.min(Math.trunc(index), weaponCount - 1));
}

function clampHp(hp: number, maxHp: number): number {
  const safeMaxHp = Math.max(1, Math.round(maxHp));
  return Math.max(0, Math.min(Math.round(hp), safeMaxHp));
}

function clampStamina(stamina: number, maxStamina: number): number {
  const safeMaxStamina = Math.max(1, maxStamina);
  return Math.max(0, Math.min(stamina, safeMaxStamina));
}

function normalizeHandle(value: string): string {
  return value.trim().toLowerCase();
}
