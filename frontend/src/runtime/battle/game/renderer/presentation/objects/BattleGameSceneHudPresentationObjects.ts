import type Phaser from "phaser";
import type { BattleGameSnapshot as GameSnapshot } from "../../../../../../objects/battle/microservices/session/objects/state/BattleGameSnapshot";
import type { WeaponSwitchStateBridge } from "../../../../local/weapons/weaponSwitchStateBridge";
import type { ObstacleBounds } from "../../arena/objects/ArenaBuilderObjects";
import type { LocalHeroDisplay } from "../../entities/BattleLocalHeroDisplay";
import type { BattleHudSceneBridge } from "../../hud/battleHudSceneBridge";

export interface RenderGameSceneHudInput {
  hudBridge: BattleHudSceneBridge;
  snapshot: GameSnapshot;
  fps: number;
  weaponSwitchStateBridge: WeaponSwitchStateBridge;
  sharedAuthoritativeRuntime: boolean;
  localHeroDisplay: LocalHeroDisplay;
  camera: Phaser.Cameras.Scene2D.Camera;
  obstacleBounds: readonly ObstacleBounds[];
  mapExpanded: boolean;
}
