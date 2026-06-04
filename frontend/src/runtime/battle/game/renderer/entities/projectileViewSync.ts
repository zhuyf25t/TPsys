import type Phaser from "phaser";
import { isProjectileInsideCullBounds } from "./functions/ProjectilePresentationRules";
import type {
  ProjectileViewSyncContext,
  ProjectileCullWorldView
} from "./objects/ProjectileViewObjects";
import { resolveProjectileViewDisplayState } from "./projectileDisplayStateSync";
import {
  acquireProjectileView,
  releaseProjectileView,
  syncProjectileViewVisuals
} from "./projectileViewLifecycle";

export type {
  ProjectileCullWorldView,
  ProjectileDisplayState,
  ProjectileInterpolationBuffer,
  ProjectileInterpolationSample,
  ProjectileViewState,
  ProjectileViewSyncContext,
  ProjectileView
} from "./objects/ProjectileViewObjects";

export function syncProjectileViews({
  scene,
  snapshot,
  worldViews,
  deltaMs,
  sharedAuthoritativeRuntime = false
}: ProjectileViewSyncContext): void {
  const liveIds = worldViews.scratchLiveProjectileIds;
  liveIds.clear();

  if (!sharedAuthoritativeRuntime) {
    worldViews.projectileInterpolationBuffers.clear();
  }

  snapshot.projectiles.forEach((projectile) => {
    liveIds.add(projectile.projectileId);

    if (!isProjectileInsideCullBounds(projectile, resolveProjectileCullWorldView(scene))) {
      const existing = worldViews.projectileViews.get(projectile.projectileId);
      if (existing) {
        releaseProjectileView(worldViews, existing);
        worldViews.projectileViews.delete(projectile.projectileId);
      }
      worldViews.projectileInterpolationBuffers.delete(projectile.projectileId);
      return;
    }

    const existing = worldViews.projectileViews.get(projectile.projectileId) ?? acquireProjectileView(scene, worldViews, projectile);
    worldViews.projectileViews.set(projectile.projectileId, existing);

    const isLocalPlayerProjectile = projectile.ownerHeroId === snapshot.playerHeroId;
    const useAuthoritativeInterpolation = sharedAuthoritativeRuntime && !isLocalPlayerProjectile;

    if (!useAuthoritativeInterpolation) {
      worldViews.projectileInterpolationBuffers.delete(projectile.projectileId);
    }

    const displayState = resolveProjectileViewDisplayState({
      scene,
      worldViews,
      view: existing,
      projectile,
      deltaMs,
      useAuthoritativeInterpolation
    });
    syncProjectileViewVisuals(existing, projectile, displayState.position, displayState.facing);
  });

  for (const [projectileId, view] of worldViews.projectileViews.entries()) {
    if (liveIds.has(projectileId)) {
      continue;
    }

    releaseProjectileView(worldViews, view);
    worldViews.projectileViews.delete(projectileId);
    worldViews.projectileInterpolationBuffers.delete(projectileId);
  }

  for (const projectileId of worldViews.projectileInterpolationBuffers.keys()) {
    if (!liveIds.has(projectileId)) {
      worldViews.projectileInterpolationBuffers.delete(projectileId);
    }
  }
}

function resolveProjectileCullWorldView(scene: Phaser.Scene): ProjectileCullWorldView {
  const camera = scene.cameras.main;
  return {
    x: Number.isFinite(camera.scrollX) ? camera.scrollX : camera.worldView.x,
    y: Number.isFinite(camera.scrollY) ? camera.scrollY : camera.worldView.y,
    width: camera.width > 0 ? camera.width : camera.worldView.width,
    height: camera.height > 0 ? camera.height : camera.worldView.height
  };
}
