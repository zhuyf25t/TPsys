import type { ResolveWeaponActionSceneBridgeReadinessInput } from "../objects/WeaponActionSceneBridgeObjects";

export function canResolveWeaponActionSceneBridgeFire({
  player,
  playerMotionActive,
  weaponSwitchRemainingMs
}: ResolveWeaponActionSceneBridgeReadinessInput): boolean {
  return player.alive && player.preparedSkill === null && !playerMotionActive && weaponSwitchRemainingMs <= 0;
}
