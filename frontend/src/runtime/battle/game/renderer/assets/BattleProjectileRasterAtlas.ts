import type { ProjectileKind } from "../../../../../objects/battle/microservices/combat/objects/projectile/ProjectileKind";
import type { ProjectileTextureRef } from "./objects/BattleProjectileRasterAtlasObjects";

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

export function getProjectileTextureRef(projectileKind: ProjectileKind): ProjectileTextureRef {
  return PROJECTILE_TEXTURE_REFS[projectileKind];
}
