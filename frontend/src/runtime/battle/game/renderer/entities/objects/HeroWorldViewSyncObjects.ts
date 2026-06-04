import type { BattleHeroViewState as Hero } from "../../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { BattleGameSnapshot as GameSnapshot } from "../../../../../../objects/battle/microservices/session/objects/state/BattleGameSnapshot";
import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";
import type {
  HeroActionProgressPlan,
  HeroDisplayState,
  HeroView
} from "./WorldViewFactoryObjects";

export interface SyncHeroWorldViewFrameInput {
  view: HeroView;
  hero: Hero;
  displayState: HeroDisplayState;
  isPlayer: boolean;
  snapshot: Pick<GameSnapshot, "elapsedMs" | "slowFields">;
  deltaMs: number;
  weaponSwitchRemainingMs: number;
  weaponSwitchTotalMs: number;
}

export interface ResolveHeroWorldViewFrameLayoutPlanInput {
  displayPosition: Vec2;
  actionProgress: HeroActionProgressPlan;
}

export interface HeroWorldViewFrameLayoutPlan {
  spritePosition: Vec2;
  nameLabelPosition: Vec2;
  healthBackgroundPosition: Vec2;
  healthFillPosition: Vec2;
  markerPosition: Vec2;
  actionBar: HeroWorldViewActionBarLayoutPlan;
}

export type HeroWorldViewActionBarLayoutPlan =
  | {
      visible: false;
    }
  | {
      visible: true;
      visibility: HeroWorldViewActionBarVisibilityPlan;
      backgroundPosition: Vec2;
      fillPosition: Vec2;
      fillWidth: number;
    };

export interface HeroWorldViewActionBarVisibilityPlan {
  background: boolean;
  fill: boolean;
}
