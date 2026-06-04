import type Phaser from "phaser";
import type { BattlePlayerCommand as PlayerCommand } from "../../../../../../objects/battle/microservices/session/objects/command/BattlePlayerCommand";
import type { BattleGameSnapshot as GameSnapshot } from "../../../../../../objects/battle/microservices/session/objects/state/BattleGameSnapshot";
import type { WeaponSwitchStateBridge } from "../../../../local/weapons/weaponSwitchStateBridge";
import type { ObstacleBounds } from "../../arena/objects/ArenaBuilderObjects";
import type { PlayerAbilitySceneBridge } from "../../effects/playerAbilitySceneBridge";
import type { LocalHeroDisplay } from "../../entities/BattleLocalHeroDisplay";
import type { WorldViewState } from "../../entities/worldViewFactory";

export interface SyncGameSceneWorldViewsInput {
  scene: Phaser.Scene;
  snapshot: GameSnapshot;
  worldViews: WorldViewState;
  command: PlayerCommand;
  deltaMs: number;
  weaponSwitchStateBridge: WeaponSwitchStateBridge;
  playerAbilityBridge: PlayerAbilitySceneBridge;
  sharedAuthoritativeRuntime: boolean;
  remoteAuthoritativeHeroIds: ReadonlySet<string>;
  localHeroDisplay: LocalHeroDisplay;
  obstacleBounds: readonly ObstacleBounds[];
}
