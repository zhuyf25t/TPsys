import {
  resolveHiddenPickupViewIds,
  resolveLiveItemPickupIds,
  resolveLiveWeaponPickupIds
} from "./functions/PickupViewSyncRules";
import {
  setPickupViewVisible,
  syncItemPickupView,
  syncWeaponPickupView,
  type PickupView
} from "./pickupViewPresentation";
import type { PickupViewSyncContext } from "./objects/PickupViewSyncObjects";

export type {
  PickupViewSyncContext,
  PickupViewSyncState
} from "./objects/PickupViewSyncObjects";

export function syncPickupViews({ snapshot, worldViews }: PickupViewSyncContext): void {
  const liveWeaponPickupIds = worldViews.scratchLiveWeaponPickupIds;
  replaceScratchPickupIds(liveWeaponPickupIds, resolveLiveWeaponPickupIds(snapshot.weaponPickups));
  resolveHiddenPickupViewIds(liveWeaponPickupIds, worldViews.pickupViews.keys()).forEach((pickupId) =>
    setHiddenPickupView(worldViews.pickupViews, pickupId)
  );

  const liveItemPickupIds = worldViews.scratchLiveItemPickupIds;
  replaceScratchPickupIds(liveItemPickupIds, resolveLiveItemPickupIds(snapshot.itemPickups));
  resolveHiddenPickupViewIds(liveItemPickupIds, worldViews.itemPickupViews.keys()).forEach((pickupId) =>
    setHiddenPickupView(worldViews.itemPickupViews, pickupId)
  );

  snapshot.weaponPickups.forEach((pickup) => {
    const view = worldViews.pickupViews.get(pickup.pickupId);
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

function replaceScratchPickupIds(target: Set<string>, source: ReadonlySet<string>): void {
  target.clear();
  source.forEach((pickupId) => {
    target.add(pickupId);
  });
}

function setHiddenPickupView(pickupViews: Map<string, PickupView>, pickupId: string): void {
  const view = pickupViews.get(pickupId);
  if (view) {
    setPickupViewVisible(view, false);
  }
}
