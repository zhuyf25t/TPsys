import Phaser from "phaser";
import type { GameSnapshot, Hero, PlayerCommand, Vec2 } from "../domain/types";
import { BASE_MOVE_SPEED, GLOBAL_BACKGROUND_PADDING, HERO_SPRITE_SCALE, SPRINT_MULTIPLIER, STAMINA_DRAIN_PER_SECOND, STAMINA_RECOVER_PER_SECOND, WEAPON_SWITCH_MS, WORLD_SIZE } from "../game/constants";
import { resolveHeroVisual } from "../game/spawn";
import { getSelectedSkillBindings } from "../features/loadout/loadoutGateway";
import { readSkillBindingPresses } from "../features/battle/input/skillBindingInputAdapter";
import { buildArena, type ObstacleBounds, type OccludableView } from "../features/battle/renderer/arena/arenaBuilder";
import { createWorldViewState, syncWorldViews, type HeroView, type WorldViewState } from "../features/battle/renderer/entities/worldViewFactory";
import { CombatProjectileEffectSceneBridge } from "../features/battle/renderer/effects/combatProjectileEffectSceneBridge";
import { SceneVfxController } from "../features/battle/renderer/effects/sceneVfxController";
import { PlayerMotionTweenController } from "../features/battle/renderer/effects/playerMotionTweenController";
import { configureBattleCamera, updateBattleCameraTarget } from "../features/battle/renderer/camera/battleCameraDirector";
import { preloadBattleAssets } from "../features/battle/renderer/battleAssetPreloader";
import { cloneGameSnapshot } from "../features/battle/renderer/snapshot/exportSnapshotClone";
import { createBattleControlKeys, type ControlKeys } from "../features/battle/input/controlKeys";
import { createPlayerCommand, type InputCommandContext } from "../features/battle/adapters/inputCommandMapper";
import { CombatDebugReporter } from "../features/battle/debug/combatDebugReporter";
import type { WheelSwitchDetail } from "../features/battle/input/wheelSwitchAdapter";
import { advanceHeroWeaponSkillTimers } from "../features/battle/runtime-local/timers/heroWeaponSkillTimers";
import { BattleTemporalFrameBridge } from "../features/battle/runtime-local/timers/battleTemporalFrameBridge";
import { advanceMovement } from "../features/battle/runtime-local/movement/movementController";
import { isMotionTargetPointValid } from "../features/battle/runtime-local/movement/motionController";
import { appendFreezeField, getFreezeSpeedMultiplier } from "../features/battle/runtime-local/skills/freezeFieldController";
import { applyKnockbackDisplacement, applyRecoilDisplacement } from "../features/battle/runtime-local/geometry/heroDisplacementAdapter";
import { PlayerAbilitySceneBridge } from "../features/battle/renderer/effects/playerAbilitySceneBridge";
import { BattleHudSceneBridge } from "../features/battle/renderer/hud/battleHudSceneBridge";
import { beginWeaponSwitchTransaction } from "../features/battle/runtime-local/weapons/weaponController";
import { updateOccludableAlpha } from "../features/battle/renderer/arena/occlusionAlphaController";
import { createInitialBattleSnapshot } from "../features/battle/runtime-local/session/initialBattleSnapshot";
import { RespawnSceneBridge } from "../features/battle/runtime-local/session/respawnSceneBridge";
import { WeaponActionSceneBridge } from "../features/battle/renderer/effects/weaponActionSceneBridge";
import { ProjectileFrameSceneBridge } from "../features/battle/renderer/effects/projectileFrameSceneBridge";
import { BotFrameBridge } from "../features/battle/runtime-local/bots/botFrameBridge";
import { PickupFrameBridge } from "../features/battle/runtime-local/pickups/pickupFrameBridge";
export class GameScene extends Phaser.Scene {
  private snapshot!: GameSnapshot;
  private controls!: ControlKeys;
  private wallBodies!: Phaser.Physics.Arcade.StaticGroup;
  private playerActor!: Phaser.Physics.Arcade.Image;
  private cameraTarget!: Phaser.GameObjects.Zone;
  private worldViews!: WorldViewState;
  private heroViews = new Map<string, HeroView>();
  private obstacleBounds: ObstacleBounds[] = [];
  private occludables: OccludableView[] = [];
  private projectileSequence = 0;
  private slowFieldSequence = 0;
  private pointerJustPressed = false;
  private secondaryJustPressed = false;
  private pendingWeaponSwitchDirection: -1 | 0 | 1 = 0;
  private cameraOffset: Vec2 = { x: 0, y: 0 };
  private lastMoveDirection: Vec2 = { x: 1, y: 0 };
  private pendingWeaponIndex: number | null = null;
  private weaponSwitchRemainingMs = 0;
  private weaponSwitchTotalMs = 0;
  private pickupNoticeCooldowns = new Map<string, number>();
  private motionController!: PlayerMotionTweenController;
  private playerAbilityBridge!: PlayerAbilitySceneBridge;
  private combatEffectBridge!: CombatProjectileEffectSceneBridge;
  private weaponActionBridge!: WeaponActionSceneBridge;
  private projectileFrameBridge!: ProjectileFrameSceneBridge;
  private botFrameBridge!: BotFrameBridge;
  private pickupFrameBridge!: PickupFrameBridge;
  private respawnBridge!: RespawnSceneBridge;
  private temporalFrameBridge!: BattleTemporalFrameBridge;
  private vfx!: SceneVfxController;
  private hudBridge!: BattleHudSceneBridge;
  private readonly combatDebugReporter = new CombatDebugReporter({ enabled: true });
  private lastWheelHandledAt = 0;
  private lastWheelHandledDeltaY = 0;
  private elapsedOffsetMs = 0;
  constructor(private readonly initialSnapshot: GameSnapshot | null = null) {
    super("game-scene");
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

    this.controls = createBattleControlKeys(this.input);
    this.physics.world.setBounds(0, 0, WORLD_SIZE.x, WORLD_SIZE.y);
    this.wallBodies = this.physics.add.staticGroup();

    this.createArena();
    this.createPlayerActor();
    this.createCameraTarget();
    this.createHeroViews();
    this.hudBridge = new BattleHudSceneBridge();
    this.hudBridge.layout(this.scale.width, this.scale.height);
    this.events.once(Phaser.Scenes.Events.SHUTDOWN, () => {
      this.hudBridge.destroy();
    });
    this.configureCamera();
    this.vfx = new SceneVfxController(this);
    this.motionController = new PlayerMotionTweenController({
      scene: this,
      playerActor: this.playerActor,
      heroViews: this.heroViews,
      getPlayerHero: () => this.getPlayerHero(),
      getBaseHeroScale: (heroId) => (heroId === this.snapshot.playerHeroId ? 1.46 : HERO_SPRITE_SCALE),
      createPulse: (position, radius, color) => this.vfx.createPulse(position, radius, color)
    });
    this.playerAbilityBridge = new PlayerAbilitySceneBridge({
      getPlayerHero: () => this.getPlayerHero(),
      getWorldSize: () => this.snapshot.worldSize,
      getObstacleBounds: () => this.obstacleBounds,
      getHeroViews: () => this.heroViews,
      getBaseHeroScale: (heroId) => (heroId === this.snapshot.playerHeroId ? 1.46 : HERO_SPRITE_SCALE),
      isPlayerMotionActive: () => this.isPlayerMotionActive(),
      isBlinkTargetValid: (player, target) => this.isBlinkTargetValid(player, target),
      startPlayerMotion: (destination, durationMs, motionType) => this.startPlayerMotion(destination, durationMs, motionType),
      createAfterimage: (position, rotation, scale, textureKey, tint, alpha) => this.createAfterimage(position, rotation, scale, textureKey, tint, alpha),
      createPulse: (position, radius, color) => this.vfx.createPulse(position, radius, color),
      createFloatingText: (position, text, color) => this.vfx.createFloatingText(position, text, color),
      showFloatingText: (position, text, tone) => this.vfx.showFloatingText(position, text, tone),
      addFreezeField: (ownerHeroId, position, radius, durationMs) => this.addFreezeField(ownerHeroId, position, radius, durationMs)
    });
    this.combatEffectBridge = new CombatProjectileEffectSceneBridge({
      getSnapshot: () => this.snapshot,
      combatDebugReporter: this.combatDebugReporter,
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
      applyKnockback: (hero, direction, strength) =>
        applyKnockbackDisplacement({
          hero,
          direction,
          strength,
          worldSize: this.snapshot.worldSize,
          obstacleBounds: this.obstacleBounds,
          setHeroPosition: (position) => this.setHeroPosition(hero, position)
        }),
      pushEvent: (type, message) => this.temporalFrameBridge.pushEvent(this.snapshot, type, message)
    });
    this.weaponActionBridge = new WeaponActionSceneBridge({
      getPlayerHero: () => this.getPlayerHero(),
      getWeaponSwitchRemainingMs: () => this.weaponSwitchRemainingMs,
      isPlayerMotionActive: () => this.isPlayerMotionActive(),
      getProjectileSequence: () => this.projectileSequence,
      setProjectileSequence: (next) => {
        this.projectileSequence = next;
      },
      addProjectile: (projectile) => {
        this.snapshot.projectiles.push(projectile);
      },
      showFloatingText: (position, text, tone) => this.vfx.showFloatingText(position, text, tone),
      createMuzzleBurst: (position, color, radius, sparks) => this.vfx.createMuzzleBurst(position, color, radius, sparks),
      createPulse: (position, radius, color) => this.vfx.createPulse(position, radius, color),
      createImpactSpark: (position, color) => this.vfx.createImpactSpark(position, color),
      applyRecoil: (direction, strength) => {
        const player = this.getPlayerHero();
        applyRecoilDisplacement({
          hero: player,
          direction,
          strength,
          worldSize: this.snapshot.worldSize,
          obstacleBounds: this.obstacleBounds,
          setHeroPosition: (position) => this.setHeroPosition(player, position)
        });
      }
    });
    this.projectileFrameBridge = new ProjectileFrameSceneBridge({
      getSnapshot: () => this.snapshot,
      getObstacleBounds: () => this.obstacleBounds,
      presentEffect: (effect) => this.combatEffectBridge.present(effect)
    });
    this.botFrameBridge = new BotFrameBridge({
      getSnapshot: () => this.snapshot,
      getObstacleBounds: () => this.obstacleBounds,
      getProjectileSequence: () => this.projectileSequence,
      setProjectileSequence: (nextSequence) => {
        this.projectileSequence = nextSequence;
      }
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
      resetWeaponSwitchState: () => {
        this.pendingWeaponIndex = null;
        this.weaponSwitchRemainingMs = 0;
        this.weaponSwitchTotalMs = 0;
      },
      stopPlayerMotion: () => this.stopPlayerMotion(),
      pushEvent: (type, message) => this.temporalFrameBridge.pushEvent(this.snapshot, type, message),
      createPulse: (position, radius, color) => this.vfx.createPulse(position, radius, color)
    });
    this.temporalFrameBridge = new BattleTemporalFrameBridge();
    this.events.once(Phaser.Scenes.Events.SHUTDOWN, () => {
      this.vfx.destroy();
    });

    this.physics.add.collider(this.playerActor, this.wallBodies);
    this.scale.on(Phaser.Scale.Events.RESIZE, this.handleResize, this);
    this.handleResize();
  }
  update(time: number, delta: number) {
    this.snapshot.elapsedMs = this.elapsedOffsetMs + time;
    this.temporalFrameBridge.update(this.snapshot, delta);
    this.pickupFrameBridge.updatePickupLifecycle(delta);
    this.respawnBridge.updateRespawnTimers(delta);
    this.updateHeroStateTimers(delta);
    this.syncPlayerHeroFromPhysics();

    const command = this.readPlayerCommand();
    this.updatePlayerMovement(command, delta);
    this.pickupFrameBridge.handleAutomaticPickups();
    this.playerAbilityBridge.handleSkillInputs(command);
    this.playerAbilityBridge.handleJumpAction(command, this.lastMoveDirection);
    this.handleWeaponSwitchAction(command);
    this.weaponActionBridge.handleWeaponFireAction(command);
    this.botFrameBridge.updateBotActions(delta);
    this.projectileFrameBridge.updateProjectiles(delta);
    this.syncPlayerHeroFromPhysics();
    this.updateCameraTarget();

    this.syncWorldViews(command);
    updateOccludableAlpha({
      player: this.getPlayerHero(),
      occludables: this.occludables
    });
    this.hudBridge.update({
      snapshot: this.snapshot,
      fps: Math.round(this.game.loop.actualFps),
      weaponSwitchRemainingMs: this.weaponSwitchRemainingMs,
      camera: this.cameras.main
    });
    this.vfx.updateVisualEffects(delta);

    this.pointerJustPressed = false;
    this.secondaryJustPressed = false;
    this.pendingWeaponSwitchDirection = 0;
  }
  private createArena(): void {
    buildArena({
      scene: this,
      wallBodies: this.wallBodies,
      obstacleBounds: this.obstacleBounds,
      occludables: this.occludables
    });
  }
  private createPlayerActor(): void {
    const player = this.getPlayerHero();
    this.playerActor = this.physics.add.image(player.position.x, player.position.y, resolveHeroVisual(player.heroId).textureKey).setVisible(false);
    this.playerActor.setMaxVelocity(BASE_MOVE_SPEED * SPRINT_MULTIPLIER, BASE_MOVE_SPEED * SPRINT_MULTIPLIER);
    const body = this.playerActor.body as Phaser.Physics.Arcade.Body;
    body.setSize(player.radius * 2, player.radius * 2, true);
  }
  private createCameraTarget(): void {
    const player = this.getPlayerHero();
    this.cameraTarget = this.add.zone(player.position.x, player.position.y, 1, 1);
  }

