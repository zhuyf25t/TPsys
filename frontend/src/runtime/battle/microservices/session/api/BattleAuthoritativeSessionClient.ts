import {
  postBattleCommandAPIMessage,
  postBattleStateReadAPIMessage
} from "../../../../../apis/battle/microservices/session/api/BattleSessionApiMessageClient";
import { buildApiUrl, normalizeApiBase } from "../../../../../system/api/apiUrl";
import type { BattleCommandAPIMessageRequest } from "../../../../../objects/battle/microservices/session/api/command/BattleCommandRequestApiTypes";
import type {
  BattleCommandAcceptedResponseDto,
  BattleCommandAcceptPathDto,
  BattleCommandReasonDto,
  BattleCommandServerDiagnosticsResponseDto,
  BattleCommandSkillOutcomeResponseDto,
  BattleCommandStatusDto
} from "../../../../../objects/battle/microservices/session/api/command/BattleCommandResponseApiTypes";
import type { BattleApiVectorDto } from "../../../../../objects/battle/microservices/session/api/state/BattleStateSharedResponseApiTypes";
import type {
  AuthoritativeBattlePhaseDto,
  BattleStateResponseDto
} from "../../../../../objects/battle/microservices/session/api/state/BattleStateRootResponseApiTypes";
import type {
  BattleEventKindDto,
  BattleStateEventParticipantResponseDto,
  BattleStateEventResponseDto,
  BattleStatePickupResponseDto,
  BattleStateSlowFieldResponseDto
} from "../../../../../objects/battle/microservices/session/api/state/BattleStateEntityResponseApiTypes";
import type {
  BattleStatePlayerResponseDto,
  BattleStateSkillResponseDto,
  BattleStateWeaponResponseDto
} from "../../../../../objects/battle/microservices/session/api/state/BattleStatePlayerResponseApiTypes";
import type {
  BattleStateProjectileResponseDto,
  BattleStateProjectileTerminalResponseDto
} from "../../../../../objects/battle/microservices/session/api/state/BattleStateProjectileResponseApiTypes";
import type {
  BattleExtractionResponseDto,
  BattleGasZoneResponseDto,
  BattleLootCacheResponseDto
} from "../../../../../objects/battle/microservices/extraction/api/state/BattleExtractionStateResponseApiTypes";
import type { PickupKind as BattlePickupKindDto } from "../../../../../objects/battle/microservices/abilities/objects/pickup/PickupKind";
import type { SkillKind as BattleSkillKindDto } from "../../../../../objects/battle/microservices/abilities/objects/skill/SkillKind";
import type { SkillOutcomeReason as BattleSkillOutcomeReasonDto } from "../../../../../objects/battle/microservices/abilities/objects/skill/SkillOutcomeReason";
import type { SkillOutcomeStatus as BattleSkillOutcomeStatusDto } from "../../../../../objects/battle/microservices/abilities/objects/skill/SkillOutcomeStatus";
import type { ProjectileKind as BattleProjectileKindDto } from "../../../../../objects/battle/microservices/combat/objects/projectile/ProjectileKind";
import type { ProjectileTerminalReason as BattleProjectileTerminalReasonDto } from "../../../../../objects/battle/microservices/combat/objects/projectile/ProjectileTerminalReason";
import type { WeaponKind as BattleWeaponKindDto } from "../../../../../objects/battle/microservices/combat/objects/weapon/WeaponKind";
import { BATTLE_RUNTIME_REQUEST_TIMEOUT_MS } from "../../../BattleRuntimeNetworkConfig";

import {
  normalizeAim,
  normalizeSwitchDirection,
  normalizeSwitchWeaponIndex,
  normalizeVector,
  normalizeWorldPoint
} from "../functions/BattleAuthoritativeSessionNormalizerPrimitives";
import { normalizeAuthoritativeBattleState } from "../functions/BattleAuthoritativeSessionStateResponseNormalizer";
import { normalizeBattleCommandAPIMessagePayload } from "../functions/BattleAuthoritativeSessionCommandResponseNormalizer";

