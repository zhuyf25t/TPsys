import type { Vec2 } from "../../../domain/types";
import { getSelectedSkillBindings } from "../../loadout/loadoutGateway";
import { readSkillBindingPresses } from "../input/skillBindingInputAdapter";

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
  switchWeaponDirection: -1 | 0 | 1;
}

export interface AuthoritativeBattleInputCapture {
  readSnapshot: () => AuthoritativeBattleInputSnapshot;
  destroy: () => void;
}

export interface AuthoritativeBattleInputCaptureOptions {
  resolveRuntimeRoot: () => HTMLElement | null;
  resolvePlayerPosition?: () => Vec2 | null;
}

export function createAuthoritativeBattleInputCapture({
  resolveRuntimeRoot,
  resolvePlayerPosition
}: AuthoritativeBattleInputCaptureOptions): AuthoritativeBattleInputCapture {
  const pressedKeys = new Set<string>();
  let primaryHeld = false;
  let reloadPressed = false;
  let castDash = false;
  let castBlink = false;
  let castFreeze = false;
  let switchWeaponDirection: -1 | 0 | 1 = 0;
  let pointerClientX = typeof window !== "undefined" ? window.innerWidth / 2 : 0;
  let pointerClientY = typeof window !== "undefined" ? window.innerHeight / 2 : 0;

  const handleKeyDown = (event: KeyboardEvent): void => {
    const key = normalizeKey(event.key, event.code);
    if (!key) {
      return;
    }

    pressedKeys.add(key);
    if (key === "t" && !event.repeat) {
      reloadPressed = true;
    }
    const skillPresses = readSkillBindingPresses(getSelectedSkillBindings(), {
      Q: key === "q" && !event.repeat,
      E: key === "e" && !event.repeat,
      R: key === "r" && !event.repeat
    });
    castDash = castDash || skillPresses.Dash;
    castBlink = castBlink || skillPresses.Blink;
    castFreeze = castFreeze || skillPresses.Freeze;
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
    } else if (event.deltaY > 0) {
      switchWeaponDirection = 1;
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
            x: playerPosition.x + fallbackAim.x,
            y: playerPosition.y + fallbackAim.y
          }
        : null;
      const snapshot: AuthoritativeBattleInputSnapshot = {
        movement,
        aim: fallbackAim,
        pointerWorld,
        primaryHeld,
        sprint: pressedKeys.has("shift"),
        reloadPressed,
        castDash,
        castBlink,
        castFreeze,
        switchWeaponDirection
      };

      reloadPressed = false;
      castDash = false;
      castBlink = false;
      castFreeze = false;
      switchWeaponDirection = 0;

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

  const normalized = key.trim().toLowerCase();
  if (normalized === "shift" || normalized === "leftshift" || normalized === "rightshift") {
    return "shift";
  }

  return normalized ? normalized : null;
}
