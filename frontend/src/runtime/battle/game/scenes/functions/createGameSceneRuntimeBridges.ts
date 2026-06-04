import type { BattleGameSnapshot as GameSnapshot } from "../../../../../objects/battle/microservices/session/objects/state/BattleGameSnapshot";
import type { BattleHeroViewState as Hero } from "../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { BattleVector2 as Vec2 } from "../../../../../objects/battle/objects/core/BattleCoreScalars";
import Phaser from "phaser";
import type { ObstacleBounds, OccludableView } from "../../renderer/arena/objects/ArenaBuilderObjects";
import type { HeroView, WorldViewState } from "../../renderer/entities/worldViewFactory";
import type { LocalHeroDisplay } from "../../renderer/entities/BattleLocalHeroDisplay";
import { createGameSceneHeroDisplacementBridge } from "../../renderer/entities/BattleGameSceneHeroDisplacementBridge";
import { CombatProjectileEffectSceneBridge } from "../../renderer/effects/combatProjectileEffectSceneBridge";
import { PlayerAbilitySceneBridge } from "../../renderer/effects/playerAbilitySceneBridge";
import { PlayerMotionTweenController } from "../../renderer/effects/playerMotionTweenController";
import type { SceneVfxController } from "../../renderer/effects/sceneVfxController";
import { WeaponActionSceneBridge } from "../../renderer/effects/weaponActionSceneBridge";
import { ProjectileFrameSceneBridge } from "../../renderer/effects/projectileFrameSceneBridge";
import { createGameSceneBattleFeedbackBridge } from "../../renderer/effects/factories/GameSceneBattleFeedbackBridgeFactory";
import { createGameSceneSharedAuthoritativeLocalFeedbackBridge } from "../../renderer/effects/factories/GameSceneSharedAuthoritativeLocalFeedbackBridgeFactory";
import { BotFrameBridge } from "../../../../bots/controller/botFrameBridge";
import { FreezeFieldSceneBridge } from "../../../local/skills/freezeFieldSceneBridge";
import { PickupFrameBridge } from "../../../local/pickups/pickupFrameBridge";
import type { ProjectileSequenceBridge } from "../../../local/projectiles/projectileSequenceBridge";
import { LocalBattleFrameSceneBridge } from "../../../local/session/localBattleFrameSceneBridge";
import { BattleTemporalFrameBridge } from "../../../local/timers/battleTemporalFrameBridge";
import type { WeaponSwitchStateBridge } from "../../../local/weapons/weaponSwitchStateBridge";
import { WeaponWheelSwitchSceneBridge } from "../../../local/weapons/weaponWheelSwitchSceneBridge";
import type { GameSceneRuntimeBridgeSet } from "../objects/GameSceneRuntimeBridgeSet";

export interface CreateGameSceneRuntimeBridgesOptions {
  readonly scene: Phaser.Scene;
  readonly playerActor: Phaser.Physics.Arcade.Image;
  readonly localHeroDisplay: LocalHeroDisplay;
  readonly worldViews: WorldViewState;
  readonly heroViews: Map<string, HeroView>;
  readonly obstacleBounds: ObstacleBounds[];
  readonly occludables: OccludableView[];
  readonly sharedAuthoritativeRuntime: boolean;
  readonly weaponSwitchStateBridge: WeaponSwitchStateBridge;
  readonly projectileSequenceBridge: ProjectileSequenceBridge;
  readonly vfx: SceneVfxController;
  readonly getSnapshot: () => GameSnapshot;
  readonly getPlayerHero: () => Hero;
  readonly getAuthoritativeHeroIds: () => Set<string>;
  readonly getBaseHeroScale: (heroId: string) => number;
  readonly syncPlayerHeroFromPhysics: () => void;
  readonly setHeroPosition: (hero: Hero, position: Vec2) => void;
  readonly flashHero: (heroId: string, color: number) => void;
}

