import type { BattleVector2 as Vec2 } from "../../../objects/battle/objects/core/BattleCoreScalars";
import { getSelectedSkillBindings } from "../../../runtime/battle/loadout/BattleLoadoutStore";
import { readSkillBindingPresses } from "../../../runtime/battle/local/input/skillBindingInputAdapter";

const FALLBACK_POINTER_WORLD_DISTANCE = 220;

export interface AuthoritativeBattleInputSnapshot {
  movement: Vec2;
  aim: Vec2;
  pointerWorld: Vec2 | null;
  primaryHeld: boolean;
  sprint: boolean;
  reloadPressed: boolean;
  castDash: boolean;
  castBlink: boolean;
  castFreeze: boolean;
  castCritical: boolean;
  switchWeaponDirection: -1 | 0 | 1;
  switchWeaponIndex: number | null;
}

export interface AuthoritativeBattleInputCapture {
  readSnapshot: () => AuthoritativeBattleInputSnapshot;
  destroy: () => void;
}

export interface AuthoritativeBattleInputCaptureOptions {
  resolveRuntimeRoot: () => HTMLElement | null;
  resolvePlayerPosition?: () => Vec2 | null;
  onImmediateCommandIntent?: () => void;
}

/** 中文名：创建authoritative战斗输入capture（createAuthoritativeBattleInputCapture）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function createAuthoritativeBattleInputCapture({
  resolveRuntimeRoot,
  resolvePlayerPosition,
  onImmediateCommandIntent
}: AuthoritativeBattleInputCaptureOptions): AuthoritativeBattleInputCapture {
  const pressedKeys = new Set<string>();
  let primaryHeld = false;
  let primaryClickPending = false;
  let reloadPressed = false;
  let castDash = false;
  let castBlink = false;
  let castFreeze = false;
  let castCritical = false;
  let switchWeaponDirection: -1 | 0 | 1 = 0;
  let switchWeaponIndex: number | null = null;
  let pointerClientX = typeof window !== "undefined" ? window.innerWidth / 2 : 0;
  let pointerClientY = typeof window !== "undefined" ? window.innerHeight / 2 : 0;

  const handleKeyDown = (event: KeyboardEvent): void => {
    const key = normalizeKey(event.key, event.code);
    if (!key) {
      return;
    }

    pressedKeys.add(key);
    let immediateCommandIntent = false;
    if (key === "t" && !event.repeat) {
      reloadPressed = true;
      immediateCommandIntent = true;
    }
    const skillPresses = readSkillBindingPresses(getSelectedSkillBindings(), {
      Q: key === "q" && !event.repeat,
      E: key === "e" && !event.repeat,
      R: key === "r" && !event.repeat
    });
    const numericWeaponIndex = readNumericWeaponIndex(event.key, event.code);
    if (numericWeaponIndex !== null && !event.repeat) {
      switchWeaponIndex = numericWeaponIndex;
      immediateCommandIntent = true;
    }
    castDash = castDash || skillPresses.Dash;
    castBlink = castBlink || skillPresses.Blink;
    castFreeze = castFreeze || skillPresses.Freeze;
    const criticalPressed = key === "control" && !event.repeat;
    castCritical = castCritical || skillPresses.Critical || criticalPressed;
    if (skillPresses.Dash || skillPresses.Blink || skillPresses.Freeze || skillPresses.Critical || criticalPressed) {
      immediateCommandIntent = true;
    }
    if (immediateCommandIntent) {
      onImmediateCommandIntent?.();
    }
  };

  const handleKeyUp = (event: KeyboardEvent): void => {
    const key = normalizeKey(event.key, event.code);
    if (!key) {
      return;
    }

    pressedKeys.delete(key);
  };

  const capturePointer = (event: MouseEvent): void => {
    pointerClientX = event.clientX;
    pointerClientY = event.clientY;
  };

  const handleMouseMove = (event: MouseEvent): void => {
    capturePointer(event);
  };

  const handleMouseDown = (event: MouseEvent): void => {
    capturePointer(event);
    if (event.button === 0) {
      primaryHeld = true;
      primaryClickPending = true;
      onImmediateCommandIntent?.();
    }
  };

  const handleMouseUp = (event: MouseEvent): void => {
    capturePointer(event);
    if (event.button === 0) {
      primaryHeld = false;
    }
  };

  const handleWheel = (event: WheelEvent): void => {
    if (event.deltaY < 0) {
      switchWeaponDirection = -1;
      onImmediateCommandIntent?.();
    } else if (event.deltaY > 0) {
      switchWeaponDirection = 1;
      onImmediateCommandIntent?.();
    }
  };

  window.addEventListener("keydown", handleKeyDown);
  window.addEventListener("keyup", handleKeyUp);
  window.addEventListener("mousemove", handleMouseMove);
  window.addEventListener("mousedown", handleMouseDown);
  window.addEventListener("mouseup", handleMouseUp);
  window.addEventListener("wheel", handleWheel, { passive: true });

  return {
    readSnapshot: () => {
      const movement = normalizeVector({
        x: Number(pressedKeys.has("d")) - Number(pressedKeys.has("a")),
        y: Number(pressedKeys.has("s")) - Number(pressedKeys.has("w"))
      });
      const runtimeRoot = resolveRuntimeRoot();
      const rect = runtimeRoot?.getBoundingClientRect();
      const centerX = rect ? rect.left + rect.width / 2 : window.innerWidth / 2;
      const centerY = rect ? rect.top + rect.height / 2 : window.innerHeight / 2;
      const aim = normalizeVector({
        x: pointerClientX - centerX,
        y: pointerClientY - centerY
      });
      const fallbackAim = aim.x === 0 && aim.y === 0 ? { x: 1, y: 0 } : aim;
      const playerPosition = resolvePlayerPosition?.() ?? null;
      const pointerWorld = playerPosition
        ? {
            x: playerPosition.x + fallbackAim.x * FALLBACK_POINTER_WORLD_DISTANCE,
            y: playerPosition.y + fallbackAim.y * FALLBACK_POINTER_WORLD_DISTANCE
          }
        : null;
      const primaryFireIntent = primaryHeld || primaryClickPending;
      const snapshot: AuthoritativeBattleInputSnapshot = {
        movement,
        aim: fallbackAim,
        pointerWorld,
        primaryHeld: primaryFireIntent,
        sprint: pressedKeys.has("shift"),
        reloadPressed,
        castDash,
        castBlink,
        castFreeze,
        castCritical,
        switchWeaponDirection,
        switchWeaponIndex
      };

      reloadPressed = false;
      primaryClickPending = false;
      castDash = false;
      castBlink = false;
      castFreeze = false;
      castCritical = false;
      switchWeaponDirection = 0;
      switchWeaponIndex = null;

      return snapshot;
    },
    destroy: () => {
      window.removeEventListener("keydown", handleKeyDown);
      window.removeEventListener("keyup", handleKeyUp);
      window.removeEventListener("mousemove", handleMouseMove);
      window.removeEventListener("mousedown", handleMouseDown);
      window.removeEventListener("mouseup", handleMouseUp);
      window.removeEventListener("wheel", handleWheel);
    }
  };
}

function readNumericWeaponIndex(key: string, code?: string): number | null {
  const normalizedCode = code?.trim().toLowerCase();
  if (normalizedCode === "digit1" || normalizedCode === "numpad1") {
    return 0;
  }
  if (normalizedCode === "digit2" || normalizedCode === "numpad2") {
    return 1;
  }
  if (normalizedCode === "digit3" || normalizedCode === "numpad3") {
    return 2;
  }
  if (normalizedCode === "digit4" || normalizedCode === "numpad4") {
    return 3;
  }

  const normalized = key.trim();
  if (normalized === "1") {
    return 0;
  }
  if (normalized === "2") {
    return 1;
  }
  if (normalized === "3") {
    return 2;
  }
  if (normalized === "4") {
    return 3;
  }

  return null;
}

function normalizeVector(vector: Vec2): Vec2 {
  const length = Math.hypot(vector.x, vector.y);
  if (length <= 0.0001) {
    return { x: 0, y: 0 };
  }

  return {
    x: vector.x / length,
    y: vector.y / length
  };
}

function normalizeKey(key: string, code?: string): string | null {
  const normalizedCode = code?.trim().toLowerCase();
  if (normalizedCode === "shiftleft" || normalizedCode === "shiftright") {
    return "shift";
  }
  if (normalizedCode === "controlleft" || normalizedCode === "controlright") {
    return "control";
  }

  const normalized = key.trim().toLowerCase();
  if (normalized === "shift" || normalized === "leftshift" || normalized === "rightshift") {
    return "shift";
  }
  if (normalized === "control" || normalized === "ctrl" || normalized === "leftcontrol" || normalized === "rightcontrol") {
    return "control";
  }

  return normalized ? normalized : null;
}
