import type { BattleGameSnapshot as GameSnapshot } from "../../../../../objects/battle/microservices/session/objects/state/BattleGameSnapshot";
import type { BattleRuntimeAuthoritativeFrame } from "../../../microservices/session/functions/BattleRuntimeAuthoritativeFrameBuilder";
import {
  presentAuthoritativePickupFeedback,
  presentHeroFeedback
} from "./heroAndPickupFeedbackPresenter";
import {
  createBattleHeroFeedbackState,
  type BattleHeroFeedbackState as HeroFeedbackState
} from "../../../microservices/actors/functions/BattleHeroFeedbackRules";
import {
  createBattleItemPickupFeedbackState,
  createBattleWeaponPickupFeedbackState,
  type BattlePickupFeedbackState as PickupFeedbackState
} from "../../../microservices/abilities/functions/BattlePickupFeedbackRules";
import {
  presentAuthoritativeProjectileTerminalCorrectionTracer,
  presentAuthoritativeProjectileTerminalReasonVfx,
  presentAuthoritativeProjectileTerminalTracer,
  presentProjectileTerminalCorrectionTracer,
  presentProjectileTerminalDissipateVfx,
  presentProjectileTerminalRocketImpactVfx,
  presentProjectileTerminalTracer
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
  isLocalProjectileTerminal,
  resolveAuthoritativeProjectileTerminalQueueDropKey,
  resolveProjectileDirection,
  selectAuthoritativeProjectileTerminalVfxKeys,
  shouldQueueAuthoritativeProjectileTerminal,
  type AuthoritativeProjectileTerminalFeedbackState,
  type AuthoritativeProjectileTerminalVfxBudgetReason,
  type ProjectileFeedbackState
} from "../../../microservices/combat/functions/BattleProjectileFeedbackRules";
import {
  collectBattleLiveProjectileIds,
  hasBattlePlayedAuthoritativeProjectileTerminalForProjectile,
  resolveBattleAuthoritativeProjectileTerminalFreshnessBaseline,
  resolveBattleBoundedFeedbackKeyMemoryUpdate,
  resolveBattleReadyAuthoritativeProjectileTerminals,
  shouldPresentBattleAuthoritativeTerminalTracer
} from "../../../microservices/combat/functions/BattleProjectileFeedbackQueueRules";
import {
  recordAuthoritativeProjectileTerminalDiagnostics,
  recordProjectileTerminalDiagnostics,
  recordSkippedAuthoritativeProjectileTerminalDiagnostics
} from "./projectileTerminalDiagnosticsRecorder";
import { presentAuthoritativeRemoteProjectileBirthFeedback } from "./remoteProjectileBirthFeedbackPresenter";
import type { BattleFeedbackSceneBridgeOptions } from "./objects/BattleFeedbackSceneBridgeObjects";

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
  private previousSharedAuthoritativeRuntime = false;
  private authoritativeProjectileTerminalFreshnessBaselineElapsedMs: number | null = null;

  public constructor(private readonly options: BattleFeedbackSceneBridgeOptions) {}

  public applyAuthoritativeFrame(frame: BattleRuntimeAuthoritativeFrame): void {
    const freshnessBaselineElapsedMs = resolveBattleAuthoritativeProjectileTerminalFreshnessBaseline({
      frame,
      initialized: this.initialized,
      currentBaselineElapsedMs: this.authoritativeProjectileTerminalFreshnessBaselineElapsedMs
    });
    this.authoritativeProjectileTerminalFreshnessBaselineElapsedMs = freshnessBaselineElapsedMs;
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
      presentAuthoritativeRemoteProjectileBirthFeedback({
        snapshot,
        previousProjectileStates: this.projectileStates,
        callbacks: this.options
      });
      if (this.previousSharedAuthoritativeRuntime) {
        this.presentAuthoritativeProjectileTerminalFeedback(snapshot);
      }
    }
    this.capture(snapshot);
    this.previousSharedAuthoritativeRuntime = sharedAuthoritativeRuntime;
  }

  private presentAuthoritativeProjectileTerminalFeedback(snapshot: GameSnapshot): void {
    const liveProjectileIds = collectBattleLiveProjectileIds(snapshot.projectiles);

    this.presentQueuedAuthoritativeProjectileTerminals(snapshot, liveProjectileIds);

    this.projectileStates.forEach((previous, projectileId) => {
      if (
        liveProjectileIds.has(projectileId) ||
        hasBattlePlayedAuthoritativeProjectileTerminalForProjectile({
          playedTerminalKeys: this.playedAuthoritativeProjectileTerminals,
          projectileId
        })
      ) {
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
    const readyResolution = resolveBattleReadyAuthoritativeProjectileTerminals({
      queuedTerminals: this.authoritativeProjectileTerminals,
      playedTerminalKeys: this.playedAuthoritativeProjectileTerminals,
      liveProjectileIds,
      projectileStates: this.projectileStates
    });
    readyResolution.staleTerminalKeys.forEach((terminalKey) => {
      this.authoritativeProjectileTerminals.delete(terminalKey);
    });

    const vfxTerminalKeys = selectAuthoritativeProjectileTerminalVfxKeys(
      readyResolution.readyTerminals,
      AUTHORITATIVE_PROJECTILE_TERMINAL_VFX_PER_UPDATE_LIMIT
    );

    readyResolution.readyTerminals.forEach(({ terminalKey, terminal, previous }) => {
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
        if (
          shouldPresentBattleAuthoritativeTerminalTracer({
            terminal,
            previous,
            playerHeroId: snapshot.playerHeroId
          })
        ) {
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
      this.heroStates.set(hero.heroId, createBattleHeroFeedbackState(hero, displayPosition ?? hero.position));
    });

    this.weaponPickupStates.clear();
    snapshot.weaponPickups.forEach((pickup) => {
      this.weaponPickupStates.set(pickup.pickupId, createBattleWeaponPickupFeedbackState(pickup));
    });

    this.itemPickupStates.clear();
    snapshot.itemPickups.forEach((pickup) => {
      this.itemPickupStates.set(pickup.pickupId, createBattleItemPickupFeedbackState(pickup));
    });

    const liveProjectileIds = collectBattleLiveProjectileIds(snapshot.projectiles);
    snapshot.projectiles.forEach((projectile) => {
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
    const update = resolveBattleBoundedFeedbackKeyMemoryUpdate({
      key: projectileId,
      rememberedKeys: this.seenLiveProjectileIds,
      keyQueue: this.seenLiveProjectileIdQueue,
      limit: REMEMBERED_LIVE_PROJECTILE_ID_LIMIT
    });
    if (!update.shouldRemember) {
      return;
    }

    this.seenLiveProjectileIds.add(projectileId);
    this.seenLiveProjectileIdQueue.push(projectileId);
    update.expiredKeys.forEach(() => {
      const expiredProjectileId = this.seenLiveProjectileIdQueue.shift();
      if (expiredProjectileId) {
        this.seenLiveProjectileIds.delete(expiredProjectileId);
      }
    });
  }

  private rememberPlayedAuthoritativeProjectileTerminal(terminalKey: string): void {
    const update = resolveBattleBoundedFeedbackKeyMemoryUpdate({
      key: terminalKey,
      rememberedKeys: this.playedAuthoritativeProjectileTerminals,
      keyQueue: this.playedAuthoritativeProjectileTerminalQueue,
      limit: PLAYED_AUTHORITATIVE_PROJECTILE_TERMINAL_LIMIT
    });
    if (!update.shouldRemember) {
      return;
    }

    this.playedAuthoritativeProjectileTerminals.add(terminalKey);
    this.playedAuthoritativeProjectileTerminalQueue.push(terminalKey);
    update.expiredKeys.forEach(() => {
      const expiredTerminalKey = this.playedAuthoritativeProjectileTerminalQueue.shift();
      if (expiredTerminalKey) {
        this.playedAuthoritativeProjectileTerminals.delete(expiredTerminalKey);
      }
    });
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
    recordSkippedAuthoritativeProjectileTerminalDiagnostics({
      terminal,
      previous: this.projectileStates.get(terminal.projectileId),
      getSnapshot: () => this.options.getSnapshot(),
      getHeroDisplayPosition: (heroId) => this.options.getHeroDisplayPosition(heroId),
      vfxBudgetReason
    });
  }

}
