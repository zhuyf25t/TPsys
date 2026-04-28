import Phaser from "phaser";
import { GAME_HEIGHT, GAME_WIDTH } from "../../../game/constants";
import type { GameSnapshot, PlayerCommand, PreparedSkill } from "../../../domain/types";
import { installWheelSwitchBridge, type WheelSwitchBridge } from "../input/wheelSwitchAdapter";
import type { AuthoritativeBattleState } from "../adapters/authoritativeBattleClient";
import { GameScene } from "../../../scenes/GameScene";
import { getSelectedSkillBindings } from "../../loadout/loadoutGateway";
import { readSkillBindingPresses } from "../input/skillBindingInputAdapter";
import {
  createInitialBattleSnapshot,
  type InitialBattleParticipantsConfig
} from "../runtime-local/session/initialBattleSnapshot";
import { buildBattleRuntimeAuthoritativeFrame } from "./authoritativeBattleStateBridge";
import { applyAuthoritativeFrameToSnapshot } from "./authoritativeFrameSnapshotApplier";
import type { AuthoritativeLocalHeroReplayCommandEntry } from "./authoritativeLocalHeroReplay";

export interface BattleRuntimeHandle {
  destroy: () => void;
  readSnapshot: () => GameSnapshot | null;
  readPlayerCommand: () => PlayerCommand | null;
  captureThumbnail: () => string | null;
  setAuthoritativePreparedSkill: (preparedSkill: PreparedSkill) => void;
  applyAuthoritativeState: (
    state: AuthoritativeBattleState,
    localPlayerId: string,
    commandHistory?: readonly AuthoritativeLocalHeroReplayCommandEntry[]
  ) => boolean;
}

export interface CreateBattleRuntimeOptions {
  mountNode: HTMLElement;
  hudRoot: HTMLElement;
  initialSnapshot?: GameSnapshot | null;
  initialParticipants?: InitialBattleParticipantsConfig;
  initialAuthoritativeState?: AuthoritativeBattleState | null;
  localAuthoritativePlayerId?: string;
  sharedAuthoritativeRuntime?: boolean;
}

function installContextMenuLock(): () => void {
  const listener = (event: MouseEvent): void => {
    event.preventDefault();
  };

  window.addEventListener("contextmenu", listener);

  return () => {
    window.removeEventListener("contextmenu", listener);
  };
}

export function createBattleRuntime({
  mountNode,
  hudRoot,
  initialSnapshot = null,
  initialParticipants,
  initialAuthoritativeState = null,
  localAuthoritativePlayerId = "",
  sharedAuthoritativeRuntime = false
}: CreateBattleRuntimeOptions): BattleRuntimeHandle {
  mountNode.replaceChildren();
  hudRoot.replaceChildren();
  hudRoot.id = "hud-root";

  const cleanupWheelBridge: WheelSwitchBridge = installWheelSwitchBridge();
  const cleanupContextMenuLock = installContextMenuLock();

  const bootSnapshot = createBootSnapshot(
    initialSnapshot,
    initialParticipants,
    initialAuthoritativeState,
    localAuthoritativePlayerId
  );
  const stableSeatHeroIds = bootSnapshot.heroes.map((hero) => hero.heroId);
  const scene = new GameScene(bootSnapshot, { sharedAuthoritativeRuntime });
  const playerCommandTap = sharedAuthoritativeRuntime ? installPlayerCommandTap(scene) : null;
  const readScenePlayerCommand = playerCommandTap?.readPlayerCommand ?? (() => null);
  let destroyed = false;
  const game = new Phaser.Game({
    type: Phaser.AUTO,
    parent: mountNode,
    width: mountNode.clientWidth || window.innerWidth || GAME_WIDTH,
    height: mountNode.clientHeight || window.innerHeight || GAME_HEIGHT,
    pixelArt: true,
    backgroundColor: "#0b1016",
    physics: {
      default: "arcade",
      arcade: {
        debug: false
      }
    },
    scale: {
      mode: Phaser.Scale.RESIZE,
      autoCenter: Phaser.Scale.CENTER_BOTH,
      width: mountNode.clientWidth || window.innerWidth || GAME_WIDTH,
      height: mountNode.clientHeight || window.innerHeight || GAME_HEIGHT
    },
    scene: [scene]
  });

  function readSnapshot(): GameSnapshot | null {
    return scene.exportSnapshot();
  }

  function readPlayerCommand(): PlayerCommand | null {
    return readScenePlayerCommand();
  }

  function captureThumbnail(): string | null {
    const canvas = mountNode.querySelector("canvas");
    if (!(canvas instanceof HTMLCanvasElement)) {
      return null;
    }

    try {
      return canvas.toDataURL("image/png");
    } catch {
      return null;
    }
  }

  return {
    readSnapshot,
    readPlayerCommand,
    captureThumbnail,
    setAuthoritativePreparedSkill: (preparedSkill) => {
      scene.setAuthoritativePreparedSkill(preparedSkill);
    },
    applyAuthoritativeState: (state, localPlayerId, commandHistory = []) => {
      const snapshot = scene.exportSnapshot();
      if (!snapshot) {
        return false;
      }

      const frame = buildBattleRuntimeAuthoritativeFrame(snapshot, state, localPlayerId, stableSeatHeroIds);
      if (!frame) {
        return false;
      }

      scene.applyAuthoritativeFrame(frame, {
        localCommandHistory: commandHistory,
        localLastClientCommandSeq: resolveLocalLastClientCommandSeq(state, localPlayerId),
        nowMs: Date.now()
      });
      return true;
    },
    destroy: () => {
      if (destroyed) {
        return;
      }

      destroyed = true;
      playerCommandTap?.destroy();
      cleanupWheelBridge();
      cleanupContextMenuLock();
      game.destroy(true);
      mountNode.replaceChildren();
      hudRoot.replaceChildren();
    }
  };
}

