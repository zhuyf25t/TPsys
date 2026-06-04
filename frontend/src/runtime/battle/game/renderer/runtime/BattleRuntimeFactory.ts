import type { BattlePlayerCommand as PlayerCommand } from "../../../../../objects/battle/microservices/session/objects/command/BattlePlayerCommand";
import type { BattleGameSnapshot as GameSnapshot } from "../../../../../objects/battle/microservices/session/objects/state/BattleGameSnapshot";
import { installBattleAuthoritativePlayerCommandTap } from "../../../local/input/BattleAuthoritativePlayerCommandTap";
import { installWheelSwitchBridge, type WheelSwitchBridge } from "../../../local/input/wheelSwitchAdapter";
import { buildBattleRuntimeAuthoritativeFrame } from "../../../microservices/session/functions/BattleRuntimeAuthoritativeFrameBuilder";
import { resolveBattleRuntimeLocalLastClientCommandSeq } from "../../../microservices/session/functions/BattleRuntimeAuthoritativeStartupRules";
import { setActiveBattleMap } from "../../../microservices/world/services/BattleArenaCatalog";
import { GameScene } from "../../scenes/GameScene";
import { createBattleRuntimeBootSnapshot } from "./BattleRuntimeBootSnapshotFactory";
import { createBattlePhaserGame } from "./BattlePhaserGameFactory";
import {
  captureBattleRuntimeThumbnail,
  clearBattleRuntimeMountRoots,
  installBattleRuntimeContextMenuLock,
  prepareBattleRuntimeMountRoots
} from "./BattleRuntimeDomLifecycle";
import type { BattleRuntimeHandle, CreateBattleRuntimeOptions } from "./objects/BattleRuntimeFactoryObjects";

export type { BattleRuntimeHandle, CreateBattleRuntimeOptions } from "./objects/BattleRuntimeFactoryObjects";

export function createBattleRuntime({
  mountNode,
  hudRoot,
  initialSnapshot = null,
  initialParticipants,
  initialAuthoritativeState = null,
  localAuthoritativePlayerId = "",
  sharedAuthoritativeRuntime = false,
  mapId
}: CreateBattleRuntimeOptions): BattleRuntimeHandle {
  setActiveBattleMap(initialAuthoritativeState?.mapId ?? mapId);
  prepareBattleRuntimeMountRoots({ mountNode, hudRoot });

  const cleanupWheelBridge: WheelSwitchBridge = installWheelSwitchBridge();
  const cleanupContextMenuLock = installBattleRuntimeContextMenuLock();

  const bootSnapshot = createBattleRuntimeBootSnapshot({
    initialSnapshot,
    initialParticipants,
    initialAuthoritativeState,
    localAuthoritativePlayerId
  });
  const stableSeatHeroIds = bootSnapshot.heroes.map((hero) => hero.heroId);
  const scene = new GameScene(bootSnapshot, { sharedAuthoritativeRuntime });
  const playerCommandTap = sharedAuthoritativeRuntime ? installBattleAuthoritativePlayerCommandTap(scene) : null;
  const readScenePlayerCommand = playerCommandTap?.readPlayerCommand ?? (() => null);
  let destroyed = false;
  const game = createBattlePhaserGame({ mountNode, scene });

  function readSnapshot(): GameSnapshot | null {
    return scene.exportSnapshot();
  }

  function readPlayerCommand(): PlayerCommand | null {
    return readScenePlayerCommand();
  }

  function captureThumbnail(): string | null {
    return captureBattleRuntimeThumbnail(mountNode);
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
        localLastClientCommandSeq: resolveBattleRuntimeLocalLastClientCommandSeq(state, localPlayerId),
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
      clearBattleRuntimeMountRoots({ mountNode, hudRoot });
    }
  };
}
