import type { GameSnapshot, Vec2 } from "../../../../domain/types";
import type { BattleRuntimeAuthoritativeFrame } from "../authoritativeBattleStateBridge";
import { recordRemoteProjectileBirthDiagnostics } from "../remoteViewDiagnostics";
import {
  createHeroFeedbackState,
  createItemPickupFeedbackState,
  createWeaponPickupFeedbackState,
  presentAuthoritativePickupFeedback,
  presentHeroFeedback,
  type HeroFeedbackState,
  type PickupFeedbackState
} from "./heroAndPickupFeedbackPresenter";
import {
  presentAuthoritativeProjectileTerminalCorrectionTracer,
  presentAuthoritativeProjectileTerminalReasonVfx,
  presentAuthoritativeProjectileTerminalTracer,
  presentProjectileTerminalCorrectionTracer,
  presentProjectileTerminalDissipateVfx,
  presentProjectileTerminalRocketImpactVfx,
  presentProjectileTerminalTracer,
  type ProjectileTerminalVfxPresenterCallbacks
} from "./projectileTerminalVfxPresenter";
import {
  AUTHORITATIVE_PROJECTILE_TERMINAL_VFX_PER_UPDATE_LIMIT,
  AUTHORITATIVE_PROJECTILE_TERMINAL_VFX_QUEUE_LIMIT,
  PLAYED_AUTHORITATIVE_PROJECTILE_TERMINAL_LIMIT,
  PROJECTILE_SPARK_COLORS,
  REMEMBERED_LIVE_PROJECTILE_ID_LIMIT,
  createAuthoritativeProjectileTerminalFeedbackState,
  createAuthoritativeProjectileTerminalKey,
  createProjectileFeedbackState,
  createRemoteGatlingProjectileBirthTracerOptions,
  isLocalAuthoritativeProjectileTerminal,
  isLocalProjectileTerminal,
  resolveAuthoritativeFrameElapsedWatermark,
  resolveAuthoritativeProjectileTerminalQueueDropKey,
  resolveProjectileDirection,
  resolveRemoteProjectileBirthFeedbackPosition,
  selectAuthoritativeProjectileTerminalVfxKeys,
  shouldQueueAuthoritativeProjectileTerminal,
  type AuthoritativeProjectileTerminalFeedbackState,
  type AuthoritativeProjectileTerminalVfxBudgetReason,
  type ProjectileFeedbackState
} from "./projectileTerminalFeedbackPolicy";
import {
  recordAuthoritativeProjectileTerminalDiagnostics,
  recordProjectileTerminalDiagnostics,
  shouldRecordProjectileTerminalDiagnostics
} from "./projectileTerminalDiagnosticsRecorder";

export interface BattleFeedbackSceneBridgeOptions extends ProjectileTerminalVfxPresenterCallbacks {
  getSnapshot(): GameSnapshot;
  getHeroDisplayPosition(heroId: string): Vec2 | null;
  getProjectileDisplayPosition(projectileId: string): Vec2 | null;
  flashHero(heroId: string, color: number): void;
  showFloatingText(position: Vec2, text: string, tone: "neutral" | "success" | "warning" | "error"): void;
  createHitConfirm(position: Vec2, color: number): void;
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

    presentHeroFeedback({
      snapshot,
      previousHeroStates: this.heroStates,
      sharedAuthoritativeRuntime,
      getHeroDisplayPosition: (heroId) => this.options.getHeroDisplayPosition(heroId),
      flashHero: (heroId, color) => this.options.flashHero(heroId, color),
      showFloatingText: (position, text, tone) => this.options.showFloatingText(position, text, tone),
      createPulse: (position, radius, color) => this.options.createPulse(position, radius, color),
      createImpactSpark: (position, color) => this.options.createImpactSpark(position, color),
      createHitConfirm: (position, color) => this.options.createHitConfirm(position, color),
      shakeCamera: (duration, intensity) => this.options.shakeCamera(duration, intensity)
    });
    if (sharedAuthoritativeRuntime) {
      presentAuthoritativePickupFeedback({
        snapshot,
        previousWeaponPickupStates: this.weaponPickupStates,
        previousItemPickupStates: this.itemPickupStates,
        showFloatingText: (position, text, tone) => this.options.showFloatingText(position, text, tone),
        createPulse: (position, radius, color) => this.options.createPulse(position, radius, color)
      });
      this.presentAuthoritativeRemoteProjectileBirthFeedback(snapshot);
      if (this.previousSharedAuthoritativeRuntime) {
        this.presentAuthoritativeProjectileTerminalFeedback(snapshot);
      }
    }
    this.capture(snapshot);
    this.previousSharedAuthoritativeRuntime = sharedAuthoritativeRuntime;
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
        presentProjectileTerminalTracer({ previous, color, callbacks: this.options });
        presentProjectileTerminalCorrectionTracer({ previous, color, callbacks: this.options });
      }
      presentProjectileTerminalDissipateVfx({ previous, color, callbacks: this.options });
      recordProjectileTerminalDiagnostics({
        previous,
        projectileId,
        snapshot,
        getHeroDisplayPosition: (heroId) => this.options.getHeroDisplayPosition(heroId)
      });
      presentProjectileTerminalRocketImpactVfx({ previous, color, callbacks: this.options });
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
      recordAuthoritativeProjectileTerminalDiagnostics({
        terminal,
        previous,
        snapshot,
        getHeroDisplayPosition: (heroId) => this.options.getHeroDisplayPosition(heroId),
        vfxBudgetReason: shouldPlayVfx ? null : "per-update-limit"
      });

      if (shouldPlayVfx) {
        const color = PROJECTILE_SPARK_COLORS[terminal.kind];
        if (!isLocalAuthoritativeProjectileTerminal(terminal, snapshot.playerHeroId)) {
          presentAuthoritativeProjectileTerminalTracer({ terminal, previous, color, callbacks: this.options });
          presentAuthoritativeProjectileTerminalCorrectionTracer({ terminal, previous, color, callbacks: this.options });
        }
        presentAuthoritativeProjectileTerminalReasonVfx({ terminal, color, callbacks: this.options });
      }

      this.rememberPlayedAuthoritativeProjectileTerminal(terminalKey);
      this.authoritativeProjectileTerminals.delete(terminalKey);
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
    if (!shouldRecordProjectileTerminalDiagnostics()) {
      return;
    }

    recordAuthoritativeProjectileTerminalDiagnostics({
      terminal,
      previous: this.projectileStates.get(terminal.projectileId),
      snapshot: this.options.getSnapshot(),
      getHeroDisplayPosition: (heroId) => this.options.getHeroDisplayPosition(heroId),
      vfxBudgetReason
    });
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
