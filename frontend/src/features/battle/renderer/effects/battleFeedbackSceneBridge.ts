import type { GameSnapshot, Hero, ItemPickup, Vec2, WeaponPickup } from "../../../../domain/types";
import type { BattleRuntimeAuthoritativeFrame } from "../authoritativeBattleStateBridge";
import {
  recordRemoteProjectileBirthDiagnostics,
  recordRemoteProjectileTerminalDiagnostics,
  shouldRecordRemoteProjectileTerminalDiagnostics
} from "../remoteViewDiagnostics";
import {
  AUTHORITATIVE_PROJECTILE_TERMINAL_VFX_PER_UPDATE_LIMIT,
  AUTHORITATIVE_PROJECTILE_TERMINAL_VFX_QUEUE_LIMIT,
  PLAYED_AUTHORITATIVE_PROJECTILE_TERMINAL_LIMIT,
  PROJECTILE_SPARK_COLORS,
  REMEMBERED_LIVE_PROJECTILE_ID_LIMIT,
  ROCKET_SPLASH_VISUAL_RADIUS,
  createAuthoritativeProjectileTerminalCorrectionTracerOptions,
  createAuthoritativeProjectileTerminalFeedbackState,
  createAuthoritativeProjectileTerminalKey,
  createAuthoritativeProjectileTerminalTracerOptions,
  createProjectileFeedbackState,
  createProjectileTerminalCorrectionTracerOptions,
  createProjectileTerminalTracerOptions,
  createRemoteGatlingProjectileBirthTracerOptions,
  createTerminalDiagnosticProjectileState,
  isLocalAuthoritativeProjectileTerminal,
  isLocalProjectileTerminal,
  resolveAuthoritativeFrameElapsedWatermark,
  resolveAuthoritativeProjectileTerminalQueueDropKey,
  resolveAuthoritativeTerminalVfxStrategy,
  resolveNearestTerminalHero,
  resolveProjectileDirection,
  resolveRemoteProjectileBirthFeedbackPosition,
  resolveRocketShockwaveStartRadius,
  selectAuthoritativeProjectileTerminalVfxKeys,
  shouldQueueAuthoritativeProjectileTerminal,
  softenColor,
  type AuthoritativeProjectileTerminalFeedbackState,
  type AuthoritativeProjectileTerminalVfxBudgetReason,
  type ProjectileFeedbackState
} from "./projectileTerminalFeedbackPolicy";

interface HeroFeedbackState {
  hp: number;
  alive: boolean;
  score: number;
  currentWeaponAmmoTotal: number | null;
  position: Vec2;
}

interface PickupFeedbackState {
  available: boolean;
  position: Vec2;
}

const AMMO_PICKUP_PULSE_RADIUS = 30;
const AMMO_PICKUP_PULSE_COLOR = 0xffd86d;

export interface BattleFeedbackSceneBridgeOptions {
  getSnapshot(): GameSnapshot;
  getHeroDisplayPosition(heroId: string): Vec2 | null;
  getProjectileDisplayPosition(projectileId: string): Vec2 | null;
  flashHero(heroId: string, color: number): void;
  showFloatingText(position: Vec2, text: string, tone: "neutral" | "success" | "warning" | "error"): void;
  createPulse(position: Vec2, radius: number, color: number): void;
  createImpactSpark(position: Vec2, color: number): void;
  createProjectileDissipate(position: Vec2, color: number): void;
  createHitConfirm(position: Vec2, color: number): void;
  createShockwave(position: Vec2, startRadius: number, endRadius: number, color: number, duration: number): void;
  createProjectileTracer(options: {
    start: Vec2;
    direction: Vec2;
    length: number;
    color: number;
    thickness: number;
    durationMs: number;
    alpha?: number;
    ghostScale?: number;
    glintAlphaScale?: number;
    underglowAlphaScale?: number;
    coreAlphaScale?: number;
    ghostAlphaScale?: number;
  }): void;
  shakeCamera(duration: number, intensity: number): void;
}

