import type { BattleGameSnapshot as GameSnapshot } from "../../../../../../objects/battle/microservices/session/objects/state/BattleGameSnapshot";
import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";
import type { HudMinimapRect } from "../../../ui/Hud";
import type { HudPresenterObstacleBounds } from "../../../presenters/hudPresenter";

export interface BattleHudSceneBridgeContext {
  snapshot: GameSnapshot;
  fps: number;
  weaponSwitchRemainingMs: number;
  sharedAuthoritativeHud: boolean;
  playerDisplayPosition?: Vec2;
  camera: {
    worldView: HudMinimapRect;
  };
  obstacleBounds: readonly HudPresenterObstacleBounds[];
  mapExpanded: boolean;
}