  private createHeroViews(): void {
    this.worldViews = createWorldViewState({
      scene: this,
      snapshot: this.snapshot,
      getBaseHeroScale: (heroId) => (heroId === this.snapshot.playerHeroId ? 1.46 : HERO_SPRITE_SCALE)
    });
    this.heroViews = this.worldViews.heroViews;
  }
  private configureCamera(): void {
    configureBattleCamera({
      camera: this.cameras.main,
      worldSize: WORLD_SIZE,
      globalPadding: GLOBAL_BACKGROUND_PADDING
    });
    this.cameras.main.startFollow(this.cameraTarget, true, 0.12, 0.12);
  }
  private updateCameraTarget(): void {
    const player = this.getPlayerHero();
    updateBattleCameraTarget({
      pointer: this.input.activePointer,
      scaleSize: this.scale.gameSize,
      playerPosition: player.position,
      cameraTarget: this.cameraTarget,
      cameraOffset: this.cameraOffset
    });
  }
  private handleResize(_gameSize?: Phaser.Structs.Size): void {
    this.cameras.main.setSize(this.scale.width, this.scale.height);
    this.hudBridge?.layout(this.scale.width, this.scale.height);
  }
  private handlePointerDown(pointer: Phaser.Input.Pointer): void {
    if (pointer.button === 0) { this.pointerJustPressed = true; return; }

    if (pointer.button === 2) { this.secondaryJustPressed = true; }
  }
  private handleMouseWheel(_pointer: Phaser.Input.Pointer, _gameObjects: Phaser.GameObjects.GameObject[], _deltaX: number, deltaY: number, _deltaZ: number, event: WheelEvent): void {
    event.preventDefault();

    if (event.ctrlKey) { return; }

    this.combatDebugReporter.reportWheelSample("Phaser", deltaY);

    if (deltaY < 0) {
      this.requestSwitchWeapon(-1, "Phaser", deltaY);
    } else if (deltaY > 0) {
      this.requestSwitchWeapon(1, "Phaser", deltaY);
    }
  }
  private readonly onGlobalWheelSwitch = (event: Event): void => {
    const customEvent = event as CustomEvent<WheelSwitchDetail>;
    const deltaY = customEvent.detail?.deltaY ?? 0;
    this.combatDebugReporter.reportWheelSample("Window", deltaY);

    if (deltaY < 0) {
      this.requestSwitchWeapon(-1, "Window", deltaY);
    } else if (deltaY > 0) {
      this.requestSwitchWeapon(1, "Window", deltaY);
    }
  };
  private requestSwitchWeapon(direction: -1 | 1, source: "Phaser" | "Window", deltaY: number): void {
    const now = performance.now();
    if (Math.abs(deltaY - this.lastWheelHandledDeltaY) < 0.01 && now - this.lastWheelHandledAt < 50) {
      return;
    }

    this.lastWheelHandledAt = now;
    this.lastWheelHandledDeltaY = deltaY;
    const player = this.getPlayerHero();
    if (!player.alive || player.weapons.length <= 1 || this.weaponSwitchRemainingMs > 0) {
      return;
    }

    const switchResult = beginWeaponSwitchTransaction({
      player,
      switchDirection: direction,
      weaponSwitchRemainingMs: this.weaponSwitchRemainingMs,
      weaponSwitchMs: WEAPON_SWITCH_MS
    });

    if (!switchResult.switched) { return; }

    this.pendingWeaponIndex = switchResult.nextIndex;
    this.weaponSwitchRemainingMs = switchResult.weaponSwitchRemainingMs;
    this.weaponSwitchTotalMs = switchResult.weaponSwitchTotalMs;
    this.combatDebugReporter.reportWeaponSwitch({
      source,
      deltaY,
      nextIndex: switchResult.nextIndex,
      weapon: player.weapons[switchResult.nextIndex]?.weaponKind ?? "unknown"
    });
    this.vfx.showFloatingText(player.position, "正在切枪", "neutral");
  }
  private readPlayerCommand(): PlayerCommand {
    return createPlayerCommand(this.collectPlayerInputContext());
  }
  private collectPlayerInputContext(): InputCommandContext {
    const player = this.getPlayerHero();
    const skillPresses = readSkillBindingPresses(getSelectedSkillBindings(), this.readSkillSlotJustPressed());

    return {
      playerPosition: { x: player.position.x, y: player.position.y },
      pointerWorld: { x: this.input.activePointer.worldX, y: this.input.activePointer.worldY },
      moveUp: this.controls.up.isDown,
      moveDown: this.controls.down.isDown,
      moveLeft: this.controls.left.isDown,
      moveRight: this.controls.right.isDown,
      primaryHeld: this.input.activePointer.leftButtonDown(),
      primaryJustPressed: this.pointerJustPressed,
      secondaryJustPressed: this.secondaryJustPressed,
      sprint: this.controls.sprint.isDown,
      switchWeaponDirection: this.pendingWeaponSwitchDirection,
      toggleBlink: skillPresses.Blink,
      toggleFreeze: skillPresses.Freeze,
      castDash: skillPresses.Dash,
      reloadPressed: Phaser.Input.Keyboard.JustDown(this.controls.reload)
    };
  }
  private readSkillSlotJustPressed(): Record<"Q" | "E" | "R", boolean> {
    return { Q: Phaser.Input.Keyboard.JustDown(this.controls.skillQ), E: Phaser.Input.Keyboard.JustDown(this.controls.skillE), R: Phaser.Input.Keyboard.JustDown(this.controls.skillR) };
  }
  private updateHeroStateTimers(deltaMs: number): void {
    const nextState = advanceHeroWeaponSkillTimers({
      deltaMs,
      playerHeroId: this.snapshot.playerHeroId,
      heroes: this.snapshot.heroes,
      pickupNoticeCooldowns: this.pickupNoticeCooldowns,
      weaponSwitchRemainingMs: this.weaponSwitchRemainingMs,
      weaponSwitchTotalMs: this.weaponSwitchTotalMs,
      pendingWeaponIndex: this.pendingWeaponIndex
    });

    this.weaponSwitchRemainingMs = nextState.weaponSwitchRemainingMs;
    this.weaponSwitchTotalMs = nextState.weaponSwitchTotalMs;
    this.pendingWeaponIndex = nextState.pendingWeaponIndex;
  }
  private syncPlayerHeroFromPhysics(): void {
    const player = this.getPlayerHero();
    const body = this.playerActor.body as Phaser.Physics.Arcade.Body;
    player.position.x = this.playerActor.x;
    player.position.y = this.playerActor.y;
    player.velocity = { x: body.velocity.x, y: body.velocity.y };
  }
  private updatePlayerMovement(command: PlayerCommand, deltaMs: number): void {
    const player = this.getPlayerHero();

    if (!player.alive) {
      this.playerActor.setVelocity(0, 0);
      player.velocity = { x: 0, y: 0 };
      return;
    }

    player.facing = Math.atan2(command.aim.y, command.aim.x);

    const result = advanceMovement({
      alive: player.alive,
      motionActive: this.isPlayerMotionActive(),
      movement: command.movement,
      sprint: command.sprint,
      stamina: player.stamina,
      maxStamina: player.maxStamina,
      lastMoveDirection: this.lastMoveDirection,
      deltaMs,
      baseMoveSpeed: BASE_MOVE_SPEED,
      sprintMultiplier: SPRINT_MULTIPLIER,
      staminaDrainPerSecond: STAMINA_DRAIN_PER_SECOND,
      staminaRecoverPerSecond: STAMINA_RECOVER_PER_SECOND,
      speedMultiplier: getFreezeSpeedMultiplier(player.position, this.snapshot.slowFields)
    });

    this.lastMoveDirection = result.lastMoveDirection;
    this.playerActor.setVelocity(result.velocity.x, result.velocity.y);
    player.velocity = { x: result.velocity.x, y: result.velocity.y };
    player.stamina = result.stamina;
  }
  private handleWeaponSwitchAction(command: PlayerCommand): void {
    const player = this.getPlayerHero();
    const switchResult = beginWeaponSwitchTransaction({
      player,
      switchDirection: command.switchWeaponDirection,
      weaponSwitchRemainingMs: this.weaponSwitchRemainingMs,
      weaponSwitchMs: WEAPON_SWITCH_MS
    });

    if (!switchResult.switched) { return; }

    this.pendingWeaponIndex = switchResult.nextIndex;
    this.weaponSwitchRemainingMs = switchResult.weaponSwitchRemainingMs;
    this.weaponSwitchTotalMs = switchResult.weaponSwitchTotalMs;
    if (switchResult.showNotice) {
      this.vfx.showFloatingText(player.position, "正在切枪", "neutral");
    }
  }
  private addFreezeField(ownerHeroId: string, position: Vec2, radius: number, durationMs: number): void {
    const result = appendFreezeField({
      fields: this.snapshot.slowFields,
      sequence: this.slowFieldSequence,
      ownerHeroId,
      position,
      radius,
      durationMs
    });

    this.snapshot.slowFields = result.nextFields;
    this.slowFieldSequence = result.nextSequence;
    this.vfx.showFloatingText(position, "冰雾", "success");
  }
  private setHeroPosition(hero: Hero, position: Vec2): void {
    hero.position = { x: position.x, y: position.y };
    if (hero.heroId === this.snapshot.playerHeroId) {
      this.playerActor.setPosition(position.x, position.y);
    }
  }

