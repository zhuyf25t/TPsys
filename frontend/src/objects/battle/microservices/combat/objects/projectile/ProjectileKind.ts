export type ProjectileKind = "pistol-bullet" | "rocket" | "gatling-bullet" | "shotgun-pellet";

export function isProjectileKind(value: unknown): value is ProjectileKind {
  return value === "pistol-bullet" ||
    value === "rocket" ||
    value === "gatling-bullet" ||
    value === "shotgun-pellet";
}

