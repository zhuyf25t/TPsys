import { buildApiUrl, normalizeApiBase } from "../../../../shared/api/apiUrl";

export interface AuthoritativeBattleVector {
  x: number;
  y: number;
}

export type AuthoritativeBattleWeaponKind = "Pistol" | "RocketLauncher" | "Gatling" | "Shotgun";
export type AuthoritativeBattleSkillKind = "Blink" | "Dash" | "Freeze";
export type AuthoritativeBattleCommandStatus = "applied" | "ignored";
export type AuthoritativeBattleCommandReason = "battle_finished" | "battle_inactive" | "player_dead";
export type AuthoritativeBattleSkillOutcomeStatus = "applied" | "noop";
export type AuthoritativeBattleSkillOutcomeReason =
  | "skill_not_owned"
  | "cooldown"
  | "missing_target"
  | "out_of_range"
  | "invalid_target"
  | "no_direction"
  | "blocked";

export interface AuthoritativeBattleSkillOutcome {
  action: AuthoritativeBattleSkillKind;
  status: AuthoritativeBattleSkillOutcomeStatus;
  reason?: AuthoritativeBattleSkillOutcomeReason;
}

export interface AuthoritativeBattleSkillState {
  kind: AuthoritativeBattleSkillKind;
  cooldownMs: number;
  activeMs: number;
}

export interface AuthoritativeBattleWeaponState {
  weaponKind: AuthoritativeBattleWeaponKind;
  ammoInMagazine: number;
  magazineSize: number;
  reserveAmmo: number | null;
  fireCooldownMs: number;
  reloadRemainingMs: number;
  heat: number;
  overheated: boolean;
  overheatRemainingMs: number;
}

export interface AuthoritativeBattlePlayerState {
  playerId: string;
  heroId: string;
  handle: string;
  displayName: string;
  seat: number;
  isBot: boolean;
  position: AuthoritativeBattleVector;
  aim: AuthoritativeBattleVector;
  facing: number;
  primaryHeld: boolean;
  reloadPressed: boolean;
  lastClientCommandSeq: number;
  currentWeaponIndex: number;
  weapons: AuthoritativeBattleWeaponState[];
  currentWeaponKind: AuthoritativeBattleWeaponKind;
  ammoInMagazine: number;
  magazineSize: number;
  reserveAmmo: number | null;
  fireCooldownMs: number;
  reloadRemainingMs: number;
  heat: number;
  overheated: boolean;
  overheatRemainingMs: number;
  hp: number;
  maxHp: number;
  stamina: number;
  maxStamina: number;
  score: number;
  kills: number;
  skills: AuthoritativeBattleSkillState[];
  alive: boolean;
  eliminatedAtMs: number | null;
  respawnMs: number;
}

export interface AuthoritativeBattleProjectileState {
  projectileId: string;
  ownerHeroId: string;
  kind: string;
  position: AuthoritativeBattleVector;
  velocity: AuthoritativeBattleVector;
  facing: number;
  radius: number;
  damage: number;
  ttlMs: number;
  maxLifetimeMs: number;
  splashRadius: number;
}

export interface AuthoritativeBattleProjectileTerminalState {
  projectileId: string;
  kind: string;
  ownerPlayerId: string;
  ownerHeroId: string;
  reason: string;
  start: AuthoritativeBattleVector;
  end: AuthoritativeBattleVector;
  terminalPosition: AuthoritativeBattleVector;
  ttlBefore: number;
  ttlAfter: number;
  elapsedMs: number;
  targetPlayerId: string | null;
  targetHeroId: string | null;
  hpBefore: number | null;
  hpAfter: number | null;
  damage: number | null;
}

export interface AuthoritativeBattleSlowFieldState {
  fieldId: string;
  ownerPlayerId: string;
  ownerHeroId: string;
  position: AuthoritativeBattleVector;
  radius: number;
  ttlMs: number;
  durationMs: number;
}

export type AuthoritativeBattlePickupKind = "Medkit" | "Weapon";

export interface AuthoritativeBattlePickupState {
  pickupId: string;
  kind: AuthoritativeBattlePickupKind;
  weaponKind?: AuthoritativeBattleWeaponKind;
  position: AuthoritativeBattleVector;
  available: boolean;
  respawnMs: number;
}

