import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";
import type {
  ResolveWeaponActionPresentationPlanInput,
  WeaponActionPresentationPlan
} from "../objects/WeaponActionPlanPresenterObjects";

export function resolveWeaponActionPresentationPlan({
  plan,
  playerPosition,
  aimDirection
}: ResolveWeaponActionPresentationPlanInput): WeaponActionPresentationPlan {
  const floatingText = plan.floatingText
    ? {
        position: playerPosition,
        text: plan.floatingText.text,
        tone: plan.floatingText.tone
      }
    : undefined;

  if (!plan.canFire) {
    return {
      floatingText,
      projectiles: []
    };
  }

  const normalizedAimDirection = normalizeAimDirection(aimDirection);

  return {
    floatingText,
    projectiles: plan.projectiles,
    muzzleBurst: plan.muzzle
      ? {
          position: plan.muzzle.position,
          color: plan.muzzle.color,
          radius: plan.muzzle.radius,
          sparks: plan.muzzle.sparks,
          direction: normalizedAimDirection
        }
      : undefined,
    pulse: plan.muzzle?.pulse
      ? {
          position: plan.muzzle.position,
          radius: plan.muzzle.pulse.radius,
          color: plan.muzzle.pulse.color
        }
      : undefined,
    impactSpark:
      plan.muzzle?.impactSparkColor !== undefined
        ? {
            position: plan.muzzle.position,
            color: plan.muzzle.impactSparkColor
          }
        : undefined,
    recoil:
      plan.recoilStrength > 0
        ? {
            direction: normalizedAimDirection,
            strength: plan.recoilStrength
          }
        : undefined
  };
}

function normalizeAimDirection(direction: Vec2): Vec2 {
  const angle = Math.atan2(direction.y, direction.x);
  return {
    x: Math.cos(angle),
    y: Math.sin(angle)
  };
}
