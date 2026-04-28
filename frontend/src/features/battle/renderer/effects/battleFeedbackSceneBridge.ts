import type { GameSnapshot, Hero, ItemPickup, Projectile, Vec2, WeaponPickup } from "../../../../domain/types";
import { WEAPON_DEFINITIONS } from "../../../../game/weapons";
import type { BattleRuntimeAuthoritativeFrame } from "../authoritativeBattleStateBridge";
import {
  recordRemoteProjectileBirthDiagnostics,
  recordRemoteProjectileTerminalDiagnostics,
  shouldRecordRemoteProjectileTerminalDiagnostics
} from "../remoteViewDiagnostics";

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

interface ProjectileFeedbackState {
  ownerHeroId: string;
  kind: Projectile["kind"];
  displayPosition: Vec2;
  authoritativePosition: Vec2;
  direction: Vec2;
  ttlMs: number;
  maxLifetimeMs: number;
}

type AuthoritativeProjectileTerminalFrame = BattleRuntimeAuthoritativeFrame["projectileTerminals"][number];

interface AuthoritativeProjectileTerminalFeedbackState {
  projectileId: string;
  ownerPlayerId: string;
  ownerHeroId: string;
  kind: Projectile["kind"];
  reason: string;
  start: Vec2;
  end: Vec2;
  terminalPosition: Vec2;
  ttlBefore: number;
  ttlAfter: number;
  elapsedMs: number;
  targetPlayerId: string | null;
  targetHeroId: string | null;
  hpBefore: number | null;
  hpAfter: number | null;
  damage: number | null;
}

interface AuthoritativeProjectileTerminalVfxStrategy {
  impactSpark: "none" | "normal" | "weak";
  pulseRadius: number | null;
  shockwaveRadius: number | null;
  dissipate: boolean;
}

const PROJECTILE_SPARK_COLORS: Record<Projectile["kind"], number> = {
  "pistol-bullet": 0xfff0c6,
  rocket: 0xffb36f,
  "gatling-bullet": 0xffd86d,
  "shotgun-pellet": 0xffefb7
};
const PROJECTILE_TERMINAL_TRACER_LENGTHS: Record<Projectile["kind"], number> = {
  "pistol-bullet": 34,
  rocket: 48,
  "gatling-bullet": 34,
  "shotgun-pellet": 22
};
const PROJECTILE_TERMINAL_TRACER_THICKNESS: Record<Projectile["kind"], number> = {
  "pistol-bullet": 2,
  rocket: 6,
  "gatling-bullet": 2,
  "shotgun-pellet": 4
};
const PROJECTILE_TERMINAL_TRACER_DURATION_MS = 180;
const PROJECTILE_TERMINAL_TRACER_ALPHA: Record<Projectile["kind"], number> = {
  "pistol-bullet": 0.34,
  rocket: 0.58,
  "gatling-bullet": 0.32,
  "shotgun-pellet": 0.3
};
const PROJECTILE_TERMINAL_TRACER_GHOST_SCALE: Record<Projectile["kind"], number> = {
  "pistol-bullet": 0.52,
  rocket: 1,
  "gatling-bullet": 0.48,
  "shotgun-pellet": 0.45
};
const PROJECTILE_CORRECTION_TRACER_MIN_DISTANCE = 18;
const PROJECTILE_CORRECTION_TRACER_MAX_DISTANCE = 140;
const PROJECTILE_CORRECTION_TRACER_DURATION_MS = 140;
const AUTHORITATIVE_PROJECTILE_BIRTH_CLEARANCE = 4;
const REMOTE_PROJECTILE_BIRTH_FALLBACK_BACKSTEP = 10;
const REMEMBERED_LIVE_PROJECTILE_ID_LIMIT = 256;
const PLAYED_AUTHORITATIVE_PROJECTILE_TERMINAL_LIMIT = 256;
const AUTHORITATIVE_PROJECTILE_TERMINAL_VFX_QUEUE_LIMIT = 96;
const AUTHORITATIVE_PROJECTILE_TERMINAL_VFX_PER_UPDATE_LIMIT = 12;
const AMMO_PICKUP_PULSE_RADIUS = 30;
const AMMO_PICKUP_PULSE_COLOR = 0xffd86d;
const ROCKET_SPLASH_VISUAL_RADIUS = WEAPON_DEFINITIONS.RocketLauncher.splashRadius;