export interface AuthoritativeBattleEventParticipant {
  playerId: string;
  heroId: string;
  displayName: string;
}

export interface AuthoritativeBattleEventState {
  eventId: string;
  type: "kill" | "heal" | "pickup" | "respawn";
  kind: "kill" | "heal" | "pickup" | "respawn";
  elapsedMs: number;
  message: string;
  source: AuthoritativeBattleEventParticipant;
  target: AuthoritativeBattleEventParticipant;
}

export interface AuthoritativeBattleState {
  battleId: string;
  roomId: string;
  phase: string;
  serverTime: number;
  startedAt: number;
  durationMs: number;
  elapsedMs: number;
  endsAt: number;
  worldSize: AuthoritativeBattleVector;
  tick: number;
  resultReady: boolean;
  replayReady: boolean;
  players: AuthoritativeBattlePlayerState[];
  projectiles: AuthoritativeBattleProjectileState[];
  projectileTerminals: AuthoritativeBattleProjectileTerminalState[];
  slowFields: AuthoritativeBattleSlowFieldState[];
  pickups: AuthoritativeBattlePickupState[];
  events: AuthoritativeBattleEventState[];
  winnerPlayerId?: string | null;
  winnerHeroId?: string | null;
}

export interface AuthoritativeBattleCommand {
  battleId: string;
  playerId: string;
  ticketId: string;
  clientTick: number;
  clientCommandSeq: number;
  movement: AuthoritativeBattleVector;
  aim: AuthoritativeBattleVector;
  primaryHeld: boolean;
  sprint: boolean;
  reloadPressed: boolean;
  castDash: boolean;
  castBlink: boolean;
  castFreeze: boolean;
  pointerWorld?: AuthoritativeBattleVector | null;
  switchWeaponDirection: -1 | 0 | 1;
  switchWeaponIndex: number | null;
}

export interface AuthoritativeBattleCommandAccepted {
  battleId: string;
  acceptedTick: number;
  acceptedCommandSeq: number;
  serverTime: number;
  commandStatus: AuthoritativeBattleCommandStatus;
  commandReason?: AuthoritativeBattleCommandReason;
  outcomes: AuthoritativeBattleSkillOutcome[];
}

export type AuthoritativeBattleCommandSubmitOutcome =
  | { ok: true; accepted: AuthoritativeBattleCommandAccepted }
  | { ok: false; kind: "http"; status: number; errorCode?: string }
  | { ok: false; kind: "network" }
  | { ok: false; kind: "parse" };

export interface AuthoritativeBattleStateStreamHandle {
  close: () => void;
}

export interface AuthoritativeBattleStateStreamOptions {
  onState: (state: AuthoritativeBattleState) => void;
  onFallback: () => void;
}

const BATTLE_API_BASE = normalizeApiBase(import.meta.env.VITE_BATTLE_API_BASE ?? "", "/api");
const BATTLE_REQUEST_TIMEOUT_MS = 1_250;

export async function loadAuthoritativeBattleState(battleId: string): Promise<AuthoritativeBattleState | null> {
  const normalizedBattleId = battleId.trim();
  if (!BATTLE_API_BASE || !normalizedBattleId || typeof window === "undefined") {
    return null;
  }

  const url = buildApiUrl(BATTLE_API_BASE, `/battle/state/${encodeURIComponent(normalizedBattleId)}`);
  return fetchJson(url, { method: "GET", cache: "no-store" }).then((payload) => normalizeAuthoritativeBattleState(payload));
}