export function createGameSceneRuntimeBridges(options: CreateGameSceneRuntimeBridgesOptions): GameSceneRuntimeBridgeSet {
  const temporalFrameBridge = new BattleTemporalFrameBridge();

  const weaponWheelSwitchBridge = new WeaponWheelSwitchSceneBridge({
    getPlayerHero: options.getPlayerHero,
    isAuthoritativeRendererHost: () => options.sharedAuthoritativeRuntime,
    getNowMs: () => performance.now(),
    weaponSwitchStateBridge: options.weaponSwitchStateBridge,
    showFloatingText: (position, text, tone) => options.vfx.showFloatingText(position, text, tone)
  });

  const freezeFieldBridge = new FreezeFieldSceneBridge({
    getSlowFields: () => options.getSnapshot().slowFields,
    setSlowFields: (fields) => {
      options.getSnapshot().slowFields = fields;
    },
    showFloatingText: (position, text, tone) => options.vfx.showFloatingText(position, text, tone)
  });

  const motionController = new PlayerMotionTweenController({
    scene: options.scene,
    playerActor: options.playerActor,
    heroViews: options.heroViews,
    getPlayerHero: options.getPlayerHero,
    getBaseHeroScale: options.getBaseHeroScale,
    createPulse: (position, radius, color) => options.vfx.createPulse(position, radius, color)
  });

  const playerAbilityBridge = new PlayerAbilitySceneBridge({
    getPlayerHero: options.getPlayerHero,
    getWorldSize: () => options.getSnapshot().worldSize,
    getObstacleBounds: () => options.obstacleBounds,
    getHeroViews: () => options.heroViews,
    getBaseHeroScale: options.getBaseHeroScale,
    isPlayerMotionActive: () => motionController.isActive(),
    startPlayerMotion: (destination, durationMs, motionType) => motionController.start(destination, durationMs, motionType),
    createAfterimage: (position, rotation, scale, textureKey, tint, alpha) =>
      motionController.createAfterimage(position, rotation, scale, textureKey, tint, alpha),
    createPulse: (position, radius, color) => options.vfx.createPulse(position, radius, color),
    createFloatingText: (position, text, color) => options.vfx.createFloatingText(position, text, color),
    showFloatingText: (position, text, tone) => options.vfx.showFloatingText(position, text, tone),
    addFreezeField: (ownerHeroId, position, radius, durationMs) =>
      freezeFieldBridge.addFreezeField(ownerHeroId, position, radius, durationMs)
  });

  const heroDisplacementBridge = createGameSceneHeroDisplacementBridge({
    getWorldSize: () => options.getSnapshot().worldSize,
    getObstacleBounds: () => options.obstacleBounds,
    getPlayerHero: options.getPlayerHero,
    setHeroPosition: options.setHeroPosition
  });

  const combatEffectBridge = new CombatProjectileEffectSceneBridge({
    getSnapshot: options.getSnapshot,
    createPulse: (position, radius, color) => options.vfx.createPulse(position, radius, color),
    createImpactSpark: (position, color) => options.vfx.createImpactSpark(position, color),
    createShockwave: (position, startRadius, endRadius, color, duration) =>
      options.vfx.createShockwave(position, startRadius, endRadius, color, duration),
    createFloatingText: (position, text, color) => options.vfx.createFloatingText(position, text, color),
    flashHero: options.flashHero,
    shakeCamera: (duration, intensity) => options.scene.cameras.main.shake(duration, intensity),
    stopPlayerMotion: () => motionController.stop(),
    setPlayerActorDisabled: () => {
      const body = options.playerActor.body as Phaser.Physics.Arcade.Body;
      body.enable = false;
      options.playerActor.setVelocity(0, 0);
    },
    applyKnockback: (hero, direction, strength) => heroDisplacementBridge.applyKnockback(hero, direction, strength),
    pushEvent: (type, message) => temporalFrameBridge.pushEvent(options.getSnapshot(), type, message)
  });

  const weaponActionBridge = new WeaponActionSceneBridge({
    getPlayerHero: options.getPlayerHero,
    getWeaponSwitchRemainingMs: () => options.weaponSwitchStateBridge.getWeaponSwitchRemainingMs(),
    isPlayerMotionActive: () => motionController.isActive(),
    getProjectileSequence: () => options.projectileSequenceBridge.getSequence(),
    setProjectileSequence: (next) => options.projectileSequenceBridge.setSequence(next),
    addProjectile: (projectile) => {
      options.getSnapshot().projectiles.push(projectile);
    },
    showFloatingText: (position, text, tone) => options.vfx.showFloatingText(position, text, tone),
    createMuzzleBurst: (position, color, radius, sparks, direction) =>
      options.vfx.createMuzzleBurst(position, color, radius, sparks, direction),
    createPulse: (position, radius, color) => options.vfx.createPulse(position, radius, color),
    createImpactSpark: (position, color) => options.vfx.createImpactSpark(position, color),
    applyRecoil: (direction, strength) => heroDisplacementBridge.applyRecoil(direction, strength)
  });

  const projectileFrameBridge = new ProjectileFrameSceneBridge({
    getSnapshot: options.getSnapshot,
    getObstacleBounds: () => options.obstacleBounds,
    presentEffect: (effect) => combatEffectBridge.present(effect)
  });

  const battleFeedbackBridge = createGameSceneBattleFeedbackBridge({
    getSnapshot: options.getSnapshot,
    getWorldViews: () => options.worldViews,
    flashHero: options.flashHero,
    vfx: options.vfx,
    camera: options.scene.cameras.main
  });

  const sharedAuthoritativeLocalFeedbackBridge = createGameSceneSharedAuthoritativeLocalFeedbackBridge({
    getPlayerHero: options.getPlayerHero,
    localHeroDisplay: options.localHeroDisplay,
    getWorldSize: () => options.getSnapshot().worldSize,
    getObstacleBounds: () => options.obstacleBounds,
    getNowMs: () => performance.now(),
    vfx: options.vfx
  });

  const botFrameBridge = new BotFrameBridge({
    getSnapshot: options.getSnapshot,
    getObstacleBounds: () => options.obstacleBounds,
    getProjectileSequence: () => options.projectileSequenceBridge.getSequence(),
    setProjectileSequence: (nextSequence) => options.projectileSequenceBridge.setSequence(nextSequence),
    getAuthoritativeHeroIds: options.getAuthoritativeHeroIds
  });

  const pickupFrameBridge = new PickupFrameBridge({
    getSnapshot: options.getSnapshot,
    getPlayerHero: options.getPlayerHero,
    getObstacleBounds: () => options.obstacleBounds,
    getOccludables: () => options.occludables,
    showFloatingText: (position, text, tone) => options.vfx.showFloatingText(position, text, tone),
    createPulse: (position, radius, color) => options.vfx.createPulse(position, radius, color),
    pushEvent: (type, message) => temporalFrameBridge.pushEvent(options.getSnapshot(), type, message)
  });

  const localBattleFrameBridge = new LocalBattleFrameSceneBridge({
    getSnapshot: options.getSnapshot,
    getPlayerHero: options.getPlayerHero,
    syncPlayerHeroFromPhysics: options.syncPlayerHeroFromPhysics,
    setPlayerActorVelocity: (velocity) => options.playerActor.setVelocity(velocity.x, velocity.y),
    isPlayerMotionActive: () => motionController.isActive(),
    showFloatingText: (position, text, tone) => options.vfx.showFloatingText(position, text, tone),
    pickupFrameBridge,
    playerAbilityBridge,
    weaponActionBridge,
    botFrameBridge,
    projectileFrameBridge,
    weaponSwitchStateBridge: options.weaponSwitchStateBridge
  });

  return {
    weaponSwitchStateBridge: options.weaponSwitchStateBridge,
    projectileSequenceBridge: options.projectileSequenceBridge,
    weaponWheelSwitchBridge,
    freezeFieldBridge,
    motionController,
    playerAbilityBridge,
    combatEffectBridge,
    weaponActionBridge,
    projectileFrameBridge,
    battleFeedbackBridge,
    sharedAuthoritativeLocalFeedbackBridge,
    botFrameBridge,
    pickupFrameBridge,
    temporalFrameBridge,
    localBattleFrameBridge
  };
}