export interface AuthoritativeBattleVector extends BattleApiVectorDto {}

export type AuthoritativeBattleWeaponKind = BattleWeaponKindDto;
export type AuthoritativeBattleProjectileKind = BattleProjectileKindDto;
export type AuthoritativeBattleSkillKind = BattleSkillKindDto;
export type AuthoritativeBattlePhase = AuthoritativeBattlePhaseDto;
export type AuthoritativeBattleCommandStatus = BattleCommandStatusDto;
export type AuthoritativeBattleCommandReason = BattleCommandReasonDto;
export type { BattleCommandAcceptPathDto, BattleCommandServerDiagnosticsResponseDto };
export type AuthoritativeBattleSkillOutcomeStatus = BattleSkillOutcomeStatusDto;
export type AuthoritativeBattleProjectileTerminalReason = BattleProjectileTerminalReasonDto;
export type AuthoritativeBattleSkillOutcomeReason = BattleSkillOutcomeReasonDto;

export interface AuthoritativeBattleSkillOutcome extends BattleCommandSkillOutcomeResponseDto {
  action: AuthoritativeBattleSkillKind;
  status: AuthoritativeBattleSkillOutcomeStatus;
  reason?: AuthoritativeBattleSkillOutcomeReason;
}

export interface AuthoritativeBattleSkillState extends BattleStateSkillResponseDto {
  kind: AuthoritativeBattleSkillKind;
  cooldownMs: number;
  activeMs: number;
}

