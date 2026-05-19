import type { ItemPickup, WeaponPickup } from "../../objects/types";

export const MATCH_DURATION_MS = 5 * 60 * 1000;

export type ProjectileDisplayKind = "pistol-bullet" | "rocket" | "gatling-bullet" | "shotgun-pellet" | "rocket-explosion";

export function formatMatchTime(elapsedMs: number, totalMatchMs: number = MATCH_DURATION_MS): string {
  const remainingMs = Math.max(0, totalMatchMs - elapsedMs);
  const totalSeconds = Math.floor(remainingMs / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes.toString().padStart(2, "0")}:${seconds.toString().padStart(2, "0")}`;
}

export function getWeaponDisplayLabel(weaponKind: WeaponPickup["weaponKind"]): string {
  switch (weaponKind) {
    case "Pistol":
      return "手枪";
    case "RocketLauncher":
      return "火箭炮";
    case "Gatling":
      return "加特林";
    case "Shotgun":
      return "霰弹枪";
    default:
      return weaponKind;
  }
}

export function getProjectileDisplayLabel(projectileKind: ProjectileDisplayKind): string {
  switch (projectileKind) {
    case "pistol-bullet":
      return "手枪";
    case "rocket":
      return "火箭炮";
    case "gatling-bullet":
      return "加特林";
    case "shotgun-pellet":
      return "霰弹枪";
    case "rocket-explosion":
      return "火箭炮";
    default:
      return projectileKind;
  }
}

export function getItemPickupDisplayLabel(kind: ItemPickup["kind"]): string {
  switch (kind) {
    case "Medkit":
      return "医疗包";
    default:
      return kind;
  }
}

export function getWeaponPickupTint(weaponKind: WeaponPickup["weaponKind"]): number {
  switch (weaponKind) {
    case "RocketLauncher":
      return 0xffb36f;
    case "Gatling":
      return 0xffd86d;
    case "Shotgun":
      return 0xe6ecff;
    default:
      return 0xd9f4ff;
  }
}
