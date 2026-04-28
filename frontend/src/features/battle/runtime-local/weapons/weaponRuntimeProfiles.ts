import type { WeaponKind } from "../../../../domain/types";
import type { WeaponDefinition } from "../../../../game/weapons";

export type WeaponTriggerMode = "pressed" | "held";
export type WeaponAmmoMode = "magazine" | "heat";

export interface WeaponMuzzleVfxProfile {
  color: number;
  radius: number;
  sparks: number;
  impactSparkColor?: number;
  pulse?: {
    radius: number;
    color: number;
  };
}

export type WeaponProjectileSpawnPlan =
  | { mode: "single" }
  | { mode: "spread"; projectileCount: number }
  | { mode: "pellets" };

export interface WeaponRuntimeProfile {
  triggerMode: WeaponTriggerMode;
  ammoMode: WeaponAmmoMode;
  muzzleVfx: WeaponMuzzleVfxProfile;
  projectileSpawnPlan: WeaponProjectileSpawnPlan;
}

export const WEAPON_RUNTIME_PROFILES: Readonly<Record<WeaponKind, Readonly<WeaponRuntimeProfile>>> = {
  Pistol: {
    triggerMode: "pressed",
    ammoMode: "magazine",
    muzzleVfx: {
      color: 0xfff0c6,
      radius: 10,
      sparks: 3
    },
    projectileSpawnPlan: { mode: "single" }
  },
  RocketLauncher: {
    triggerMode: "pressed",
    ammoMode: "magazine",
    muzzleVfx: {
      color: 0xffb36f,
      radius: 18,
      sparks: 5,
      pulse: {
        radius: 18,
        color: 0xffb36f
      }
    },
    projectileSpawnPlan: { mode: "single" }
  },
  Gatling: {
    triggerMode: "held",
    ammoMode: "heat",
    muzzleVfx: {
      color: 0xffd86d,
      radius: 10,
      sparks: 4
    },
    projectileSpawnPlan: { mode: "spread", projectileCount: 1 }
  },
  Shotgun: {
    triggerMode: "pressed",
    ammoMode: "magazine",
    muzzleVfx: {
      color: 0xffefb7,
      radius: 20,
      sparks: 7,
      impactSparkColor: 0xffe2ba
    },
    projectileSpawnPlan: { mode: "pellets" }
  }
};

export function getWeaponRuntimeProfile(weaponKind: WeaponKind): Readonly<WeaponRuntimeProfile> {
  return WEAPON_RUNTIME_PROFILES[weaponKind];
}

export function resolveWeaponAmmoMode(
  profile: Pick<WeaponRuntimeProfile, "ammoMode">,
  definition: Pick<WeaponDefinition, "usesHeat">
): WeaponAmmoMode {
  return profile.ammoMode === "heat" || definition.usesHeat ? "heat" : "magazine";
}
