import type { BattleHeroViewState as Hero } from "../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import { cycleWeaponIndex, isDisposableWeapon, isWeaponDepleted } from "./BattleWeaponInventoryRules";

export interface WeaponSwitchResult {
  switched: boolean;
  previousIndex: number;
  nextIndex: number;
  showNotice: boolean;
}

export interface WeaponSwitchTransactionResult extends WeaponSwitchResult {
  pendingWeaponIndex: number | null;
  weaponSwitchRemainingMs: number;
  weaponSwitchTotalMs: number;
}

export interface WeaponSwitchContext {
  player: Hero;
  switchDirection: -1 | 0 | 1;
  weaponSwitchRemainingMs: number;
}

export interface WeaponSwitchIndexContext {
  player: Hero;
  switchWeaponIndex: number | null;
  weaponSwitchRemainingMs: number;
}

export interface WeaponSwitchTimerAdvanceContext {
  deltaMs: number;
  weaponCount: number;
  pendingWeaponIndex: number | null;
  weaponSwitchRemainingMs: number;
  weaponSwitchTotalMs: number;
}

export interface WeaponSwitchTimerAdvanceResult {
  completedWeaponIndex: number | null;
  pendingWeaponIndex: number | null;
  weaponSwitchRemainingMs: number;
  weaponSwitchTotalMs: number;
}

export interface BeginWeaponSwitchTransactionContext extends WeaponSwitchContext {
  weaponSwitchMs: number;
}

export interface BeginWeaponSwitchIndexTransactionContext extends WeaponSwitchIndexContext {
  weaponSwitchMs: number;
}

export function requestWeaponSwitch(context: WeaponSwitchContext): WeaponSwitchResult {
  const { player, switchDirection, weaponSwitchRemainingMs } = context;
  if (!player.alive || switchDirection === 0 || player.weapons.length <= 1 || weaponSwitchRemainingMs > 0) {
    return {
      switched: false,
      previousIndex: player.currentWeaponIndex,
      nextIndex: player.currentWeaponIndex,
      showNotice: false
    };
  }

  const previousIndex = player.currentWeaponIndex;
  const nextIndex = cycleWeaponIndex(previousIndex, player.weapons.length, switchDirection);
  if (nextIndex === previousIndex) {
    return {
      switched: false,
      previousIndex,
      nextIndex,
      showNotice: false
    };
  }

  return {
    switched: true,
    previousIndex,
    nextIndex,
    showNotice: true
  };
}

export function requestWeaponSwitchToIndex(context: WeaponSwitchIndexContext): WeaponSwitchResult {
  const { player, switchWeaponIndex, weaponSwitchRemainingMs } = context;
  const targetIndex = normalizeWeaponIndex(switchWeaponIndex, player.weapons.length);
  if (!player.alive || targetIndex === null || player.weapons.length <= 1 || weaponSwitchRemainingMs > 0) {
    return {
      switched: false,
      previousIndex: player.currentWeaponIndex,
      nextIndex: player.currentWeaponIndex,
      showNotice: false
    };
  }

  const previousIndex = player.currentWeaponIndex;
  if (targetIndex === previousIndex) {
    return {
      switched: false,
      previousIndex,
      nextIndex: previousIndex,
      showNotice: false
    };
  }

  return {
    switched: true,
    previousIndex,
    nextIndex: targetIndex,
    showNotice: true
  };
}

export function beginWeaponSwitchTransaction(context: BeginWeaponSwitchTransactionContext): WeaponSwitchTransactionResult {
  const switchResult = requestWeaponSwitch(context);
  return beginWeaponSwitchFromResult(context.player, context.weaponSwitchRemainingMs, context.weaponSwitchMs, switchResult);
}

export function beginWeaponSwitchIndexTransaction(
  context: BeginWeaponSwitchIndexTransactionContext
): WeaponSwitchTransactionResult {
  const switchResult = requestWeaponSwitchToIndex(context);
  return beginWeaponSwitchFromResult(context.player, context.weaponSwitchRemainingMs, context.weaponSwitchMs, switchResult);
}

