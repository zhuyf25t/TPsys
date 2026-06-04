import { recordBattleVisionCameraDiagnostics } from "../diagnostics/visionDiagnostics";
import type { RenderGameSceneHudInput } from "./objects/BattleGameSceneHudPresentationObjects";

export function renderGameSceneHud({
  hudBridge,
  snapshot,
  fps,
  weaponSwitchStateBridge,
  sharedAuthoritativeRuntime,
  localHeroDisplay,
  camera,
  obstacleBounds,
  mapExpanded
}: RenderGameSceneHudInput): void {
  const playerDisplayPosition = sharedAuthoritativeRuntime ? localHeroDisplay.read().position : undefined;
  recordBattleVisionCameraDiagnostics({
    camera,
    playerDisplayPosition
  });
  hudBridge.update({
    snapshot,
    fps,
    weaponSwitchRemainingMs: weaponSwitchStateBridge.getWeaponSwitchRemainingMs(),
    sharedAuthoritativeHud: sharedAuthoritativeRuntime,
    playerDisplayPosition,
    camera,
    obstacleBounds,
    mapExpanded
  });
}
