import type { BattleDrawerId } from "./BattlePageState";

export interface BattleQuickAccessItem<TIconKey extends string> {
  id: BattleDrawerId;
  label: string;
  iconKey: TIconKey;
}

export const QUICK_LEFT: Array<BattleQuickAccessItem<"replay" | "discussion" | "ranking">> = [
  { id: "replay", label: "鍥炴斁", iconKey: "replay" },
  { id: "discussion", label: "璁哄潧", iconKey: "discussion" },
  { id: "rating", label: "鎺掕", iconKey: "ranking" }
];

export const QUICK_RIGHT: Array<BattleQuickAccessItem<"mails" | "social">> = [
  { id: "mails", label: "閭欢", iconKey: "mails" },
  { id: "social", label: "濂藉弸", iconKey: "social" }
];