  private syncWorldViews(command: PlayerCommand): void {
    syncWorldViews({
      scene: this,
      snapshot: this.snapshot,
      worldViews: this.worldViews,
      weaponSwitchRemainingMs: this.weaponSwitchRemainingMs,
      weaponSwitchTotalMs: this.weaponSwitchTotalMs,
      pointerWorld: command.pointerWorld,
      isBlinkTargetValid: this.isBlinkTargetValid.bind(this)
    });
  }
  private isBlinkTargetValid(player: Hero, target: Vec2): boolean {
    return isMotionTargetPointValid({
      target,
      radius: player.radius,
      worldSize: this.snapshot.worldSize,
      obstacleBounds: this.obstacleBounds
    });
  }
  private isPlayerMotionActive(): boolean {
    return this.motionController.isActive();
  }
  private stopPlayerMotion(): void {
    this.motionController.stop();
  }
  private startPlayerMotion(destination: Vec2, durationMs: number, motionType: "jump" | "dash" | "blink"): void {
    this.motionController.start(destination, durationMs, motionType);
  }
  private createAfterimage(position: Vec2, rotation: number, scale: number, textureKey: string, tint: number, alpha: number): void {
    this.motionController.createAfterimage(position, rotation, scale, textureKey, tint, alpha);
  }
  private flashHero(heroId: string, flashColor: number): void {
    const hero = this.snapshot.heroes.find((entry) => entry.heroId === heroId);
    const view = this.heroViews.get(heroId);
    if (!hero || !view) {
      return;
    }

    view.sprite.setTintFill(flashColor);
    this.time.delayedCall(80, () => {
      if (view.sprite.active) {
        view.sprite.setTint(resolveHeroVisual(hero.heroId).tint);
      }
    });
  }
  private getPlayerHero(): Hero {
    const player = this.snapshot.heroes.find((hero) => hero.heroId === this.snapshot.playerHeroId);
    if (!player) { throw new Error("Player hero not found"); }
    return player;
  }

  exportSnapshot() {
    if (this.snapshot === undefined) { return null; }
    return cloneGameSnapshot(this.snapshot);
  }
}
