import type { GameSnapshot, Vec2 } from "../../../objects/types";
import { WEAPON_PICKUP_RADIUS } from "../../constants";
import { getSelectedSkillSlots } from "../../../api/loadoutGateway";
import type { HudMinimapRect } from "../../ui/Hud";
import { formatMatchTime } from "../../../components/presenters/battleDisplayCatalog";
import { findNearbyItemPickup, findNearbyPickup } from "../../../runtime/local/pickups/pickupLifecycle";
import { Hud } from "../../ui/Hud";
import { createHudState, type HudPresenterObstacleBounds } from "../../../components/presenters/hudPresenter";
import { getCurrentWeapon } from "../../../runtime/local/weapons/weaponActionController";

const HUD_UPDATE_INTERVAL_MS = 75;

export interface BattleHudSceneBridgeContext {
  snapshot: GameSnapshot;
  fps: number;
  weaponSwitchRemainingMs: number;
  sharedAuthoritativeHud: boolean;
  playerDisplayPosition?: Vec2;
  camera: {
    worldView: HudMinimapRect;
  };
  obstacleBounds: readonly HudPresenterObstacleBounds[];
}

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

    const { fps, weaponSwitchRemainingMs, sharedAuthoritativeHud, playerDisplayPosition, camera, obstacleBounds } = context;
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
      nearbyWeaponPickup: findNearbyPickup(nearbyPickupPosition, snapshot.weaponPickups, WEAPON_PICKUP_RADIUS),
      nearbyItemPickup: findNearbyItemPickup(nearbyPickupPosition, snapshot.itemPickups, WEAPON_PICKUP_RADIUS),
      skillBindings: sharedAuthoritativeHud
        ? []
        : getSelectedSkillSlots().map((slot) => ({
            key: slot.key,
            skillId: slot.skillId,
            label: slot.label
          })),
      cameraRect: camera.worldView,
      obstacleBounds
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
