export type BattleCommandReason = "battle_finished" | "battle_inactive" | "player_dead" | "stale_command";

export function isBattleCommandReason(value: unknown): value is BattleCommandReason {
  return (
    value === "battle_finished" ||
    value === "battle_inactive" ||
    value === "player_dead" ||
    value === "stale_command"
  );
}