export interface AuthoritativeBattleWeaponState extends BattleStateWeaponResponseDto {
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

export interface AuthoritativeBattlePlayerState extends BattleStatePlayerResponseDto {
  playerId: string;
  heroId: string;
  handle: string;
  displayName: string;
  seat: number;
  isBot: boolean;
  position: AuthoritativeBattleVector;
  aim: AuthoritativeBattleVector;
  facing: number;
  movement: AuthoritativeBattleVector;
  sprint: boolean;
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

export interface AuthoritativeBattleProjectileState extends BattleStateProjectileResponseDto {
  projectileId: string;
  ownerHeroId: string;
  kind: AuthoritativeBattleProjectileKind;
  position: AuthoritativeBattleVector;
  velocity: AuthoritativeBattleVector;
  facing: number;
  radius: number;
  damage: number;
  ttlMs: number;
  maxLifetimeMs: number;
  splashRadius: number;
}

export interface AuthoritativeBattleProjectileTerminalState extends BattleStateProjectileTerminalResponseDto {
  projectileId: string;
  kind: AuthoritativeBattleProjectileKind;
  ownerPlayerId: string;
  ownerHeroId: string;
  reason: AuthoritativeBattleProjectileTerminalReason;
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

export interface AuthoritativeBattleSlowFieldState extends BattleStateSlowFieldResponseDto {
  fieldId: string;
  ownerPlayerId: string;
  ownerHeroId: string;
  position: AuthoritativeBattleVector;
  radius: number;
  ttlMs: number;
  durationMs: number;
}

export type AuthoritativeBattlePickupKind = BattlePickupKindDto;

export interface AuthoritativeBattlePickupState extends BattleStatePickupResponseDto {
  pickupId: string;
  kind: AuthoritativeBattlePickupKind;
  weaponKind?: AuthoritativeBattleWeaponKind;
  position: AuthoritativeBattleVector;
  available: boolean;
  respawnMs: number;
}

export interface AuthoritativeBattleEventParticipant extends BattleStateEventParticipantResponseDto {
  playerId: string;
  heroId: string;
  displayName: string;
}

export interface AuthoritativeBattleEventState extends BattleStateEventResponseDto {
  eventId: string;
  type: BattleEventKindDto;
  kind: BattleEventKindDto;
  elapsedMs: number;
  message: string;
  source: AuthoritativeBattleEventParticipant;
  target: AuthoritativeBattleEventParticipant;
}

export interface AuthoritativeBattleGasZoneState extends BattleGasZoneResponseDto {}
export interface AuthoritativeBattleExtractionState extends BattleExtractionResponseDto {}
export interface AuthoritativeBattleLootCacheState extends BattleLootCacheResponseDto {}

export interface AuthoritativeBattleState extends BattleStateResponseDto {
  battleId: string;
  roomId: string;
  mapId: string;
  phase: AuthoritativeBattlePhase;
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
  gasZone: AuthoritativeBattleGasZoneState | null;
  extraction: AuthoritativeBattleExtractionState | null;
  lootCaches: AuthoritativeBattleLootCacheState[];
  events: AuthoritativeBattleEventState[];
  winnerPlayerId?: string;
  winnerHeroId?: string;
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
  castCritical: boolean;
  pointerWorld?: AuthoritativeBattleVector | null;
  switchWeaponDirection: -1 | 0 | 1;
  switchWeaponIndex: number | null;
}

export interface AuthoritativeBattleCommandAccepted extends BattleCommandAcceptedResponseDto {
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
  transport?: "channel" | "stream";
  sendCommand?: (request: BattleCommandAPIMessageRequest) => Promise<AuthoritativeBattleCommandSubmitOutcome | null>;
}

export interface AuthoritativeBattleStateStreamOptions {
  onState: (state: AuthoritativeBattleState) => void;
  onFallback: () => void;
}

const BATTLE_REQUEST_TIMEOUT_MS = BATTLE_RUNTIME_REQUEST_TIMEOUT_MS;
const CONFIGURED_BATTLE_API_BASE = (import.meta.env.VITE_BATTLE_API_BASE ?? "").trim();
const CONFIGURED_BATTLE_REALTIME_API_BASE = (import.meta.env.VITE_BATTLE_REALTIME_API_BASE ?? "").trim();
const BATTLE_STATE_STREAM_API_BASE = normalizeApiBase(CONFIGURED_BATTLE_API_BASE, "/api");
const BATTLE_LOCAL_BACKEND_API_BASE = "http://127.0.0.1:8080/api";
const BATTLE_CHANNEL_PATH = "/battle/channel";
const BATTLE_DIRECT_COMMAND_PATH = "/battle/command";
const BATTLE_COMMAND_CHANNEL_PATH = "/battle/command/channel";
const BATTLE_COMMAND_CHANNEL_TIMEOUT_MS = Math.min(BATTLE_REQUEST_TIMEOUT_MS, 1_000);
const BATTLE_CHANNEL_OPEN_TIMEOUT_MS = Math.min(BATTLE_REQUEST_TIMEOUT_MS, 3_000);

let activeBattleChannel: BattleFullChannel | null = null;
let activeBattleCommandChannel: BattleCommandChannel | null = null;

export async function loadAuthoritativeBattleState(battleId: string): Promise<AuthoritativeBattleState | null> {
  const normalizedBattleId = battleId.trim();
  if (!normalizedBattleId || typeof window === "undefined") {
    return null;
  }

  const response = await postBattleStateReadAPIMessage(
    { battleId: normalizedBattleId },
    normalizeAuthoritativeBattleState,
    { timeoutMs: BATTLE_REQUEST_TIMEOUT_MS, cache: "no-store" }
  );
  return response?.ok ? response.payload : null;
}

/** 涓枃鍚嶏細openauthoritative鎴樻枟鐘舵€乻tream锛坥penAuthoritativeBattleStateStream锛夈€傛父鎴忚亴璐ｏ細鍦ㄥ墠绔垬鏂楀煙涓粍缁囨垬鏂楃晫闈€佺姸鎬併€佽緭鍏ユ垨娓叉煋鏁版嵁锛屼繚鎸佸鎴风鐜╂硶琛ㄨ揪涓庡悗绔绾︿竴鑷?*/
export function openAuthoritativeBattleStateStream(
  battleId: string,
  options: AuthoritativeBattleStateStreamOptions
): AuthoritativeBattleStateStreamHandle | null {
  const normalizedBattleId = battleId.trim();
  if (!normalizedBattleId || typeof window === "undefined") {
    return null;
  }

  const battleChannel = openAuthoritativeBattleChannel(normalizedBattleId, options);
  if (battleChannel) {
    return battleChannel;
  }

  if (typeof window.EventSource === "undefined") {
    return null;
  }

  let closed = false;
  let fallbackNotified = false;
  const stream = new window.EventSource(
    buildApiUrl(resolveBattleRealtimeApiBase(), `/battle/state/stream?battleId=${encodeURIComponent(normalizedBattleId)}`)
  );

  const notifyFallback = (): void => {
    if (closed || fallbackNotified) {
      return;
    }

    fallbackNotified = true;
    stream.close();
    options.onFallback();
  };

  stream.addEventListener("state", (event) => {
    try {
      const payload = JSON.parse((event as MessageEvent<string>).data) as unknown;
      const state = normalizeAuthoritativeBattleState(payload);
      if (!state) {
        notifyFallback();
        return;
      }

      options.onState(state);
    } catch {
      notifyFallback();
    }
  });
  stream.onerror = notifyFallback;

  return {
    transport: "stream",
    close: () => {
      closed = true;
      stream.close();
    }
  };
}

function openAuthoritativeBattleChannel(
  battleId: string,
  options: AuthoritativeBattleStateStreamOptions
): AuthoritativeBattleStateStreamHandle | null {
  if (typeof window.WebSocket === "undefined") {
    return null;
  }

  const channelUrl = buildBattleChannelUrl(battleId);
  if (!channelUrl) {
    return null;
  }

  activeBattleChannel?.close();
  const channel = new BattleFullChannel(channelUrl, battleId, options);
  activeBattleChannel = channel;
  channel.open();
  return {
    transport: "channel",
    close: () => {
      if (activeBattleChannel === channel) {
        activeBattleChannel = null;
      }
      channel.close();
    },
    sendCommand: (request) => channel.sendCommand(request)
  };
}

export async function sendAuthoritativeBattleCommand(
  command: AuthoritativeBattleCommand
): Promise<AuthoritativeBattleCommandSubmitOutcome> {
  const normalizedBattleId = command.battleId.trim();
  const normalizedPlayerId = command.playerId.trim();
  const normalizedTicketId = command.ticketId.trim();
  if (
    !normalizedBattleId ||
    !normalizedPlayerId ||
    !normalizedTicketId ||
    typeof window === "undefined"
  ) {
    return { ok: false, kind: "network" };
  }

  try {
    const request: BattleCommandAPIMessageRequest = {
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
      castCritical: command.castCritical,
      pointerWorld: command.pointerWorld ? normalizeWorldPoint(command.pointerWorld) : null,
      switchWeaponDirection: normalizeSwitchDirection(command.switchWeaponDirection),
      switchWeaponIndex: normalizeSwitchWeaponIndex(command.switchWeaponIndex)
    };
    const battleChannelResponse = await postBattleChannelAuthoritativeBattleCommand(request);
    if (battleChannelResponse) {
      return battleChannelResponse;
    }

    const channelResponse = await postChannelAuthoritativeBattleCommand(request);
    if (channelResponse) {
      return channelResponse;
    }

    const directResponse = await postDirectAuthoritativeBattleCommand(request);
    if (directResponse) {
      return directResponse;
    }

    const response = await postBattleCommandAPIMessage(
      request,
      normalizeBattleCommandAPIMessagePayload,
      { timeoutMs: BATTLE_REQUEST_TIMEOUT_MS }
    );

    if (!response) {
      return { ok: false, kind: "network" };
    }

    if (!response.ok) {
      return response.payload?.kind === "error" && response.payload.errorCode
        ? { ok: false, kind: "http", status: response.status, errorCode: response.payload.errorCode }
        : { ok: false, kind: "http", status: response.status };
    }

    return response.payload?.kind === "accepted"
      ? { ok: true, accepted: response.payload.accepted }
      : { ok: false, kind: "parse" };
  } catch {
    return { ok: false, kind: "network" };
  }
}

async function postBattleChannelAuthoritativeBattleCommand(
  request: BattleCommandAPIMessageRequest
): Promise<AuthoritativeBattleCommandSubmitOutcome | null> {
  if (!activeBattleChannel || activeBattleChannel.battleId !== request.battleId) {
    return null;
  }

  return activeBattleChannel.sendCommand(request);
}

async function postChannelAuthoritativeBattleCommand(
  request: BattleCommandAPIMessageRequest
): Promise<AuthoritativeBattleCommandSubmitOutcome | null> {
  if (typeof window === "undefined" || typeof window.WebSocket === "undefined") {
    return null;
  }

  const channelUrl = buildBattleCommandChannelUrl();
  if (!channelUrl) {
    return null;
  }

  if (!activeBattleCommandChannel || activeBattleCommandChannel.key !== channelUrl) {
    activeBattleCommandChannel?.close();
    activeBattleCommandChannel = new BattleCommandChannel(channelUrl);
  }

  return activeBattleCommandChannel.send(request);
}

async function postDirectAuthoritativeBattleCommand(
  request: BattleCommandAPIMessageRequest
): Promise<AuthoritativeBattleCommandSubmitOutcome | null> {
  const controller = new AbortController();
  const timeoutMs = Math.max(0, Math.trunc(BATTLE_REQUEST_TIMEOUT_MS));
  const timeout =
    timeoutMs > 0
      ? window.setTimeout(() => {
          controller.abort();
        }, timeoutMs)
      : null;

  try {
    const response = await fetch(buildApiUrl(resolveBattleRealtimeApiBase(), BATTLE_DIRECT_COMMAND_PATH), {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(request),
      cache: "no-store",
      signal: controller.signal
    });
    const rawPayload = await response.json().catch(() => null);
    const payload = normalizeBattleCommandAPIMessagePayload(rawPayload);

    if (!response.ok) {
      return payload?.kind === "error" && payload.errorCode
        ? { ok: false, kind: "http", status: response.status, errorCode: payload.errorCode }
        : { ok: false, kind: "http", status: response.status };
    }

    return payload?.kind === "accepted"
      ? { ok: true, accepted: payload.accepted }
      : { ok: false, kind: "parse" };
  } catch {
    return null;
  } finally {
    if (timeout !== null) {
      window.clearTimeout(timeout);
    }
  }
}

function resolveBattleRealtimeApiBase(): string {
  if (CONFIGURED_BATTLE_REALTIME_API_BASE) {
    return normalizeApiBase(CONFIGURED_BATTLE_REALTIME_API_BASE, "/api");
  }

  if (!CONFIGURED_BATTLE_API_BASE && typeof window !== "undefined" && isLoopbackHost(window.location.hostname)) {
    return BATTLE_LOCAL_BACKEND_API_BASE;
  }

  return BATTLE_STATE_STREAM_API_BASE;
}

function isLoopbackHost(hostname: string): boolean {
  const normalized = hostname.trim().toLowerCase();
  return normalized === "localhost" || normalized === "127.0.0.1" || normalized === "::1" || normalized === "[::1]";
}

function buildBattleCommandChannelUrl(): string | null {
  try {
    const httpUrl = buildApiUrl(resolveBattleRealtimeApiBase(), BATTLE_COMMAND_CHANNEL_PATH);
    const url = new URL(httpUrl, window.location.href);
    url.protocol = url.protocol === "https:" ? "wss:" : "ws:";
    return url.toString();
  } catch {
    return null;
  }
}

function buildBattleChannelUrl(battleId: string): string | null {
  try {
    const httpUrl = buildApiUrl(
      resolveBattleRealtimeApiBase(),
      `${BATTLE_CHANNEL_PATH}?battleId=${encodeURIComponent(battleId)}`
    );
    const url = new URL(httpUrl, window.location.href);
    url.protocol = url.protocol === "https:" ? "wss:" : "ws:";
    return url.toString();
  } catch {
    return null;
  }
}

class BattleFullChannel {
  readonly key: string;
  readonly battleId: string;
  private socket: WebSocket | null = null;
  private opening: Promise<WebSocket | null> | null = null;
  private closed = false;
  private fallbackNotified = false;
  private readonly pendingBySeq = new Map<number, PendingBattleCommandChannelRequest>();
  private readonly pendingOrder: number[] = [];

