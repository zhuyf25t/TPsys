import type { BattleProjectileState as Projectile } from "../../../../../../objects/battle/microservices/combat/objects/projectile/BattleProjectileState";
import { getProjectileTextureRef } from "../../assets/BattleProjectileRasterAtlas";
import type {
  ProjectileCullWorldView,
  ProjectileViewActivationPlan,
  ProjectileViewCreationPlan,
  ProjectileViewReleasePlan,
  ProjectileViewTexturePlan,
  ProjectileViewVisualPlan,
  ResolveProjectileViewCreationPlanInput,
  ResolveProjectileViewVisualPlanInput
} from "../objects/ProjectileViewObjects";

const PROJECTILE_VIEW_CULL_PADDING = 320;
const PROJECTILE_VIEW_POOL_LIMIT = 96;
const PROJECTILE_VIEW_DEPTH = 43;
const PROJECTILE_VIEW_ORIGIN = { x: 0.5, y: 0.5 };

export function isProjectileInsideCullBounds(projectile: Projectile, worldView: ProjectileCullWorldView): boolean {
  const cullRadius = Math.max(projectile.radius, PROJECTILE_VIEW_CULL_PADDING);
  return (
    projectile.position.x >= worldView.x - cullRadius &&
    projectile.position.x <= worldView.x + worldView.width + cullRadius &&
    projectile.position.y >= worldView.y - cullRadius &&
    projectile.position.y <= worldView.y + worldView.height + cullRadius
  );
}

export function resolveProjectileLifetimeAlpha(projectile: Projectile): number {
  return clamp(projectile.ttlMs / Math.max(1, projectile.maxLifetimeMs), 0.2, 1);
}

export function resolveProjectileViewCreationPlan({
  projectile
}: ResolveProjectileViewCreationPlanInput): ProjectileViewCreationPlan {
  return {
    position: projectile.position,
    texture: resolveProjectileViewTexturePlan(projectile),
    origin: PROJECTILE_VIEW_ORIGIN,
    depth: PROJECTILE_VIEW_DEPTH
  };
}

export function resolveProjectileViewTexturePlan(projectile: Projectile): ProjectileViewTexturePlan {
  return getProjectileTextureRef(projectile.kind);
}

export function resolveProjectileViewAcquirePlan(): ProjectileViewActivationPlan {
  return {
    active: true,
    visible: true
  };
}

export function resolveProjectileViewVisualPlan({
  projectile,
  displayPosition,
  displayFacing
}: ResolveProjectileViewVisualPlanInput): ProjectileViewVisualPlan {
  return {
    position: displayPosition,
    rotation: displayFacing,
    alpha: resolveProjectileLifetimeAlpha(projectile)
  };
}

export function resolveProjectileViewReleasePlan(poolSize: number): ProjectileViewReleasePlan {
  return {
    active: false,
    visible: false,
    destroy: poolSize >= PROJECTILE_VIEW_POOL_LIMIT
  };
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}
