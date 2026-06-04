import type {
  BattleItemPickupState as ItemPickup,
  BattleWeaponPickupState as WeaponPickup
} from "../../../../../objects/battle/microservices/abilities/objects/pickup/BattlePickupState";
import type { BattleVector2 as Vec2 } from "../../../../../objects/battle/objects/core/BattleCoreScalars";

export interface BattlePickupFeedbackState {
  available: boolean;
  position: Vec2;
}

export interface BattlePickupFeedbackPlan {
  floatingText: {
    position: Vec2;
    text: string;
    tone: "success";
  };
  pulse: {
    position: Vec2;
    radius: number;
    color: number;
  };
}

export interface ResolveBattleWeaponPickupFeedbackInput {
  pickup: WeaponPickup;
  previous: BattlePickupFeedbackState | undefined;
}

export interface ResolveBattleItemPickupFeedbackInput {
  pickup: ItemPickup;
  previous: BattlePickupFeedbackState | undefined;
}

const WEAPON_PICKUP_TEXT = "\u62fe\u53d6\u6b66\u5668";
const ITEM_PICKUP_TEXT = "\u62fe\u53d6\u8865\u7ed9";

export function createBattleWeaponPickupFeedbackState(pickup: WeaponPickup): BattlePickupFeedbackState {
  return {
    available: pickup.available,
    position: cloneVector(pickup.position)
  };
}

export function createBattleItemPickupFeedbackState(pickup: ItemPickup): BattlePickupFeedbackState {
  return {
    available: pickup.available,
    position: cloneVector(pickup.position)
  };
}

export function resolveBattleWeaponPickupFeedbackPlan(
  input: ResolveBattleWeaponPickupFeedbackInput
): BattlePickupFeedbackPlan | null {
  if (!input.previous?.available || input.pickup.available) {
    return null;
  }

  return createPickupFeedbackPlan(input.previous.position, WEAPON_PICKUP_TEXT, 0x9dffb4);
}

export function resolveBattleItemPickupFeedbackPlan(
  input: ResolveBattleItemPickupFeedbackInput
): BattlePickupFeedbackPlan | null {
  if (!input.previous?.available || input.pickup.available) {
    return null;
  }

  return createPickupFeedbackPlan(input.previous.position, ITEM_PICKUP_TEXT, 0x7dff9d);
}

function createPickupFeedbackPlan(position: Vec2, text: string, color: number): BattlePickupFeedbackPlan {
  const feedbackPosition = cloneVector(position);
  return {
    floatingText: {
      position: feedbackPosition,
      text,
      tone: "success"
    },
    pulse: {
      position: feedbackPosition,
      radius: 34,
      color
    }
  };
}

function cloneVector(vector: Vec2): Vec2 {
  return { x: vector.x, y: vector.y };
}
