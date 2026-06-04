import type Phaser from "phaser";
import {
  createProjectileInterpolationSample,
  recordProjectileInterpolationSample,
  resolveInterpolatedProjectileDisplayState,
  resolveProjectileFallbackDisplayState
} from "./functions/ProjectileInterpolationRules";
import type {
  ProjectileDisplayState,
  ProjectileInterpolationBuffer,
  ProjectileViewState,
  ResolveProjectileDisplayStateInput
} from "./objects/ProjectileViewObjects";

export function resolveProjectileViewDisplayState({
  scene,
  worldViews,
  view,
  projectile,
  deltaMs,
  useAuthoritativeInterpolation
}: ResolveProjectileDisplayStateInput): ProjectileDisplayState {
  if (!useAuthoritativeInterpolation) {
    return {
      position: projectile.position,
      facing: projectile.facing
    };
  }

  const receivedAtMs = resolveRenderNowMs(scene);
  const sample = createProjectileInterpolationSample(projectile, receivedAtMs);

  if (!sample) {
    return resolveProjectileFallbackDisplayState({
      currentPosition: { x: view.sprite.x, y: view.sprite.y },
      currentFacing: view.sprite.rotation,
      projectile,
      deltaMs
    });
  }

  const buffer = getProjectileInterpolationBuffer(worldViews, projectile.projectileId);
  recordProjectileInterpolationSample(buffer, sample);

  return (
    resolveInterpolatedProjectileDisplayState(buffer, receivedAtMs) ??
    resolveProjectileFallbackDisplayState({
      currentPosition: { x: view.sprite.x, y: view.sprite.y },
      currentFacing: view.sprite.rotation,
      projectile,
      deltaMs
    })
  );
}

function getProjectileInterpolationBuffer(worldViews: ProjectileViewState, projectileId: string): ProjectileInterpolationBuffer {
  const existing = worldViews.projectileInterpolationBuffers.get(projectileId);
  if (existing) {
    return existing;
  }

  const created: ProjectileInterpolationBuffer = { samples: [] };
  worldViews.projectileInterpolationBuffers.set(projectileId, created);
  return created;
}

function resolveRenderNowMs(scene: Phaser.Scene): number {
  const sceneNowMs = scene.time?.now;
  return Number.isFinite(sceneNowMs) ? sceneNowMs : performance.now();
}
