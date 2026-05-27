import Phaser from "phaser";
import type { GameSnapshot, Hero, PlayerCommand, PreparedSkill, Vec2 } from "../../../../objects/battle/types";
import { buildArena, type ObstacleBounds, type OccludableView } from "../renderer/arena/arenaBuilder";
import { createWorldViewState, type HeroView, type WorldViewState } from "../renderer/entities/worldViewFactory";
import { CombatProjectileEffectSceneBridge } from "../renderer/effects/combatProjectileEffectSceneBridge";
import { SceneVfxController } from "../renderer/effects/sceneVfxController";
import { PlayerMotionTweenController } from "../renderer/effects/playerMotionTweenController";
import { preloadBattleAssets } from "../renderer/battleAssetPreloader";
import { cloneGameSnapshot } from "../renderer/snapshot/exportSnapshotClone";
import { getPlayerHeroFromSnapshot } from "../renderer/snapshot/playerHeroLookup";
import { createBattleControlKeys, type ControlKeys } from "../../local/input/controlKeys";
import type { WheelSwitchDetail } from "../../local/input/wheelSwitchAdapter";
import { BattleTemporalFrameBridge } from "../../local/timers/battleTemporalFrameBridge";
import { FreezeFieldSceneBridge } from "../../local/skills/freezeFieldSceneBridge";
import { PlayerAbilitySceneBridge } from "../renderer/effects/playerAbilitySceneBridge";
import { BattleHudSceneBridge } from "../renderer/hud/battleHudSceneBridge";
import { createInitialBattleSnapshot } from "../../local/session/initialBattleSnapshot";
import { RespawnSceneBridge } from "../../local/session/respawnSceneBridge";
import { WeaponActionSceneBridge } from "../renderer/effects/weaponActionSceneBridge";
import { ProjectileFrameSceneBridge } from "../renderer/effects/projectileFrameSceneBridge";
import type { BattleFeedbackSceneBridge } from "../renderer/effects/battleFeedbackSceneBridge";
import type { SharedAuthoritativeLocalFeedbackSceneBridge } from "../renderer/effects/sharedAuthoritativeLocalFeedbackSceneBridge";
import { BotFrameBridge } from "../../../bots/controller/botFrameBridge";
import { PickupFrameBridge } from "../../local/pickups/pickupFrameBridge";
import { WeaponSwitchStateBridge } from "../../local/weapons/weaponSwitchStateBridge";
import { WeaponWheelSwitchSceneBridge } from "../../local/weapons/weaponWheelSwitchSceneBridge";
import { createProjectileSequenceBridge } from "../../local/projectiles/projectileSequenceBridge";
import {
  AuthoritativeFrameSceneBridge,
  type GameSceneAuthoritativeFrame,
  type GameSceneAuthoritativeFrameOptions
} from "../renderer/authoritativeFrameSceneBridge";
import { LocalHeroDisplay } from "../renderer/localHeroDisplayPose";
import { LocalBattleFrameSceneBridge } from "../../local/session/localBattleFrameSceneBridge";
import { getHeroBasePresentationScale } from "../renderer/entities/heroPresentationScale";
import {
  renderGameSceneHud,
  syncGameSceneWorldViews,
  updateGameSceneOcclusion
} from "../renderer/gameScenePresentationBridge";
import { createGameScenePlayerActor, flashGameSceneHero } from "../renderer/gameSceneHeroActorBridge";
import {
  configureGameSceneCamera,
  configureGameSceneCameraBounds,
  createGameSceneCameraTarget,
  updateGameSceneCameraTarget
} from "../renderer/gameSceneCameraBridge";
import { readGameScenePlayerCommand } from "../renderer/gameSceneInputBridge";
import { createGameSceneHeroDisplacementBridge } from "../renderer/gameSceneHeroDisplacementBridge";
import {
  createGameSceneBattleFeedbackBridge,
  createGameSceneSharedAuthoritativeLocalFeedbackBridge
} from "../renderer/gameSceneFeedbackBridgeFactory";

interface GameSceneOptions {
  sharedAuthoritativeRuntime?: boolean;
}

