import type { BattleGameSnapshot as GameSnapshot } from "../../../../../../objects/battle/microservices/session/objects/state/BattleGameSnapshot";
import type { PickupView } from "./PickupViewPresentationObjects";

export interface PickupViewSyncState {
  pickupViews: Map<string, PickupView>;
  itemPickupViews: Map<string, PickupView>;
  scratchLiveWeaponPickupIds: Set<string>;
  scratchLiveItemPickupIds: Set<string>;
}

export interface PickupViewSyncContext {
  snapshot: GameSnapshot;
  worldViews: PickupViewSyncState;
}
