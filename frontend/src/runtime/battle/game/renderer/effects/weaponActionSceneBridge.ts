import type { BattlePlayerCommand as PlayerCommand } from "../../../../../objects/battle/microservices/session/objects/command/BattlePlayerCommand";
import { WEAPON_DEFINITIONS } from "../../../../../objects/battle/microservices/combat/objects/combat/BattleCombatRuleDefinitions";
import { getCurrentWeapon } from "../../../microservices/combat/functions/BattleWeaponInventoryRules";
import { presentWeaponActionPlan } from "./weaponActionPlanPresenter";
import { resolveWeaponAction } from "../../../microservices/combat/functions/BattleWeaponActionRules";
import { canResolveWeaponActionSceneBridgeFire } from "./functions/WeaponActionSceneBridgeRules";
import type { WeaponActionSceneBridgeOptions } from "./objects/WeaponActionSceneBridgeObjects";

export class WeaponActionSceneBridge {
  public constructor(private readonly options: WeaponActionSceneBridgeOptions) {}

  public handleWeaponFireAction(command: PlayerCommand): void {
    const player = this.options.getPlayerHero();
    const playerMotionActive = this.options.isPlayerMotionActive();
    const weaponSwitchRemainingMs = this.options.getWeaponSwitchRemainingMs();

    if (
      !canResolveWeaponActionSceneBridgeFire({
        player,
        playerMotionActive,
        weaponSwitchRemainingMs
      })
    ) {
      return;
    }

    const weapon = getCurrentWeapon(player);
    const weaponDefinition = WEAPON_DEFINITIONS[weapon.weaponKind];
    const plan = resolveWeaponAction({
      player,
      weapon,
      weaponDefinition,
      command,
      weaponSwitchRemainingMs,
      playerMotionActive,
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
