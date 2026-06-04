export type PickupKind = "Medkit" | "Weapon";
export type ItemPickupKind = "Medkit";

export function isPickupKind(value: unknown): value is PickupKind {
  return value === "Medkit" || value === "Weapon";
}

