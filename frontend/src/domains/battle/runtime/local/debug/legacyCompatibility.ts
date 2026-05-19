export type LegacyBattleMethodName =
  | "legacyHandleJump"
  | "legacyHandleWeaponSwitch"
  | "legacyHandleWeaponFire";

export interface LegacyCompatibilityEntry {
  methodName: LegacyBattleMethodName;
  purpose: string;
  note: string;
}

export const LEGACY_BATTLE_COMPATIBILITY: readonly LegacyCompatibilityEntry[] = [
  {
    methodName: "legacyHandleJump",
    purpose: "Compatibility path for the old jump flow.",
    note: "Keep in GameScene until a later retirement ticket removes it."
  },
  {
    methodName: "legacyHandleWeaponSwitch",
    purpose: "Compatibility path for the old weapon switch flow.",
    note: "Keep behavior intact and isolate the method explicitly."
  },
  {
    methodName: "legacyHandleWeaponFire",
    purpose: "Compatibility path for the old weapon fire flow.",
    note: "Keep behavior intact and isolate the method explicitly."
  }
] as const;
