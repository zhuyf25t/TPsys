import type { BattlePlayerCommand as PlayerCommand } from "../../../../objects/battle/microservices/session/objects/command/BattlePlayerCommand";
import { getSelectedSkillBindings } from "../../loadout/BattleLoadoutStore";
import type { GameScene } from "../../game/scenes/GameScene";
import { readSkillBindingPresses } from "./skillBindingInputAdapter";

export interface BattleAuthoritativePlayerCommandTap {
  readPlayerCommand: () => PlayerCommand | null;
  destroy: () => void;
}

export function installBattleAuthoritativePlayerCommandTap(
  scene: GameScene
): BattleAuthoritativePlayerCommandTap {
  type RuntimeCommandScene = {
    readPlayerCommand?: () => PlayerCommand;
  };

  const runtimeScene = scene as unknown as RuntimeCommandScene;
  const originalReadPlayerCommand = runtimeScene.readPlayerCommand;
  let latestPlayerCommand: PlayerCommand | null = null;
  let pendingReloadPressed = false;
  let pendingScenePrimaryJustPressed = false;
  let pendingPrimaryJustPressed = false;
  let pendingPrimaryPointerWorld: PlayerCommand["pointerWorld"] | null = null;
  let pendingSceneCastDash = false;
  let pendingSceneCastCritical = false;
  let pendingUplinkCastDash = false;
  let pendingUplinkCastCritical = false;
  let pendingSceneToggleBlink = false;
  let pendingSceneToggleFreeze = false;
  let pendingUplinkToggleBlink = false;
  let pendingUplinkToggleFreeze = false;
  let pendingSwitchWeaponDirection: -1 | 0 | 1 = 0;
  let pendingSwitchWeaponIndex: number | null = null;
  const pressedSkillTapKeys = new Set<"q" | "e" | "r">();

  const retainCommand = (command: PlayerCommand): void => {
    latestPlayerCommand = clonePlayerCommand(command);
    pendingReloadPressed = pendingReloadPressed || command.reloadPressed;
    if (command.primaryJustPressed) {
      pendingPrimaryJustPressed = true;
      pendingPrimaryPointerWorld = { x: command.pointerWorld.x, y: command.pointerWorld.y };
    }
    pendingUplinkCastDash = pendingUplinkCastDash || command.castDash;
    pendingUplinkCastCritical = pendingUplinkCastCritical || command.castCritical;
    pendingUplinkToggleBlink = pendingUplinkToggleBlink || command.toggleBlink;
    pendingUplinkToggleFreeze = pendingUplinkToggleFreeze || command.toggleFreeze;
    if (command.switchWeaponDirection !== 0) {
      pendingSwitchWeaponDirection = command.switchWeaponDirection;
    }
    if (command.switchWeaponIndex !== null) {
      pendingSwitchWeaponIndex = command.switchWeaponIndex;
    }
  };

  const handleGlobalMouseDown = (event: MouseEvent): void => {
    if (event.button !== 0) {
      return;
    }

    pendingScenePrimaryJustPressed = true;
    pendingPrimaryJustPressed = true;
  };

  const handleGlobalKeyDown = (event: KeyboardEvent): void => {
    if (event.repeat) {
      return;
    }

    const key = normalizeSkillTapKey(event.key, event.code);
    if (!key) {
      return;
    }
    if (pressedSkillTapKeys.has(key)) {
      return;
    }

    pressedSkillTapKeys.add(key);

    const skillPresses = readSkillBindingPresses(getSelectedSkillBindings(), {
      Q: key === "q",
      E: key === "e",
      R: key === "r"
    });

    if (skillPresses.Dash) {
      pendingSceneCastDash = true;
    }
    if (skillPresses.Critical) {
      pendingSceneCastCritical = true;
    }
    if (skillPresses.Blink) {
      pendingSceneToggleBlink = true;
    }
    if (skillPresses.Freeze) {
      pendingSceneToggleFreeze = true;
    }
  };

  const handleGlobalKeyUp = (event: KeyboardEvent): void => {
    const key = normalizeSkillTapKey(event.key, event.code);
    if (!key) {
      return;
    }

    pressedSkillTapKeys.delete(key);
  };

  window.addEventListener("mousedown", handleGlobalMouseDown);
  window.addEventListener("keydown", handleGlobalKeyDown);
  window.addEventListener("keyup", handleGlobalKeyUp);

  if (typeof originalReadPlayerCommand === "function") {
    runtimeScene.readPlayerCommand = () => {
      const command = originalReadPlayerCommand.call(scene);
      const applyPendingScenePrimaryJustPressed = pendingScenePrimaryJustPressed;
      if (applyPendingScenePrimaryJustPressed) {
        pendingScenePrimaryJustPressed = false;
      }
      const applyPendingSceneDash = pendingSceneCastDash;
      if (applyPendingSceneDash) {
        pendingSceneCastDash = false;
      }
      const applyPendingSceneCritical = pendingSceneCastCritical;
      if (applyPendingSceneCritical) {
        pendingSceneCastCritical = false;
      }
      const applyPendingSceneToggleBlink = pendingSceneToggleBlink;
      const applyPendingSceneToggleFreeze = pendingSceneToggleFreeze;
      if (applyPendingSceneToggleBlink) {
        pendingSceneToggleBlink = false;
      }
      if (applyPendingSceneToggleFreeze) {
        pendingSceneToggleFreeze = false;
      }

      const sceneCommand = clonePlayerCommand(command);
      sceneCommand.primaryJustPressed = sceneCommand.primaryJustPressed || applyPendingScenePrimaryJustPressed;
      sceneCommand.castDash = sceneCommand.castDash || applyPendingSceneDash;
      sceneCommand.castCritical = sceneCommand.castCritical || applyPendingSceneCritical;
      sceneCommand.toggleBlink = sceneCommand.toggleBlink || applyPendingSceneToggleBlink;
      sceneCommand.toggleFreeze = sceneCommand.toggleFreeze || applyPendingSceneToggleFreeze;

      const uplinkCommand = clonePlayerCommand(sceneCommand);
      if (applyPendingScenePrimaryJustPressed && !pendingPrimaryJustPressed && !command.primaryJustPressed) {
        uplinkCommand.primaryJustPressed = false;
      }
      if (applyPendingSceneDash && !pendingUplinkCastDash && !command.castDash) {
        uplinkCommand.castDash = false;
      }
      if (applyPendingSceneCritical && !pendingUplinkCastCritical && !command.castCritical) {
        uplinkCommand.castCritical = false;
      }
      if (applyPendingSceneToggleBlink && !pendingUplinkToggleBlink && !command.toggleBlink) {
        uplinkCommand.toggleBlink = false;
      }
      if (applyPendingSceneToggleFreeze && !pendingUplinkToggleFreeze && !command.toggleFreeze) {
        uplinkCommand.toggleFreeze = false;
      }
      retainCommand(uplinkCommand);
      return sceneCommand;
    };
  }

  return {
    readPlayerCommand: () => {
      if (!latestPlayerCommand && typeof runtimeScene.readPlayerCommand === "function") {
        try {
          runtimeScene.readPlayerCommand.call(scene);
        } catch {
          return null;
        }
      }

      if (!latestPlayerCommand) {
        return null;
      }

      const command = clonePlayerCommand(latestPlayerCommand);
      command.reloadPressed = command.reloadPressed || pendingReloadPressed;
      if (!command.primaryJustPressed && pendingPrimaryJustPressed && pendingPrimaryPointerWorld) {
        command.pointerWorld = { x: pendingPrimaryPointerWorld.x, y: pendingPrimaryPointerWorld.y };
      }
      command.primaryJustPressed = command.primaryJustPressed || pendingPrimaryJustPressed;
      command.castDash = command.castDash || pendingUplinkCastDash;
      command.castCritical = command.castCritical || pendingUplinkCastCritical;
      command.toggleBlink = command.toggleBlink || pendingUplinkToggleBlink;
      command.toggleFreeze = command.toggleFreeze || pendingUplinkToggleFreeze;
      if (command.switchWeaponDirection === 0) {
        command.switchWeaponDirection = pendingSwitchWeaponDirection;
      }
      command.switchWeaponIndex = command.switchWeaponIndex ?? pendingSwitchWeaponIndex;

      pendingReloadPressed = false;
      pendingPrimaryJustPressed = false;
      pendingPrimaryPointerWorld = null;
      pendingUplinkCastDash = false;
      pendingUplinkCastCritical = false;
      pendingUplinkToggleBlink = false;
      pendingUplinkToggleFreeze = false;
      pendingSwitchWeaponDirection = 0;
      pendingSwitchWeaponIndex = null;

      return command;
    },
    destroy: () => {
      window.removeEventListener("mousedown", handleGlobalMouseDown);
      window.removeEventListener("keydown", handleGlobalKeyDown);
      window.removeEventListener("keyup", handleGlobalKeyUp);
    }
  };
}