const OCCLUSION_SYNC_INTERVAL_MS = 120;
const OCCLUSION_SYNC_POSITION_DELTA = 48;

export class GameScene extends Phaser.Scene {
  private snapshot!: GameSnapshot;
  private controls!: ControlKeys;
  private wallBodies!: Phaser.Physics.Arcade.StaticGroup;
  private playerActor!: Phaser.Physics.Arcade.Image;
  private localHeroDisplay!: LocalHeroDisplay;
  private authoritativeFrameBridge!: AuthoritativeFrameSceneBridge;
  private cameraTarget!: Phaser.GameObjects.Zone;
  private worldViews!: WorldViewState;
  private heroViews = new Map<string, HeroView>();
  private obstacleBounds: ObstacleBounds[] = [];
  private occludables: OccludableView[] = [];
  private pointerJustPressed = false;
  private secondaryJustPressed = false;
  private pendingWeaponSwitchDirection: -1 | 0 | 1 = 0;
  private cameraOffset: Vec2 = { x: 0, y: 0 };
  private motionController!: PlayerMotionTweenController;
  private freezeFieldBridge!: FreezeFieldSceneBridge;
  private playerAbilityBridge!: PlayerAbilitySceneBridge;
  private combatEffectBridge!: CombatProjectileEffectSceneBridge;
  private weaponActionBridge!: WeaponActionSceneBridge;
  private projectileFrameBridge!: ProjectileFrameSceneBridge;
  private battleFeedbackBridge!: BattleFeedbackSceneBridge;
  private sharedAuthoritativeLocalFeedbackBridge!: SharedAuthoritativeLocalFeedbackSceneBridge;
  private botFrameBridge!: BotFrameBridge;
  private pickupFrameBridge!: PickupFrameBridge;
  private respawnBridge!: RespawnSceneBridge;
  private temporalFrameBridge!: BattleTemporalFrameBridge;
  private localBattleFrameBridge!: LocalBattleFrameSceneBridge;
  private weaponWheelSwitchBridge!: WeaponWheelSwitchSceneBridge;
  private vfx!: SceneVfxController;
  private hudBridge!: BattleHudSceneBridge;
  private readonly weaponSwitchStateBridge = new WeaponSwitchStateBridge();
  private readonly projectileSequenceBridge = createProjectileSequenceBridge();
  private authoritativeRemoteHeroIds = new Set<string>();
  private latestPlayerCommand: PlayerCommand | null = null;
  private authoritativePreparedSkillOverride: PreparedSkill = null;
  private lastOcclusionSyncAtMs = Number.NEGATIVE_INFINITY;
  private lastOcclusionSyncPosition: Vec2 | null = null;
  private elapsedOffsetMs = 0;
  private readonly sharedAuthoritativeRuntime: boolean;

  constructor(private readonly initialSnapshot: GameSnapshot | null = null, options: GameSceneOptions = {}) {
    super("game-scene");
    this.sharedAuthoritativeRuntime = options.sharedAuthoritativeRuntime ?? false;
  }

  preload() {
    preloadBattleAssets(this);
  }

