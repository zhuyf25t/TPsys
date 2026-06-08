import type { BattleHeroViewState as Hero } from "../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import { WEAPON_SWITCH_MS } from "../../game/objects/BattleGameConstants";
import {
  advanceWeaponSwitchTimerState,
  beginWeaponSwitchIndexTransaction,
  beginWeaponSwitchTransaction,
  type WeaponSwitchTransactionResult
} from "../../microservices/combat/functions/BattleWeaponSwitchRules";

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

const WHEEL_SWITCH_DUPLICATE_WINDOW_MS = 80;

export class WeaponSwitchStateBridge {
  private pendingWeaponIndex: number | null = null;
  private weaponSwitchRemainingMs = 0;
  private weaponSwitchTotalMs = 0;
  private lastWheelHandledAt = 0;

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
    if (context.nowMs - this.lastWheelHandledAt < WHEEL_SWITCH_DUPLICATE_WINDOW_MS) {
      return null;
    }

    this.lastWheelHandledAt = context.nowMs;
    return this.beginSwitchTransaction(context.player, context.switchDirection);
  }

  public handleWeaponSwitchAction(context: WeaponSwitchCommandRequestContext): WeaponSwitchTransactionResult {
    if (context.switchWeaponIndex !== null) {
      return this.beginSwitchIndexTransaction(context.player, context.switchWeaponIndex);
    }

    return this.beginSwitchTransaction(context.player, context.switchDirection);
  }

  public advancePreviewTimer(deltaMs: number, weaponCount: number): void {
    const nextState = advanceWeaponSwitchTimerState({
      deltaMs,
      weaponCount,
      pendingWeaponIndex: this.pendingWeaponIndex,
      weaponSwitchRemainingMs: this.weaponSwitchRemainingMs,
      weaponSwitchTotalMs: this.weaponSwitchTotalMs
    });

    this.pendingWeaponIndex = nextState.pendingWeaponIndex;
    this.weaponSwitchRemainingMs = nextState.weaponSwitchRemainingMs;
    this.weaponSwitchTotalMs = nextState.weaponSwitchTotalMs;
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
