export type WeaponKind = "Pistol" | "RocketLauncher" | "Gatling" | "Shotgun";

export function isWeaponKind(value: unknown): value is WeaponKind {
  return value === "Pistol" ||
    value === "RocketLauncher" ||
    value === "Gatling" ||
    value === "Shotgun";
}

