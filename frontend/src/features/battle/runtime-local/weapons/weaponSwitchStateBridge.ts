import type { Hero } from "../../../../domain/types";
import { WEAPON_SWITCH_MS } from "../../../../game/constants";
import {
  beginWeaponSwitchIndexTransaction,
  beginWeaponSwitchTransaction,
  type WeaponSwitchTransactionResult
} from "./weaponController";

export interface WeaponSwitchStateSnapshot {
  pendingWeaponIndex: number | null;
  weaponSwitchRemainingMs: number;
  weaponSwitchTotalMs: number;
}

export interface WeaponSwitchWheelRequestContext {
  player: Hero;
  switchDirection: -1 | 1;
  deltaY: number;
  nowMs: number;
}

export interface WeaponSwitchCommandRequestContext {
  player: Hero;
  switchDirection: -1 | 0 | 1;
  switchWeaponIndex: number | null;
}

export class WeaponSwitchStateBridge {
  private pendingWeaponIndex: number | null = null;
  private weaponSwitchRemainingMs = 0;
  private weaponSwitchTotalMs = 0;
  private lastWheelHandledAt = 0;
  private lastWheelHandledDeltaY = 0;

  public getState(): WeaponSwitchStateSnapshot {
    return {
      pendingWeaponIndex: this.pendingWeaponIndex,
      weaponSwitchRemainingMs: this.weaponSwitchRemainingMs,
      weaponSwitchTotalMs: this.weaponSwitchTotalMs
    };
  }

  public getPendingWeaponIndex(): number | null {
    return this.pendingWeaponIndex;
  }

  public getWeaponSwitchRemainingMs(): number {
    return this.weaponSwitchRemainingMs;
  }

  public getWeaponSwitchTotalMs(): number {
    return this.weaponSwitchTotalMs;
  }

  public syncState(state: WeaponSwitchStateSnapshot): void {
    this.pendingWeaponIndex = state.pendingWeaponIndex;
    this.weaponSwitchRemainingMs = state.weaponSwitchRemainingMs;
    this.weaponSwitchTotalMs = state.weaponSwitchTotalMs;
  }

  public reset(): void {
    this.pendingWeaponIndex = null;
    this.weaponSwitchRemainingMs = 0;
    this.weaponSwitchTotalMs = 0;
  }

  public requestWheelSwitch(context: WeaponSwitchWheelRequestContext): WeaponSwitchTransactionResult | null {
    if (Math.abs(context.deltaY - this.lastWheelHandledDeltaY) < 0.01 && context.nowMs - this.lastWheelHandledAt < 50) {
      return null;
    }

    this.lastWheelHandledAt = context.nowMs;
    this.lastWheelHandledDeltaY = context.deltaY;
    return this.beginSwitchTransaction(context.player, context.switchDirection);
  }

  public handleWeaponSwitchAction(context: WeaponSwitchCommandRequestContext): WeaponSwitchTransactionResult {
    if (context.switchWeaponIndex !== null) {
      return this.beginSwitchIndexTransaction(context.player, context.switchWeaponIndex);
    }

    return this.beginSwitchTransaction(context.player, context.switchDirection);
  }

  private beginSwitchTransaction(player: Hero, switchDirection: -1 | 0 | 1): WeaponSwitchTransactionResult {
    const switchResult = beginWeaponSwitchTransaction({
      player,
      switchDirection,
      weaponSwitchRemainingMs: this.weaponSwitchRemainingMs,
      weaponSwitchMs: WEAPON_SWITCH_MS
    });

    if (!switchResult.switched) {
      return switchResult;
    }

    this.pendingWeaponIndex = switchResult.nextIndex;
    this.weaponSwitchRemainingMs = switchResult.weaponSwitchRemainingMs;
    this.weaponSwitchTotalMs = switchResult.weaponSwitchTotalMs;
    return switchResult;
  }

  private beginSwitchIndexTransaction(player: Hero, switchWeaponIndex: number): WeaponSwitchTransactionResult {
    const switchResult = beginWeaponSwitchIndexTransaction({
      player,
      switchWeaponIndex,
      weaponSwitchRemainingMs: this.weaponSwitchRemainingMs,
      weaponSwitchMs: WEAPON_SWITCH_MS
    });

    if (!switchResult.switched) {
      return switchResult;
    }

    this.pendingWeaponIndex = switchResult.nextIndex;
    this.weaponSwitchRemainingMs = switchResult.weaponSwitchRemainingMs;
    this.weaponSwitchTotalMs = switchResult.weaponSwitchTotalMs;
    return switchResult;
  }
}
