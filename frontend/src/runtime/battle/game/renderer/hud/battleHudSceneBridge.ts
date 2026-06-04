import { WEAPON_PICKUP_RADIUS } from "../../objects/BattleGameConstants";
import { getSelectedSkillSlots } from "../../../loadout/BattleLoadoutStore";
import { formatMatchTime } from "../../../game/presenters/battleDisplayCatalog";
import { findNearbyItemPickup, findNearbyWeaponPickup } from "../../../microservices/abilities/functions/BattlePickupRules";
import { Hud } from "../../ui/Hud";
import { createHudState } from "../../presenters/hudPresenter";
import { getCurrentWeapon } from "../../../microservices/combat/functions/BattleWeaponInventoryRules";
import type { BattleHudSceneBridgeContext } from "./objects/BattleHudSceneBridgeObjects";

const HUD_UPDATE_INTERVAL_MS = 75;

export class BattleHudSceneBridge {
  private readonly hud: Hud;
  private lastHudRenderElapsedMs: number | null = null;

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
    const { snapshot } = context;
    if (!this.shouldRenderHud(snapshot.elapsedMs)) {
      return;
    }
    this.markHudRendered(snapshot.elapsedMs);

    const { fps, weaponSwitchRemainingMs, sharedAuthoritativeHud, playerDisplayPosition, camera, obstacleBounds, mapExpanded } = context;
    const playerHero = snapshot.heroes.find((hero) => hero.heroId === snapshot.playerHeroId);
    if (!playerHero) {
      throw new Error(`Missing player hero ${snapshot.playerHeroId}`);
    }

    const nearbyPickupPosition = playerDisplayPosition ?? playerHero.position;
    const hudState = createHudState({
      snapshot,
      playerHero,
      currentWeapon: getCurrentWeapon(playerHero),
      fps,
      timer: formatMatchTime(snapshot.elapsedMs),
      weaponSwitchRemainingMs,
      sharedAuthoritativeHud,
      nearbyWeaponPickup: findNearbyWeaponPickup(nearbyPickupPosition, snapshot.weaponPickups, WEAPON_PICKUP_RADIUS),
      nearbyItemPickup: findNearbyItemPickup(nearbyPickupPosition, snapshot.itemPickups, WEAPON_PICKUP_RADIUS),
      skillBindings: getSelectedSkillSlots().map((slot) => ({
        key: slot.key,
        skillId: slot.skillId,
        label: slot.label
      })),
      cameraRect: camera.worldView,
      obstacleBounds,
      mapExpanded
    });

    this.hud.update(hudState);
  }

  private shouldRenderHud(elapsedMs: number): boolean {
    if (!Number.isFinite(elapsedMs)) {
      return true;
    }

    if (this.lastHudRenderElapsedMs === null) {
      return true;
    }

    if (elapsedMs < this.lastHudRenderElapsedMs) {
      return true;
    }

    return elapsedMs - this.lastHudRenderElapsedMs >= HUD_UPDATE_INTERVAL_MS;
  }

  private markHudRendered(elapsedMs: number): void {
    this.lastHudRenderElapsedMs = Number.isFinite(elapsedMs) ? elapsedMs : null;
  }

  public destroy(): void {
    this.hud.destroy();
  }
}
