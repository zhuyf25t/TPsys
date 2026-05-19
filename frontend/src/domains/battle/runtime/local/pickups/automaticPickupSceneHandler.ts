import type { Hero, ItemPickup, Vec2, WeaponPickup } from "../../../objects/types";
import { applyAutomaticItemPickup, applyAutomaticWeaponPickup } from "./pickupController";

export interface AutomaticPickupSceneCallbacks {
  showFloatingText(position: Vec2, text: string, tone: "success"): void;
  createPulse(position: Vec2, radius: number, color: number): void;
  pushEvent(type: "pickup" | "heal", message: string): void;
}

export interface AutomaticPickupSceneInput {
  player: Hero;
  weaponPickups: readonly WeaponPickup[];
  itemPickups: readonly ItemPickup[];
  autoPickupRadius: number;
  callbacks: AutomaticPickupSceneCallbacks;
}

/** 中文名：玩家名automatic拾取物scene（handleAutomaticPickupScene）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function handleAutomaticPickupScene(input: AutomaticPickupSceneInput): void {
  const weaponResult = applyAutomaticWeaponPickup({
    player: input.player,
    weaponPickups: input.weaponPickups,
    itemPickups: input.itemPickups,
    autoPickupRadius: input.autoPickupRadius
  });

  if (weaponResult) {
    presentWeaponPickupResult(input.player.position, weaponResult, input.callbacks);
  }

  const itemResult = applyAutomaticItemPickup({
    player: input.player,
    weaponPickups: input.weaponPickups,
    itemPickups: input.itemPickups,
    autoPickupRadius: input.autoPickupRadius
  });

  if (itemResult) {
    presentItemPickupResult(input.player.position, itemResult, input.callbacks);
  }
}

function presentWeaponPickupResult(
  position: Vec2,
  result: NonNullable<ReturnType<typeof applyAutomaticWeaponPickup>>,
  callbacks: AutomaticPickupSceneCallbacks
): void {
  callbacks.showFloatingText(position, result.presentation.floatingText, result.presentation.tone);
  callbacks.pushEvent(result.event.type, result.event.message);
}

function presentItemPickupResult(
  position: Vec2,
  result: NonNullable<ReturnType<typeof applyAutomaticItemPickup>>,
  callbacks: AutomaticPickupSceneCallbacks
): void {
  callbacks.createPulse(position, result.pulse.radius, result.pulse.color);
  callbacks.showFloatingText(position, result.presentation.floatingText, result.presentation.tone);
  callbacks.pushEvent(result.event.type, result.event.message);
}
