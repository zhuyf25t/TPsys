import type { WeaponKind } from "../../../combat/objects/weapon/WeaponKind";
import type { BattleVector2 } from "../../../../objects/core/BattleCoreScalars";
import type { ItemPickupKind, PickupKind } from "./PickupKind";

export type PickupId = string;

export interface BattlePickupState {
  pickupId: PickupId;
  kind: PickupKind;
  weaponKind?: WeaponKind;
  position: BattleVector2;
  available: boolean;
  respawnMs: number;
}

export interface BattleWeaponPickupState {
  pickupId: PickupId;
  weaponKind: WeaponKind;
  position: BattleVector2;
  available: boolean;
  respawnMs: number;
}

export interface BattleItemPickupState {
  pickupId: PickupId;
  kind: ItemPickupKind;
  position: BattleVector2;
  available: boolean;
  respawnMs: number;
}
