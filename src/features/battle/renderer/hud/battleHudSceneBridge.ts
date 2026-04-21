import type { GameSnapshot } from "../../../../domain/types";
import { WEAPON_PICKUP_RADIUS } from "../../../../game/constants";
import { getSelectedSkillSlots } from "../../../../features/loadout/loadoutGateway";
import type { HudMinimapRect } from "../../../../ui/Hud";
import { formatMatchTime } from "../../presenters/battleDisplayCatalog";
import { findNearbyItemPickup, findNearbyPickup } from "../../runtime-local/pickups/pickupLifecycle";
import { Hud } from "../../../../ui/Hud";
import { createHudState } from "../../presenters/hudPresenter";
import { getCurrentWeapon } from "../../runtime-local/weapons/weaponActionController";

export interface BattleHudSceneBridgeContext {
  snapshot: GameSnapshot;
  fps: number;
  weaponSwitchRemainingMs: number;
  camera: {
    worldView: HudMinimapRect;
  };
}

export class BattleHudSceneBridge {
  private readonly hud: Hud;

  public constructor(rootId: string = "hud-root") {
    const root = document.getElementById(rootId);
    if (!root) {
      throw new Error(`Missing #${rootId}`);
    }

    this.hud = new Hud(root);
  }

  public layout(width: number, height: number): void {
    this.hud.layout(width, height);
  }

  public update(context: BattleHudSceneBridgeContext): void {
    const { snapshot, fps, weaponSwitchRemainingMs, camera } = context;
    const playerHero = snapshot.heroes.find((hero) => hero.heroId === snapshot.playerHeroId);
    if (!playerHero) {
      throw new Error(`Missing player hero ${snapshot.playerHeroId}`);
    }

    const hudState = createHudState({
      snapshot,
      playerHero,
      currentWeapon: getCurrentWeapon(playerHero),
      fps,
      timer: formatMatchTime(snapshot.elapsedMs),
      weaponSwitchRemainingMs,
      nearbyWeaponPickup: findNearbyPickup(playerHero.position, snapshot.weaponPickups, WEAPON_PICKUP_RADIUS),
      nearbyItemPickup: findNearbyItemPickup(playerHero.position, snapshot.itemPickups, WEAPON_PICKUP_RADIUS),
      skillBindings: getSelectedSkillSlots().map((slot) => ({
        key: slot.key,
        skillId: slot.skillId,
        label: slot.label
      })),
      cameraRect: camera.worldView
    });

    this.hud.update(hudState);
  }

  public destroy(): void {
    this.hud.destroy();
  }
}
