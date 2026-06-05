import {
  postBattleCommandAPIMessage,
  postBattleStateReadAPIMessage
} from "../../../../../apis/battle/microservices/session/api/BattleSessionApiMessageClient";
import { buildApiUrl, normalizeApiBase } from "../../../../../system/api/apiUrl";
import type { BattleCommandAPIMessageRequest } from "../../../../../objects/battle/microservices/session/api/command/BattleCommandRequestApiTypes";
import type {
  BattleCommandAcceptedResponseDto,
  BattleCommandReasonDto,
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
}

export interface AuthoritativeBattleStateStreamOptions {
  onState: (state: AuthoritativeBattleState) => void;
  onFallback: () => void;
}

const BATTLE_REQUEST_TIMEOUT_MS = 1_250;
const BATTLE_STATE_STREAM_API_BASE = normalizeApiBase(import.meta.env.VITE_BATTLE_API_BASE ?? "", "/api");

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
  if (!normalizedBattleId || typeof window === "undefined" || typeof window.EventSource === "undefined") {
    return null;
  }

  let closed = false;
  let fallbackNotified = false;
  const stream = new window.EventSource(
    buildApiUrl(BATTLE_STATE_STREAM_API_BASE, `/battle/state/stream?battleId=${encodeURIComponent(normalizedBattleId)}`)
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
    close: () => {
      closed = true;
      stream.close();
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

