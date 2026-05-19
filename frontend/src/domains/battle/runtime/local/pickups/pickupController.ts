import type { Hero, ItemPickup, WeaponPickup } from "../../../objects/types";
import { createWeaponState, findWeaponIndex, refillWeaponState } from "../../../game/weapons";
import { getWeaponDisplayLabel } from "../../../components/presenters/battleDisplayCatalog";

export interface AutomaticPickupControllerInput {
  player: Hero;
  weaponPickups: readonly WeaponPickup[];
  itemPickups: readonly ItemPickup[];
  autoPickupRadius: number;
}

export interface AutomaticPickupPresentation {
  floatingText: string;
  tone: "success";
}

export interface AutomaticPickupEvent {
  type: "pickup" | "heal";
  message: string;
}

export interface AutomaticWeaponPickupResult {
  pickup: WeaponPickup;
  presentation: AutomaticPickupPresentation;
  event: AutomaticPickupEvent;
}

export interface AutomaticItemPickupResult {
  pickup: ItemPickup;
  presentation: AutomaticPickupPresentation;
  pulse: {
    radius: number;
    color: number;
  };
  event: AutomaticPickupEvent;
}

export function applyAutomaticWeaponPickup(input: AutomaticPickupControllerInput): AutomaticWeaponPickupResult | null {
  if (!input.player.alive) {
    return null;
  }

  const pickup = findNearbyWeaponPickup(input.player.position, input.weaponPickups, input.autoPickupRadius);
  if (!pickup) {
    return null;
  }

  const existingIndex = findWeaponIndex(input.player.weapons, pickup.weaponKind);
  if (existingIndex >= 0) {
    input.player.weapons[existingIndex] = refillWeaponState(input.player.weapons[existingIndex]);
  } else {
    input.player.weapons.push(createWeaponState(pickup.weaponKind));
  }

  pickup.available = false;
  pickup.respawnMs = 10000;

  const weaponLabel = getWeaponDisplayLabel(pickup.weaponKind);
  return {
    pickup,
    presentation:
      existingIndex >= 0
        ? { floatingText: "\u83b7\u5f97\u6b66\u5668\u8865\u7ed9", tone: "success" }
        : { floatingText: `\u83b7\u5f97 ${weaponLabel}`, tone: "success" },
    event: {
      type: "pickup",
      message: `${input.player.displayName} \u83b7\u5f97\u4e86${weaponLabel}`
    }
  };
}

export function applyAutomaticItemPickup(input: AutomaticPickupControllerInput): AutomaticItemPickupResult | null {
  if (!input.player.alive) {
    return null;
  }

  const pickup = findNearbyItemPickup(input.player.position, input.itemPickups, input.autoPickupRadius);
  if (!pickup) {
    return null;
  }

  if (pickup.kind !== "Medkit") {
    return null;
  }

  const wasFullHp = input.player.hp >= input.player.maxHp;
  input.player.hp = Math.min(input.player.maxHp, input.player.hp + 25);
  pickup.available = false;
  pickup.respawnMs = 10000;

  return {
    pickup,
    presentation: {
      floatingText: wasFullHp ? "\u62a2\u5360\u533b\u7597\u5305" : "\u83b7\u5f97\u533b\u7597\u5305",
      tone: "success"
    },
    pulse: {
      radius: 40,
      color: 0x7bff9b
    },
    event: {
      type: "heal",
      message: `${input.player.displayName} ${wasFullHp ? "\u62a2\u5360\u4e86" : "\u62fe\u53d6\u4e86"}\u533b\u7597\u5305`
    }
  };
}

function findNearbyWeaponPickup(
  position: Hero["position"],
  pickups: readonly WeaponPickup[],
  radius: number
): WeaponPickup | null {
  let closest: WeaponPickup | null = null;
  let closestDistance = radius;

  pickups.forEach((pickup) => {
    if (!pickup.available) {
      return;
    }

    const distance = distanceBetween(position, pickup.position);
    if (distance <= closestDistance) {
      closest = pickup;
      closestDistance = distance;
    }
  });

  return closest;
}

function findNearbyItemPickup(position: Hero["position"], pickups: readonly ItemPickup[], radius: number): ItemPickup | null {
  let closest: ItemPickup | null = null;
  let closestDistance = radius;

  pickups.forEach((pickup) => {
    if (!pickup.available) {
      return;
    }

    const distance = distanceBetween(position, pickup.position);
    if (distance <= closestDistance) {
      closest = pickup;
      closestDistance = distance;
    }
  });

  return closest;
}

function distanceBetween(left: { x: number; y: number }, right: { x: number; y: number }): number {
  return Math.hypot(left.x - right.x, left.y - right.y);
}