export function advanceWeaponSwitchTimerState(
  context: WeaponSwitchTimerAdvanceContext
): WeaponSwitchTimerAdvanceResult {
  if (context.weaponSwitchRemainingMs <= 0) {
    return resetWeaponSwitchTimerState();
  }

  const nextRemaining = Math.max(0, context.weaponSwitchRemainingMs - Math.max(0, context.deltaMs));
  if (nextRemaining > 0) {
    return {
      completedWeaponIndex: null,
      pendingWeaponIndex: context.pendingWeaponIndex,
      weaponSwitchRemainingMs: nextRemaining,
      weaponSwitchTotalMs: context.weaponSwitchTotalMs
    };
  }

  if (context.pendingWeaponIndex === null) {
    return resetWeaponSwitchTimerState();
  }

  return {
    ...resetWeaponSwitchTimerState(),
    completedWeaponIndex: clampWeaponIndex(context.pendingWeaponIndex, context.weaponCount)
  };
}

function beginWeaponSwitchFromResult(
  player: Hero,
  weaponSwitchRemainingMs: number,
  weaponSwitchMs: number,
  switchResult: WeaponSwitchResult
): WeaponSwitchTransactionResult {
  if (!switchResult.switched) {
    return {
      ...switchResult,
      pendingWeaponIndex: null,
      weaponSwitchRemainingMs,
      weaponSwitchTotalMs: weaponSwitchRemainingMs
    };
  }

  const currentWeapon = player.weapons[player.currentWeaponIndex];
  if (!currentWeapon) {
    return {
      switched: false,
      previousIndex: player.currentWeaponIndex,
      nextIndex: player.currentWeaponIndex,
      showNotice: false,
      pendingWeaponIndex: null,
      weaponSwitchRemainingMs,
      weaponSwitchTotalMs: weaponSwitchRemainingMs
    };
  }

  currentWeapon.reloadRemainingMs = 0;
  const nextWeaponIndex = switchResult.nextIndex;
  pruneDepletedDisposableWeapon(player, switchResult.previousIndex);

  return {
    ...switchResult,
    pendingWeaponIndex: nextWeaponIndex,
    weaponSwitchRemainingMs: weaponSwitchMs,
    weaponSwitchTotalMs: weaponSwitchMs
  };
}

function pruneDepletedDisposableWeapon(player: Hero, previousIndex: number): boolean {
  const previousWeapon = player.weapons[previousIndex];
  if (!previousWeapon || !isDisposableWeapon(previousWeapon.weaponKind) || !isWeaponDepleted(previousWeapon)) {
    return false;
  }

  player.weapons.splice(previousIndex, 1);
  if (player.weapons.length === 0) {
    return true;
  }

  if (previousIndex < player.currentWeaponIndex) {
    player.currentWeaponIndex -= 1;
  }

  player.currentWeaponIndex = Math.max(0, Math.min(player.currentWeaponIndex, player.weapons.length - 1));
  return true;
}

function normalizeWeaponIndex(index: number | null, weaponCount: number): number | null {
  if (index === null || !Number.isFinite(index) || weaponCount <= 0) {
    return null;
  }

  const normalized = Math.trunc(index);
  return normalized >= 0 && normalized < weaponCount ? normalized : null;
}

function clampWeaponIndex(index: number, weaponCount: number): number {
  if (weaponCount <= 0) {
    return 0;
  }

  return Math.max(0, Math.min(index, weaponCount - 1));
}

function resetWeaponSwitchTimerState(): WeaponSwitchTimerAdvanceResult {
  return {
    completedWeaponIndex: null,
    pendingWeaponIndex: null,
    weaponSwitchRemainingMs: 0,
    weaponSwitchTotalMs: 0
  };
}
