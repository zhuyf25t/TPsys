import type { WeaponKind } from "../../../../../objects/battle/microservices/combat/objects/weapon/WeaponKind";
import {
  resolveWeaponAmmoMode,
  type WeaponAmmoMode,
  type WeaponTriggerMode
} from "./BattleWeaponFireDecisionRules";

export { resolveWeaponAmmoMode, type WeaponAmmoMode, type WeaponTriggerMode };

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

export interface WeaponRuntimeProfile {
  triggerMode: WeaponTriggerMode;
  ammoMode: WeaponAmmoMode;
  muzzleVfx: WeaponMuzzleVfxProfile;
}

export const WEAPON_RUNTIME_PROFILES: Readonly<Record<WeaponKind, Readonly<WeaponRuntimeProfile>>> = {
  Pistol: {
    triggerMode: "pressed",
    ammoMode: "magazine",
    muzzleVfx: {
      color: 0xfff0c6,
      radius: 10,
      sparks: 3
    }
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
    }
  },
  Gatling: {
    triggerMode: "held",
    ammoMode: "heat",
    muzzleVfx: {
      color: 0xffd86d,
      radius: 10,
      sparks: 4
    }
  },
  Shotgun: {
    triggerMode: "pressed",
    ammoMode: "magazine",
    muzzleVfx: {
      color: 0xffefb7,
      radius: 20,
      sparks: 7,
      impactSparkColor: 0xffe2ba
    }
  }
};

export function getWeaponRuntimeProfile(weaponKind: WeaponKind): Readonly<WeaponRuntimeProfile> {
  return WEAPON_RUNTIME_PROFILES[weaponKind];
}
