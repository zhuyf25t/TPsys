import type { Projectile, Vec2 } from "../../../../domain/types";
import type { WeaponActionFloatingText, WeaponActionMuzzleVfx, WeaponActionPlan } from "../../runtime-local/weapons/weaponActionController";

export interface WeaponActionPlanPresenterCallbacks {
  showFloatingText(position: Vec2, text: string, tone: WeaponActionFloatingText["tone"]): void;
  addProjectile(projectile: Projectile): void;
  createMuzzleBurst(position: Vec2, color: number, radius: number, sparks: number): void;
  createPulse(position: Vec2, radius: number, color: number): void;
  createImpactSpark(position: Vec2, color: number): void;
  applyRecoil(direction: Vec2, strength: number): void;
}

export function presentWeaponActionPlan(input: {
  plan: WeaponActionPlan;
  playerPosition: Vec2;
  aimDirection: Vec2;
  callbacks: WeaponActionPlanPresenterCallbacks;
}): void {
  const { plan, playerPosition, aimDirection, callbacks } = input;

  if (plan.floatingText) {
    callbacks.showFloatingText(playerPosition, plan.floatingText.text, plan.floatingText.tone);
  }

  if (!plan.canFire) {
    return;
  }

  plan.projectiles.forEach((projectile) => {
    callbacks.addProjectile(projectile);
  });

  if (plan.muzzle) {
    applyMuzzleVfx(plan.muzzle, callbacks);
  }

  if (plan.recoilStrength > 0) {
    callbacks.applyRecoil(normalizeAimDirection(aimDirection), plan.recoilStrength);
  }
}

function applyMuzzleVfx(
  muzzle: WeaponActionMuzzleVfx,
  callbacks: WeaponActionPlanPresenterCallbacks
): void {
  callbacks.createMuzzleBurst(muzzle.position, muzzle.color, muzzle.radius, muzzle.sparks);
  if (muzzle.pulse) {
    callbacks.createPulse(muzzle.position, muzzle.pulse.radius, muzzle.pulse.color);
  }
  if (muzzle.impactSparkColor !== undefined) {
    callbacks.createImpactSpark(muzzle.position, muzzle.impactSparkColor);
  }
}

function normalizeAimDirection(direction: Vec2): Vec2 {
  const angle = Math.atan2(direction.y, direction.x);
  return {
    x: Math.cos(angle),
    y: Math.sin(angle)
  };
}