export class BattleFeedbackSceneBridge {
  private initialized = false;
  private heroStates = new Map<string, HeroFeedbackState>();
  private weaponPickupStates = new Map<string, PickupFeedbackState>();
  private itemPickupStates = new Map<string, PickupFeedbackState>();
  private projectileStates = new Map<string, ProjectileFeedbackState>();
  private authoritativeProjectileTerminals = new Map<string, AuthoritativeProjectileTerminalFeedbackState>();
  private playedAuthoritativeProjectileTerminals = new Set<string>();
  private playedAuthoritativeProjectileTerminalQueue: string[] = [];
  private seenLiveProjectileIds = new Set<string>();
  private seenLiveProjectileIdQueue: string[] = [];
  private scratchLiveProjectileIds = new Set<string>();
  private previousSharedAuthoritativeRuntime = false;
  private authoritativeProjectileTerminalFreshnessBaselineElapsedMs: number | null = null;

  public constructor(private readonly options: BattleFeedbackSceneBridgeOptions) {}

  public applyAuthoritativeFrame(frame: BattleRuntimeAuthoritativeFrame): void {
    const freshnessBaselineElapsedMs = this.resolveAuthoritativeProjectileTerminalFreshnessBaseline(frame);
    if (!this.initialized || frame.projectileTerminals.length === 0) {
      return;
    }

    frame.projectileTerminals.forEach((terminal) => {
      const terminalKey = createAuthoritativeProjectileTerminalKey(terminal);
      if (this.playedAuthoritativeProjectileTerminals.has(terminalKey)) {
        return;
      }

      const seenLive = this.seenLiveProjectileIds.has(terminal.projectileId);
      if (
        !shouldQueueAuthoritativeProjectileTerminal({
          seenLive,
          terminalElapsedMs: terminal.elapsedMs,
          freshnessBaselineElapsedMs
        })
      ) {
        this.rememberPlayedAuthoritativeProjectileTerminal(terminalKey);
        return;
      }

      const terminalState = createAuthoritativeProjectileTerminalFeedbackState(terminal);
      if (terminalState) {
        this.enqueueAuthoritativeProjectileTerminal(terminalKey, terminalState);
      }
    });
  }

  public update(sharedAuthoritativeRuntime: boolean): void {
    const snapshot = this.options.getSnapshot();
    if (!this.initialized) {
      this.capture(snapshot);
      this.initialized = true;
      this.previousSharedAuthoritativeRuntime = sharedAuthoritativeRuntime;
      return;
    }

    this.presentHeroFeedback(snapshot, sharedAuthoritativeRuntime);
    if (sharedAuthoritativeRuntime) {
      this.presentAuthoritativePickupFeedback(snapshot);
      this.presentAuthoritativeRemoteProjectileBirthFeedback(snapshot);
      if (this.previousSharedAuthoritativeRuntime) {
        this.presentAuthoritativeProjectileTerminalFeedback(snapshot);
      }
    }
    this.capture(snapshot);
    this.previousSharedAuthoritativeRuntime = sharedAuthoritativeRuntime;
  }

  private presentHeroFeedback(snapshot: GameSnapshot, sharedAuthoritativeRuntime: boolean): void {
    snapshot.heroes.forEach((hero) => {
      const previous = this.heroStates.get(hero.heroId);
      if (!previous) {
        return;
      }

      if (sharedAuthoritativeRuntime) {
        this.presentAuthoritativeHealthDelta(hero, previous, snapshot.playerHeroId);
        this.presentAuthoritativeAmmoDelta(hero, previous);
      }

      if (previous.alive && !hero.alive) {
        this.options.showFloatingText(previous.position, "出局", "error");
        this.options.createPulse(previous.position, 42, 0xff6b6b);
        if (hero.heroId === snapshot.playerHeroId) {
          this.options.shakeCamera(140, 0.0024);
        }
      }

      if (!sharedAuthoritativeRuntime && !previous.alive && hero.alive) {
        const feedbackPosition = this.options.getHeroDisplayPosition(hero.heroId) ?? hero.position;
        // Legacy snapshot compatibility: one-life mode must not present this as player return.
        this.options.createPulse(feedbackPosition, 42, 0xffb36f);
      }

      if (hero.heroId === snapshot.playerHeroId && hero.score > previous.score) {
        const feedbackPosition = this.options.getHeroDisplayPosition(hero.heroId) ?? hero.position;
        this.options.showFloatingText(feedbackPosition, `击败 +${hero.score - previous.score}`, "success");
      }
    });
  }

