import type { BattleWeaponSwitchDirectionStep } from "../../../combat/objects/weapon/BattleWeaponSwitchDirection";
import type { BattleApiVectorDto } from "../state/BattleStateSharedResponseApiTypes";

export interface BattleCommandAPIMessageRequest {
  userToken?: string;
  battleId: string;
  playerId: string;
  ticketId: string;
  clientTick: number;
  clientCommandSeq?: number;
  movement: BattleApiVectorDto;
  aim: BattleApiVectorDto;
  primaryHeld: boolean;
  sprint?: boolean;
  reloadPressed: boolean;
  castDash?: boolean;
  castBlink?: boolean;
  castFreeze?: boolean;
  castCritical?: boolean;
  pointerWorld?: BattleApiVectorDto | null;
  switchWeaponDirection: BattleWeaponSwitchDirectionStep;
  switchWeaponIndex?: number | null;
}