  create() {
    this.input.setDefaultCursor("crosshair");
    this.input.mouse?.disableContextMenu();
    this.input.on("pointerdown", this.handlePointerDown, this);
    this.input.on("wheel", this.handleMouseWheel, this);
    window.addEventListener("game-wheel-switch", this.onGlobalWheelSwitch as EventListener);
    this.events.once(Phaser.Scenes.Events.SHUTDOWN, () => {
      window.removeEventListener("game-wheel-switch", this.onGlobalWheelSwitch as EventListener);
    });

    this.snapshot = this.initialSnapshot ? cloneGameSnapshot(this.initialSnapshot) : createInitialBattleSnapshot();
    this.elapsedOffsetMs = Math.max(0, this.snapshot.elapsedMs);
    this.applyAuthoritativePreparedSkillOverride();

    this.controls = createBattleControlKeys(this.input);
    this.configureWorldBounds();
    this.wallBodies = this.physics.add.staticGroup();

    this.createArena();
    this.createPlayerActor();
    this.cameraTarget = createGameSceneCameraTarget(this, this.getPlayerHero());
    this.createHeroViews();
    this.hudBridge = new BattleHudSceneBridge();
    this.hudBridge.layout(this.scale.width, this.scale.height);
    this.events.once(Phaser.Scenes.Events.SHUTDOWN, () => {
      this.hudBridge.destroy();
    });
    configureGameSceneCamera(this.cameras.main, this.cameraTarget, this.snapshot.worldSize);
    this.vfx = new SceneVfxController(this);
    this.weaponWheelSwitchBridge = new WeaponWheelSwitchSceneBridge({
      getPlayerHero: () => this.getPlayerHero(),
      isAuthoritativeRendererHost: () => this.sharedAuthoritativeRuntime,
      getNowMs: () => performance.now(),
      weaponSwitchStateBridge: this.weaponSwitchStateBridge,
      showFloatingText: (position, text, tone) => this.vfx.showFloatingText(position, text, tone)
    });
    this.freezeFieldBridge = new FreezeFieldSceneBridge({
      getSlowFields: () => this.snapshot.slowFields,
      setSlowFields: (fields) => {
        this.snapshot.slowFields = fields;
      },
      showFloatingText: (position, text, tone) => this.vfx.showFloatingText(position, text, tone)
    });
    this.motionController = new PlayerMotionTweenController({
      scene: this,
      playerActor: this.playerActor,
      heroViews: this.heroViews,
      getPlayerHero: () => this.getPlayerHero(),
      getBaseHeroScale: (heroId) => getHeroBasePresentationScale(heroId, this.snapshot.playerHeroId),
      createPulse: (position, radius, color) => this.vfx.createPulse(position, radius, color)
    });
    this.playerAbilityBridge = new PlayerAbilitySceneBridge({
      getPlayerHero: () => this.getPlayerHero(),
      getWorldSize: () => this.snapshot.worldSize,
      getObstacleBounds: () => this.obstacleBounds,
      getHeroViews: () => this.heroViews,
      getBaseHeroScale: (heroId) => getHeroBasePresentationScale(heroId, this.snapshot.playerHeroId),
      isPlayerMotionActive: () => this.isPlayerMotionActive(),
      startPlayerMotion: (destination, durationMs, motionType) => this.startPlayerMotion(destination, durationMs, motionType),
      createAfterimage: (position, rotation, scale, textureKey, tint, alpha) => this.createAfterimage(position, rotation, scale, textureKey, tint, alpha),
      createPulse: (position, radius, color) => this.vfx.createPulse(position, radius, color),
      createFloatingText: (position, text, color) => this.vfx.createFloatingText(position, text, color),
      showFloatingText: (position, text, tone) => this.vfx.showFloatingText(position, text, tone),
      addFreezeField: (ownerHeroId, position, radius, durationMs) =>
        this.freezeFieldBridge.addFreezeField(ownerHeroId, position, radius, durationMs)
    });
    const heroDisplacementBridge = createGameSceneHeroDisplacementBridge({
      getWorldSize: () => this.snapshot.worldSize,
      getObstacleBounds: () => this.obstacleBounds,
      getPlayerHero: () => this.getPlayerHero(),
      setHeroPosition: (hero, position) => this.setHeroPosition(hero, position)
    });
    this.combatEffectBridge = new CombatProjectileEffectSceneBridge({
      getSnapshot: () => this.snapshot,
      createPulse: (position, radius, color) => this.vfx.createPulse(position, radius, color),
      createImpactSpark: (position, color) => this.vfx.createImpactSpark(position, color),
      createShockwave: (position, startRadius, endRadius, color, duration) => this.vfx.createShockwave(position, startRadius, endRadius, color, duration),
      createFloatingText: (position, text, color) => this.vfx.createFloatingText(position, text, color),
      flashHero: (heroId, color) => this.flashHero(heroId, color),
      shakeCamera: (duration, intensity) => this.cameras.main.shake(duration, intensity),
      stopPlayerMotion: () => this.stopPlayerMotion(),
      setPlayerActorDisabled: () => {
        const body = this.playerActor.body as Phaser.Physics.Arcade.Body;
        body.enable = false;
        this.playerActor.setVelocity(0, 0);
      },
      applyKnockback: (hero, direction, strength) => heroDisplacementBridge.applyKnockback(hero, direction, strength),
      pushEvent: (type, message) => this.temporalFrameBridge.pushEvent(this.snapshot, type, message)
    });
    this.weaponActionBridge = new WeaponActionSceneBridge({
      getPlayerHero: () => this.getPlayerHero(),
      getWeaponSwitchRemainingMs: () => this.weaponSwitchStateBridge.getWeaponSwitchRemainingMs(),
      isPlayerMotionActive: () => this.isPlayerMotionActive(),
      getProjectileSequence: () => this.projectileSequenceBridge.getSequence(),
      setProjectileSequence: (next) => this.projectileSequenceBridge.setSequence(next),
      addProjectile: (projectile) => {
        this.snapshot.projectiles.push(projectile);
      },
      showFloatingText: (position, text, tone) => this.vfx.showFloatingText(position, text, tone),
      createMuzzleBurst: (position, color, radius, sparks, direction) =>
        this.vfx.createMuzzleBurst(position, color, radius, sparks, direction),
      createPulse: (position, radius, color) => this.vfx.createPulse(position, radius, color),
      createImpactSpark: (position, color) => this.vfx.createImpactSpark(position, color),
      applyRecoil: (direction, strength) => heroDisplacementBridge.applyRecoil(direction, strength)
    });
    this.projectileFrameBridge = new ProjectileFrameSceneBridge({
      getSnapshot: () => this.snapshot,
      getObstacleBounds: () => this.obstacleBounds,
      presentEffect: (effect) => this.combatEffectBridge.present(effect)
    });
    this.battleFeedbackBridge = createGameSceneBattleFeedbackBridge({
      getSnapshot: () => this.snapshot,
      getWorldViews: () => this.worldViews,
      flashHero: (heroId, color) => this.flashHero(heroId, color),
      vfx: this.vfx,
      camera: this.cameras.main
    });
    this.sharedAuthoritativeLocalFeedbackBridge = createGameSceneSharedAuthoritativeLocalFeedbackBridge({
      getPlayerHero: () => this.getPlayerHero(),
      localHeroDisplay: this.localHeroDisplay,
      getWorldSize: () => this.snapshot.worldSize,
      getObstacleBounds: () => this.obstacleBounds,
      getNowMs: () => performance.now(),
      vfx: this.vfx
    });
    this.botFrameBridge = new BotFrameBridge({
      getSnapshot: () => this.snapshot,
      getObstacleBounds: () => this.obstacleBounds,
      getProjectileSequence: () => this.projectileSequenceBridge.getSequence(),
      setProjectileSequence: (nextSequence) => this.projectileSequenceBridge.setSequence(nextSequence),
      getAuthoritativeHeroIds: () => this.authoritativeRemoteHeroIds
    });
    this.pickupFrameBridge = new PickupFrameBridge({
      getSnapshot: () => this.snapshot,
      getPlayerHero: () => this.getPlayerHero(),
      getObstacleBounds: () => this.obstacleBounds,
      getOccludables: () => this.occludables,
      showFloatingText: (position, text, tone) => this.vfx.showFloatingText(position, text, tone),
      createPulse: (position, radius, color) => this.vfx.createPulse(position, radius, color),
      pushEvent: (type, message) => this.temporalFrameBridge.pushEvent(this.snapshot, type, message)
    });
    this.respawnBridge = new RespawnSceneBridge({
      getSnapshot: () => this.snapshot,
      getPlayerActor: () => this.playerActor,
      resetWeaponSwitchState: () => this.weaponSwitchStateBridge.reset(),
      stopPlayerMotion: () => this.stopPlayerMotion(),
      pushEvent: (type, message) => this.temporalFrameBridge.pushEvent(this.snapshot, type, message),
      createPulse: (position, radius, color) => this.vfx.createPulse(position, radius, color)
    });
    this.temporalFrameBridge = new BattleTemporalFrameBridge();
    this.localBattleFrameBridge = new LocalBattleFrameSceneBridge({
      getSnapshot: () => this.snapshot,
      getPlayerHero: () => this.getPlayerHero(),
      syncPlayerHeroFromPhysics: () => this.syncPlayerHeroFromPhysics(),
      setPlayerActorVelocity: (velocity) => this.playerActor.setVelocity(velocity.x, velocity.y),
      isPlayerMotionActive: () => this.isPlayerMotionActive(),
      showFloatingText: (position, text, tone) => this.vfx.showFloatingText(position, text, tone),
      pickupFrameBridge: this.pickupFrameBridge,
      respawnBridge: this.respawnBridge,
      playerAbilityBridge: this.playerAbilityBridge,
      weaponActionBridge: this.weaponActionBridge,
      botFrameBridge: this.botFrameBridge,
      projectileFrameBridge: this.projectileFrameBridge,
      weaponSwitchStateBridge: this.weaponSwitchStateBridge
    });
    this.events.once(Phaser.Scenes.Events.SHUTDOWN, () => {
      this.vfx.destroy();
    });

    this.physics.add.collider(this.playerActor, this.wallBodies);
    this.scale.on(Phaser.Scale.Events.RESIZE, this.handleResize, this);
    this.handleResize();
    this.renderHud(0);
  }
  update(time: number, delta: number) {
    this.advanceRuntimeLocalClock(time, delta);
    const command = this.readPlayerCommand();
    this.latestPlayerCommand = command;

    if (this.sharedAuthoritativeRuntime) {
      this.applyAuthoritativePreparedSkillOverride();
      this.stopPlayerMotion();
      this.playerActor.setVelocity(0, 0);
      this.authoritativeFrameBridge.updateLocalDisplayMotion({
        snapshot: this.snapshot,
        player: this.getPlayerHero(),
        command,
        deltaMs: delta,
        obstacleBounds: this.obstacleBounds,
        localPlayerMovementActive: this.isLatestPlayerCommandMovementActive()
      });
      this.sharedAuthoritativeLocalFeedbackBridge.update(command);
    } else {
      this.localBattleFrameBridge.update(command, delta);
    }
    this.updateCameraTarget();

    this.syncWorldViews(command, delta);
    this.battleFeedbackBridge.update(this.sharedAuthoritativeRuntime);
    this.updateOcclusionIfNeeded(time);
    this.renderHud(Math.round(this.game.loop.actualFps));
    this.vfx.updateVisualEffects(delta);

    this.pointerJustPressed = false;
    this.secondaryJustPressed = false;
    this.pendingWeaponSwitchDirection = 0;
  }
  private advanceRuntimeLocalClock(time: number, delta: number) {
    if (this.sharedAuthoritativeRuntime) {
      return;
    }

    this.snapshot.elapsedMs = this.elapsedOffsetMs + time;
    this.temporalFrameBridge.update(this.snapshot, delta);
  }
  private createArena() {
    buildArena({
      scene: this,
      wallBodies: this.wallBodies,
      obstacleBounds: this.obstacleBounds,
      occludables: this.occludables
    });
  }
  private createPlayerActor() {
    const handle = createGameScenePlayerActor(this, this.getPlayerHero());
    this.playerActor = handle.playerActor;
    this.localHeroDisplay = handle.localHeroDisplay;
    this.authoritativeFrameBridge = handle.authoritativeFrameBridge;
  }
  private createHeroViews() {
    this.worldViews = createWorldViewState({
      scene: this,
      snapshot: this.snapshot,
      getBaseHeroScale: (heroId) => getHeroBasePresentationScale(heroId, this.snapshot.playerHeroId)
    });
    this.heroViews = this.worldViews.heroViews;
  }
  private configureWorldBounds() {
    this.physics.world.setBounds(0, 0, this.snapshot.worldSize.x, this.snapshot.worldSize.y);
  }
  private updateCameraTarget() {
    const playerPosition = this.localHeroDisplay.positionFor(this.getPlayerHero(), this.sharedAuthoritativeRuntime);
    updateGameSceneCameraTarget({
      pointer: this.input.activePointer,
      scaleSize: this.scale.gameSize,
      playerPosition,
      cameraTarget: this.cameraTarget,
      cameraOffset: this.cameraOffset
    });
  }
  private updateOcclusionIfNeeded(timeMs: number) {
    const player = this.getPlayerHero();
    const playerPosition = this.localHeroDisplay.positionFor(player, this.sharedAuthoritativeRuntime);
    const elapsedMs = Number.isFinite(timeMs) ? timeMs - this.lastOcclusionSyncAtMs : OCCLUSION_SYNC_INTERVAL_MS;
    const movedDistance = this.lastOcclusionSyncPosition
      ? Math.hypot(playerPosition.x - this.lastOcclusionSyncPosition.x, playerPosition.y - this.lastOcclusionSyncPosition.y)
      : Number.POSITIVE_INFINITY;

    if (elapsedMs < OCCLUSION_SYNC_INTERVAL_MS && movedDistance < OCCLUSION_SYNC_POSITION_DELTA) {
      return;
    }

    updateGameSceneOcclusion({
      player,
      heroes: this.snapshot.heroes,
      sharedAuthoritativeRuntime: this.sharedAuthoritativeRuntime,
      localHeroDisplay: this.localHeroDisplay,
      occludables: this.occludables
    });
    this.lastOcclusionSyncAtMs = Number.isFinite(timeMs) ? timeMs : 0;
    this.lastOcclusionSyncPosition = { x: playerPosition.x, y: playerPosition.y };
  }
  private handleResize(_gameSize?: Phaser.Structs.Size) {
    this.cameras.main.setSize(this.scale.width, this.scale.height);
    this.hudBridge?.layout(this.scale.width, this.scale.height);
  }
  private renderHud(fps: number) {
    renderGameSceneHud({
      hudBridge: this.hudBridge,
      snapshot: this.snapshot,
      fps,
      weaponSwitchStateBridge: this.weaponSwitchStateBridge,
      sharedAuthoritativeRuntime: this.sharedAuthoritativeRuntime,
      localHeroDisplay: this.localHeroDisplay,
      camera: this.cameras.main,
      obstacleBounds: this.obstacleBounds
    });
  }
  private handlePointerDown(pointer: Phaser.Input.Pointer) {
    if (pointer.button === 0) { this.pointerJustPressed = true; return; }

    if (pointer.button === 2) { this.secondaryJustPressed = true; }
  }
  private handleMouseWheel(_pointer: Phaser.Input.Pointer, _gameObjects: Phaser.GameObjects.GameObject[], _deltaX: number, deltaY: number, _deltaZ: number, event: WheelEvent) {
    event.preventDefault();

    if (event.ctrlKey) { return; }

    this.captureWeaponSwitchDirection(deltaY);
    this.weaponWheelSwitchBridge.handleWheel("Phaser", deltaY);
  }
  private readonly onGlobalWheelSwitch = (event: Event): void => {
    const customEvent = event as CustomEvent<WheelSwitchDetail>;
    const deltaY = customEvent.detail?.deltaY ?? 0;
    this.captureWeaponSwitchDirection(deltaY);
    this.weaponWheelSwitchBridge.handleWheel("Window", deltaY);
  };
  private captureWeaponSwitchDirection(deltaY: number) {
    if (deltaY < 0) {
      this.pendingWeaponSwitchDirection = -1;
      return;
    }

    if (deltaY > 0) {
      this.pendingWeaponSwitchDirection = 1;
    }
  }
  private readPlayerCommand() {
    const player = this.getPlayerHero();
    return readGameScenePlayerCommand({
      input: this.input,
      controls: this.controls,
      playerPosition: this.localHeroDisplay.positionFor(player, this.sharedAuthoritativeRuntime),
      pointerJustPressed: this.pointerJustPressed,
      secondaryJustPressed: this.secondaryJustPressed,
      pendingWeaponSwitchDirection: this.pendingWeaponSwitchDirection,
      sharedAuthoritativeRuntime: this.sharedAuthoritativeRuntime,
      player,
      preparedSkill: this.authoritativePreparedSkillOverride,
      worldSize: this.snapshot.worldSize,
      obstacleBounds: this.obstacleBounds
    });
  }
  private syncPlayerHeroFromPhysics() {
    const player = this.getPlayerHero();
    const body = this.playerActor.body as Phaser.Physics.Arcade.Body;
    player.position.x = this.playerActor.x;
    player.position.y = this.playerActor.y;
    player.velocity = { x: body.velocity.x, y: body.velocity.y };
  }
  private setHeroPosition(hero: Hero, position: Vec2) {
    hero.position = { x: position.x, y: position.y };
    if (hero.heroId === this.snapshot.playerHeroId) {
      this.playerActor.setPosition(position.x, position.y);
    }
  }

