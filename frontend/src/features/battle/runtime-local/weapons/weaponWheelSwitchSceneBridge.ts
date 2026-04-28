import type { Hero, Vec2 } from "../../../../domain/types";
import type { WeaponSwitchStateBridge } from "./weaponSwitchStateBridge";

type WeaponWheelSwitchSource = "Phaser" | "Window";
type FloatingTone = "success" | "neutral" | "warning" | "error";

const WEAPON_SWITCH_NOTICE_TEXT = "\u6b63\u5728\u5207\u67aa";

export interface WeaponWheelSwitchSceneBridgeOptions {
  getPlayerHero(): Hero;
  isAuthoritativeRendererHost(): boolean;
  getNowMs(): number;
  weaponSwitchStateBridge: WeaponSwitchStateBridge;
  showFloatingText(position: Vec2, text: string, tone: FloatingTone): void;
}

export class WeaponWheelSwitchSceneBridge {
  public constructor(private readonly options: WeaponWheelSwitchSceneBridgeOptions) {}

  public handleWheel(_source: WeaponWheelSwitchSource, deltaY: number): void {
    const direction = this.resolveSwitchDirection(deltaY);
    if (direction === 0 || this.options.isAuthoritativeRendererHost()) {
      return;
    }

    const player = this.options.getPlayerHero();
    const switchResult = this.options.weaponSwitchStateBridge.requestWheelSwitch({
      player,
      switchDirection: direction,
      deltaY,
      nowMs: this.options.getNowMs()
    });

    if (!switchResult?.switched) {
      return;
    }

    this.options.showFloatingText(player.position, WEAPON_SWITCH_NOTICE_TEXT, "neutral");
  }

  private resolveSwitchDirection(deltaY: number): -1 | 0 | 1 {
    if (deltaY < 0) {
      return -1;
    }

    if (deltaY > 0) {
      return 1;
    }

    return 0;
  }
}
