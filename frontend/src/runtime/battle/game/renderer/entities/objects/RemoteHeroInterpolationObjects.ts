import type Phaser from "phaser";
import type { BattleHeroViewState as Hero } from "../../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { BattleGameSnapshot as GameSnapshot } from "../../../../../../objects/battle/microservices/session/objects/state/BattleGameSnapshot";
import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";

export interface RemoteHeroInterpolationSample {
  receivedAtMs: number;
  position: Vec2;
  facing: number;
}

export interface RemoteHeroInterpolationBuffer {
  samples: RemoteHeroInterpolationSample[];
}

export interface RemoteHeroDisplayView {
  sprite: Phaser.GameObjects.Image;
}

export interface RemoteHeroInterpolationViewState {
  remoteHeroInterpolationBuffers: Map<string, RemoteHeroInterpolationBuffer>;
  scratchActiveRemoteHeroIds: Set<string>;
  heroViews: Map<string, RemoteHeroDisplayView>;
}

export interface RemoteHeroDisplayState {
  position: Vec2;
  facing: number;
}

export interface CleanupRemoteHeroInterpolationBuffersInput {
  snapshot: GameSnapshot;
  worldViews: RemoteHeroInterpolationViewState;
  sharedAuthoritativeRuntime: boolean;
  remoteAuthoritativeHeroIds: ReadonlySet<string>;
}

export interface ResolveRemoteHeroDisplayStateInput {
  scene: Phaser.Scene;
  worldViews: RemoteHeroInterpolationViewState;
  view: RemoteHeroDisplayView;
  hero: Hero;
  deltaMs: number;
}

export interface ResolveRemoteHeroFallbackDisplayStateInput {
  currentPosition: Vec2;
  currentFacing: number;
  hero: Hero;
  deltaMs: number;
}

export interface ResolveSmoothedDisplayPositionInput {
  current: Vec2;
  target: Vec2;
  deltaMs: number;
  smoothingMs: number;
  snapDistance: number;
}