type AuthoritativeProjectileTerminalVfxBudgetReason = "queue-limit" | "per-update-limit";

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
        this.options.createProjectileTracer({
          start: {
            x: position.x - projectile.velocity.x * 0.012,
            y: position.y - projectile.velocity.y * 0.012
          },
          direction: resolveProjectileDirection(projectile),
          length: 18,
          color,
          thickness: 1,
          durationMs: 58,
          alpha: 0.26,
          ghostScale: 0.35,
          glintAlphaScale: 0,
          underglowAlphaScale: 0,
          coreAlphaScale: 0.35,
          ghostAlphaScale: 0
        });
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
      if (!this.isLocalProjectileTerminal(previous, snapshot.playerHeroId)) {
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
        if (!this.isLocalAuthoritativeProjectileTerminal(terminal, snapshot.playerHeroId)) {
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
    const length = PROJECTILE_TERMINAL_TRACER_LENGTHS[previous.kind];
    this.options.createProjectileTracer({
      start: {
        x: previous.authoritativePosition.x - previous.direction.x * length,
        y: previous.authoritativePosition.y - previous.direction.y * length
      },
      direction: previous.direction,
      length,
      color,
      thickness: PROJECTILE_TERMINAL_TRACER_THICKNESS[previous.kind],
      durationMs: PROJECTILE_TERMINAL_TRACER_DURATION_MS,
      alpha: PROJECTILE_TERMINAL_TRACER_ALPHA[previous.kind],
      ghostScale: PROJECTILE_TERMINAL_TRACER_GHOST_SCALE[previous.kind],
      ...resolveProjectileTracerNoiseOptions(previous.kind)
    });
  }

  private presentAuthoritativeProjectileTerminalTracer(
    terminal: AuthoritativeProjectileTerminalFeedbackState,
    previous: ProjectileFeedbackState | undefined,
    color: number
  ): void {
    const direction = resolveAuthoritativeTerminalDirection(terminal, previous);
    const length = PROJECTILE_TERMINAL_TRACER_LENGTHS[terminal.kind];
    this.options.createProjectileTracer({
      start: {
        x: terminal.terminalPosition.x - direction.x * length,
        y: terminal.terminalPosition.y - direction.y * length
      },
      direction,
      length,
      color,
      thickness: PROJECTILE_TERMINAL_TRACER_THICKNESS[terminal.kind],
      durationMs: PROJECTILE_TERMINAL_TRACER_DURATION_MS,
      alpha: PROJECTILE_TERMINAL_TRACER_ALPHA[terminal.kind],
      ghostScale: PROJECTILE_TERMINAL_TRACER_GHOST_SCALE[terminal.kind],
      ...resolveProjectileTracerNoiseOptions(terminal.kind)
    });
  }

  private presentProjectileTerminalCorrectionTracer(previous: ProjectileFeedbackState, color: number): void {
    const distance = distanceBetween(previous.displayPosition, previous.authoritativePosition);
    if (
      distance <= PROJECTILE_CORRECTION_TRACER_MIN_DISTANCE ||
      distance > PROJECTILE_CORRECTION_TRACER_MAX_DISTANCE
    ) {
      return;
    }

    this.options.createProjectileTracer({
      start: previous.displayPosition,
      direction: {
        x: (previous.authoritativePosition.x - previous.displayPosition.x) / distance,
        y: (previous.authoritativePosition.y - previous.displayPosition.y) / distance
      },
      length: distance,
      color,
      thickness: 2,
      durationMs: PROJECTILE_CORRECTION_TRACER_DURATION_MS,
      alpha: 0.38,
      ghostScale: 0.35,
      glintAlphaScale: 0,
      underglowAlphaScale: 0,
      coreAlphaScale: 0.46,
      ghostAlphaScale: 0
    });
  }

  private presentAuthoritativeProjectileTerminalCorrectionTracer(
    terminal: AuthoritativeProjectileTerminalFeedbackState,
    previous: ProjectileFeedbackState | undefined,
    color: number
  ): void {
    if (!previous) {
      return;
    }

    const distance = distanceBetween(previous.displayPosition, terminal.terminalPosition);
    if (
      distance <= PROJECTILE_CORRECTION_TRACER_MIN_DISTANCE ||
      distance > PROJECTILE_CORRECTION_TRACER_MAX_DISTANCE
    ) {
      return;
    }

    this.options.createProjectileTracer({
      start: previous.displayPosition,
      direction: {
        x: (terminal.terminalPosition.x - previous.displayPosition.x) / distance,
        y: (terminal.terminalPosition.y - previous.displayPosition.y) / distance
      },
      length: distance,
      color,
      thickness: 2,
      durationMs: PROJECTILE_CORRECTION_TRACER_DURATION_MS,
      alpha: 0.38,
      ghostScale: 0.35,
      glintAlphaScale: 0,
      underglowAlphaScale: 0,
      coreAlphaScale: 0.46,
      ghostAlphaScale: 0
    });
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

  private isLocalProjectileTerminal(projectile: ProjectileFeedbackState, playerHeroId: string): boolean {
    return projectile.ownerHeroId === playerHeroId;
  }

  private isLocalAuthoritativeProjectileTerminal(
    terminal: AuthoritativeProjectileTerminalFeedbackState,
    playerHeroId: string
  ): boolean {
    return terminal.ownerHeroId === playerHeroId;
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

function createProjectileFeedbackState(projectile: Projectile, displayPosition: Vec2, direction: Vec2): ProjectileFeedbackState {
  return {
    ownerHeroId: projectile.ownerHeroId,
    kind: projectile.kind,
    displayPosition: { x: displayPosition.x, y: displayPosition.y },
    authoritativePosition: { x: projectile.position.x, y: projectile.position.y },
    direction: { x: direction.x, y: direction.y },
    ttlMs: projectile.ttlMs,
    maxLifetimeMs: projectile.maxLifetimeMs
  };
}

function createAuthoritativeProjectileTerminalFeedbackState(
  terminal: AuthoritativeProjectileTerminalFrame
): AuthoritativeProjectileTerminalFeedbackState | null {
  const kind = normalizeProjectileKind(terminal.kind);
  if (!kind) {
    return null;
  }

  return {
    projectileId: terminal.projectileId,
    ownerPlayerId: terminal.ownerPlayerId,
    ownerHeroId: terminal.ownerHeroId,
    kind,
    reason: terminal.reason,
    start: { x: terminal.start.x, y: terminal.start.y },
    end: { x: terminal.end.x, y: terminal.end.y },
    terminalPosition: { x: terminal.terminalPosition.x, y: terminal.terminalPosition.y },
    ttlBefore: Math.max(0, Math.round(terminal.ttlBefore)),
    ttlAfter: Math.max(0, Math.round(terminal.ttlAfter)),
    elapsedMs: Math.max(0, Math.round(terminal.elapsedMs)),
    targetPlayerId: terminal.targetPlayerId,
    targetHeroId: terminal.targetHeroId,
    hpBefore: terminal.hpBefore,
    hpAfter: terminal.hpAfter,
    damage: terminal.damage
  };
}

function normalizeProjectileKind(kind: string): Projectile["kind"] | null {
  switch (kind) {
    case "pistol-bullet":
    case "rocket":
    case "gatling-bullet":
    case "shotgun-pellet":
      return kind;
    default:
      return null;
  }
}

function createAuthoritativeProjectileTerminalKey(terminal: AuthoritativeProjectileTerminalFrame): string {
  return [
    terminal.projectileId,
    terminal.reason,
    Math.round(terminal.elapsedMs),
    Math.round(terminal.terminalPosition.x * 100) / 100,
    Math.round(terminal.terminalPosition.y * 100) / 100
  ].join(":");
}

interface AuthoritativeProjectileTerminalQueueDecisionInput {
  seenLive: boolean;
  terminalElapsedMs: number;
  freshnessBaselineElapsedMs: number;
}

function shouldQueueAuthoritativeProjectileTerminal(input: AuthoritativeProjectileTerminalQueueDecisionInput): boolean {
  if (input.seenLive) {
    return true;
  }

  // After startup, unseen terminals represent too-fast projectiles rather than retained history.
  return normalizeElapsedMs(input.terminalElapsedMs) >= input.freshnessBaselineElapsedMs;
}

function resolveAuthoritativeFrameElapsedWatermark(frame: BattleRuntimeAuthoritativeFrame): number {
  const frameElapsedMs = normalizeElapsedMs(frame.elapsedMs);
  if (frameElapsedMs > 0 || frame.projectileTerminals.length === 0) {
    return frameElapsedMs;
  }

  return frame.projectileTerminals.reduce(
    (watermark, terminal) => Math.max(watermark, normalizeElapsedMs(terminal.elapsedMs)),
    frameElapsedMs
  );
}

function normalizeElapsedMs(elapsedMs: number): number {
  return Number.isFinite(elapsedMs) ? Math.max(0, Math.round(elapsedMs)) : 0;
}

function selectAuthoritativeProjectileTerminalVfxKeys(
  terminals: Array<{
    terminalKey: string;
    terminal: AuthoritativeProjectileTerminalFeedbackState;
  }>,
  limit: number
): Set<string> {
  if (terminals.length <= limit) {
    return new Set(terminals.map(({ terminalKey }) => terminalKey));
  }

  const selected = new Set<string>();
  terminals.forEach(({ terminalKey, terminal }) => {
    if (selected.size < limit && terminal.reason === "hit") {
      selected.add(terminalKey);
    }
  });

  terminals.forEach(({ terminalKey }) => {
    if (selected.size < limit) {
      selected.add(terminalKey);
    }
  });

  return selected;
}

function resolveAuthoritativeProjectileTerminalQueueDropKey(
  queuedTerminals: Map<string, AuthoritativeProjectileTerminalFeedbackState>,
  incomingTerminalKey: string,
  incomingTerminal: AuthoritativeProjectileTerminalFeedbackState
): string {
  for (const [terminalKey, terminal] of queuedTerminals) {
    if (terminal.reason !== "hit") {
      return terminalKey;
    }
  }

  return incomingTerminal.reason === "hit"
    ? queuedTerminals.keys().next().value ?? incomingTerminalKey
    : incomingTerminalKey;
}

function resolveAuthoritativeTerminalVfxStrategy(
  terminal: AuthoritativeProjectileTerminalFeedbackState
): AuthoritativeProjectileTerminalVfxStrategy {
  if (terminal.kind === "gatling-bullet") {
    return {
      impactSpark: "none",
      pulseRadius: null,
      shockwaveRadius: null,
      dissipate: false
    };
  }

  if (terminal.kind === "rocket") {
    return {
      impactSpark: terminal.reason === "hit" || terminal.reason === "obstacle" ? "normal" : "weak",
      pulseRadius: null,
      shockwaveRadius: ROCKET_SPLASH_VISUAL_RADIUS,
      dissipate: false
    };
  }

  switch (terminal.reason) {
    case "hit":
    case "obstacle":
      return {
        impactSpark: "normal",
        pulseRadius: null,
        shockwaveRadius: null,
        dissipate: false
      };
    case "world":
      return {
        impactSpark: "weak",
        pulseRadius: 10,
        shockwaveRadius: null,
        dissipate: false
      };
    case "ttl":
      return {
        impactSpark: "none",
        pulseRadius: null,
        shockwaveRadius: null,
        dissipate: true
      };
    default:
      return {
        impactSpark: "weak",
        pulseRadius: null,
        shockwaveRadius: null,
        dissipate: false
      };
  }
}

function resolveRocketShockwaveStartRadius(): number {
  return Math.max(18, ROCKET_SPLASH_VISUAL_RADIUS * 0.16);
}

function resolveProjectileTracerNoiseOptions(kind: Projectile["kind"]): Pick<
  Parameters<BattleFeedbackSceneBridgeOptions["createProjectileTracer"]>[0],
  "glintAlphaScale" | "underglowAlphaScale" | "coreAlphaScale" | "ghostAlphaScale"
> {
  if (kind === "gatling-bullet") {
    return {
      glintAlphaScale: 0,
      underglowAlphaScale: 0,
      coreAlphaScale: 0.36,
      ghostAlphaScale: 0
    };
  }

  return {};
}

function softenColor(color: number): number {
  const red = (color >> 16) & 0xff;
  const green = (color >> 8) & 0xff;
  const blue = color & 0xff;
  return (
    (Math.round((red + 0x80) / 2) << 16) |
    (Math.round((green + 0x80) / 2) << 8) |
    Math.round((blue + 0x80) / 2)
  );
}

function resolveAuthoritativeTerminalDirection(
  terminal: AuthoritativeProjectileTerminalFeedbackState,
  previous: ProjectileFeedbackState | undefined
): Vec2 {
  const terminalDelta = {
    x: terminal.terminalPosition.x - terminal.start.x,
    y: terminal.terminalPosition.y - terminal.start.y
  };
  const terminalDeltaLength = Math.hypot(terminalDelta.x, terminalDelta.y);
  if (terminalDeltaLength > 0.0001) {
    return {
      x: terminalDelta.x / terminalDeltaLength,
      y: terminalDelta.y / terminalDeltaLength
    };
  }

  const segmentDelta = {
    x: terminal.end.x - terminal.start.x,
    y: terminal.end.y - terminal.start.y
  };
  const segmentDeltaLength = Math.hypot(segmentDelta.x, segmentDelta.y);
  if (segmentDeltaLength > 0.0001) {
    return {
      x: segmentDelta.x / segmentDeltaLength,
      y: segmentDelta.y / segmentDeltaLength
    };
  }

  return previous?.direction ?? { x: 1, y: 0 };
}

function createTerminalDiagnosticProjectileState(
  terminal: AuthoritativeProjectileTerminalFeedbackState,
  previous: ProjectileFeedbackState | undefined
): ProjectileFeedbackState {
  const direction = resolveAuthoritativeTerminalDirection(terminal, previous);
  return {
    kind: terminal.kind,
    ownerHeroId: previous?.ownerHeroId ?? terminal.ownerHeroId,
    displayPosition: previous
      ? { x: previous.displayPosition.x, y: previous.displayPosition.y }
      : { x: terminal.terminalPosition.x, y: terminal.terminalPosition.y },
    authoritativePosition: { x: terminal.terminalPosition.x, y: terminal.terminalPosition.y },
    direction,
    ttlMs: terminal.ttlAfter,
    maxLifetimeMs: previous?.maxLifetimeMs ?? Math.max(terminal.ttlBefore, terminal.ttlAfter)
  };
}

function resolveRemoteProjectileBirthFeedbackPosition(
  projectile: Projectile,
  owner?: Hero,
  ownerDisplayPosition?: Vec2 | null
): Vec2 {
  const direction = resolveProjectileDirection(projectile);

  if (owner) {
    const basePosition = ownerDisplayPosition ?? owner.position;
    const forwardDistance = owner.radius + projectile.radius + AUTHORITATIVE_PROJECTILE_BIRTH_CLEARANCE;
    return {
      x: basePosition.x + direction.x * forwardDistance,
      y: basePosition.y + direction.y * forwardDistance
    };
  }

  return {
    x: projectile.position.x - direction.x * REMOTE_PROJECTILE_BIRTH_FALLBACK_BACKSTEP,
    y: projectile.position.y - direction.y * REMOTE_PROJECTILE_BIRTH_FALLBACK_BACKSTEP
  };
}

function resolveProjectileDirection(projectile: Projectile): Vec2 {
  const velocityLength = Math.hypot(projectile.velocity.x, projectile.velocity.y);
  if (velocityLength > 0.0001) {
    return {
      x: projectile.velocity.x / velocityLength,
      y: projectile.velocity.y / velocityLength
    };
  }

  return {
    x: Math.cos(projectile.facing),
    y: Math.sin(projectile.facing)
  };
}

interface NearestTerminalHero {
  heroId: string;
  displayName: string;
  authoritativeEdgeDistance: number;
  displayEdgeDistance: number;
}

function resolveNearestTerminalHero(
  projectile: ProjectileFeedbackState,
  heroes: Hero[],
  getHeroDisplayPosition: (heroId: string) => Vec2 | null
): NearestTerminalHero | null {
  let nearest: NearestTerminalHero | null = null;

  heroes.forEach((hero) => {
    const authoritativeEdgeDistance = distanceBetween(projectile.authoritativePosition, hero.position) - hero.radius;
    const heroDisplayPosition = getHeroDisplayPosition(hero.heroId) ?? hero.position;
    const displayEdgeDistance = distanceBetween(projectile.displayPosition, heroDisplayPosition) - hero.radius;
    if (!nearest || authoritativeEdgeDistance < nearest.authoritativeEdgeDistance) {
      nearest = {
        heroId: hero.heroId,
        displayName: hero.displayName,
        authoritativeEdgeDistance,
        displayEdgeDistance
      };
    }
  });

  return nearest;
}

function distanceBetween(left: Vec2, right: Vec2): number {
  return Math.hypot(right.x - left.x, right.y - left.y);
}
