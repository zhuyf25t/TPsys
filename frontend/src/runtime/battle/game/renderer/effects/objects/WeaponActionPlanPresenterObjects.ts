import type { BattleProjectileState as Projectile } from "../../../../../../objects/battle/microservices/combat/objects/projectile/BattleProjectileState";
import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";
import type {
  WeaponActionFloatingText,
  WeaponActionPlan
} from "../../../../microservices/combat/functions/BattleWeaponActionRules";

export interface WeaponActionPlanPresenterCallbacks {
  showFloatingText(position: Vec2, text: string, tone: WeaponActionFloatingText["tone"]): void;
  addProjectile(projectile: Projectile): void;
  createMuzzleBurst(position: Vec2, color: number, radius: number, sparks: number, direction?: Vec2): void;
  createPulse(position: Vec2, radius: number, color: number): void;
  createImpactSpark(position: Vec2, color: number): void;
  applyRecoil(direction: Vec2, strength: number): void;
}

export interface PresentWeaponActionPlanInput {
  plan: WeaponActionPlan;
  playerPosition: Vec2;
  aimDirection: Vec2;
  callbacks: WeaponActionPlanPresenterCallbacks;
}

export interface ResolveWeaponActionPresentationPlanInput {
  plan: WeaponActionPlan;
  playerPosition: Vec2;
  aimDirection: Vec2;
}

export interface WeaponActionFloatingTextPresentationPlan {
  position: Vec2;
  text: string;
  tone: WeaponActionFloatingText["tone"];
}

export interface WeaponActionMuzzleBurstPresentationPlan {
  position: Vec2;
  color: number;
  radius: number;
  sparks: number;
  direction: Vec2;
}

export interface WeaponActionPulsePresentationPlan {
  position: Vec2;
  radius: number;
  color: number;
}

export interface WeaponActionImpactSparkPresentationPlan {
  position: Vec2;
  color: number;
}

export interface WeaponActionRecoilPresentationPlan {
  direction: Vec2;
  strength: number;
}

export interface WeaponActionPresentationPlan {
  floatingText?: WeaponActionFloatingTextPresentationPlan;
  projectiles: readonly Projectile[];
  muzzleBurst?: WeaponActionMuzzleBurstPresentationPlan;
  pulse?: WeaponActionPulsePresentationPlan;
  impactSpark?: WeaponActionImpactSparkPresentationPlan;
  recoil?: WeaponActionRecoilPresentationPlan;
}
