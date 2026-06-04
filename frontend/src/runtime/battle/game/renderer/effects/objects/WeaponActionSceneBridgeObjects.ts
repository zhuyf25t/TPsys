import type { BattleProjectileState as Projectile } from "../../../../../../objects/battle/microservices/combat/objects/projectile/BattleProjectileState";
import type { BattleHeroViewState as Hero } from "../../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";
import type { WeaponActionFloatingText } from "../../../../microservices/combat/functions/BattleWeaponActionRules";

export interface WeaponActionSceneBridgeOptions {
  getPlayerHero(): Hero;
  getWeaponSwitchRemainingMs(): number;
  isPlayerMotionActive(): boolean;
  getProjectileSequence(): number;
  setProjectileSequence(next: number): void;
  addProjectile(projectile: Projectile): void;
  showFloatingText(position: Vec2, text: string, tone: WeaponActionFloatingText["tone"]): void;
  createMuzzleBurst(position: Vec2, color: number, radius: number, sparks: number, direction?: Vec2): void;
  createPulse(position: Vec2, radius: number, color: number): void;
  createImpactSpark(position: Vec2, color: number): void;
  applyRecoil(direction: Vec2, strength: number): void;
}

export interface ResolveWeaponActionSceneBridgeReadinessInput {
  player: Pick<Hero, "alive" | "preparedSkill">;
  playerMotionActive: boolean;
  weaponSwitchRemainingMs: number;
}
