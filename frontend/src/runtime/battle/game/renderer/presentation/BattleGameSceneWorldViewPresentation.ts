import type { BattleHeroViewState as Hero } from "../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { BattlePreparedSkill as PreparedSkill } from "../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { BattleVector2 as Vec2 } from "../../../../../objects/battle/objects/core/BattleCoreScalars";
import { isBattleSharedAuthoritativeTargetValid } from "../../../microservices/abilities/functions/BattleSkillTargetValidityRules";
import { syncWorldViews } from "../entities/worldViewFactory";
import type { SyncGameSceneWorldViewsInput } from "./objects/BattleGameSceneWorldViewPresentationObjects";

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
            isBattleSharedAuthoritativeTargetValid({
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
