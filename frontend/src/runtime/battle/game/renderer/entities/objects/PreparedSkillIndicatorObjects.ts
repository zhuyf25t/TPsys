import type Phaser from "phaser";
import type {
  BattleHeroViewState as Hero,
  BattlePreparedSkill as PreparedSkill
} from "../../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { BattleGameSnapshot as GameSnapshot } from "../../../../../../objects/battle/microservices/session/objects/state/BattleGameSnapshot";
import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";
import type { PreparedTargetSkillKind } from "../../../../microservices/abilities/functions/BattleSkillRuntimeProfiles";

export interface PreparedSkillIndicatorViewState {
  rangeIndicator: Phaser.GameObjects.Arc;
  targetIndicator: Phaser.GameObjects.Arc;
}

export interface PreparedSkillIndicatorDisplayOverride {
  position: Vec2;
  facing: number;
}

export interface PreparedSkillIndicatorViewSyncContext {
  snapshot: GameSnapshot;
  worldViews: PreparedSkillIndicatorViewState;
  pointerWorld: Vec2;
  isBlinkTargetValid: (player: Hero, target: Vec2) => boolean;
  isPreparedTargetValid?: (player: Hero, preparedSkill: Exclude<PreparedSkill, null>, target: Vec2) => boolean;
  sharedAuthoritativeRuntime?: boolean;
  localHeroDisplayOverride?: PreparedSkillIndicatorDisplayOverride;
}

export interface PreparedSkillIndicatorTargetValidityInput {
  player: Hero;
  preparedSkill: PreparedTargetSkillKind;
  target: Vec2;
  displayPosition: Vec2;
  localHeroDisplayOverride?: PreparedSkillIndicatorDisplayOverride;
  isBlinkTargetValid: PreparedSkillIndicatorViewSyncContext["isBlinkTargetValid"];
  isPreparedTargetValid?: PreparedSkillIndicatorViewSyncContext["isPreparedTargetValid"];
}

export interface PreparedSkillIndicatorCirclePlan {
  position: Vec2;
  radius: number;
  color: number;
  fillAlpha: number;
  strokeWidth: number;
  strokeAlpha: number;
}

export type PreparedSkillIndicatorPlan =
  | { visible: false }
  | {
      visible: true;
      range: PreparedSkillIndicatorCirclePlan;
      target: PreparedSkillIndicatorCirclePlan;
    };

export type PreparedSkillIndicatorCircleVisualMutationPlan =
  | {
      visible: false;
    }
  | ({
      visible: true;
    } & PreparedSkillIndicatorCirclePlan);

export interface PreparedSkillIndicatorVisualMutationPlan {
  rangeIndicator: PreparedSkillIndicatorCircleVisualMutationPlan;
  targetIndicator: PreparedSkillIndicatorCircleVisualMutationPlan;
}