  private presentAuthoritativeHealthDelta(hero: Hero, previous: HeroFeedbackState, playerHeroId: string): void {
    if (!hero.alive && !previous.alive) {
      return;
    }

    const hpDelta = hero.hp - previous.hp;
    const displayPosition = this.options.getHeroDisplayPosition(hero.heroId);
    const feedbackPosition = displayPosition ?? (previous.alive && !hero.alive ? previous.position : hero.position);
    if (hpDelta < 0) {
      const damage = Math.round(Math.abs(hpDelta));
      const isPlayerDamage = hero.heroId === playerHeroId;
      this.options.flashHero(hero.heroId, 0xffffff);
      this.options.createImpactSpark(feedbackPosition, 0xffe2ba);
      this.options.createHitConfirm(feedbackPosition, isPlayerDamage ? 0xff6b6b : 0xfff0c6);
      this.options.showFloatingText(feedbackPosition, `-${damage}`, "error");
      if (isPlayerDamage && previous.alive && hero.alive) {
        const shakeScale = Math.min(1, damage / Math.max(1, hero.maxHp * 0.35));
        this.options.createPulse(feedbackPosition, 28, 0xff5c5c);
        this.options.shakeCamera(70 + Math.round(shakeScale * 40), 0.0012 + shakeScale * 0.0008);
      }
      return;
    }

    if (hpDelta > 0 && hero.alive) {
      this.options.showFloatingText(feedbackPosition, `+${Math.round(hpDelta)}`, "success");
      this.options.createPulse(feedbackPosition, 36, 0x7dff9d);
    }
  }

  private presentAuthoritativeAmmoDelta(hero: Hero, previous: HeroFeedbackState): void {
    if (!previous.alive || !hero.alive) {
      return;
    }

    const currentWeaponAmmoTotal = resolveCurrentWeaponAmmoTotal(hero);
    if (currentWeaponAmmoTotal === null || previous.currentWeaponAmmoTotal === null) {
      return;
    }

    const ammoDelta = currentWeaponAmmoTotal - previous.currentWeaponAmmoTotal;
    if (ammoDelta <= 0) {
      return;
    }

    const feedbackPosition = this.options.getHeroDisplayPosition(hero.heroId) ?? hero.position;
    this.options.showFloatingText(feedbackPosition, `弹药 +${ammoDelta}`, "success");
    this.options.createPulse(feedbackPosition, AMMO_PICKUP_PULSE_RADIUS, AMMO_PICKUP_PULSE_COLOR);
  }

  private presentAuthoritativePickupFeedback(snapshot: GameSnapshot): void {
    snapshot.weaponPickups.forEach((pickup) => {
      const previous = this.weaponPickupStates.get(pickup.weaponId);
      if (previous?.available && !pickup.available) {
        this.options.showFloatingText(previous.position, "拾取武器", "success");
        this.options.createPulse(previous.position, 34, 0x9dffb4);
      }
    });

    snapshot.itemPickups.forEach((pickup) => {
      const previous = this.itemPickupStates.get(pickup.pickupId);
      if (previous?.available && !pickup.available) {
        this.options.showFloatingText(previous.position, "拾取补给", "success");
        this.options.createPulse(previous.position, 34, 0x7dff9d);
      }
    });
  }

  private presentAuthoritativeRemoteProjectileBirthFeedback(snapshot: GameSnapshot): void {
    snapshot.projectiles.forEach((projectile) => {
      if (this.projectileStates.has(projectile.projectileId) || projectile.ownerHeroId === snapshot.playerHeroId) {
        return;
      }

      const owner = snapshot.heroes.find((hero) => hero.heroId === projectile.ownerHeroId);
      const ownerDisplayPosition = owner ? this.options.getHeroDisplayPosition(owner.heroId) : null;
      const position = resolveRemoteProjectileBirthFeedbackPosition(projectile, owner, ownerDisplayPosition);
      const color = PROJECTILE_SPARK_COLORS[projectile.kind];
      recordRemoteProjectileBirthDiagnostics({
        projectile,
        ownerDisplayName: owner?.displayName,
        position
      });
      if (projectile.kind === "gatling-bullet") {
        this.options.createProjectileTracer(
          createRemoteGatlingProjectileBirthTracerOptions(projectile, position, color)
        );
        return;
      }

      this.options.createImpactSpark(position, color);

      if (projectile.kind === "rocket") {
        this.options.createPulse(position, 16, color);
      }
    });
  }

