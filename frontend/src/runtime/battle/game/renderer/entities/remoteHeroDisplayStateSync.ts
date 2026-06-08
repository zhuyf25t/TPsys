import type Phaser from "phaser";
import {
  createRemoteHeroInterpolationSample,
  recordRemoteHeroInterpolationSample,
  resolveInterpolatedRemoteHeroDisplayState,
  resolveRemoteHeroInterpolationDelayMs,
  resolveRemoteHeroFallbackDisplayState
} from "./functions/RemoteHeroInterpolationRules";
import type {
  RemoteHeroDisplayState,
  RemoteHeroInterpolationBuffer,
  RemoteHeroInterpolationViewState,
  ResolveRemoteHeroDisplayStateInput
} from "./objects/RemoteHeroInterpolationObjects";

export function resolveRemoteHeroDisplayState({
  scene,
  worldViews,
  view,
  hero,
  deltaMs
}: ResolveRemoteHeroDisplayStateInput): RemoteHeroDisplayState {
  const receivedAtMs = resolveRenderNowMs(scene);
  const sample = createRemoteHeroInterpolationSample(hero, receivedAtMs);

  if (!sample) {
    return resolveRemoteHeroFallbackDisplayState({
      currentPosition: { x: view.sprite.x, y: view.sprite.y },
      currentFacing: view.sprite.rotation,
      hero,
      deltaMs
    });
  }

  const buffer = getRemoteHeroInterpolationBuffer(worldViews, hero.heroId);
  recordRemoteHeroInterpolationSample(buffer, sample);

  const interpolationDelayMs = resolveRemoteHeroInterpolationDelayMs(buffer, receivedAtMs);
  const interpolatedState = resolveInterpolatedRemoteHeroDisplayState(buffer, receivedAtMs, interpolationDelayMs);
  if (interpolatedState) {
    return interpolatedState;
  }

  return {
    ...resolveRemoteHeroFallbackDisplayState({
      currentPosition: { x: view.sprite.x, y: view.sprite.y },
      currentFacing: view.sprite.rotation,
      hero,
      deltaMs,
      interpolationDelayMs
    }),
    interpolationSampleCount: buffer.samples.length
  };
}

function getRemoteHeroInterpolationBuffer(
  worldViews: RemoteHeroInterpolationViewState,
  heroId: string
): RemoteHeroInterpolationBuffer {
  const existing = worldViews.remoteHeroInterpolationBuffers.get(heroId);
  if (existing) {
    return existing;
  }

  const created: RemoteHeroInterpolationBuffer = { samples: [] };
  worldViews.remoteHeroInterpolationBuffers.set(heroId, created);
  return created;
}

function resolveRenderNowMs(scene: Phaser.Scene): number {
  const sceneNowMs = scene.time?.now;
  return Number.isFinite(sceneNowMs) ? sceneNowMs : performance.now();
}
