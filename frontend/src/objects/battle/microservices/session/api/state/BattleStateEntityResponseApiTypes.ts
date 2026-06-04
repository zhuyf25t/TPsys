import type { PickupKind } from "../../../abilities/objects/pickup/PickupKind";
import type { WeaponKind } from "../../../combat/objects/weapon/WeaponKind";
import type { BattleEventKind } from "../../../runtime/objects/event/BattleEventKind";
import type { BattleApiVectorDto } from "./BattleStateSharedResponseApiTypes";

export type BattleEventKindDto = BattleEventKind;

export interface BattleStateSlowFieldResponseDto {
  fieldId: string;
  ownerPlayerId: string;
  ownerHeroId: string;
  position: BattleApiVectorDto;
  radius: number;
  ttlMs: number;
  durationMs: number;
}

export interface BattleStatePickupResponseDto {
  pickupId: string;
  kind: PickupKind;
  position: BattleApiVectorDto;
  available: boolean;
  respawnMs: number;
  weaponKind?: WeaponKind;
}

export interface BattleStateEventParticipantResponseDto {
  playerId: string;
  heroId: string;
  displayName: string;
}

export interface BattleStateEventResponseDto {
  eventId: string;
  type: BattleEventKindDto;
  kind: BattleEventKindDto;
  elapsedMs: number;
  message: string;
  source: BattleStateEventParticipantResponseDto;
  target: BattleStateEventParticipantResponseDto;
}