  private presentAuthoritativeProjectileTerminalFeedback(snapshot: GameSnapshot): void {
    const liveProjectileIds = this.scratchLiveProjectileIds;
    liveProjectileIds.clear();
    snapshot.projectiles.forEach((projectile) => {
      liveProjectileIds.add(projectile.projectileId);
    });

    this.presentQueuedAuthoritativeProjectileTerminals(snapshot, liveProjectileIds);

    this.projectileStates.forEach((previous, projectileId) => {
      if (liveProjectileIds.has(projectileId) || this.hasPlayedAuthoritativeProjectileTerminalForProjectile(projectileId)) {
        return;
      }

      const color = PROJECTILE_SPARK_COLORS[previous.kind];
      if (!isLocalProjectileTerminal(previous, snapshot.playerHeroId)) {
        this.presentProjectileTerminalTracer(previous, color);
        this.presentProjectileTerminalCorrectionTracer(previous, color);
      }
      if (previous.ttlMs <= 0) {
        this.options.createProjectileDissipate(previous.authoritativePosition, softenColor(color));
      }
      this.recordProjectileTerminalDiagnostics(previous, projectileId, snapshot);
      if (previous.kind === "rocket") {
        this.options.createImpactSpark(previous.authoritativePosition, color);
        this.options.createShockwave(
          previous.authoritativePosition,
          resolveRocketShockwaveStartRadius(),
          ROCKET_SPLASH_VISUAL_RADIUS,
          color,
          240
        );
      }
    });
  }

  private presentQueuedAuthoritativeProjectileTerminals(snapshot: GameSnapshot, liveProjectileIds: Set<string>): void {
    const readyTerminals: Array<{
      terminalKey: string;
      terminal: AuthoritativeProjectileTerminalFeedbackState;
      previous: ProjectileFeedbackState | undefined;
    }> = [];

    this.authoritativeProjectileTerminals.forEach((terminal, terminalKey) => {
      if (this.playedAuthoritativeProjectileTerminals.has(terminalKey)) {
        this.authoritativeProjectileTerminals.delete(terminalKey);
        return;
      }

      if (liveProjectileIds.has(terminal.projectileId)) {
        return;
      }

      readyTerminals.push({
        terminalKey,
        terminal,
        previous: this.projectileStates.get(terminal.projectileId)
      });
    });

    const vfxTerminalKeys = selectAuthoritativeProjectileTerminalVfxKeys(
      readyTerminals,
      AUTHORITATIVE_PROJECTILE_TERMINAL_VFX_PER_UPDATE_LIMIT
    );

    readyTerminals.forEach(({ terminalKey, terminal, previous }) => {
      const shouldPlayVfx = vfxTerminalKeys.has(terminalKey);
      this.recordAuthoritativeProjectileTerminalDiagnostics(
        terminal,
        previous,
        snapshot,
        shouldPlayVfx ? null : "per-update-limit"
      );

      if (shouldPlayVfx) {
        const color = PROJECTILE_SPARK_COLORS[terminal.kind];
        if (!isLocalAuthoritativeProjectileTerminal(terminal, snapshot.playerHeroId)) {
          this.presentAuthoritativeProjectileTerminalTracer(terminal, previous, color);
          this.presentAuthoritativeProjectileTerminalCorrectionTracer(terminal, previous, color);
        }
        this.presentAuthoritativeProjectileTerminalReasonVfx(terminal, color);
      }

      this.rememberPlayedAuthoritativeProjectileTerminal(terminalKey);
      this.authoritativeProjectileTerminals.delete(terminalKey);
    });
  }

  private presentAuthoritativeProjectileTerminalReasonVfx(
    terminal: AuthoritativeProjectileTerminalFeedbackState,
    color: number
  ): void {
    const strategy = resolveAuthoritativeTerminalVfxStrategy(terminal);
    if (strategy.impactSpark !== "none") {
      this.options.createImpactSpark(
        terminal.terminalPosition,
        strategy.impactSpark === "weak" ? softenColor(color) : color
      );
    }

    if (strategy.pulseRadius !== null) {
      this.options.createPulse(terminal.terminalPosition, strategy.pulseRadius, color);
    }

    if (strategy.shockwaveRadius !== null) {
      this.options.createShockwave(
        terminal.terminalPosition,
        resolveRocketShockwaveStartRadius(),
        strategy.shockwaveRadius,
        color,
        240
      );
    }

    if (strategy.dissipate) {
      this.options.createProjectileDissipate(terminal.terminalPosition, softenColor(color));
    }
  }

