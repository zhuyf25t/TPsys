import type Phaser from "phaser";
import type {
  BattleHeroViewState as Hero,
  BattlePreparedSkill as PreparedSkill
} from "../../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { BattleGameSnapshot as GameSnapshot } from "../../../../../../objects/battle/microservices/session/objects/state/BattleGameSnapshot";
import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";
import type { HeroHealthView, HeroReadabilitySyncView } from "./HeroReadabilityViewObjects";
import type { HeroWeaponOverlayView } from "./HeroWeaponOverlayObjects";
import type { LocalHeroMotionStreakView } from "./LocalHeroMotionStreakObjects";
import type { PickupView } from "./PickupViewPresentationObjects";
import type { ProjectileInterpolationBuffer, ProjectileView } from "./ProjectileViewObjects";
import type { RemoteHeroInterpolationBuffer } from "./RemoteHeroInterpolationObjects";
import type { SlowFieldView } from "./SlowFieldViewObjects";

export interface HeroView extends HeroReadabilitySyncView, HeroHealthView {
  localMotionStreaks: LocalHeroMotionStreakView | null;
  weaponOverlay: HeroWeaponOverlayView;
  sprite: Phaser.GameObjects.Image;
  nameLabel: Phaser.GameObjects.Text;
  actionBackground: Phaser.GameObjects.Rectangle;
  actionFill: Phaser.GameObjects.Rectangle;
}

export interface WorldViewState {
  heroViews: Map<string, HeroView>;
  remoteHeroInterpolationBuffers: Map<string, RemoteHeroInterpolationBuffer>;
  projectileInterpolationBuffers: Map<string, ProjectileInterpolationBuffer>;
  projectileViews: Map<string, ProjectileView>;
  projectileViewPool: ProjectileView[];
  slowFieldViews: Map<string, SlowFieldView>;
  pickupViews: Map<string, PickupView>;
  itemPickupViews: Map<string, PickupView>;
  scratchActiveRemoteHeroIds: Set<string>;
  scratchLiveProjectileIds: Set<string>;
  scratchLiveSlowFieldIds: Set<string>;
  scratchLiveWeaponPickupIds: Set<string>;
  scratchLiveItemPickupIds: Set<string>;
  rangeIndicator: Phaser.GameObjects.Arc;
  targetIndicator: Phaser.GameObjects.Arc;
}

export interface HeroDisplayState {
  position: Vec2;
  facing: number;
  interpolationSource?: "interpolated" | "fallback";
  interpolationSampleCount?: number;
  interpolationDelayMs?: number;
}

export type LocalHeroDisplayOverride = HeroDisplayState;

export type HeroDisplayStatePlan =
  | {
      kind: "localOverride";
      displayState: HeroDisplayState;
    }
  | {
      kind: "remoteAuthoritative";
    }
  | {
      kind: "snapshot";
      displayState: HeroDisplayState;
    };

export type HeroVisibilityPlan =
  | {
      visible: true;
      clearRemoteInterpolation: false;
      resetLocalMotionStreaks: false;
    }
  | {
      visible: false;
      clearRemoteInterpolation: true;
      resetLocalMotionStreaks: true;
    };

export interface ResolveHeroDisplayStatePlanInput {
  hero: Hero;
  playerHeroId: string;
  sharedAuthoritativeRuntime: boolean;
  remoteAuthoritativeHeroIds: ReadonlySet<string>;
  localHeroDisplayOverride?: LocalHeroDisplayOverride;
}

export interface WorldViewFactoryContext {
  scene: Phaser.Scene;
  snapshot: GameSnapshot;
  getBaseHeroScale: (heroId: string) => number;
}

export interface WorldViewSyncContext {
  scene: Phaser.Scene;
  snapshot: GameSnapshot;
  worldViews: WorldViewState;
  deltaMs: number;
  weaponSwitchRemainingMs: number;
  weaponSwitchTotalMs: number;
  pointerWorld: Vec2;
  isBlinkTargetValid: (player: Hero, target: Vec2) => boolean;
  isPreparedTargetValid?: (player: Hero, preparedSkill: Exclude<PreparedSkill, null>, target: Vec2) => boolean;
  sharedAuthoritativeRuntime?: boolean;
  remoteAuthoritativeHeroIds?: ReadonlySet<string>;
  localHeroDisplayOverride?: LocalHeroDisplayOverride;
}

export type HeroActionProgressPlan =
  | { visible: false }
  | {
      visible: true;
      progress: number;
    };
