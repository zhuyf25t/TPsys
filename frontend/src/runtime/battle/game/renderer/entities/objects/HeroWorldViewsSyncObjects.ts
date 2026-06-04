import type Phaser from "phaser";
import type { BattleHeroViewState as Hero } from "../../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { BattleGameSnapshot as GameSnapshot } from "../../../../../../objects/battle/microservices/session/objects/state/BattleGameSnapshot";
import type {
  LocalHeroDisplayOverride,
  WorldViewState
} from "./WorldViewFactoryObjects";

export interface SyncHeroWorldViewsInput {
  scene: Phaser.Scene;
  snapshot: GameSnapshot;
  worldViews: WorldViewState;
  deltaMs: number;
  weaponSwitchRemainingMs: number;
  weaponSwitchTotalMs: number;
  sharedAuthoritativeRuntime?: boolean;
  remoteAuthoritativeHeroIds?: ReadonlySet<string>;
  localHeroDisplayOverride?: LocalHeroDisplayOverride;
}

export interface SyncSingleHeroWorldViewInput {
  scene: Phaser.Scene;
  snapshot: GameSnapshot;
  worldViews: WorldViewState;
  hero: Hero;
  deltaMs: number;
  weaponSwitchRemainingMs: number;
  weaponSwitchTotalMs: number;
  sharedAuthoritativeRuntime: boolean;
  remoteAuthoritativeHeroIds: ReadonlySet<string>;
  localHeroDisplayOverride?: LocalHeroDisplayOverride;
}

export interface HeroWorldViewVisibilityMutationPlan {
  shadow?: boolean;
  bodyDisc?: boolean;
  silhouetteRing?: boolean;
  hitRing?: boolean;
  statusRing?: boolean;
  weaponStock?: boolean;
  weaponCue?: boolean;
  weaponMuzzle?: boolean;
  weaponOverlay?: boolean;
  sprite?: boolean;
  nameLabel?: boolean;
  healthBackground?: boolean;
  healthFill?: boolean;
  actionBackground?: boolean;
  actionFill?: boolean;
  marker?: boolean;
  localMotionStreaks: HeroWorldViewLocalMotionStreakVisibilityPlan;
}

export type HeroWorldViewLocalMotionStreakVisibilityPlan =
  | {
      kind: "unchanged";
    }
  | {
      kind: "hidden";
      resetPosition: boolean;
    };