  private presentProjectileTerminalTracer(previous: ProjectileFeedbackState, color: number): void {
    this.options.createProjectileTracer(createProjectileTerminalTracerOptions(previous, color));
  }

  private presentAuthoritativeProjectileTerminalTracer(
    terminal: AuthoritativeProjectileTerminalFeedbackState,
    previous: ProjectileFeedbackState | undefined,
    color: number
  ): void {
    this.options.createProjectileTracer(
      createAuthoritativeProjectileTerminalTracerOptions(terminal, previous, color)
    );
  }

  private presentProjectileTerminalCorrectionTracer(previous: ProjectileFeedbackState, color: number): void {
    const tracerOptions = createProjectileTerminalCorrectionTracerOptions(previous, color);
    if (!tracerOptions) {
      return;
    }

    this.options.createProjectileTracer(tracerOptions);
  }

  private presentAuthoritativeProjectileTerminalCorrectionTracer(
    terminal: AuthoritativeProjectileTerminalFeedbackState,
    previous: ProjectileFeedbackState | undefined,
    color: number
  ): void {
    const tracerOptions = createAuthoritativeProjectileTerminalCorrectionTracerOptions(terminal, previous, color);
    if (!tracerOptions) {
      return;
    }

    this.options.createProjectileTracer(tracerOptions);
  }

  private recordProjectileTerminalDiagnostics(
    previous: ProjectileFeedbackState,
    projectileId: string,
    snapshot: GameSnapshot
  ): void {
    if (!shouldRecordRemoteProjectileTerminalDiagnostics()) {
      return;
    }

    const nearestHero = resolveNearestTerminalHero(
      previous,
      snapshot.heroes,
      (heroId) => this.options.getHeroDisplayPosition(heroId)
    );
    recordRemoteProjectileTerminalDiagnostics({
      projectileId,
      kind: previous.kind,
      source: "snapshot-diff",
      reason: previous.ttlMs <= 0 ? "ttl" : null,
      terminalPosition: previous.authoritativePosition,
      displayPosition: previous.displayPosition,
      authoritativePosition: previous.authoritativePosition,
      ttlMs: previous.ttlMs,
      maxLifetimeMs: previous.maxLifetimeMs,
      nearestHeroId: nearestHero?.heroId ?? null,
      nearestHeroDisplayName: nearestHero?.displayName ?? null,
      nearestHeroAuthoritativeEdgeDistance: nearestHero?.authoritativeEdgeDistance ?? null,
      nearestHeroDisplayEdgeDistance: nearestHero?.displayEdgeDistance ?? null
    });
  }

  private recordAuthoritativeProjectileTerminalDiagnostics(
    terminal: AuthoritativeProjectileTerminalFeedbackState,
    previous: ProjectileFeedbackState | undefined,
    snapshot: GameSnapshot,
    vfxBudgetReason: AuthoritativeProjectileTerminalVfxBudgetReason | null = null
  ): void {
    if (!shouldRecordRemoteProjectileTerminalDiagnostics()) {
      return;
    }

    const terminalProjectile = createTerminalDiagnosticProjectileState(terminal, previous);
    const nearestHero = resolveNearestTerminalHero(
      terminalProjectile,
      snapshot.heroes,
      (heroId) => this.options.getHeroDisplayPosition(heroId)
    );
    recordRemoteProjectileTerminalDiagnostics({
      projectileId: terminal.projectileId,
      kind: terminal.kind,
      source: "server",
      reason: terminal.reason,
      terminalPosition: terminal.terminalPosition,
      displayPosition: terminalProjectile.displayPosition,
      authoritativePosition: terminal.terminalPosition,
      ttlMs: terminal.ttlAfter,
      maxLifetimeMs: previous?.maxLifetimeMs ?? Math.max(terminal.ttlBefore, terminal.ttlAfter),
      targetPlayerId: terminal.targetPlayerId,
      targetHeroId: terminal.targetHeroId,
      hpBefore: terminal.hpBefore,
      hpAfter: terminal.hpAfter,
      damage: terminal.damage,
      nearestHeroId: nearestHero?.heroId ?? null,
      nearestHeroDisplayName: nearestHero?.displayName ?? null,
      nearestHeroAuthoritativeEdgeDistance: nearestHero?.authoritativeEdgeDistance ?? null,
      nearestHeroDisplayEdgeDistance: nearestHero?.displayEdgeDistance ?? null,
      ...(vfxBudgetReason ? { vfxSkipped: true, vfxBudgetReason } : {})
    });
  }

