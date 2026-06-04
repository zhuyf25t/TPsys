import type { BattleHeroViewState as Hero } from "../../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";
import type { SceneGeometryObstacleBounds } from "../../../../local/geometry/sceneGeometry";
import type { SkillFeedbackIntent } from "../../../../microservices/abilities/functions/BattleSkillRuntimeProfiles";
import type { BattleWeaponMuzzleFeedbackTracer } from "../../../../microservices/combat/functions/BattleWeaponFeedbackRules";
import type { LocalHeroDisplayPoseReader } from "../../entities/BattleLocalHeroDisplay";

export type LocalProjectileTracerFeedback = BattleWeaponMuzzleFeedbackTracer;

export interface SharedAuthoritativeLocalFeedbackSceneBridgeOptions {
  getPlayerHero(): Hero;
  localHeroDisplay: LocalHeroDisplayPoseReader;
  getWorldSize(): Vec2;
  getObstacleBounds(): readonly SceneGeometryObstacleBounds[];
  getNowMs(): number;
  createMuzzleBurst(position: Vec2, color: number, radius: number, sparks: number, direction?: Vec2): void;
  createPulse(position: Vec2, radius: number, color: number): void;
  createProjectileTracer(options: LocalProjectileTracerFeedback): void;
  createBlinkSkillTargetFeedback(position: Vec2, intent: SkillFeedbackIntent, direction?: Vec2): void;
  createFreezeSkillTargetFeedback(position: Vec2, intent: SkillFeedbackIntent): void;
  createDashSkillFeedback(position: Vec2, direction: Vec2): void;
  createSkillRejectionFeedback(position: Vec2, radius: number): void;
  showFloatingText(position: Vec2, text: string, tone: "neutral" | "success" | "warning" | "error"): void;
}
