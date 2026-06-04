import type { BattleVector2 as Vec2 } from "../../../../../objects/battle/objects/core/BattleCoreScalars";
import type { BattleHeroViewState as Hero } from "../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { BattlePlayerCommand as PlayerCommand } from "../../../../../objects/battle/microservices/session/objects/command/BattlePlayerCommand";
import {
  resolveBattleSkillFeedbackPlans,
  type BattleSkillFeedbackPlan
} from "../../../microservices/abilities/functions/BattleSkillFeedbackRules";
import {
  resolveBattleWeaponPrimaryFeedback,
  resolveBattleWeaponReloadIntentFeedback
} from "../../../microservices/combat/functions/BattleWeaponFeedbackRules";
import { recordLocalMuzzleFeedbackDiagnostics } from "../diagnostics/localFeedbackDiagnostics";
import type { SharedAuthoritativeLocalFeedbackSceneBridgeOptions } from "./objects/SharedAuthoritativeLocalFeedbackSceneBridgeObjects";

export class SharedAuthoritativeLocalFeedbackSceneBridge {
  private nextPrimaryFeedbackAtMs = 0;
  private nextSkillRejectFeedbackAtMs = 0;
  private nextReloadIntentFeedbackAtMs = 0;
  private primaryHeldLastFrame = false;

  public constructor(private readonly options: SharedAuthoritativeLocalFeedbackSceneBridgeOptions) {}

  public update(command: PlayerCommand): void {
    const primaryPressStarted = command.primaryJustPressed || (command.primaryHeld && !this.primaryHeldLastFrame);
    this.primaryHeldLastFrame = command.primaryHeld;

    const player = this.options.getPlayerHero();
    if (!player.alive) {
      return;
    }

    const displayPose = this.options.localHeroDisplay.read();
    const skillFeedback = resolveBattleSkillFeedbackPlans({
      player,
      command,
      displayPosition: displayPose.position,
      worldSize: this.options.getWorldSize(),
      obstacleBounds: this.options.getObstacleBounds(),
      primaryPressStarted,
      nowMs: this.options.getNowMs(),
      nextSkillRejectFeedbackAtMs: this.nextSkillRejectFeedbackAtMs
    });
    this.presentReloadIntentFeedback(player, command, displayPose.position);
    this.presentPrimaryFeedback(player, command, skillFeedback.suppressPrimaryFeedback);
    this.presentSkillFeedback(skillFeedback.plans);
    this.nextSkillRejectFeedbackAtMs = skillFeedback.nextSkillRejectFeedbackAtMs;
  }

  private presentReloadIntentFeedback(player: Hero, command: PlayerCommand, displayPosition: Vec2): void {
    const plan = resolveBattleWeaponReloadIntentFeedback({
      player,
      command,
      nowMs: this.options.getNowMs(),
      nextReloadIntentFeedbackAtMs: this.nextReloadIntentFeedbackAtMs
    });
    if (!plan) {
      return;
    }

    this.nextReloadIntentFeedbackAtMs = plan.nextReloadIntentFeedbackAtMs;
    this.options.showFloatingText(displayPosition, plan.floatingText.text, plan.floatingText.tone);
  }

  private presentPrimaryFeedback(
    player: Hero,
    command: PlayerCommand,
    suppressForTargetedRelease: boolean
  ): void {
    const plan = resolveBattleWeaponPrimaryFeedback({
      player,
      command,
      suppressForTargetedRelease,
      nowMs: this.options.getNowMs(),
      nextPrimaryFeedbackAtMs: this.nextPrimaryFeedbackAtMs
    });
    if (!plan) {
      return;
    }

    this.nextPrimaryFeedbackAtMs = plan.nextPrimaryFeedbackAtMs;
    this.options.createMuzzleBurst(
      plan.muzzle.position,
      plan.muzzle.color,
      plan.muzzle.radius,
      plan.muzzle.sparks,
      plan.direction
    );
    this.options.createProjectileTracer(plan.tracer);
    if (plan.reticlePulse) {
      this.options.createPulse(plan.reticlePulse.position, plan.reticlePulse.radius, plan.reticlePulse.color);
    }
    recordLocalMuzzleFeedbackDiagnostics({
      weaponKind: plan.weaponKind,
      position: plan.muzzle.position,
      pointerWorld: plan.pointerWorld
    });
  }

  private presentSkillFeedback(plans: readonly BattleSkillFeedbackPlan[]): void {
    plans.forEach((plan) => {
      switch (plan.kind) {
        case "dash":
          this.options.createDashSkillFeedback(plan.position, plan.direction);
          return;
        case "blink-target":
          this.options.createBlinkSkillTargetFeedback(plan.position, plan.intent, plan.direction);
          return;
        case "freeze-target":
          this.options.createFreezeSkillTargetFeedback(plan.position, plan.intent);
          return;
        case "rejection":
          this.options.createSkillRejectionFeedback(plan.position, plan.radius);
          return;
      }
    });
  }
}