  constructor(
    key: string,
    battleId: string,
    private readonly options: AuthoritativeBattleStateStreamOptions
  ) {
    this.key = key;
    this.battleId = battleId;
  }

  open(): Promise<WebSocket | null> {
    if (this.socket?.readyState === window.WebSocket.OPEN) {
      return Promise.resolve(this.socket);
    }

    if (this.socket?.readyState === window.WebSocket.CONNECTING && this.opening) {
      return this.opening;
    }

    const socket = new window.WebSocket(this.key);
    this.socket = socket;
    this.opening = new Promise<WebSocket | null>((resolve) => {
      const timeout = window.setTimeout(() => {
        if (this.socket === socket) {
          this.socket = null;
        }
        this.notifyFallback();
        socket.close();
        resolve(null);
      }, BATTLE_CHANNEL_OPEN_TIMEOUT_MS);

      socket.addEventListener("open", () => {
        window.clearTimeout(timeout);
        resolve(socket);
      }, { once: true });
      socket.addEventListener("error", () => {
        window.clearTimeout(timeout);
        if (this.socket === socket) {
          this.socket = null;
        }
        this.notifyFallback();
        resolve(null);
      }, { once: true });
      socket.addEventListener("close", () => {
        window.clearTimeout(timeout);
        if (this.socket === socket) {
          this.socket = null;
        }
        this.opening = null;
        this.resolveAllPending(null);
        this.notifyFallback();
      });
      socket.addEventListener("message", (event) => {
        this.handleMessage(event.data);
      });
    }).finally(() => {
      if (this.socket !== socket || socket.readyState !== window.WebSocket.OPEN) {
        this.opening = null;
      }
    });

    return this.opening;
  }

