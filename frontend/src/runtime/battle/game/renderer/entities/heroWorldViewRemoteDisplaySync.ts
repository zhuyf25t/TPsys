import { recordRemoteHeroViewDiagnostics } from "../diagnostics/remoteViewDiagnostics";
import { resolveRemoteHeroDisplayState } from "./remoteHeroDisplayStateSync";
import type { HeroDisplayState } from "./objects/WorldViewFactoryObjects";
import type {
  RecordHeroWorldViewRemoteDiagnosticsInput,
  ResolveHeroWorldViewDisplayStateInput
} from "./objects/HeroWorldViewRemoteDisplayObjects";

export function resolveHeroWorldViewDisplayState({
  scene,
  worldViews,
  view,
  hero,
  deltaMs,
  displayStatePlan
}: ResolveHeroWorldViewDisplayStateInput): HeroDisplayState {
  if (displayStatePlan.kind === "remoteAuthoritative") {
    return resolveRemoteHeroDisplayState({
      scene,
      worldViews,
      view,
      hero,
      deltaMs
    });
  }

  return displayStatePlan.displayState;
}

export function recordHeroWorldViewRemoteDiagnostics({
  hero,
  displayState,
  displayStatePlan
}: RecordHeroWorldViewRemoteDiagnosticsInput): void {
  if (displayStatePlan.kind !== "remoteAuthoritative") {
    return;
  }

  recordRemoteHeroViewDiagnostics({
    heroId: hero.heroId,
    displayName: hero.displayName,
    displayPosition: displayState.position,
    targetPosition: hero.position,
    facing: displayState.facing,
    targetFacing: hero.facing
  });
}
