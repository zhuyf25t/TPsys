import type { BattleChromeButton } from "../components/BattleChrome";
import type { BattleDrawerId, MatchPhase } from "../objects/BattlePageState";
import { QUICK_LEFT, QUICK_RIGHT } from "../objects/BattleQuickAccessItem";

export interface BattleQuickAccessButtonInput {
  matchPhase: MatchPhase;
  entryBlockNotice: string | null;
  unreadMailCount: number;
  friendRequestBadgeCount: number;
  openDrawer: (drawerId: BattleDrawerId) => void;
}

export interface BattleQuickAccessButtons {
  leftButtons: BattleChromeButton[];
  rightButtons: BattleChromeButton[];
}

export function buildBattleQuickAccessButtons({
  matchPhase,
  entryBlockNotice,
  unreadMailCount,
  friendRequestBadgeCount,
  openDrawer
}: BattleQuickAccessButtonInput): BattleQuickAccessButtons {
  const shouldShowDrawerButtons = matchPhase !== "playing" && !entryBlockNotice;
  if (!shouldShowDrawerButtons) {
    return {
      leftButtons: [],
      rightButtons: []
    };
  }

  return {
    leftButtons: QUICK_LEFT.map((item) => ({
      label: item.label,
      iconKey: item.iconKey,
      onClick: () => openDrawer(item.id)
    })),
    rightButtons: QUICK_RIGHT.map((item) => ({
      label: item.label,
      iconKey: item.iconKey,
      onClick: () => openDrawer(item.id),
      badgeCount:
        item.id === "mails"
          ? unreadMailCount
          : item.id === "social"
            ? friendRequestBadgeCount
            : undefined
    }))
  };
}
