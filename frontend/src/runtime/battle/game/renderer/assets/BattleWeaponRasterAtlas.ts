import type { WeaponKind } from "../../../../../objects/battle/microservices/combat/objects/weapon/WeaponKind";
import type { WeaponTextureRef } from "./objects/BattleWeaponRasterAtlasObjects";

export const WEAPON_RASTER_ATLAS_TEXTURE_KEY = "weapon-raster-atlas";

const WEAPON_PICKUP_ATLAS_REFS: Readonly<Record<WeaponKind, WeaponTextureRef>> = {
  Pistol: { textureKey: WEAPON_RASTER_ATLAS_TEXTURE_KEY, frameName: "pickup-pistol" },
  Gatling: { textureKey: WEAPON_RASTER_ATLAS_TEXTURE_KEY, frameName: "pickup-gatling" },
  Shotgun: { textureKey: WEAPON_RASTER_ATLAS_TEXTURE_KEY, frameName: "pickup-shotgun" },
  RocketLauncher: { textureKey: WEAPON_RASTER_ATLAS_TEXTURE_KEY, frameName: "pickup-rocket" }
};

const WEAPON_WORLD_ATLAS_REFS: Readonly<Record<WeaponKind, WeaponTextureRef>> = {
  Pistol: { textureKey: WEAPON_RASTER_ATLAS_TEXTURE_KEY, frameName: "world-pistol" },
  Gatling: { textureKey: WEAPON_RASTER_ATLAS_TEXTURE_KEY, frameName: "world-gatling" },
  Shotgun: { textureKey: WEAPON_RASTER_ATLAS_TEXTURE_KEY, frameName: "world-shotgun" },
  RocketLauncher: { textureKey: WEAPON_RASTER_ATLAS_TEXTURE_KEY, frameName: "world-rocket" }
};

export function getWeaponPickupTextureRef(weaponKind: WeaponKind): WeaponTextureRef {
  return WEAPON_PICKUP_ATLAS_REFS[weaponKind];
}

export function getWeaponWorldTextureRef(weaponKind: WeaponKind): WeaponTextureRef {
  return WEAPON_WORLD_ATLAS_REFS[weaponKind];
}
