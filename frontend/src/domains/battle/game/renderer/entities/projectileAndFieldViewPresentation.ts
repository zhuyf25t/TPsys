import Phaser from "phaser";
import type { GameSnapshot, Projectile, ProjectileKind, SlowField, Vec2 } from "../../../objects/types";
import { BULLET_TEXTURE_KEY, ROCKET_TEXTURE_KEY } from "../../constants";

export interface ProjectileView {
  sprite: Phaser.GameObjects.Image;
  glow: Phaser.GameObjects.Arc;
  trail: Phaser.GameObjects.Rectangle;
}

export interface SlowFieldView {
  fill: Phaser.GameObjects.Arc;
  rim: Phaser.GameObjects.Arc;
}

interface ProjectileInterpolationSample {
  receivedAtMs: number;
  position: Vec2;
  facing: number;
}

export interface ProjectileInterpolationBuffer {
  samples: ProjectileInterpolationSample[];
}

interface ProjectileAndFieldViewState {
  projectileInterpolationBuffers: Map<string, ProjectileInterpolationBuffer>;
  projectileViews: Map<string, ProjectileView>;
  slowFieldViews: Map<string, SlowFieldView>;
  scratchLiveProjectileIds: Set<string>;
  scratchLiveSlowFieldIds: Set<string>;
}

export interface ProjectileAndFieldSyncContext {
  scene: Phaser.Scene;
  snapshot: GameSnapshot;
  worldViews: ProjectileAndFieldViewState;
  deltaMs: number;
  sharedAuthoritativeRuntime?: boolean;
}

const AUTHORITATIVE_REMOTE_ENTITY_INTERPOLATION_DELAY_MS = 70;
const AUTHORITATIVE_PROJECTILE_SNAP_DISTANCE = 260;
const AUTHORITATIVE_PROJECTILE_SMOOTHING_MS = 55;
const AUTHORITATIVE_PROJECTILE_INTERPOLATION_BUFFER_CAP = 8;
const AUTHORITATIVE_PROJECTILE_POSITION_EPSILON = 0.05;
const AUTHORITATIVE_PROJECTILE_FACING_EPSILON = 0.001;

