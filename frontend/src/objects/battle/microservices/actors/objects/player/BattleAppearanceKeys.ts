export type BattleAvatarKey = string;
export type BattleSkinKey = string;

export function battleAvatarKeyFromWire(value: string): BattleAvatarKey | null {
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : null;
}

export function battleSkinKeyFromWire(value: string): BattleSkinKey | null {
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : null;
}

