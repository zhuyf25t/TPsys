import type Phaser from "phaser";
import type { BattleHeroViewState as Hero } from "../../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { BattlePreparedSkill as PreparedSkill } from "../../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { BattleGameSnapshot as GameSnapshot } from "../../../../../../objects/battle/microservices/session/objects/state/BattleGameSnapshot";
import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";
import type { ControlKeys } from "../../../../local/input/controlKeys";
import type { ObstacleBounds } from "../../arena/objects/ArenaBuilderObjects";

export interface ReadGameScenePlayerCommandInput {
  input: Phaser.Input.InputPlugin;
  controls: ControlKeys;
  playerPosition: Vec2;
  pointerJustPressed: boolean;
  secondaryJustPressed: boolean;
  pendingWeaponSwitchDirection: -1 | 0 | 1;
  sharedAuthoritativeRuntime: boolean;
  player: Hero;
  preparedSkill: PreparedSkill;
  worldSize: GameSnapshot["worldSize"];
  obstacleBounds: readonly ObstacleBounds[];
}
