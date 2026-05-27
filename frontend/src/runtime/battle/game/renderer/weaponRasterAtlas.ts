import type { WeaponKind } from "../../../../objects/battle/types";

export interface WeaponTextureRef {
  textureKey: string;
  frameName?: string;
}

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

/** 中文名：获取武器拾取贴图引用（getWeaponPickupTextureRef）。游戏职责：返回预烘焙 PNG atlas 中的拾取物 frame，避免运行时直接解析 SVG。 */
export function getWeaponPickupTextureRef(weaponKind: WeaponKind): WeaponTextureRef {
  return WEAPON_PICKUP_ATLAS_REFS[weaponKind];
}

/** 中文名：获取角色持枪贴图引用（getWeaponWorldTextureRef）。游戏职责：返回预烘焙 PNG atlas 中的 world 持枪 frame，供角色渲染层复用。 */
export function getWeaponWorldTextureRef(weaponKind: WeaponKind): WeaponTextureRef {
  return WEAPON_WORLD_ATLAS_REFS[weaponKind];
}