  async sendCommand(request: BattleCommandAPIMessageRequest): Promise<AuthoritativeBattleCommandSubmitOutcome | null> {
    if (request.battleId !== this.battleId) {
      return null;
    }

    const socket = await this.open();
    if (!socket || socket.readyState !== window.WebSocket.OPEN) {
      return null;
    }

    const seq = Math.max(0, Math.trunc(request.clientCommandSeq ?? 0));
    return new Promise<AuthoritativeBattleCommandSubmitOutcome | null>((resolve) => {
      const timeout = window.setTimeout(() => {
        this.removePending(seq);
        resolve(null);
      }, BATTLE_COMMAND_CHANNEL_TIMEOUT_MS);
      this.pendingBySeq.set(seq, { seq, resolve, timeout });
      this.pendingOrder.push(seq);

      try {
        socket.send(JSON.stringify(request));
      } catch {
        window.clearTimeout(timeout);
        this.removePending(seq);
        resolve(null);
      }
    });
  }

  close(): void {
    this.closed = true;
    const socket = this.socket;
    this.socket = null;
    this.opening = null;
    this.resolveAllPending(null);
    if (socket && socket.readyState !== window.WebSocket.CLOSED && socket.readyState !== window.WebSocket.CLOSING) {
      socket.close();
    }
  }

