import type { ProjectileKind } from "../../objects/types";

export interface ProjectileTextureRef {
  textureKey: string;
  frameName: string;
  scale: number;
  tint: number;
}

export const PROJECTILE_RASTER_ATLAS_TEXTURE_KEY = "projectile-raster-atlas";

const PROJECTILE_TEXTURE_REFS: Readonly<Record<ProjectileKind, ProjectileTextureRef>> = {
  "pistol-bullet": {
    textureKey: PROJECTILE_RASTER_ATLAS_TEXTURE_KEY,
    frameName: "pistol-bullet",
    scale: 0.35,
    tint: 0xdaf3ff
  },
  rocket: {
    textureKey: PROJECTILE_RASTER_ATLAS_TEXTURE_KEY,
    frameName: "rocket",
    scale: 0.45,
    tint: 0xffb36f
  },
  "gatling-bullet": {
    textureKey: PROJECTILE_RASTER_ATLAS_TEXTURE_KEY,
    frameName: "gatling-bullet",
    scale: 0.35,
    tint: 0xffd86d
  },
  "shotgun-pellet": {
    textureKey: PROJECTILE_RASTER_ATLAS_TEXTURE_KEY,
    frameName: "shotgun-pellet",
    scale: 0.28,
    tint: 0xfff7cf
  }
};

/** 中文名：获取投射物贴图引用（getProjectileTextureRef）。游戏职责：返回预烘焙 PNG atlas 中的子弹/火箭 frame，避免每个投射物直接使用 SVG texture。 */
export function getProjectileTextureRef(projectileKind: ProjectileKind): ProjectileTextureRef {
  return PROJECTILE_TEXTURE_REFS[projectileKind];
}