export function openAuthoritativeBattleStateStream(
  battleId: string,
  options: AuthoritativeBattleStateStreamOptions
): AuthoritativeBattleStateStreamHandle | null {
  const normalizedBattleId = battleId.trim();
  if (!BATTLE_API_BASE || !normalizedBattleId || typeof window === "undefined" || !("EventSource" in window)) {
    return null;
  }

  let closedByClient = false;
  const url = buildApiUrl(
    BATTLE_API_BASE,
    `/battle/state/stream?battleId=${encodeURIComponent(normalizedBattleId)}`
  );
  const source = new EventSource(url);

  source.onmessage = (event) => {
    const state = parseAuthoritativeBattleStateEvent(event.data);
    if (state) {
      options.onState(state);
    }
  };
  source.addEventListener("state", (event) => {
    const state = parseAuthoritativeBattleStateEvent(event.data);
    if (state) {
      options.onState(state);
    }
  });
  source.onerror = () => {
    source.close();
    if (!closedByClient) {
      options.onFallback();
    }
  };

  return {
    close: () => {
      closedByClient = true;
      source.close();
    }
  };
}

export async function sendAuthoritativeBattleCommand(
  command: AuthoritativeBattleCommand
): Promise<AuthoritativeBattleCommandSubmitOutcome> {
  const normalizedBattleId = command.battleId.trim();
  const normalizedPlayerId = command.playerId.trim();
  const normalizedTicketId = command.ticketId.trim();
  if (
    !BATTLE_API_BASE ||
    !normalizedBattleId ||
    !normalizedPlayerId ||
    !normalizedTicketId ||
    typeof window === "undefined"
  ) {
    return { ok: false, kind: "network" };
  }

  const url = buildApiUrl(BATTLE_API_BASE, "/battle/commands");
  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), BATTLE_REQUEST_TIMEOUT_MS);

  try {
    const response = await fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        battleId: normalizedBattleId,
        playerId: normalizedPlayerId,
        ticketId: normalizedTicketId,
        clientTick: Math.max(0, Math.trunc(command.clientTick)),
        clientCommandSeq: Math.max(0, Math.trunc(command.clientCommandSeq)),
        movement: normalizeVector(command.movement),
        aim: normalizeAim(command.aim),
        primaryHeld: command.primaryHeld,
        sprint: command.sprint,
        reloadPressed: command.reloadPressed,
        castDash: command.castDash,
        castBlink: command.castBlink,
        castFreeze: command.castFreeze,
        pointerWorld: command.pointerWorld ? normalizeWorldPoint(command.pointerWorld) : null,
        switchWeaponDirection: normalizeSwitchDirection(command.switchWeaponDirection),
        switchWeaponIndex: normalizeSwitchWeaponIndex(command.switchWeaponIndex)
      }),
      signal: controller.signal
    });

    if (!response.ok) {
      const errorPayload = await response.json().catch(() => null);
      const errorCode = readCommandSubmitErrorCode(errorPayload);
      return errorCode
        ? { ok: false, kind: "http", status: response.status, errorCode }
        : { ok: false, kind: "http", status: response.status };
    }

    const payload = await response.json().catch(() => null);
    const accepted = normalizeBattleCommandAccepted(payload);
    return accepted ? { ok: true, accepted } : { ok: false, kind: "parse" };
  } catch {
    return { ok: false, kind: "network" };
  } finally {
    window.clearTimeout(timeout);
  }
}

async function fetchJson(url: string, init: RequestInit): Promise<unknown> {
  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), BATTLE_REQUEST_TIMEOUT_MS);

  try {
    const response = await fetch(url, {
      ...init,
      signal: controller.signal
    });

    if (!response.ok) {
      return null;
    }

    return response.json().catch(() => null);
  } catch {
    return null;
  } finally {
    window.clearTimeout(timeout);
  }
}

function parseAuthoritativeBattleStateEvent(data: string): AuthoritativeBattleState | null {
  try {
    return normalizeAuthoritativeBattleState(JSON.parse(data));
  } catch {
    return null;
  }
}

