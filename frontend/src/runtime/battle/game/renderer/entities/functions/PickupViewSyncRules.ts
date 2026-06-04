import type { BattleItemPickupState as ItemPickup, BattleWeaponPickupState as WeaponPickup } from "../../../../../../objects/battle/microservices/abilities/objects/pickup/BattlePickupState";

export function resolveLiveWeaponPickupIds(pickups: readonly WeaponPickup[]): Set<string> {
  return resolveLivePickupIds(pickups);
}

export function resolveLiveItemPickupIds(pickups: readonly ItemPickup[]): Set<string> {
  return resolveLivePickupIds(pickups);
}

export function resolveHiddenPickupViewIds(livePickupIds: ReadonlySet<string>, viewIds: Iterable<string>): string[] {
  const hiddenIds: string[] = [];
  for (const pickupId of viewIds) {
    if (!livePickupIds.has(pickupId)) {
      hiddenIds.push(pickupId);
    }
  }
  return hiddenIds;
}

function resolveLivePickupIds(pickups: readonly { pickupId: string }[]): Set<string> {
  const pickupIds = new Set<string>();
  pickups.forEach((pickup) => {
    pickupIds.add(pickup.pickupId);
  });
  return pickupIds;
}