  private handleMessage(data: unknown): void {
    const text = typeof data === "string" ? data : "";
    if (!text) {
      return;
    }

    let parsed: unknown;
    try {
      parsed = JSON.parse(text) as unknown;
    } catch {
      this.notifyFallback();
      return;
    }

    if (!parsed || typeof parsed !== "object") {
      return;
    }

    const kind = (parsed as { readonly kind?: unknown }).kind;
    if (kind === "state") {
      const statePayload = (parsed as { readonly state?: unknown }).state;
      const state = normalizeAuthoritativeBattleState(statePayload);
      if (!state) {
        this.notifyFallback();
        return;
      }

      this.options.onState(state);
      return;
    }

    if (kind === "command") {
      this.handleCommandPayload((parsed as { readonly payload?: unknown }).payload);
    }
  }

  private handleCommandPayload(payloadSource: unknown): void {
    const payload = normalizeBattleCommandAPIMessagePayload(payloadSource);
    if (!payload) {
      this.resolveOldestPending({ ok: false, kind: "parse" });
      return;
    }

    if (payload.kind === "accepted") {
      const pending = this.takePending(payload.accepted.acceptedCommandSeq) ?? this.takeOldestPending();
      pending?.resolve({ ok: true, accepted: payload.accepted });
      return;
    }

    this.resolveOldestPending(
      payload.errorCode
        ? { ok: false, kind: "http", status: 400, errorCode: payload.errorCode }
        : { ok: false, kind: "http", status: 400 }
    );
  }