  private capture(snapshot: GameSnapshot): void {
    this.heroStates.clear();
    snapshot.heroes.forEach((hero) => {
      const displayPosition = this.options.getHeroDisplayPosition(hero.heroId);
      this.heroStates.set(hero.heroId, createHeroFeedbackState(hero, displayPosition ?? hero.position));
    });

    this.weaponPickupStates.clear();
    snapshot.weaponPickups.forEach((pickup) => {
      this.weaponPickupStates.set(pickup.weaponId, createWeaponPickupFeedbackState(pickup));
    });

    this.itemPickupStates.clear();
    snapshot.itemPickups.forEach((pickup) => {
      this.itemPickupStates.set(pickup.pickupId, createItemPickupFeedbackState(pickup));
    });

    const liveProjectileIds = this.scratchLiveProjectileIds;
    liveProjectileIds.clear();
    snapshot.projectiles.forEach((projectile) => {
      liveProjectileIds.add(projectile.projectileId);
      this.rememberSeenLiveProjectileId(projectile.projectileId);
      const displayPosition = this.options.getProjectileDisplayPosition(projectile.projectileId);
      const feedbackPosition = displayPosition ?? projectile.position;
      const direction = resolveProjectileDirection(projectile);
      const existing = this.projectileStates.get(projectile.projectileId);
      if (existing) {
        existing.kind = projectile.kind;
        existing.ownerHeroId = projectile.ownerHeroId;
        existing.displayPosition.x = feedbackPosition.x;
        existing.displayPosition.y = feedbackPosition.y;
        existing.authoritativePosition.x = projectile.position.x;
        existing.authoritativePosition.y = projectile.position.y;
        existing.direction.x = direction.x;
        existing.direction.y = direction.y;
        existing.ttlMs = projectile.ttlMs;
        existing.maxLifetimeMs = projectile.maxLifetimeMs;
        return;
      }

      this.projectileStates.set(projectile.projectileId, createProjectileFeedbackState(projectile, feedbackPosition, direction));
    });

    for (const projectileId of this.projectileStates.keys()) {
      if (!liveProjectileIds.has(projectileId)) {
        this.projectileStates.delete(projectileId);
      }
    }
  }

  private rememberSeenLiveProjectileId(projectileId: string): void {
    if (this.seenLiveProjectileIds.has(projectileId)) {
      return;
    }

    this.seenLiveProjectileIds.add(projectileId);
    this.seenLiveProjectileIdQueue.push(projectileId);
    while (this.seenLiveProjectileIdQueue.length > REMEMBERED_LIVE_PROJECTILE_ID_LIMIT) {
      const expiredProjectileId = this.seenLiveProjectileIdQueue.shift();
      if (expiredProjectileId) {
        this.seenLiveProjectileIds.delete(expiredProjectileId);
      }
    }
  }

  private rememberPlayedAuthoritativeProjectileTerminal(terminalKey: string): void {
    if (this.playedAuthoritativeProjectileTerminals.has(terminalKey)) {
      return;
    }

    this.playedAuthoritativeProjectileTerminals.add(terminalKey);
    this.playedAuthoritativeProjectileTerminalQueue.push(terminalKey);
    while (this.playedAuthoritativeProjectileTerminalQueue.length > PLAYED_AUTHORITATIVE_PROJECTILE_TERMINAL_LIMIT) {
      const expiredTerminalKey = this.playedAuthoritativeProjectileTerminalQueue.shift();
      if (expiredTerminalKey) {
        this.playedAuthoritativeProjectileTerminals.delete(expiredTerminalKey);
      }
    }
  }

