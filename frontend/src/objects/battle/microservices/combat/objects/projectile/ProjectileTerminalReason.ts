export type ProjectileTerminalReason = "hit" | "ttl" | "obstacle" | "world";

export function isProjectileTerminalReason(value: unknown): value is ProjectileTerminalReason {
  return value === "hit" || value === "ttl" || value === "obstacle" || value === "world";
}