  private syncWorldViews(command: PlayerCommand, deltaMs: number) {
    syncGameSceneWorldViews({
      scene: this,
      snapshot: this.snapshot,
      worldViews: this.worldViews,
      command,
      deltaMs,
      weaponSwitchStateBridge: this.weaponSwitchStateBridge,
      playerAbilityBridge: this.playerAbilityBridge,
      sharedAuthoritativeRuntime: this.sharedAuthoritativeRuntime,
      remoteAuthoritativeHeroIds: this.authoritativeRemoteHeroIds,
      localHeroDisplay: this.localHeroDisplay,
      obstacleBounds: this.obstacleBounds
    });
  }
  private isPlayerMotionActive() {
    return this.motionController.isActive();
  }
  private stopPlayerMotion() {
    this.motionController.stop();
  }
  private startPlayerMotion(destination: Vec2, durationMs: number, motionType: "jump" | "dash" | "blink") {
    this.motionController.start(destination, durationMs, motionType);
  }
  private createAfterimage(position: Vec2, rotation: number, scale: number, textureKey: string, tint: number, alpha: number) {
    this.motionController.createAfterimage(position, rotation, scale, textureKey, tint, alpha);
  }
  private flashHero(heroId: string, flashColor: number) {
    const hero = this.snapshot.heroes.find((entry) => entry.heroId === heroId);
    const view = this.heroViews.get(heroId);
    if (!hero || !view) {
      return;
    }

    flashGameSceneHero(this.time, hero, view, flashColor);
  }
  private getPlayerHero() {
    return getPlayerHeroFromSnapshot(this.snapshot);
  }

