import { isBattleVisitorHandle } from "../../../objects/battle/objects/core/BattleCoreRules";
import type { ActiveBattleSessionOwner } from "../objects/BattlePageState";

export function isVisitorBattleIdentity(owner: ActiveBattleSessionOwner): boolean {
  return !owner.sessionToken?.trim() || isBattleVisitorHandle(owner.handle);
}
