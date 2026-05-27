import type { Hero, PlayerCommand, Projectile, Vec2 } from "../../../../../objects/battle/types";
import { WEAPON_DEFINITIONS } from "../../weapons";
import { presentWeaponActionPlan } from "./weaponActionPlanPresenter";
import { getCurrentWeapon, resolveWeaponAction } from "../../../local/weapons/weaponActionController";

export interface WeaponActionSceneBridgeOptions {
  getPlayerHero(): Hero;
  getWeaponSwitchRemainingMs(): number;
  isPlayerMotionActive(): boolean;
  getProjectileSequence(): number;
  setProjectileSequence(next: number): void;
  addProjectile(projectile: Projectile): void;
  showFloatingText(position: Vec2, text: string, tone: "neutral" | "warning" | "error" | "success"): void;
  createMuzzleBurst(position: Vec2, color: number, radius: number, sparks: number, direction?: Vec2): void;
  createPulse(position: Vec2, radius: number, color: number): void;
  createImpactSpark(position: Vec2, color: number): void;
  applyRecoil(direction: Vec2, strength: number): void;
}

export class WeaponActionSceneBridge {
  public constructor(private readonly options: WeaponActionSceneBridgeOptions) {}

  public handleWeaponFireAction(command: PlayerCommand): void {
    const player = this.options.getPlayerHero();

    if (!player.alive || player.preparedSkill !== null || this.options.isPlayerMotionActive() || this.options.getWeaponSwitchRemainingMs() > 0) {
      return;
    }

    const weapon = getCurrentWeapon(player);
    const weaponDefinition = WEAPON_DEFINITIONS[weapon.weaponKind];
    const plan = resolveWeaponAction({
      player,
      weapon,
      weaponDefinition,
      command,
      weaponSwitchRemainingMs: this.options.getWeaponSwitchRemainingMs(),
      playerMotionActive: this.options.isPlayerMotionActive(),
      projectileSequence: this.options.getProjectileSequence()
    });

    this.options.setProjectileSequence(plan.nextProjectileSequence);
    presentWeaponActionPlan({
      plan,
      playerPosition: player.position,
      aimDirection: command.aim,
      callbacks: {
        showFloatingText: (position, text, tone) => this.options.showFloatingText(position, text, tone),
        addProjectile: (projectile) => this.options.addProjectile(projectile),
        createMuzzleBurst: (position, color, radius, sparks, direction) =>
          this.options.createMuzzleBurst(position, color, radius, sparks, direction),
        createPulse: (position, radius, color) => this.options.createPulse(position, radius, color),
        createImpactSpark: (position, color) => this.options.createImpactSpark(position, color),
        applyRecoil: (direction, strength) => this.options.applyRecoil(direction, strength)
      }
    });
  }
}