/** 中文名：获取投射物展示position从views（getProjectileDisplayPositionFromViews）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function getProjectileDisplayPositionFromViews(
  worldViews: Pick<ProjectileAndFieldViewState, "projectileViews">,
  projectileId: string
): Vec2 | null {
  const view = worldViews.projectileViews.get(projectileId);
  if (!view?.sprite.active || !view.sprite.visible) {
    return null;
  }

  return { x: view.sprite.x, y: view.sprite.y };
}

/** 中文名：sync投射物views（syncProjectileViews）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function syncProjectileViews({
  scene,
  snapshot,
  worldViews,
  deltaMs,
  sharedAuthoritativeRuntime = false
}: ProjectileAndFieldSyncContext): void {
  const liveIds = worldViews.scratchLiveProjectileIds;
  liveIds.clear();

  if (!sharedAuthoritativeRuntime) {
    worldViews.projectileInterpolationBuffers.clear();
  }

  snapshot.projectiles.forEach((projectile) => {
    liveIds.add(projectile.projectileId);
    const existing = worldViews.projectileViews.get(projectile.projectileId) ?? createProjectileView(scene, projectile);
    worldViews.projectileViews.set(projectile.projectileId, existing);

    const isLocalPlayerProjectile = projectile.ownerHeroId === snapshot.playerHeroId;
    const useAuthoritativeInterpolation = sharedAuthoritativeRuntime && !isLocalPlayerProjectile;

    if (!useAuthoritativeInterpolation) {
      worldViews.projectileInterpolationBuffers.delete(projectile.projectileId);
    }

    const displayState = resolveProjectileDisplayState({
      scene,
      worldViews,
      view: existing,
      projectile,
      deltaMs,
      useAuthoritativeInterpolation
    });
    existing.sprite.setPosition(displayState.position.x, displayState.position.y);
    existing.sprite.setRotation(displayState.facing);
    syncProjectileReadabilityVisuals(existing, projectile, displayState.position, displayState.facing);
  });

  for (const [projectileId, view] of worldViews.projectileViews.entries()) {
    if (liveIds.has(projectileId)) {
      continue;
    }

    destroyProjectileView(view);
    worldViews.projectileViews.delete(projectileId);
    worldViews.projectileInterpolationBuffers.delete(projectileId);
  }

  for (const projectileId of worldViews.projectileInterpolationBuffers.keys()) {
    if (!liveIds.has(projectileId)) {
      worldViews.projectileInterpolationBuffers.delete(projectileId);
    }
  }
}

interface ProjectileDisplayState {
  position: Vec2;
  facing: number;
}

interface ResolveProjectileDisplayStateInput {
  scene: Phaser.Scene;
  worldViews: ProjectileAndFieldViewState;
  view: ProjectileView;
  projectile: Projectile;
  deltaMs: number;
  useAuthoritativeInterpolation: boolean;
}

function resolveProjectileDisplayState({
  scene,
  worldViews,
  view,
  projectile,
  deltaMs,
  useAuthoritativeInterpolation
}: ResolveProjectileDisplayStateInput): ProjectileDisplayState {
  if (!useAuthoritativeInterpolation) {
    return {
      position: projectile.position,
      facing: projectile.facing
    };
  }

  const receivedAtMs = resolveRenderNowMs(scene);
  const sample = createProjectileInterpolationSample(projectile, receivedAtMs);

  if (!sample) {
    return resolveProjectileFallbackDisplayState(view, projectile, deltaMs);
  }

  const buffer = getProjectileInterpolationBuffer(worldViews, projectile.projectileId);
  recordProjectileInterpolationSample(buffer, sample);

  return (
    resolveInterpolatedProjectileDisplayState(buffer, receivedAtMs) ??
    resolveProjectileFallbackDisplayState(view, projectile, deltaMs)
  );
}

function getProjectileInterpolationBuffer(worldViews: ProjectileAndFieldViewState, projectileId: string): ProjectileInterpolationBuffer {
  const existing = worldViews.projectileInterpolationBuffers.get(projectileId);
  if (existing) {
    return existing;
  }

  const created: ProjectileInterpolationBuffer = { samples: [] };
  worldViews.projectileInterpolationBuffers.set(projectileId, created);
  return created;
}

function createProjectileInterpolationSample(projectile: Projectile, receivedAtMs: number): ProjectileInterpolationSample | null {
  if (!Number.isFinite(receivedAtMs) || !isFiniteVec2(projectile.position) || !Number.isFinite(projectile.facing)) {
    return null;
  }

  return {
    receivedAtMs,
    position: { x: projectile.position.x, y: projectile.position.y },
    facing: projectile.facing
  };
}

function recordProjectileInterpolationSample(
  buffer: ProjectileInterpolationBuffer,
  sample: ProjectileInterpolationSample
): void {
  const lastSample = buffer.samples[buffer.samples.length - 1];
  if (lastSample) {
    const distance = Phaser.Math.Distance.Between(lastSample.position.x, lastSample.position.y, sample.position.x, sample.position.y);
    if (!Number.isFinite(distance) || distance >= AUTHORITATIVE_PROJECTILE_SNAP_DISTANCE) {
      buffer.samples = [sample];
      return;
    }

    const facingDelta = Math.abs(Phaser.Math.Angle.Wrap(sample.facing - lastSample.facing));
    if (distance <= AUTHORITATIVE_PROJECTILE_POSITION_EPSILON && facingDelta <= AUTHORITATIVE_PROJECTILE_FACING_EPSILON) {
      return;
    }

    if (sample.receivedAtMs <= lastSample.receivedAtMs) {
      sample.receivedAtMs = lastSample.receivedAtMs + 0.001;
    }
  }

  buffer.samples.push(sample);
  if (buffer.samples.length > AUTHORITATIVE_PROJECTILE_INTERPOLATION_BUFFER_CAP) {
    buffer.samples.splice(0, buffer.samples.length - AUTHORITATIVE_PROJECTILE_INTERPOLATION_BUFFER_CAP);
  }
}

function resolveInterpolatedProjectileDisplayState(
  buffer: ProjectileInterpolationBuffer,
  renderNowMs: number
): ProjectileDisplayState | null {
  const renderAtMs = renderNowMs - AUTHORITATIVE_REMOTE_ENTITY_INTERPOLATION_DELAY_MS;
  if (!Number.isFinite(renderAtMs) || buffer.samples.length < 2) {
    return null;
  }

  for (let index = 0; index < buffer.samples.length - 1; index += 1) {
    const from = buffer.samples[index];
    const to = buffer.samples[index + 1];
    if (renderAtMs < from.receivedAtMs || renderAtMs > to.receivedAtMs) {
      continue;
    }

    const durationMs = to.receivedAtMs - from.receivedAtMs;
    const alpha = durationMs > 0 ? Phaser.Math.Clamp((renderAtMs - from.receivedAtMs) / durationMs, 0, 1) : 1;
    const position = {
      x: Phaser.Math.Linear(from.position.x, to.position.x, alpha),
      y: Phaser.Math.Linear(from.position.y, to.position.y, alpha)
    };
    const facing = interpolateFacing(from.facing, to.facing, alpha);

    if (!isFiniteVec2(position) || !Number.isFinite(facing)) {
      return null;
    }

    return { position, facing };
  }

  return null;
}

function resolveProjectileFallbackDisplayState(view: ProjectileView, projectile: Projectile, deltaMs: number): ProjectileDisplayState {
  const currentPosition = resolveFinitePosition({ x: view.sprite.x, y: view.sprite.y });
  const targetPosition = isFiniteVec2(projectile.position) ? projectile.position : currentPosition;

  return {
    position: resolveSmoothedDisplayPosition({
      current: currentPosition,
      target: targetPosition,
      deltaMs,
      smoothingMs: AUTHORITATIVE_PROJECTILE_SMOOTHING_MS,
      snapDistance: AUTHORITATIVE_PROJECTILE_SNAP_DISTANCE
    }),
    facing: Number.isFinite(projectile.facing) ? projectile.facing : view.sprite.rotation
  };
}

/** 中文名：sync减速fieldviews（syncSlowFieldViews）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function syncSlowFieldViews({ scene, snapshot, worldViews }: ProjectileAndFieldSyncContext): void {
  const liveIds = worldViews.scratchLiveSlowFieldIds;
  liveIds.clear();

  snapshot.slowFields.forEach((field) => {
    liveIds.add(field.fieldId);
    const existing = worldViews.slowFieldViews.get(field.fieldId) ?? createSlowFieldView(scene, field);
    worldViews.slowFieldViews.set(field.fieldId, existing);
    const alpha = Phaser.Math.Clamp(field.ttlMs / Math.max(1, field.durationMs), 0, 1);
    existing.fill.setPosition(field.position.x, field.position.y);
    existing.fill.setRadius(field.radius);
    existing.fill.setFillStyle(0x9beeff, 0.12 * alpha);
    existing.rim.setPosition(field.position.x, field.position.y);
    existing.rim.setRadius(field.radius);
    existing.rim.setStrokeStyle(3, 0xb9f7ff, 0.58 * alpha);
  });

  for (const [fieldId, view] of worldViews.slowFieldViews.entries()) {
    if (liveIds.has(fieldId)) {
      continue;
    }

    view.fill.destroy();
    view.rim.destroy();
    worldViews.slowFieldViews.delete(fieldId);
  }
}

function createSlowFieldView(scene: Phaser.Scene, field: SlowField): SlowFieldView {
  const fill = scene.add.circle(field.position.x, field.position.y, field.radius, 0x9beeff, 0.12).setDepth(21);
  const rim = scene.add.circle(field.position.x, field.position.y, field.radius, 0xb9f7ff, 0).setDepth(22);
  rim.setStrokeStyle(3, 0xb9f7ff, 0.58);
  return { fill, rim };
}

function createProjectileView(scene: Phaser.Scene, projectile: Projectile): ProjectileView {
  const isRocket = projectile.kind === "rocket";
  const style = getProjectileReadabilityStyle(projectile);
  const trail = scene.add
    .rectangle(projectile.position.x, projectile.position.y, style.trailLength, style.trailHeight, style.trailTint, style.trailAlpha)
    .setOrigin(1, 0.5)
    .setDepth(41);
  const glow = scene.add.circle(projectile.position.x, projectile.position.y, style.glowRadius, style.glowTint, style.glowAlpha).setDepth(42);
  const sprite = scene.add
    .image(projectile.position.x, projectile.position.y, isRocket ? ROCKET_TEXTURE_KEY : BULLET_TEXTURE_KEY)
    .setScale(projectile.kind === "shotgun-pellet" ? 0.22 : isRocket ? 0.52 : projectile.kind === "gatling-bullet" ? 0.26 : 0.32)
    .setDepth(43);

  if (projectile.kind === "rocket") {
    sprite.setTint(0xffb36f);
  } else if (projectile.kind === "gatling-bullet") {
    sprite.setTint(0xffd86d);
  } else if (projectile.kind === "shotgun-pellet") {
    sprite.setTint(0xfff7cf);
  } else {
    sprite.setTint(0xdaf3ff);
  }

  syncProjectileReadabilityVisuals({ sprite, glow, trail }, projectile, projectile.position, projectile.facing);
  return { sprite, glow, trail };
}

interface ProjectileReadabilityStyle {
  glowAlpha: number;
  glowRadius: number;
  glowTint: number;
  trailAlpha: number;
  trailHeight: number;
  trailLength: number;
  trailTint: number;
}

const PROJECTILE_READABILITY_STYLES: Record<ProjectileKind, ProjectileReadabilityStyle> = {
  "pistol-bullet": {
    glowAlpha: 0.18,
    glowRadius: 5,
    glowTint: 0xdaf3ff,
    trailAlpha: 0.34,
    trailHeight: 2,
    trailLength: 20,
    trailTint: 0xaeeeff
  },
  rocket: {
    glowAlpha: 0.24,
    glowRadius: 12,
    glowTint: 0xff9b55,
    trailAlpha: 0.42,
    trailHeight: 9,
    trailLength: 42,
    trailTint: 0xff7a32
  },
  "gatling-bullet": {
    glowAlpha: 0.2,
    glowRadius: 5,
    glowTint: 0xffd86d,
    trailAlpha: 0.48,
    trailHeight: 3,
    trailLength: 30,
    trailTint: 0xffe28a
  },
  "shotgun-pellet": {
    glowAlpha: 0.14,
    glowRadius: 4,
    glowTint: 0xfff7cf,
    trailAlpha: 0.32,
    trailHeight: 3,
    trailLength: 16,
    trailTint: 0xfff0b8
  }
};

function getProjectileReadabilityStyle(projectile: Projectile): ProjectileReadabilityStyle {
  return PROJECTILE_READABILITY_STYLES[projectile.kind];
}

function syncProjectileReadabilityVisuals(
  view: ProjectileView,
  projectile: Projectile,
  displayPosition: Vec2,
  displayFacing: number
): void {
  const style = getProjectileReadabilityStyle(projectile);
  const lifetimeAlpha = Phaser.Math.Clamp(projectile.ttlMs / Math.max(1, projectile.maxLifetimeMs), 0.2, 1);

  view.glow.setPosition(displayPosition.x, displayPosition.y);
  view.glow.setRadius(style.glowRadius);
  view.glow.setFillStyle(style.glowTint, style.glowAlpha * lifetimeAlpha);

  view.trail.setPosition(displayPosition.x, displayPosition.y);
  view.trail.setRotation(displayFacing);
  view.trail.setDisplaySize(style.trailLength, style.trailHeight);
  view.trail.setFillStyle(style.trailTint, style.trailAlpha * lifetimeAlpha);
}

function destroyProjectileView(view: ProjectileView): void {
  view.sprite.destroy();
  view.glow.destroy();
  view.trail.destroy();
}

function resolveRenderNowMs(scene: Phaser.Scene): number {
  const sceneNowMs = scene.time?.now;
  return Number.isFinite(sceneNowMs) ? sceneNowMs : performance.now();
}

function interpolateFacing(from: number, to: number, alpha: number): number {
  if (!Number.isFinite(from) || !Number.isFinite(to) || !Number.isFinite(alpha)) {
    return to;
  }

  return Phaser.Math.Angle.Wrap(from + Phaser.Math.Angle.Wrap(to - from) * alpha);
}

function isFiniteVec2(position: Vec2): boolean {
  return Number.isFinite(position.x) && Number.isFinite(position.y);
}

function resolveFinitePosition(position: Vec2): Vec2 {
  return isFiniteVec2(position) ? position : { x: 0, y: 0 };
}

interface ResolveSmoothedDisplayPositionInput {
  current: Vec2;
  target: Vec2;
  deltaMs: number;
  smoothingMs: number;
  snapDistance: number;
}

function resolveSmoothedDisplayPosition({
  current,
  target,
  deltaMs,
  smoothingMs,
  snapDistance
}: ResolveSmoothedDisplayPositionInput): Vec2 {
  const distance = Phaser.Math.Distance.Between(current.x, current.y, target.x, target.y);
  if (!Number.isFinite(distance) || distance <= 0.5 || distance >= snapDistance) {
    return target;
  }

  const safeDeltaMs = Number.isFinite(deltaMs) ? Math.max(0, deltaMs) : 0;
  const alpha = Phaser.Math.Clamp(1 - Math.exp(-safeDeltaMs / Math.max(1, smoothingMs)), 0.12, 0.72);
  return {
    x: current.x + (target.x - current.x) * alpha,
    y: current.y + (target.y - current.y) * alpha
  };
}
