import type { CleanupRemoteHeroInterpolationBuffersInput } from "./objects/RemoteHeroInterpolationObjects";

export function cleanupRemoteHeroInterpolationBuffers({
  snapshot,
  worldViews,
  sharedAuthoritativeRuntime,
  remoteAuthoritativeHeroIds
}: CleanupRemoteHeroInterpolationBuffersInput): void {
  if (!sharedAuthoritativeRuntime) {
    worldViews.remoteHeroInterpolationBuffers.clear();
    return;
  }

  const activeRemoteHeroIds = worldViews.scratchActiveRemoteHeroIds;
  activeRemoteHeroIds.clear();
  snapshot.heroes.forEach((hero) => {
    if (
      hero.alive &&
      hero.heroId !== snapshot.playerHeroId &&
      remoteAuthoritativeHeroIds.has(hero.heroId) &&
      worldViews.heroViews.has(hero.heroId)
    ) {
      activeRemoteHeroIds.add(hero.heroId);
    }
  });

  for (const heroId of worldViews.remoteHeroInterpolationBuffers.keys()) {
    if (!activeRemoteHeroIds.has(heroId)) {
      worldViews.remoteHeroInterpolationBuffers.delete(heroId);
    }
  }
}
