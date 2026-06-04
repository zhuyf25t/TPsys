import { resolveWeaponActionPresentationPlan } from "./functions/WeaponActionPlanPresentationRules";
import type { PresentWeaponActionPlanInput } from "./objects/WeaponActionPlanPresenterObjects";

/** 中文名：present武器actionplan（presentWeaponActionPlan）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function presentWeaponActionPlan(input: PresentWeaponActionPlanInput): void {
  const { plan, playerPosition, aimDirection, callbacks } = input;
  const presentationPlan = resolveWeaponActionPresentationPlan({ plan, playerPosition, aimDirection });

  if (presentationPlan.floatingText) {
    callbacks.showFloatingText(
      presentationPlan.floatingText.position,
      presentationPlan.floatingText.text,
      presentationPlan.floatingText.tone
    );
  }

  presentationPlan.projectiles.forEach((projectile) => {
    callbacks.addProjectile(projectile);
  });

  if (presentationPlan.muzzleBurst) {
    callbacks.createMuzzleBurst(
      presentationPlan.muzzleBurst.position,
      presentationPlan.muzzleBurst.color,
      presentationPlan.muzzleBurst.radius,
      presentationPlan.muzzleBurst.sparks,
      presentationPlan.muzzleBurst.direction
    );
  }

  if (presentationPlan.pulse) {
    callbacks.createPulse(presentationPlan.pulse.position, presentationPlan.pulse.radius, presentationPlan.pulse.color);
  }

  if (presentationPlan.impactSpark) {
    callbacks.createImpactSpark(presentationPlan.impactSpark.position, presentationPlan.impactSpark.color);
  }

  if (presentationPlan.recoil) {
    callbacks.applyRecoil(presentationPlan.recoil.direction, presentationPlan.recoil.strength);
  }
}
