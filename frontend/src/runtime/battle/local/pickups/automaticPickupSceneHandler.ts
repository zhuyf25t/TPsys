import type { BattleItemPickupState as ItemPickup, BattleWeaponPickupState as WeaponPickup } from "../../../../objects/battle/microservices/abilities/objects/pickup/BattlePickupState";
import type { BattleVector2 as Vec2 } from "../../../../objects/battle/objects/core/BattleCoreScalars";
import type { BattleHeroViewState as Hero } from "../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import {
  applyAutomaticItemPickup,
  applyAutomaticWeaponPickup,
  type AutomaticItemPickupResult,
  type AutomaticWeaponPickupResult
} from "../../microservices/abilities/functions/BattlePickupRules";
import { getItemPickupDisplayLabel, getWeaponDisplayLabel } from "../../game/presenters/battleDisplayCatalog";

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
    presentWeaponPickupResult(input.player.position, weaponResult, input.player.displayName, input.callbacks);
  }

  const itemResult = applyAutomaticItemPickup({
    player: input.player,
    weaponPickups: input.weaponPickups,
    itemPickups: input.itemPickups,
    autoPickupRadius: input.autoPickupRadius
  });

  if (itemResult) {
    presentItemPickupResult(input.player.position, itemResult, input.player.displayName, input.callbacks);
  }
}

function presentWeaponPickupResult(
  position: Vec2,
  result: AutomaticWeaponPickupResult,
  playerDisplayName: string,
  callbacks: AutomaticPickupSceneCallbacks
): void {
  const weaponLabel = getWeaponDisplayLabel(result.weaponKind);
  const floatingText = result.action === "refill" ? "\u83b7\u5f97\u6b66\u5668\u8865\u7ed9" : `\u83b7\u5f97 ${weaponLabel}`;

  callbacks.showFloatingText(position, floatingText, "success");
  callbacks.pushEvent("pickup", `${playerDisplayName} \u83b7\u5f97\u4e86${weaponLabel}`);
}

function presentItemPickupResult(
  position: Vec2,
  result: AutomaticItemPickupResult,
  playerDisplayName: string,
  callbacks: AutomaticPickupSceneCallbacks
): void {
  const pickupLabel = getItemPickupDisplayLabel(result.kind);
  const floatingText = result.wasFullHp ? "\u62a2\u5360\u533b\u7597\u5305" : "\u83b7\u5f97\u533b\u7597\u5305";
  const actionText = result.wasFullHp ? "\u62a2\u5360\u4e86" : "\u62fe\u53d6\u4e86";

  callbacks.createPulse(position, 40, 0x7bff9b);
  callbacks.showFloatingText(position, floatingText, "success");
  callbacks.pushEvent("heal", `${playerDisplayName} ${actionText}${pickupLabel}`);
}