function resolveLocalLastClientCommandSeq(state: AuthoritativeBattleState, localPlayerId: string): number {
  const normalizedLocalPlayerId = localPlayerId.trim();
  if (!normalizedLocalPlayerId) {
    return 0;
  }

  const localPlayer = state.players.find((player) => player.playerId === normalizedLocalPlayerId);
  return localPlayer ? Math.max(0, Math.trunc(localPlayer.lastClientCommandSeq)) : 0;
}

interface PlayerCommandTap {
  readPlayerCommand: () => PlayerCommand | null;
  destroy: () => void;
}

function installPlayerCommandTap(scene: GameScene): PlayerCommandTap {
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
  let pendingUplinkCastDash = false;
  let pendingSceneToggleBlink = false;
  let pendingSceneToggleFreeze = false;
  let pendingUplinkToggleBlink = false;
  let pendingUplinkToggleFreeze = false;
  let pendingSwitchWeaponDirection: -1 | 0 | 1 = 0;
  let pendingSwitchWeaponIndex: number | null = null;

  const retainCommand = (command: PlayerCommand): void => {
    latestPlayerCommand = clonePlayerCommand(command);
    pendingReloadPressed = pendingReloadPressed || command.reloadPressed;
    if (command.primaryJustPressed) {
      pendingPrimaryJustPressed = true;
      pendingPrimaryPointerWorld = { x: command.pointerWorld.x, y: command.pointerWorld.y };
    }
    pendingUplinkCastDash = pendingUplinkCastDash || command.castDash;
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

    const skillPresses = readSkillBindingPresses(getSelectedSkillBindings(), {
      Q: key === "q",
      E: key === "e",
      R: key === "r"
    });

    if (skillPresses.Dash) {
      pendingSceneCastDash = true;
      pendingUplinkCastDash = true;
    }
    if (skillPresses.Blink) {
      pendingSceneToggleBlink = true;
      pendingUplinkToggleBlink = true;
    }
    if (skillPresses.Freeze) {
      pendingSceneToggleFreeze = true;
      pendingUplinkToggleFreeze = true;
    }
  };

  window.addEventListener("mousedown", handleGlobalMouseDown);
  window.addEventListener("keydown", handleGlobalKeyDown);

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
      sceneCommand.toggleBlink = sceneCommand.toggleBlink || applyPendingSceneToggleBlink;
      sceneCommand.toggleFreeze = sceneCommand.toggleFreeze || applyPendingSceneToggleFreeze;

      const uplinkCommand = clonePlayerCommand(sceneCommand);
      if (applyPendingScenePrimaryJustPressed && !pendingPrimaryJustPressed && !command.primaryJustPressed) {
        uplinkCommand.primaryJustPressed = false;
      }
      if (applyPendingSceneDash && !pendingUplinkCastDash && !command.castDash) {
        uplinkCommand.castDash = false;
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
      pendingUplinkToggleBlink = false;
      pendingUplinkToggleFreeze = false;
      pendingSwitchWeaponDirection = 0;
      pendingSwitchWeaponIndex = null;

      return command;
    },
    destroy: () => {
      window.removeEventListener("mousedown", handleGlobalMouseDown);
      window.removeEventListener("keydown", handleGlobalKeyDown);
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
  return normalizedKey === "q" || normalizedKey === "e" || normalizedKey === "r" ? normalizedKey : null;
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
    reloadPressed: command.reloadPressed
  };
}

function createBootSnapshot(
  initialSnapshot: GameSnapshot | null,
  initialParticipants: InitialBattleParticipantsConfig | undefined,
  initialAuthoritativeState: AuthoritativeBattleState | null,
  localAuthoritativePlayerId: string
): GameSnapshot {
  const snapshot = initialSnapshot ?? createInitialBattleSnapshot(initialParticipants, initialAuthoritativeState?.worldSize);
  if (!initialAuthoritativeState) {
    return snapshot;
  }

  const stableSeatHeroIds = snapshot.heroes.map((hero) => hero.heroId);
  const frame = buildBattleRuntimeAuthoritativeFrame(
    snapshot,
    initialAuthoritativeState,
    localAuthoritativePlayerId || initialParticipants?.localPlayerId || "",
    stableSeatHeroIds
  );
  if (!frame) {
    return snapshot;
  }

  applyAuthoritativeFrameToSnapshot({
    snapshot,
    frame
  });

  return snapshot;
}
