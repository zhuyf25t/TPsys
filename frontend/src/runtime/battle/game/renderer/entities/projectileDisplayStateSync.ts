import type Phaser from "phaser";
import {
  createProjectileInterpolationSample,
  recordProjectileInterpolationSample,
  resolveInterpolatedProjectileDisplayState,
  resolveProjectileInterpolationDelayMs,
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
      facing: projectile.facing,
      interpolationSource: "snapshot",
      interpolationSampleCount: 0,
      interpolationDelayMs: 0
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

  const interpolationDelayMs = resolveProjectileInterpolationDelayMs(buffer, receivedAtMs);
  const interpolatedState = resolveInterpolatedProjectileDisplayState(buffer, receivedAtMs, interpolationDelayMs);
  if (interpolatedState) {
    return interpolatedState;
  }

  return {
    ...resolveProjectileFallbackDisplayState({
      currentPosition: { x: view.sprite.x, y: view.sprite.y },
      currentFacing: view.sprite.rotation,
      projectile,
      deltaMs,
      interpolationDelayMs
    }),
    interpolationSampleCount: buffer.samples.length
  };
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
