import type { BattleGameSnapshot as GameSnapshot } from "../../../../objects/battle/microservices/session/objects/state/BattleGameSnapshot";
import type { BattleVector2 as Vec2 } from "../../../../objects/battle/objects/core/BattleCoreScalars";
import Phaser from "phaser";
import type { BattleHeroViewState as Hero } from "../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { BattlePreparedSkill as PreparedSkill } from "../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { BattlePlayerCommand as PlayerCommand } from "../../../../objects/battle/microservices/session/objects/command/BattlePlayerCommand";
import { buildArena } from "../renderer/arena/arenaBuilder";
import type { ObstacleBounds, OccludableView, StaticMapView } from "../renderer/arena/objects/ArenaBuilderObjects";
import { createWorldViewState, type HeroView, type WorldViewState } from "../renderer/entities/worldViewFactory";
import { SceneVfxController } from "../renderer/effects/sceneVfxController";
import { WinterZombieVisualController } from "../renderer/effects/winterZombieVisualController";
import { preloadBattleAssets } from "../renderer/assets/BattleAssetPreloader";
import { cloneGameSnapshot } from "../renderer/snapshot/exportSnapshotClone";
import { getPlayerHeroFromSnapshot } from "../renderer/snapshot/playerHeroLookup";
import { createBattleControlKeys, type ControlKeys } from "../../local/input/controlKeys";
import type { WheelSwitchDetail } from "../../local/input/wheelSwitchAdapter";
import { BattleHudSceneBridge } from "../renderer/hud/battleHudSceneBridge";
import { createInitialBattleSnapshot } from "../../local/session/initialBattleSnapshot";
import { WeaponSwitchStateBridge } from "../../local/weapons/weaponSwitchStateBridge";
import { createProjectileSequenceBridge } from "../../local/projectiles/projectileSequenceBridge";
import {
  AuthoritativeFrameSceneBridge,
  type GameSceneAuthoritativeFrame,
  type GameSceneAuthoritativeFrameOptions
} from "../renderer/authoritative/BattleAuthoritativeFrameSceneBridge";
import { LocalHeroDisplay } from "../renderer/entities/BattleLocalHeroDisplay";
import { getHeroBasePresentationScale } from "../renderer/entities/functions/HeroPresentationScaleRules";
import {
  renderGameSceneHud
} from "../renderer/presentation/BattleGameSceneHudPresentation";
import {
  syncGameSceneWorldViews
} from "../renderer/presentation/BattleGameSceneWorldViewPresentation";
import {
  updateGameSceneOcclusion
} from "../renderer/presentation/BattleGameSceneOcclusionPresentation";
import {
  updateGameSceneStaticMapCulling
} from "../renderer/presentation/BattleGameSceneStaticMapCullingPresentation";
import { createGameScenePlayerActor, flashGameSceneHero } from "../renderer/entities/BattleGameSceneHeroActorBridge";
import {
  configureGameSceneCamera,
  configureGameSceneCameraBounds,
  createGameSceneCameraTarget,
  updateGameSceneCameraTarget
} from "../renderer/camera/BattleGameSceneCameraBridge";
import { readGameScenePlayerCommand } from "../renderer/input/BattleGameSceneInputBridge";
import { BattleExtractionObjectiveOverlay } from "../../microservices/extraction/components/BattleExtractionObjectiveOverlay";
import { advanceBattleSharedAuthoritativeGasZone } from "../../microservices/extraction/functions/advanceBattleSharedAuthoritativeGasZone";
import {
  createBattleAuthoritativeClockAnchor,
  resolveBattleAuthoritativeClockElapsedMs,
  type BattleAuthoritativeClockAnchor
} from "../../microservices/session/functions/BattleAuthoritativeClockSyncRules";
import { createGameSceneRuntimeBridges } from "./functions/createGameSceneRuntimeBridges";
import { installGameSceneInputLifecycle } from "./functions/installGameSceneInputLifecycle";
import type { GameSceneRuntimeBridgeSet } from "./objects/GameSceneRuntimeBridgeSet";

interface GameSceneOptions {
  sharedAuthoritativeRuntime?: boolean;
}

