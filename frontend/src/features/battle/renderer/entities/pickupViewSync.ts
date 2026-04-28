import type { GameSnapshot } from "../../../../domain/types";
import {
  setPickupViewVisible,
  syncItemPickupView,
  syncWeaponPickupView,
  type PickupView
} from "./pickupViewPresentation";

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

export function syncPickupViews({ snapshot, worldViews }: PickupViewSyncContext): void {
  const liveWeaponPickupIds = worldViews.scratchLiveWeaponPickupIds;
  liveWeaponPickupIds.clear();
  snapshot.weaponPickups.forEach((pickup) => {
    liveWeaponPickupIds.add(pickup.weaponId);
  });

  for (const [weaponId, view] of worldViews.pickupViews.entries()) {
    if (liveWeaponPickupIds.has(weaponId)) {
      continue;
    }

    setPickupViewVisible(view, false);
  }

  const liveItemPickupIds = worldViews.scratchLiveItemPickupIds;
  liveItemPickupIds.clear();
  snapshot.itemPickups.forEach((pickup) => {
    liveItemPickupIds.add(pickup.pickupId);
  });

  for (const [pickupId, view] of worldViews.itemPickupViews.entries()) {
    if (liveItemPickupIds.has(pickupId)) {
      continue;
    }

    setPickupViewVisible(view, false);
  }

  snapshot.weaponPickups.forEach((pickup) => {
    const view = worldViews.pickupViews.get(pickup.weaponId);
    if (!view) {
      return;
    }

    syncWeaponPickupView(view, pickup, snapshot.elapsedMs);
  });

  snapshot.itemPickups.forEach((pickup) => {
    const view = worldViews.itemPickupViews.get(pickup.pickupId);
    if (!view) {
      return;
    }

    syncItemPickupView(view, pickup, snapshot.elapsedMs);
  });
}
