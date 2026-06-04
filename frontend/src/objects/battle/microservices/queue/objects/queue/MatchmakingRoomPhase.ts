export type MatchmakingRoomPhase = "waiting" | "active" | "finished" | "unknown";

export function isMatchmakingRoomPhase(value: unknown): value is MatchmakingRoomPhase {
  return value === "waiting" || value === "active" || value === "finished" || value === "unknown";
}

