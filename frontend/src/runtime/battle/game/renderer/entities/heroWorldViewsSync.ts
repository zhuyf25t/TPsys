import {
  recordHeroWorldViewRemoteDiagnostics,
  resolveHeroWorldViewDisplayState
} from "./heroWorldViewRemoteDisplaySync";
import { syncHeroWorldViewFrame } from "./heroWorldViewSync";
import {
  hideHeroWorldView,
  showHeroWorldViewBase
} from "./heroWorldViewVisibilitySync";
import { cleanupRemoteHeroInterpolationBuffers } from "./remoteHeroInterpolationBufferSync";
import {
  isLocalPlayerHero,
  resolveHeroDisplayStatePlan,
  resolveHeroVisibilityPlan
} from "./functions/WorldViewFactoryRules";
import type {
  SyncHeroWorldViewsInput,
  SyncSingleHeroWorldViewInput
} from "./objects/HeroWorldViewsSyncObjects";

const EMPTY_REMOTE_AUTH_HERO_IDS: ReadonlySet<string> = new Set<string>();

export function syncHeroWorldViews({
  scene,
  snapshot,
  worldViews,
  deltaMs,
  weaponSwitchRemainingMs,
  weaponSwitchTotalMs,
  sharedAuthoritativeRuntime = false,
  remoteAuthoritativeHeroIds = EMPTY_REMOTE_AUTH_HERO_IDS,
  localHeroDisplayOverride
}: SyncHeroWorldViewsInput): void {
  cleanupRemoteHeroInterpolationBuffers({
    snapshot,
    worldViews,
    sharedAuthoritativeRuntime,
    remoteAuthoritativeHeroIds
  });

  snapshot.heroes.forEach((hero) => {
    syncSingleHeroWorldView({
      scene,
      snapshot,
      worldViews,
      hero,
      deltaMs,
      weaponSwitchRemainingMs,
      weaponSwitchTotalMs,
      sharedAuthoritativeRuntime,
      remoteAuthoritativeHeroIds,
      localHeroDisplayOverride
    });
  });
}

function syncSingleHeroWorldView({
  scene,
  snapshot,
  worldViews,
  hero,
  deltaMs,
  weaponSwitchRemainingMs,
  weaponSwitchTotalMs,
  sharedAuthoritativeRuntime,
  remoteAuthoritativeHeroIds,
  localHeroDisplayOverride
}: SyncSingleHeroWorldViewInput): void {
  const view = worldViews.heroViews.get(hero.heroId);
  if (!view) {
    return;
  }

  const isPlayer = isLocalPlayerHero(hero, snapshot.playerHeroId);
  const visibilityPlan = resolveHeroVisibilityPlan(hero);
  if (!visibilityPlan.visible) {
    if (visibilityPlan.clearRemoteInterpolation) {
      worldViews.remoteHeroInterpolationBuffers.delete(hero.heroId);
    }
    hideHeroWorldView(view, visibilityPlan);
    return;
  }

  showHeroWorldViewBase(view);

  const displayStatePlan = resolveHeroDisplayStatePlan({
    hero,
    playerHeroId: snapshot.playerHeroId,
    sharedAuthoritativeRuntime,
    remoteAuthoritativeHeroIds,
    localHeroDisplayOverride
  });
  const displayState = resolveHeroWorldViewDisplayState({
    scene,
    worldViews,
    view,
    hero,
    deltaMs,
    displayStatePlan
  });

  syncHeroWorldViewFrame({
    view,
    hero,
    displayState,
    isPlayer,
    snapshot,
    deltaMs,
    weaponSwitchRemainingMs,
    weaponSwitchTotalMs
  });

  recordHeroWorldViewRemoteDiagnostics({
    hero,
    displayState,
    displayStatePlan
  });
}
