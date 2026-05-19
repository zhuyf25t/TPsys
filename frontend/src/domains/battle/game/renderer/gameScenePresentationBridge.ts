import type Phaser from "phaser";
import type { GameSnapshot, Hero, PlayerCommand, PreparedSkill, Vec2 } from "../../objects/types";
import type { ObstacleBounds, OccludableView } from "./arena/arenaBuilder";
import { updateOccludableAlpha } from "./arena/occlusionAlphaController";
import type { BattleHudSceneBridge } from "./hud/battleHudSceneBridge";
import type { LocalHeroDisplay } from "./localHeroDisplayPose";
import { isSharedAuthoritativeTargetValid } from "./effects/sharedAuthoritativeTargetValidity";
import {
  syncWorldViews,
  type WorldViewState
} from "./entities/worldViewFactory";
import type { PlayerAbilitySceneBridge } from "./effects/playerAbilitySceneBridge";
import type { WeaponSwitchStateBridge } from "../../runtime/local/weapons/weaponSwitchStateBridge";
import { recordBattleVisionCameraDiagnostics } from "./visionDiagnostics";

export interface SyncGameSceneWorldViewsInput {
  scene: Phaser.Scene;
  snapshot: GameSnapshot;
  worldViews: WorldViewState;
  command: PlayerCommand;
  deltaMs: number;
  weaponSwitchStateBridge: WeaponSwitchStateBridge;
  playerAbilityBridge: PlayerAbilitySceneBridge;
  sharedAuthoritativeRuntime: boolean;
  remoteAuthoritativeHeroIds: ReadonlySet<string>;
  localHeroDisplay: LocalHeroDisplay;
  obstacleBounds: readonly ObstacleBounds[];
}

export interface RenderGameSceneHudInput {
  hudBridge: BattleHudSceneBridge;
  snapshot: GameSnapshot;
  fps: number;
  weaponSwitchStateBridge: WeaponSwitchStateBridge;
  sharedAuthoritativeRuntime: boolean;
  localHeroDisplay: LocalHeroDisplay;
  camera: Phaser.Cameras.Scene2D.Camera;
  obstacleBounds: readonly ObstacleBounds[];
}

export interface UpdateGameSceneOcclusionInput {
  player: Hero;
  sharedAuthoritativeRuntime: boolean;
  localHeroDisplay: LocalHeroDisplay;
  occludables: readonly OccludableView[];
}

export function syncGameSceneWorldViews({
  scene,
  snapshot,
  worldViews,
  command,
  deltaMs,
  weaponSwitchStateBridge,
  playerAbilityBridge,
  sharedAuthoritativeRuntime,
  remoteAuthoritativeHeroIds,
  localHeroDisplay,
  obstacleBounds
}: SyncGameSceneWorldViewsInput): void {
  syncWorldViews({
    scene,
    snapshot,
    worldViews,
    deltaMs,
    weaponSwitchRemainingMs: weaponSwitchStateBridge.getWeaponSwitchRemainingMs(),
    weaponSwitchTotalMs: weaponSwitchStateBridge.getWeaponSwitchTotalMs(),
    pointerWorld: command.pointerWorld,
    isBlinkTargetValid: (player, target) => playerAbilityBridge.isBlinkTargetValid(player, target),
    ...(sharedAuthoritativeRuntime
      ? {
          isPreparedTargetValid: (player: Hero, preparedSkill: Exclude<PreparedSkill, null>, target: Vec2) =>
            isSharedAuthoritativeTargetValid({
              player,
              preparedSkill,
              target,
              worldSize: snapshot.worldSize,
              obstacleBounds
            })
        }
      : {}),
    sharedAuthoritativeRuntime,
    remoteAuthoritativeHeroIds,
    localHeroDisplayOverride: sharedAuthoritativeRuntime ? localHeroDisplay.read() : undefined
  });
}

export function renderGameSceneHud({
  hudBridge,
  snapshot,
  fps,
  weaponSwitchStateBridge,
  sharedAuthoritativeRuntime,
  localHeroDisplay,
  camera,
  obstacleBounds
}: RenderGameSceneHudInput): void {
  const playerDisplayPosition = sharedAuthoritativeRuntime ? localHeroDisplay.read().position : undefined;
  recordBattleVisionCameraDiagnostics({
    camera,
    playerDisplayPosition
  });
  hudBridge.update({
    snapshot,
    fps,
    weaponSwitchRemainingMs: weaponSwitchStateBridge.getWeaponSwitchRemainingMs(),
    sharedAuthoritativeHud: sharedAuthoritativeRuntime,
    playerDisplayPosition,
    camera,
    obstacleBounds
  });
}

export function updateGameSceneOcclusion({
  player,
  sharedAuthoritativeRuntime,
  localHeroDisplay,
  occludables
}: UpdateGameSceneOcclusionInput): void {
  updateOccludableAlpha({
    player: localHeroDisplay.heroFor(player, sharedAuthoritativeRuntime),
    occludables
  });
}