  private notifyFallback(): void {
    if (this.closed || this.fallbackNotified) {
      return;
    }

    this.fallbackNotified = true;
    if (activeBattleChannel === this) {
      activeBattleChannel = null;
    }
    this.options.onFallback();
  }

  private resolveOldestPending(outcome: AuthoritativeBattleCommandSubmitOutcome | null): void {
    this.takeOldestPending()?.resolve(outcome);
  }

  private resolveAllPending(outcome: AuthoritativeBattleCommandSubmitOutcome | null): void {
    for (const pending of Array.from(this.pendingBySeq.values())) {
      window.clearTimeout(pending.timeout);
      pending.resolve(outcome);
    }
    this.pendingBySeq.clear();
    this.pendingOrder.length = 0;
  }

  private takePending(seq: number): PendingBattleCommandChannelRequest | null {
    const pending = this.pendingBySeq.get(seq) ?? null;
    if (!pending) {
      return null;
    }

    this.pendingBySeq.delete(seq);
    window.clearTimeout(pending.timeout);
    const index = this.pendingOrder.indexOf(seq);
    if (index >= 0) {
      this.pendingOrder.splice(index, 1);
    }
    return pending;
  }

  private takeOldestPending(): PendingBattleCommandChannelRequest | null {
    while (this.pendingOrder.length > 0) {
      const seq = this.pendingOrder.shift();
      if (seq === undefined) {
        break;
      }

      const pending = this.pendingBySeq.get(seq) ?? null;
      if (pending) {
        this.pendingBySeq.delete(seq);
        window.clearTimeout(pending.timeout);
        return pending;
      }
    }
    return null;
  }

  private removePending(seq: number): void {
    this.pendingBySeq.delete(seq);
    const index = this.pendingOrder.indexOf(seq);
    if (index >= 0) {
      this.pendingOrder.splice(index, 1);
    }
  }
}

interface PendingBattleCommandChannelRequest {
  readonly seq: number;
  readonly resolve: (outcome: AuthoritativeBattleCommandSubmitOutcome | null) => void;
  readonly timeout: number;
}

class BattleCommandChannel {
  readonly key: string;
  private socket: WebSocket | null = null;
  private opening: Promise<WebSocket | null> | null = null;
  private readonly pendingBySeq = new Map<number, PendingBattleCommandChannelRequest>();
  private readonly pendingOrder: number[] = [];

  constructor(key: string) {
    this.key = key;
  }

  async send(request: BattleCommandAPIMessageRequest): Promise<AuthoritativeBattleCommandSubmitOutcome | null> {
    const socket = await this.open();
    if (!socket || socket.readyState !== window.WebSocket.OPEN) {
      return null;
    }

    const seq = Math.max(0, Math.trunc(request.clientCommandSeq ?? 0));
    return new Promise<AuthoritativeBattleCommandSubmitOutcome | null>((resolve) => {
      const timeout = window.setTimeout(() => {
        this.removePending(seq);
        resolve(null);
      }, BATTLE_COMMAND_CHANNEL_TIMEOUT_MS);
      this.pendingBySeq.set(seq, { seq, resolve, timeout });
      this.pendingOrder.push(seq);

      try {
        socket.send(JSON.stringify(request));
      } catch {
        window.clearTimeout(timeout);
        this.removePending(seq);
        resolve(null);
      }
    });
  }

  close(): void {
    const socket = this.socket;
    this.socket = null;
    this.opening = null;
    this.resolveAllPending(null);
    if (socket && socket.readyState !== window.WebSocket.CLOSED && socket.readyState !== window.WebSocket.CLOSING) {
      socket.close();
    }
  }