  private enqueueAuthoritativeProjectileTerminal(
    terminalKey: string,
    terminal: AuthoritativeProjectileTerminalFeedbackState
  ): void {
    if (this.authoritativeProjectileTerminals.has(terminalKey)) {
      this.authoritativeProjectileTerminals.set(terminalKey, terminal);
      return;
    }

    if (this.authoritativeProjectileTerminals.size < AUTHORITATIVE_PROJECTILE_TERMINAL_VFX_QUEUE_LIMIT) {
      this.authoritativeProjectileTerminals.set(terminalKey, terminal);
      return;
    }

    const droppedTerminalKey = resolveAuthoritativeProjectileTerminalQueueDropKey(
      this.authoritativeProjectileTerminals,
      terminalKey,
      terminal
    );
    const droppedTerminal =
      droppedTerminalKey === terminalKey ? terminal : this.authoritativeProjectileTerminals.get(droppedTerminalKey);
    if (droppedTerminal) {
      this.recordSkippedAuthoritativeProjectileTerminalDiagnostics(droppedTerminal, "queue-limit");
      this.rememberPlayedAuthoritativeProjectileTerminal(droppedTerminalKey);
    }

    if (droppedTerminalKey !== terminalKey) {
      this.authoritativeProjectileTerminals.delete(droppedTerminalKey);
      this.authoritativeProjectileTerminals.set(terminalKey, terminal);
    }
  }

  private recordSkippedAuthoritativeProjectileTerminalDiagnostics(
    terminal: AuthoritativeProjectileTerminalFeedbackState,
    vfxBudgetReason: AuthoritativeProjectileTerminalVfxBudgetReason
  ): void {
    if (!shouldRecordRemoteProjectileTerminalDiagnostics()) {
      return;
    }

    this.recordAuthoritativeProjectileTerminalDiagnostics(
      terminal,
      this.projectileStates.get(terminal.projectileId),
      this.options.getSnapshot(),
      vfxBudgetReason
    );
  }

  private hasPlayedAuthoritativeProjectileTerminalForProjectile(projectileId: string): boolean {
    for (const terminalKey of this.playedAuthoritativeProjectileTerminals) {
      if (terminalKey.startsWith(`${projectileId}:`)) {
        return true;
      }
    }

    return false;
  }

  private resolveAuthoritativeProjectileTerminalFreshnessBaseline(frame: BattleRuntimeAuthoritativeFrame): number {
    const frameElapsedMs = resolveAuthoritativeFrameElapsedWatermark(frame);
    if (this.initialized && this.authoritativeProjectileTerminalFreshnessBaselineElapsedMs !== null) {
      return this.authoritativeProjectileTerminalFreshnessBaselineElapsedMs;
    }

    if (this.authoritativeProjectileTerminalFreshnessBaselineElapsedMs === null) {
      this.authoritativeProjectileTerminalFreshnessBaselineElapsedMs = frameElapsedMs;
      return frameElapsedMs;
    }

    if (!this.initialized) {
      this.authoritativeProjectileTerminalFreshnessBaselineElapsedMs = Math.max(
        this.authoritativeProjectileTerminalFreshnessBaselineElapsedMs,
        frameElapsedMs
      );
    }

    return this.authoritativeProjectileTerminalFreshnessBaselineElapsedMs;
  }
}

function createHeroFeedbackState(hero: Hero, position: Vec2): HeroFeedbackState {
  return {
    hp: hero.hp,
    alive: hero.alive,
    score: hero.score,
    currentWeaponAmmoTotal: resolveCurrentWeaponAmmoTotal(hero),
    position: { x: position.x, y: position.y }
  };
}

function resolveCurrentWeaponAmmoTotal(hero: Hero): number | null {
  const weapon = hero.weapons[hero.currentWeaponIndex];
  if (!weapon) {
    return null;
  }

  return toSafeAmmoCount(weapon.ammoInMagazine) + toSafeAmmoCount(weapon.reserveAmmo);
}

function toSafeAmmoCount(value: number | null | undefined): number {
  return typeof value === "number" && Number.isFinite(value) ? Math.max(0, Math.round(value)) : 0;
}

function createWeaponPickupFeedbackState(pickup: WeaponPickup): PickupFeedbackState {
  return {
    available: pickup.available,
    position: { x: pickup.position.x, y: pickup.position.y }
  };
}

function createItemPickupFeedbackState(pickup: ItemPickup): PickupFeedbackState {
  return {
    available: pickup.available,
    position: { x: pickup.position.x, y: pickup.position.y }
  };
}