  setAuthoritativePreparedSkill(preparedSkill: PreparedSkill) {
    this.authoritativePreparedSkillOverride = preparedSkill;
    this.applyAuthoritativePreparedSkillOverride();
  }

  private applyAuthoritativePreparedSkillOverride() {
    if (!this.sharedAuthoritativeRuntime || this.snapshot === undefined) {
      return;
    }

    this.getPlayerHero().preparedSkill = this.authoritativePreparedSkillOverride;
  }

  applyAuthoritativeFrame(frame: GameSceneAuthoritativeFrame, options: GameSceneAuthoritativeFrameOptions = {}) {
    if (this.snapshot === undefined) {
      return;
    }

    this.battleFeedbackBridge?.applyAuthoritativeFrame(frame);
    this.authoritativeRemoteHeroIds = this.authoritativeFrameBridge.applyFrame({
      snapshot: this.snapshot,
      frame,
      localPlayerMovementActive: this.isLatestPlayerCommandMovementActive(),
      obstacleBounds: this.obstacleBounds,
      options
    });
    this.applyAuthoritativePreparedSkillOverride();
    this.configureWorldBounds();
    configureGameSceneCameraBounds(this.cameras.main, this.snapshot.worldSize);
  }

  exportSnapshot() {
    if (this.snapshot === undefined) { return null; }
    return cloneGameSnapshot(this.snapshot);
  }

  private isLatestPlayerCommandMovementActive() {
    const command = this.latestPlayerCommand;
    if (!command) {
      return false;
    }

    return command.castDash || Math.hypot(command.movement.x, command.movement.y) > 0.001;
  }
}