  private open(): Promise<WebSocket | null> {
    if (this.socket?.readyState === window.WebSocket.OPEN) {
      return Promise.resolve(this.socket);
    }

    if (this.socket?.readyState === window.WebSocket.CONNECTING && this.opening) {
      return this.opening;
    }

    const socket = new window.WebSocket(this.key);
    this.socket = socket;
    this.opening = new Promise<WebSocket | null>((resolve) => {
      const timeout = window.setTimeout(() => {
        if (this.socket === socket) {
          this.socket = null;
        }
        socket.close();
        resolve(null);
      }, BATTLE_COMMAND_CHANNEL_TIMEOUT_MS);

      socket.addEventListener("open", () => {
        window.clearTimeout(timeout);
        resolve(socket);
      }, { once: true });
      socket.addEventListener("error", () => {
        window.clearTimeout(timeout);
        if (this.socket === socket) {
          this.socket = null;
        }
        resolve(null);
      }, { once: true });
      socket.addEventListener("close", () => {
        window.clearTimeout(timeout);
        if (this.socket === socket) {
          this.socket = null;
        }
        this.opening = null;
        this.resolveAllPending(null);
      });
      socket.addEventListener("message", (event) => {
        this.handleMessage(event.data);
      });
    }).finally(() => {
      if (this.socket !== socket || socket.readyState !== window.WebSocket.OPEN) {
        this.opening = null;
      }
    });

    return this.opening;
  }

  private handleMessage(data: unknown): void {
    const text = typeof data === "string" ? data : "";
    if (!text) {
      return;
    }

    let parsed: unknown;
    try {
      parsed = JSON.parse(text) as unknown;
    } catch {
      this.resolveOldestPending({ ok: false, kind: "parse" });
      return;
    }

    const payload = normalizeBattleCommandAPIMessagePayload(parsed);
    if (!payload) {
      this.resolveOldestPending({ ok: false, kind: "parse" });
      return;
    }

    if (payload.kind === "accepted") {
      const pending = this.takePending(payload.accepted.acceptedCommandSeq) ?? this.takeOldestPending();
      pending?.resolve({ ok: true, accepted: payload.accepted });
      return;
    }

    this.resolveOldestPending(
      payload.errorCode
        ? { ok: false, kind: "http", status: 400, errorCode: payload.errorCode }
        : { ok: false, kind: "http", status: 400 }
    );
  }

  private resolveOldestPending(outcome: AuthoritativeBattleCommandSubmitOutcome | null): void {
    this.takeOldestPending()?.resolve(outcome);
  }

  private resolveAllPending(outcome: AuthoritativeBattleCommandSubmitOutcome | null): void {
    for (const pending of Array.from(this.pendingBySeq.values())) {
      window.clearTimeout(pending.timeout);
      pending.resolve(outcome);
    }
    this.pendingBySeq.clear();
    this.pendingOrder.length = 0;
  }

  private takePending(seq: number): PendingBattleCommandChannelRequest | null {
    const pending = this.pendingBySeq.get(seq) ?? null;
    if (!pending) {
      return null;
    }

    this.pendingBySeq.delete(seq);
    window.clearTimeout(pending.timeout);
    const index = this.pendingOrder.indexOf(seq);
    if (index >= 0) {
      this.pendingOrder.splice(index, 1);
    }
    return pending;
  }

  private takeOldestPending(): PendingBattleCommandChannelRequest | null {
    while (this.pendingOrder.length > 0) {
      const seq = this.pendingOrder.shift();
      if (seq === undefined) {
        break;
      }

      const pending = this.pendingBySeq.get(seq) ?? null;
      if (pending) {
        this.pendingBySeq.delete(seq);
        window.clearTimeout(pending.timeout);
        return pending;
      }
    }
    return null;
  }

  private removePending(seq: number): void {
    this.pendingBySeq.delete(seq);
    const index = this.pendingOrder.indexOf(seq);
    if (index >= 0) {
      this.pendingOrder.splice(index, 1);
    }
  }
}

