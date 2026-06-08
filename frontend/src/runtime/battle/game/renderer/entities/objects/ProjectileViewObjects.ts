import type Phaser from "phaser";
import type { BattleProjectileState as Projectile } from "../../../../../../objects/battle/microservices/combat/objects/projectile/BattleProjectileState";
import type { BattleGameSnapshot as GameSnapshot } from "../../../../../../objects/battle/microservices/session/objects/state/BattleGameSnapshot";
import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";
import type { ProjectileTextureRef } from "../../assets/objects/BattleProjectileRasterAtlasObjects";

export interface ProjectileView {
  sprite: Phaser.GameObjects.Image;
  textureKey: string;
  frameName: string;
}

export interface ProjectileInterpolationSample {
  receivedAtMs: number;
  position: Vec2;
  facing: number;
}

export interface ProjectileInterpolationBuffer {
  samples: ProjectileInterpolationSample[];
}

export interface ProjectileViewState {
  projectileInterpolationBuffers: Map<string, ProjectileInterpolationBuffer>;
  projectileViews: Map<string, ProjectileView>;
  projectileViewPool: ProjectileView[];
  scratchLiveProjectileIds: Set<string>;
}

export interface ProjectileViewSyncContext {
  scene: Phaser.Scene;
  snapshot: GameSnapshot;
  worldViews: ProjectileViewState;
  deltaMs: number;
  sharedAuthoritativeRuntime?: boolean;
}

export interface ResolveProjectileViewCreationPlanInput {
  projectile: Projectile;
}

export interface ProjectileViewCreationPlan {
  position: Vec2;
  texture: ProjectileViewTexturePlan;
  origin: Vec2;
  depth: number;
}

export interface ProjectileViewTexturePlan {
  textureKey: ProjectileTextureRef["textureKey"];
  frameName: ProjectileTextureRef["frameName"];
  scale: ProjectileTextureRef["scale"];
  tint: ProjectileTextureRef["tint"];
}

export interface ResolveProjectileViewVisualPlanInput {
  projectile: Projectile;
  displayPosition: Vec2;
  displayFacing: number;
}

export interface ProjectileViewVisualPlan {
  position: Vec2;
  rotation: number;
  alpha: number;
}

export interface ProjectileViewActivationPlan {
  active: boolean;
  visible: boolean;
}

export interface ProjectileViewReleasePlan extends ProjectileViewActivationPlan {
  destroy: boolean;
}

export interface ProjectileCullWorldView {
  x: number;
  y: number;
  width: number;
  height: number;
}

export interface ProjectileDisplayState {
  position: Vec2;
  facing: number;
  interpolationSource?: "interpolated" | "fallback" | "snapshot";
  interpolationSampleCount?: number;
  interpolationDelayMs?: number;
}

export interface ResolveProjectileDisplayStateInput {
  scene: Phaser.Scene;
  worldViews: ProjectileViewState;
  view: ProjectileView;
  projectile: Projectile;
  deltaMs: number;
  useAuthoritativeInterpolation: boolean;
}

export interface ResolveProjectileFallbackDisplayStateInput {
  currentPosition: Vec2;
  currentFacing: number;
  projectile: Projectile;
  deltaMs: number;
  interpolationDelayMs?: number;
}

export interface ResolveSmoothedDisplayPositionInput {
  current: Vec2;
  target: Vec2;
  deltaMs: number;
  smoothingMs: number;
  snapDistance: number;
}