const OCCLUSION_SYNC_INTERVAL_MS = 120;
const OCCLUSION_SYNC_POSITION_DELTA = 48;
const STATIC_MAP_CULL_INTERVAL_MS = 240;
const STATIC_MAP_CULL_POSITION_DELTA = 192;

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
  private staticMapViews: StaticMapView[] = [];
  private pointerJustPressed = false;
  private secondaryJustPressed = false;
  private pendingWeaponSwitchDirection: -1 | 0 | 1 = 0;
  private cameraOffset: Vec2 = { x: 0, y: 0 };
  private runtimeBridges!: GameSceneRuntimeBridgeSet;
  private vfx!: SceneVfxController;
  private winterZombieVisuals!: WinterZombieVisualController;
  private hudBridge!: BattleHudSceneBridge;
  private objectiveOverlay!: BattleExtractionObjectiveOverlay;
  private readonly weaponSwitchStateBridge = new WeaponSwitchStateBridge();
  private readonly projectileSequenceBridge = createProjectileSequenceBridge();
  private authoritativeRemoteHeroIds = new Set<string>();
  private latestPlayerCommand: PlayerCommand | null = null;
  private authoritativePreparedSkillOverride: PreparedSkill = null;
  private authoritativeClockAnchor: BattleAuthoritativeClockAnchor | null = null;
  private lastOcclusionSyncAtMs = Number.NEGATIVE_INFINITY;
  private lastOcclusionSyncPosition: Vec2 | null = null;
  private lastStaticMapCullAtMs = Number.NEGATIVE_INFINITY;
  private lastStaticMapCullPosition: Vec2 | null = null;
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
    installGameSceneInputLifecycle(this, {
      onPointerDown: (pointer) => this.handlePointerDown(pointer),
      onMouseWheel: (pointer, gameObjects, deltaX, deltaY, deltaZ, event) =>
        this.handleMouseWheel(pointer, gameObjects, deltaX, deltaY, deltaZ, event),
      onGlobalWheelSwitch: (event) => this.onGlobalWheelSwitch(event)
    });

    this.snapshot = this.initialSnapshot ? cloneGameSnapshot(this.initialSnapshot) : createInitialBattleSnapshot();
    this.elapsedOffsetMs = Math.max(0, this.snapshot.elapsedMs);
    this.applyAuthoritativePreparedSkillOverride();

    this.controls = createBattleControlKeys(this.input);
    this.configureWorldBounds();
    this.wallBodies = this.physics.add.staticGroup();

    this.createArena();
    this.objectiveOverlay = new BattleExtractionObjectiveOverlay(this);
    this.createPlayerActor();
    this.cameraTarget = createGameSceneCameraTarget(this, this.getPlayerHero());
    this.createHeroViews();
    this.winterZombieVisuals = new WinterZombieVisualController(this);
    this.hudBridge = new BattleHudSceneBridge();
    this.hudBridge.layout(this.scale.width, this.scale.height);
    this.events.once(Phaser.Scenes.Events.SHUTDOWN, () => {
      this.hudBridge.destroy();
    });
    configureGameSceneCamera(this.cameras.main, this.cameraTarget, this.snapshot.worldSize);
    this.vfx = new SceneVfxController(this);
    this.runtimeBridges = createGameSceneRuntimeBridges({
      scene: this,
      playerActor: this.playerActor,
      localHeroDisplay: this.localHeroDisplay,
      worldViews: this.worldViews,
      heroViews: this.heroViews,
      obstacleBounds: this.obstacleBounds,
      occludables: this.occludables,
      sharedAuthoritativeRuntime: this.sharedAuthoritativeRuntime,
      weaponSwitchStateBridge: this.weaponSwitchStateBridge,
      projectileSequenceBridge: this.projectileSequenceBridge,
      vfx: this.vfx,
      getSnapshot: () => this.snapshot,
      getPlayerHero: () => this.getPlayerHero(),
      getAuthoritativeHeroIds: () => this.authoritativeRemoteHeroIds,
      getBaseHeroScale: (heroId) => getHeroBasePresentationScale(heroId, this.snapshot.playerHeroId),
      syncPlayerHeroFromPhysics: () => this.syncPlayerHeroFromPhysics(),
      setHeroPosition: (hero, position) => this.setHeroPosition(hero, position),
      flashHero: (heroId, color) => this.flashHero(heroId, color)
    });
    this.events.once(Phaser.Scenes.Events.SHUTDOWN, () => {
      this.vfx.destroy();
      this.winterZombieVisuals.destroy();
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
      this.runtimeBridges.motionController.stop();
      this.playerActor.setVelocity(0, 0);
      this.authoritativeFrameBridge.updateLocalDisplayMotion({
        snapshot: this.snapshot,
        player: this.getPlayerHero(),
        command,
        deltaMs: delta,
        obstacleBounds: this.obstacleBounds,
        localPlayerMovementActive: this.isLatestPlayerCommandMovementActive()
      });
      this.runtimeBridges.sharedAuthoritativeLocalFeedbackBridge.update(command);
    } else {
      this.runtimeBridges.localBattleFrameBridge.update(command, delta);
    }
    this.updateCameraTarget();
    this.updateStaticMapCullingIfNeeded(time);

    this.syncWorldViews(command, delta);
    this.winterZombieVisuals.update(this.snapshot, this.heroViews, delta);
    this.objectiveOverlay.render(this.snapshot);
    this.runtimeBridges.battleFeedbackBridge.update(this.sharedAuthoritativeRuntime);
    this.updateOcclusionIfNeeded(time);
    this.renderHud(Math.round(this.game.loop.actualFps));
    this.vfx.updateVisualEffects(delta);

    this.pointerJustPressed = false;
    this.secondaryJustPressed = false;
    this.pendingWeaponSwitchDirection = 0;
  }
  private advanceRuntimeLocalClock(time: number, delta: number) {
    if (this.sharedAuthoritativeRuntime) {
      const previousElapsedMs = Math.max(0, this.snapshot.elapsedMs);
      const fallbackDeltaMs = Math.max(0, Number.isFinite(delta) ? delta : 0);
      this.snapshot.elapsedMs = resolveBattleAuthoritativeClockElapsedMs({
        anchor: this.authoritativeClockAnchor,
        fallbackElapsedMs: previousElapsedMs + fallbackDeltaMs,
        nowMs: Date.now()
      });
      const deltaMs = Math.max(0, this.snapshot.elapsedMs - previousElapsedMs);
      this.snapshot.gasZone = advanceBattleSharedAuthoritativeGasZone({
        gasZone: this.snapshot.gasZone,
        elapsedMs: this.snapshot.elapsedMs,
        deltaMs
      });
      this.runtimeBridges.temporalFrameBridge.applyGasDamage(this.snapshot, deltaMs);
      return;
    }

    this.snapshot.elapsedMs = this.elapsedOffsetMs + time;
    this.runtimeBridges.temporalFrameBridge.update(this.snapshot, delta);
  }
  private createArena() {
    buildArena({
      scene: this,
      wallBodies: this.wallBodies,
      obstacleBounds: this.obstacleBounds,
      occludables: this.occludables,
      staticMapViews: this.staticMapViews
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
  private updateStaticMapCullingIfNeeded(timeMs: number) {
    const playerPosition = this.localHeroDisplay.positionFor(this.getPlayerHero(), this.sharedAuthoritativeRuntime);
    const elapsedMs = Number.isFinite(timeMs) ? timeMs - this.lastStaticMapCullAtMs : STATIC_MAP_CULL_INTERVAL_MS;
    const movedDistance = this.lastStaticMapCullPosition
      ? Math.hypot(playerPosition.x - this.lastStaticMapCullPosition.x, playerPosition.y - this.lastStaticMapCullPosition.y)
      : Number.POSITIVE_INFINITY;

    if (elapsedMs < STATIC_MAP_CULL_INTERVAL_MS && movedDistance < STATIC_MAP_CULL_POSITION_DELTA) {
      return;
    }

    updateGameSceneStaticMapCulling({
      camera: this.cameras.main,
      staticMapViews: this.staticMapViews
    });
    this.lastStaticMapCullAtMs = Number.isFinite(timeMs) ? timeMs : 0;
    this.lastStaticMapCullPosition = { x: playerPosition.x, y: playerPosition.y };
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
      obstacleBounds: this.obstacleBounds,
      mapExpanded: this.controls.map.isDown
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
    this.runtimeBridges.weaponWheelSwitchBridge.handleWheel("Phaser", deltaY);
  }
  private readonly onGlobalWheelSwitch = (event: Event): void => {
    const customEvent = event as CustomEvent<WheelSwitchDetail>;
    const deltaY = customEvent.detail?.deltaY ?? 0;
    this.captureWeaponSwitchDirection(deltaY);
    this.runtimeBridges.weaponWheelSwitchBridge.handleWheel("Window", deltaY);
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
      playerAbilityBridge: this.runtimeBridges.playerAbilityBridge,
      sharedAuthoritativeRuntime: this.sharedAuthoritativeRuntime,
      remoteAuthoritativeHeroIds: this.authoritativeRemoteHeroIds,
      localHeroDisplay: this.localHeroDisplay,
      obstacleBounds: this.obstacleBounds
    });
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

    const receivedAtMs = options.nowMs ?? Date.now();
    this.authoritativeClockAnchor = createBattleAuthoritativeClockAnchor({ frame, receivedAtMs });
    this.runtimeBridges.battleFeedbackBridge.applyAuthoritativeFrame(frame);
    this.authoritativeRemoteHeroIds = this.authoritativeFrameBridge.applyFrame({
      snapshot: this.snapshot,
      frame,
      localPlayerMovementActive: this.isLatestPlayerCommandMovementActive(),
      obstacleBounds: this.obstacleBounds,
      options: { ...options, nowMs: receivedAtMs }
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
