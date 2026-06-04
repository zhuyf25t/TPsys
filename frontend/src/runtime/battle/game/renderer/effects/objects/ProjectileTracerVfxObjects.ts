import type Phaser from "phaser";
import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";
import type { ProjectileTracerFeedbackOptions } from "../../../../microservices/combat/functions/BattleProjectileFeedbackRules";

export type ProjectileTracerOptions = ProjectileTracerFeedbackOptions;

export interface ProjectileTracerVfxRendererDependencies {
  scene: Phaser.Scene;
  trackTransient: <TObject extends Phaser.GameObjects.GameObject>(object: TObject) => TObject;
  destroyTransient: (object: Phaser.GameObjects.GameObject) => void;
}

export type ProjectileTracerGlintOffsetDirection = -1 | 1;

export interface ResolveProjectileTracerVfxPlanInput {
  options: ProjectileTracerOptions;
  glintOffsetDirection: ProjectileTracerGlintOffsetDirection;
}

export interface ProjectileTracerRectanglePlan {
  position: Vec2;
  width: number;
  height: number;
  color: number;
  alpha: number;
  origin: Vec2;
  rotation: number;
  depth: number;
}

export interface ProjectileTracerCirclePlan {
  position: Vec2;
  radius: number;
  color: number;
  alpha: number;
  depth: number;
}

export interface ProjectileTracerTweenPlan {
  alpha: number;
  scaleX?: number;
  scaleY?: number;
  scale?: number;
  durationMs: number;
  ease: string;
}

export interface ProjectileTracerShapePlan<TShape> {
  shape: TShape;
  tween: ProjectileTracerTweenPlan;
}

export interface ProjectileTracerVfxPlan {
  underglow?: ProjectileTracerShapePlan<ProjectileTracerRectanglePlan>;
  tracer: ProjectileTracerShapePlan<ProjectileTracerRectanglePlan>;
  core?: ProjectileTracerShapePlan<ProjectileTracerRectanglePlan>;
  ghost?: ProjectileTracerShapePlan<ProjectileTracerCirclePlan>;
  glint?: ProjectileTracerShapePlan<ProjectileTracerRectanglePlan>;
}