function normalizeSkillTapKey(key: string, code?: string): "q" | "e" | "r" | null {
  const normalizedCode = code?.trim().toLowerCase();
  if (normalizedCode === "keyq") {
    return "q";
  }
  if (normalizedCode === "keye") {
    return "e";
  }
  if (normalizedCode === "keyr") {
    return "r";
  }
  const normalizedKey = key.trim().toLowerCase();
  if (normalizedKey === "q" || normalizedKey === "e" || normalizedKey === "r") {
    return normalizedKey;
  }
  return null;
}

function clonePlayerCommand(command: PlayerCommand): PlayerCommand {
  return {
    movement: { x: command.movement.x, y: command.movement.y },
    aim: { x: command.aim.x, y: command.aim.y },
    pointerWorld: { x: command.pointerWorld.x, y: command.pointerWorld.y },
    primaryHeld: command.primaryHeld,
    primaryJustPressed: command.primaryJustPressed,
    secondaryJustPressed: command.secondaryJustPressed,
    sprint: command.sprint,
    switchWeaponDirection: command.switchWeaponDirection,
    switchWeaponIndex: command.switchWeaponIndex,
    toggleBlink: command.toggleBlink,
    toggleFreeze: command.toggleFreeze,
    castDash: command.castDash,
    castCritical: command.castCritical,
    reloadPressed: command.reloadPressed
  };
}
