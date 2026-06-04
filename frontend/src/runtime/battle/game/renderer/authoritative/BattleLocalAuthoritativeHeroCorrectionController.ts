import type { BattleVector2 as Vec2 } from "../../../../../objects/battle/objects/core/BattleCoreScalars";
import {
  resolveLocalAuthoritativeHeroCorrection,
  type LocalAuthoritativeHeroCorrectionContext,
  type LocalAuthoritativeHeroCorrectionResult
} from "../../../local/movement/BattleLocalAuthoritativeHeroCorrectionRules";
import { recordLocalHeroCorrectionDiagnostics } from "../diagnostics/localHeroCorrectionDiagnostics";
import type { LocalHeroDisplayPositionStore } from "../entities/BattleLocalHeroDisplay";
import {
  createPendingLocalAuthoritativeCorrection,
  resolveLocalAuthoritativeHeroCorrectionUpdatePlan
} from "./functions/BattleLocalAuthoritativeHeroCorrectionRuntimeRules";
import type { PendingLocalAuthoritativeCorrection } from "./objects/BattleLocalAuthoritativeHeroCorrectionObjects";

export type {
  LocalAuthoritativeHeroCorrectionContext,
  LocalAuthoritativeHeroCorrectionInput,
  LocalAuthoritativeHeroCorrectionMode,
  LocalAuthoritativeHeroCorrectionResult
} from "../../../local/movement/BattleLocalAuthoritativeHeroCorrectionRules";

export class LocalAuthoritativeHeroCorrectionController {
  private pendingCorrection: PendingLocalAuthoritativeCorrection | null = null;

  public constructor(private readonly displayPoseStore: LocalHeroDisplayPositionStore) {}

  public observeAuthoritativePosition(
    authoritativePosition: Vec2,
    context: LocalAuthoritativeHeroCorrectionContext = {}
  ): LocalAuthoritativeHeroCorrectionResult {
    const currentPosition = this.displayPoseStore.read().position;
    const correction = resolveLocalAuthoritativeHeroCorrection({
      currentPosition,
      authoritativePosition,
      context
    });

    recordLocalHeroCorrectionDiagnostics({
      currentPosition,
      authoritativePosition,
      nextPosition: correction.nextPosition,
      hardSnap: correction.hardSnap,
      applied: correction.applied,
      ignoredByDeadzone: correction.ignoredByDeadzone,
      mode: correction.mode
    });

    if (correction.hardSnap) {
      this.pendingCorrection = null;
      this.displayPoseStore.writePosition(correction.nextPosition);
      return correction;
    }

    if (correction.mode === "smooth" && correction.targetPosition) {
      this.pendingCorrection = createPendingLocalAuthoritativeCorrection(correction.targetPosition);
      return correction;
    }

    if (correction.ignoredByDeadzone || correction.mode === "none") {
      this.pendingCorrection = null;
    }

    return correction;
  }

  public update(deltaMs: number, context: LocalAuthoritativeHeroCorrectionContext = {}): void {
    if (!this.pendingCorrection) {
      return;
    }

    const currentPosition = this.displayPoseStore.read().position;
    const plan = resolveLocalAuthoritativeHeroCorrectionUpdatePlan({
      pendingCorrection: this.pendingCorrection,
      currentPosition,
      deltaMs,
      localMovementActive: context.localMovementActive === true
    });

    switch (plan.kind) {
      case "clear-pending":
        this.pendingCorrection = null;
        return;
      case "keep-pending":
        return;
      case "write-position":
        this.displayPoseStore.writePosition(plan.nextPosition);
        if (plan.clearPending) {
          this.pendingCorrection = null;
        }
        return;
    }
  }
}