function normalizeAuthoritativeBattleState(payload: unknown): AuthoritativeBattleState | null {
  if (!payload || typeof payload !== "object") {
    return null;
  }

  const value = payload as Partial<AuthoritativeBattleState> & Record<string, unknown>;
  const battleId = readString(value.battleId);
  const roomId = readString(value.roomId);
  const phase = readString(value.phase);
  const serverTime = readNumber(value.serverTime);
  const startedAt = readNumber(value.startedAt);
  const durationMs = readNumber(value.durationMs);
  const elapsedMs = readNumber(value.elapsedMs);
  const endsAt = readNumber(value.endsAt);
  const worldSize = normalizeVectorPayload(value.worldSize);
  const tick = readNumber(value.tick);

  if (
    !battleId ||
    !roomId ||
    !phase ||
    serverTime === null ||
    startedAt === null ||
    durationMs === null ||
    elapsedMs === null ||
    endsAt === null ||
    worldSize === null ||
    tick === null ||
    !Array.isArray(value.players) ||
    !Array.isArray(value.projectiles) ||
    !Array.isArray(value.events)
  ) {
    return null;
  }

  const players = value.players
    .map((entry) => normalizeAuthoritativeBattlePlayerState(entry))
    .filter((entry): entry is AuthoritativeBattlePlayerState => entry !== null)
    .sort((left, right) => left.seat - right.seat);
  const projectiles = value.projectiles
    .map((entry) => normalizeAuthoritativeBattleProjectileState(entry))
    .filter((entry): entry is AuthoritativeBattleProjectileState => entry !== null);
  const projectileTerminals = (Array.isArray(value.projectileTerminals) ? value.projectileTerminals : [])
    .map((entry) => normalizeAuthoritativeBattleProjectileTerminalState(entry))
    .filter((entry): entry is AuthoritativeBattleProjectileTerminalState => entry !== null);
  const slowFields = (Array.isArray(value.slowFields) ? value.slowFields : [])
    .map((entry) => normalizeAuthoritativeBattleSlowFieldState(entry))
    .filter((entry): entry is AuthoritativeBattleSlowFieldState => entry !== null);
  const pickups = (Array.isArray(value.pickups) ? value.pickups : [])
    .map((entry) => normalizeAuthoritativeBattlePickupState(entry))
    .filter((entry): entry is AuthoritativeBattlePickupState => entry !== null);
  const events = value.events
    .map((entry) => normalizeAuthoritativeBattleEventState(entry))
    .filter((entry): entry is AuthoritativeBattleEventState => entry !== null);

  return {
    battleId,
    roomId,
    phase,
    serverTime,
    startedAt,
    durationMs: Math.max(1, Math.round(durationMs)),
    elapsedMs: Math.max(0, Math.round(elapsedMs)),
    endsAt,
    worldSize,
    tick,
    resultReady: value.resultReady === true,
    replayReady: value.replayReady === true,
    players,
    projectiles,
    projectileTerminals,
    slowFields,
    pickups,
    events,
    winnerPlayerId: readOptionalString(value.winnerPlayerId),
    winnerHeroId: readOptionalString(value.winnerHeroId)
  };
}

