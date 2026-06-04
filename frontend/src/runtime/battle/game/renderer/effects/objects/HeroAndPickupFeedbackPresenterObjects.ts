import type { BattleGameSnapshot as GameSnapshot } from "../../../../../../objects/battle/microservices/session/objects/state/BattleGameSnapshot";
import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";
import type {
  BattleHeroFeedbackPlan,
  BattleHeroFeedbackState,
  BattleHeroFeedbackTone
} from "../../../../microservices/actors/functions/BattleHeroFeedbackRules";
import type {
  BattlePickupFeedbackPlan,
  BattlePickupFeedbackState
} from "../../../../microservices/abilities/functions/BattlePickupFeedbackRules";

export interface HeroFeedbackPresentationCallbacks {
  flashHero(heroId: string, color: number): void;
  showFloatingText(position: Vec2, text: string, tone: BattleHeroFeedbackTone): void;
  createPulse(position: Vec2, radius: number, color: number): void;
  createImpactSpark(position: Vec2, color: number): void;
  createHitConfirm(position: Vec2, color: number): void;
  shakeCamera(duration: number, intensity: number): void;
}

export interface PickupFeedbackPresentationCallbacks {
  showFloatingText(position: Vec2, text: string, tone: BattleHeroFeedbackTone): void;
  createPulse(position: Vec2, radius: number, color: number): void;
}

export interface HeroFeedbackPresentationOptions extends HeroFeedbackPresentationCallbacks {
  snapshot: GameSnapshot;
  previousHeroStates: ReadonlyMap<string, BattleHeroFeedbackState>;
  sharedAuthoritativeRuntime: boolean;
  getHeroDisplayPosition(heroId: string): Vec2 | null;
}

export interface AuthoritativePickupFeedbackPresentationOptions extends PickupFeedbackPresentationCallbacks {
  snapshot: GameSnapshot;
  previousWeaponPickupStates: ReadonlyMap<string, BattlePickupFeedbackState>;
  previousItemPickupStates: ReadonlyMap<string, BattlePickupFeedbackState>;
}

export interface FloatingTextFeedbackPresentationAction {
  kind: "floating-text";
  position: Vec2;
  text: string;
  tone: BattleHeroFeedbackTone;
}

export interface PulseFeedbackPresentationAction {
  kind: "pulse";
  position: Vec2;
  radius: number;
  color: number;
}

export interface FlashHeroFeedbackPresentationAction {
  kind: "flash-hero";
  heroId: string;
  color: number;
}

export interface ImpactSparkFeedbackPresentationAction {
  kind: "impact-spark";
  position: Vec2;
  color: number;
}

export interface HitConfirmFeedbackPresentationAction {
  kind: "hit-confirm";
  position: Vec2;
  color: number;
}

export interface CameraShakeFeedbackPresentationAction {
  kind: "camera-shake";
  durationMs: number;
  intensity: number;
}

export type HeroFeedbackPresentationAction =
  | FloatingTextFeedbackPresentationAction
  | PulseFeedbackPresentationAction
  | FlashHeroFeedbackPresentationAction
  | ImpactSparkFeedbackPresentationAction
  | HitConfirmFeedbackPresentationAction
  | CameraShakeFeedbackPresentationAction;

export type PickupFeedbackPresentationAction =
  | FloatingTextFeedbackPresentationAction
  | PulseFeedbackPresentationAction;

export interface ResolveHeroFeedbackPresentationActionsInput {
  plan: BattleHeroFeedbackPlan;
}

export interface ResolvePickupFeedbackPresentationActionsInput {
  plan: BattlePickupFeedbackPlan;
}