function normalizeAuthoritativeBattlePickupState(payload: unknown): AuthoritativeBattlePickupState | null {
  if (!payload || typeof payload !== "object") {
    return null;
  }

  const value = payload as Partial<AuthoritativeBattlePickupState> & Record<string, unknown>;
  const pickupId = readString(value.pickupId);
  const kind = normalizeAuthoritativeBattlePickupKind(value.kind);
  const weaponKind = normalizeAuthoritativeBattleWeaponKind(value.weaponKind);
  const position = normalizeVectorPayload(value.position);
  const respawnMs = readNumber(value.respawnMs);

  if (
    !pickupId ||
    kind === null ||
    position === null ||
    typeof value.available !== "boolean" ||
    respawnMs === null ||
    (kind === "Weapon" && weaponKind === null)
  ) {
    return null;
  }

  if (kind === "Weapon") {
    return {
      pickupId,
      kind,
      weaponKind: weaponKind ?? "Pistol",
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

function normalizeAuthoritativeBattlePlayerState(payload: unknown): AuthoritativeBattlePlayerState | null {
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
  const currentWeaponIndex = readNumber(value.currentWeaponIndex);
  const lastClientCommandSeq = readOptionalNumber(value.lastClientCommandSeq);
  const currentWeaponKind = normalizeAuthoritativeBattleWeaponKind(value.currentWeaponKind);
  const ammoInMagazine = readNumber(value.ammoInMagazine);
  const magazineSize = readNumber(value.magazineSize);
  const hasReserveAmmo = Object.prototype.hasOwnProperty.call(value, "reserveAmmo");
  const reserveAmmo = readOptionalNumber(value.reserveAmmo);
  const fireCooldownMs = readNumber(value.fireCooldownMs);
  const reloadRemainingMs = readNumber(value.reloadRemainingMs);
  const heat = readOptionalNumber(value.heat);
  const overheated = typeof value.overheated === "boolean" ? value.overheated : false;
  const overheatRemainingMs = readOptionalNumber(value.overheatRemainingMs);
  const weapons = (Array.isArray(value.weapons) ? value.weapons : [])
    .map((entry) => normalizeAuthoritativeBattleWeaponState(entry))
    .filter((entry): entry is AuthoritativeBattleWeaponState => entry !== null);
  const hp = readNumber(value.hp);
  const maxHp = readNumber(value.maxHp);
  const stamina = readNumber(value.stamina);
  const maxStamina = readNumber(value.maxStamina);
  const score = readNumber(value.score);
  const kills = readNumber(value.kills);
  const skills = (Array.isArray(value.skills) ? value.skills : [])
    .map((entry) => normalizeAuthoritativeBattleSkillState(entry))
    .filter((entry): entry is AuthoritativeBattleSkillState => entry !== null);
  const eliminatedAtMs = readOptionalNumber(value.eliminatedAtMs);
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
    currentWeaponIndex === null ||
    currentWeaponKind === null ||
    ammoInMagazine === null ||
    magazineSize === null ||
    !hasReserveAmmo ||
    fireCooldownMs === null ||
    reloadRemainingMs === null ||
    hp === null ||
    maxHp === null ||
    stamina === null ||
    maxStamina === null ||
    respawnMs === null ||
    typeof value.isBot !== "boolean" ||
    typeof value.primaryHeld !== "boolean" ||
    typeof value.reloadPressed !== "boolean" ||
    typeof value.alive !== "boolean"
  ) {
    return null;
  }

  const scalarWeapon: AuthoritativeBattleWeaponState = {
    weaponKind: currentWeaponKind,
    ammoInMagazine: Math.max(0, Math.round(ammoInMagazine)),
    magazineSize: Math.max(0, Math.round(magazineSize)),
    reserveAmmo: reserveAmmo === null ? null : Math.max(0, Math.round(reserveAmmo)),
    fireCooldownMs: Math.max(0, Math.round(fireCooldownMs)),
    reloadRemainingMs: Math.max(0, Math.round(reloadRemainingMs)),
    heat: Math.max(0, heat ?? 0),
    overheated,
    overheatRemainingMs: Math.max(0, Math.round(overheatRemainingMs ?? 0))
  };
  const normalizedWeapons = weapons.length > 0 ? weapons : [scalarWeapon];
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
    primaryHeld: value.primaryHeld,
    reloadPressed: value.reloadPressed,
    lastClientCommandSeq: Math.max(0, Math.trunc(lastClientCommandSeq ?? 0)),
    currentWeaponIndex: Math.max(0, Math.trunc(currentWeaponIndex)),
    weapons: normalizedWeapons,
    currentWeaponKind,
    ammoInMagazine: Math.max(0, Math.round(ammoInMagazine)),
    magazineSize: Math.max(0, Math.round(magazineSize)),
    reserveAmmo: reserveAmmo === null ? null : Math.max(0, Math.round(reserveAmmo)),
    fireCooldownMs: Math.max(0, Math.round(fireCooldownMs)),
    reloadRemainingMs: Math.max(0, Math.round(reloadRemainingMs)),
    heat: Math.max(0, heat ?? 0),
    overheated,
    overheatRemainingMs: Math.max(0, Math.round(overheatRemainingMs ?? 0)),
    hp: Math.max(0, hp),
    maxHp: Math.max(1, maxHp),
    stamina: Math.max(0, Math.min(stamina, safeMaxStamina)),
    maxStamina: safeMaxStamina,
    score: Math.max(0, Math.round(score ?? 0)),
    kills: Math.max(0, Math.round(kills ?? 0)),
    skills,
    alive: value.alive,
    eliminatedAtMs: eliminatedAtMs === null ? null : Math.max(0, Math.round(eliminatedAtMs)),
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
  const heat = readOptionalNumber(value.heat);
  const overheated = typeof value.overheated === "boolean" ? value.overheated : false;
  const overheatRemainingMs = readOptionalNumber(value.overheatRemainingMs);

  if (
    weaponKind === null ||
    ammoInMagazine === null ||
    magazineSize === null ||
    !hasReserveAmmo ||
    fireCooldownMs === null ||
    reloadRemainingMs === null
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
    heat: Math.max(0, heat ?? 0),
    overheated,
    overheatRemainingMs: Math.max(0, Math.round(overheatRemainingMs ?? 0))
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

function normalizeAuthoritativeBattleSkillKind(payload: unknown): AuthoritativeBattleSkillKind | null {
  return payload === "Blink" || payload === "Dash" || payload === "Freeze" ? payload : null;
}

function normalizeAuthoritativeBattleWeaponKind(payload: unknown): AuthoritativeBattleWeaponKind | null {
  return payload === "Pistol" ||
    payload === "RocketLauncher" ||
    payload === "Gatling" ||
    payload === "Shotgun"
    ? payload
    : null;
}

function normalizeAuthoritativeBattleProjectileState(payload: unknown): AuthoritativeBattleProjectileState | null {
  if (!payload || typeof payload !== "object") {
    return null;
  }

  const value = payload as Partial<AuthoritativeBattleProjectileState> & Record<string, unknown>;
  const projectileId = readString(value.projectileId);
  const ownerHeroId = readString(value.ownerHeroId);
  const kind = readString(value.kind);
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
    !kind ||
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

function normalizeAuthoritativeBattleProjectileTerminalState(
  payload: unknown
): AuthoritativeBattleProjectileTerminalState | null {
  if (!payload || typeof payload !== "object") {
    return null;
  }

  const value = payload as Partial<AuthoritativeBattleProjectileTerminalState> & Record<string, unknown>;
  const projectileId = readString(value.projectileId);
  const kind = readString(value.kind);
  const ownerPlayerId = readString(value.ownerPlayerId);
  const ownerHeroId = readString(value.ownerHeroId);
  const reason = readString(value.reason);
  const start = normalizeVectorPayload(value.start);
  const end = normalizeVectorPayload(value.end);
  const terminalPosition = normalizeVectorPayload(value.terminalPosition);
  const ttlBefore = readNumber(value.ttlBefore);
  const ttlAfter = readNumber(value.ttlAfter);
  const elapsedMs = readNumber(value.elapsedMs);

  if (
    !projectileId ||
    !kind ||
    !ownerPlayerId ||
    !ownerHeroId ||
    !reason ||
    start === null ||
    end === null ||
    terminalPosition === null ||
    ttlBefore === null ||
    ttlAfter === null ||
    elapsedMs === null
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
    targetPlayerId: readOptionalString(value.targetPlayerId),
    targetHeroId: readOptionalString(value.targetHeroId),
    hpBefore: normalizeOptionalNonNegativeInteger(value.hpBefore),
    hpAfter: normalizeOptionalNonNegativeInteger(value.hpAfter),
    damage: normalizeOptionalNonNegativeInteger(value.damage)
  };
}

function normalizeAuthoritativeBattleSlowFieldState(payload: unknown): AuthoritativeBattleSlowFieldState | null {
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

function normalizeAuthoritativeBattleEventState(payload: unknown): AuthoritativeBattleEventState | null {
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

function normalizeBattleCommandAccepted(payload: unknown): AuthoritativeBattleCommandAccepted | null {
  if (!payload || typeof payload !== "object") {
    return null;
  }

  const value = payload as Partial<AuthoritativeBattleCommandAccepted> & Record<string, unknown>;
  const battleId = readString(value.battleId);
  const acceptedTick = readNumber(value.acceptedTick);
  const acceptedCommandSeq = readOptionalNumber(value.acceptedCommandSeq);
  const serverTime = readNumber(value.serverTime);
  const commandStatus = normalizeBattleCommandStatus(value.commandStatus) ?? "applied";
  const commandReason = normalizeBattleCommandReason(value.commandReason);
  const outcomes = (Array.isArray(value.outcomes) ? value.outcomes : [])
    .map(normalizeBattleSkillOutcome)
    .filter((outcome): outcome is AuthoritativeBattleSkillOutcome => outcome !== null);

  if (!battleId || acceptedTick === null || serverTime === null) {
    return null;
  }

  return {
    battleId,
    acceptedTick: Math.max(0, Math.trunc(acceptedTick)),
    acceptedCommandSeq: Math.max(0, Math.trunc(acceptedCommandSeq ?? 0)),
    serverTime,
    commandStatus,
    ...(commandReason ? { commandReason } : {}),
    outcomes
  };
}

function normalizeBattleCommandStatus(payload: unknown): AuthoritativeBattleCommandStatus | null {
  return payload === "applied" || payload === "ignored" ? payload : null;
}

function normalizeBattleCommandReason(payload: unknown): AuthoritativeBattleCommandReason | null {
  return payload === "battle_finished" || payload === "battle_inactive" || payload === "player_dead" ? payload : null;
}

function normalizeBattleSkillOutcome(payload: unknown): AuthoritativeBattleSkillOutcome | null {
  if (!payload || typeof payload !== "object") {
    return null;
  }

  const value = payload as Partial<AuthoritativeBattleSkillOutcome> & Record<string, unknown>;
  const action = normalizeAuthoritativeBattleSkillKind(value.action);
  const status = normalizeBattleSkillOutcomeStatus(value.status);
  const reason = normalizeBattleSkillOutcomeReason(value.reason);
  if (action === null || status === null) {
    return null;
  }

  return {
    action,
    status,
    ...(reason ? { reason } : {})
  };
}

function normalizeBattleSkillOutcomeStatus(payload: unknown): AuthoritativeBattleSkillOutcomeStatus | null {
  return payload === "applied" || payload === "noop" ? payload : null;
}

function normalizeBattleSkillOutcomeReason(payload: unknown): AuthoritativeBattleSkillOutcomeReason | null {
  return payload === "skill_not_owned" ||
    payload === "cooldown" ||
    payload === "missing_target" ||
    payload === "out_of_range" ||
    payload === "invalid_target" ||
    payload === "no_direction" ||
    payload === "blocked"
    ? payload
    : null;
}

function readCommandSubmitErrorCode(payload: unknown): string | undefined {
  if (!payload || typeof payload !== "object") {
    return undefined;
  }

  const error = (payload as Record<string, unknown>).error;
  return typeof error === "string" && error.trim() ? error.trim() : undefined;
}

function normalizeVectorPayload(payload: unknown): AuthoritativeBattleVector | null {
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

function normalizeVector(vector: AuthoritativeBattleVector): AuthoritativeBattleVector {
  const x = Number.isFinite(vector.x) ? vector.x : 0;
  const y = Number.isFinite(vector.y) ? vector.y : 0;
  return { x, y };
}

function normalizeWorldPoint(point: AuthoritativeBattleVector): AuthoritativeBattleVector {
  return normalizeVector(point);
}

function normalizeAim(aim: AuthoritativeBattleVector): AuthoritativeBattleVector {
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

function normalizeSwitchDirection(direction: number): -1 | 0 | 1 {
  if (direction < 0) {
    return -1;
  }

  if (direction > 0) {
    return 1;
  }

  return 0;
}

function normalizeSwitchWeaponIndex(index: number | null): number | null {
  return index === null || !Number.isFinite(index) ? null : Math.max(0, Math.trunc(index));
}

function readString(value: unknown): string | null {
  return typeof value === "string" && value.trim() ? value.trim() : null;
}

function readOptionalString(value: unknown): string | null {
  return typeof value === "string" && value.trim() ? value.trim() : null;
}

function readNumber(value: unknown): number | null {
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

function readOptionalNumber(value: unknown): number | null {
  return typeof value === "number" && Number.isFinite(value) ? value : null;
}

function normalizeOptionalNonNegativeInteger(value: unknown): number | null {
  const numberValue = readOptionalNumber(value);
  return numberValue === null ? null : Math.max(0, Math.round(numberValue));
}
