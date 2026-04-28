import Phaser from "phaser";
import type { GameSnapshot, Hero, PreparedSkill, SlowField, Vec2, WeaponKind } from "../../../../domain/types";
import { resolveHeroVisual } from "../../../../game/spawn";
import { WEAPON_DEFINITIONS } from "../../../../game/weapons";
import {
  getPreparedTargetSkillRuntimeProfile,
  isPreparedTargetSkillKind,
  type PreparedTargetSkillKind
} from "../../runtime-local/skills/skillRuntimeProfiles";
import { recordRemoteHeroViewDiagnostics } from "../remoteViewDiagnostics";
import {
  createHeroWeaponOverlayView,
  setHeroWeaponOverlayVisible,
  syncHeroWeaponOverlayVisuals,
  type HeroWeaponOverlayView
} from "./heroWeaponOverlayView";
import {
  createItemPickupView,
  createWeaponPickupView,
  setPickupViewVisible,
  syncItemPickupView,
  syncWeaponPickupView,
  type PickupView
} from "./pickupViewPresentation";
import {
  getProjectileDisplayPositionFromViews,
  syncProjectileViews,
  syncSlowFieldViews,
  type ProjectileInterpolationBuffer,
  type ProjectileView,
  type SlowFieldView
} from "./projectileAndFieldViewPresentation";

export interface HeroView {
  localMotionStreaks: LocalHeroMotionStreakView | null;
  shadow: Phaser.GameObjects.Arc;
  bodyDisc: Phaser.GameObjects.Arc;
  silhouetteRing: Phaser.GameObjects.Arc;
  hitRing: Phaser.GameObjects.Arc;
  statusRing: Phaser.GameObjects.Arc;
  weaponStock: Phaser.GameObjects.Rectangle;
  weaponCue: Phaser.GameObjects.Rectangle;
  weaponMuzzle: Phaser.GameObjects.Arc;
  weaponOverlay: HeroWeaponOverlayView;
  sprite: Phaser.GameObjects.Image;
  nameLabel: Phaser.GameObjects.Text;
  healthBackground: Phaser.GameObjects.Rectangle;
  healthFill: Phaser.GameObjects.Rectangle;
  actionBackground: Phaser.GameObjects.Rectangle;
  actionFill: Phaser.GameObjects.Rectangle;
  marker: Phaser.GameObjects.Arc | null;
}

interface LocalHeroMotionStreakView {
  streaks: Phaser.GameObjects.Rectangle[];
  lastPosition: Vec2 | null;
  lastAngle: number;
  intensity: number;
}

interface RemoteHeroInterpolationSample {
  receivedAtMs: number;
  position: Vec2;
  facing: number;
}

interface RemoteHeroInterpolationBuffer {
  samples: RemoteHeroInterpolationSample[];
}

export interface WorldViewState {
  heroViews: Map<string, HeroView>;
  remoteHeroInterpolationBuffers: Map<string, RemoteHeroInterpolationBuffer>;
  projectileInterpolationBuffers: Map<string, ProjectileInterpolationBuffer>;
  projectileViews: Map<string, ProjectileView>;
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

export interface LocalHeroDisplayOverride {
  position: Vec2;
  facing: number;
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

const AUTHORITATIVE_REMOTE_HERO_SNAP_DISTANCE = 150;
// Remote heroes prioritize readability and input feel; keep only a small interpolation cushion over 60ms snapshots.
const AUTHORITATIVE_REMOTE_HERO_SMOOTHING_MS = 58;
const AUTHORITATIVE_REMOTE_ENTITY_INTERPOLATION_DELAY_MS = 70;
const AUTHORITATIVE_REMOTE_HERO_INTERPOLATION_BUFFER_CAP = 10;
const AUTHORITATIVE_REMOTE_HERO_POSITION_EPSILON = 0.05;
const AUTHORITATIVE_REMOTE_HERO_FACING_EPSILON = 0.001;
const EMPTY_REMOTE_AUTH_HERO_IDS: ReadonlySet<string> = new Set<string>();
const HERO_READABILITY_MIN_RADIUS = 18;
const HERO_READABILITY_MARKER_DEPTH = 32;
const HERO_LOCAL_MOTION_STREAK_DEPTH = 31;
const HERO_READABILITY_SHADOW_DEPTH = 33;
const HERO_READABILITY_BODY_DEPTH = 34;
const HERO_READABILITY_SILHOUETTE_DEPTH = 35;
const HERO_READABILITY_HIT_RING_DEPTH = 36;
const HERO_READABILITY_STATUS_RING_DEPTH = 37;
const HERO_READABILITY_WEAPON_STOCK_DEPTH = 38;
const HERO_READABILITY_WEAPON_CUE_DEPTH = 39;
const HERO_READABILITY_WEAPON_MUZZLE_DEPTH = 40;
const HERO_HEALTH_WARNING_RATIO = 0.55;
const HERO_HEALTH_DANGER_RATIO = 0.3;
const HERO_HEALTH_WARNING_TINT = 0xffc857;
const HERO_HEALTH_DANGER_TINT = 0xff5a4f;
const HERO_HEALTH_BACKGROUND_TINT = 0x0d1014;
const HERO_HEALTH_BACKGROUND_NORMAL_ALPHA = 0.95;
const HERO_SLOWED_STATUS_TINT = 0x9bf8ff;
const HERO_SLOWED_STATUS_FILL_ALPHA = 0.055;
const HERO_SLOWED_STATUS_STROKE_ALPHA = 0.58;
const LOCAL_HERO_MOTION_STREAK_COUNT = 3;
const LOCAL_HERO_MOTION_MIN_SPEED = 70;
const LOCAL_HERO_MOTION_MAX_SPEED = 470;
const LOCAL_HERO_MOTION_DECAY = 0.34;
const LOCAL_HERO_MOTION_TINT = 0x8fe8ff;

interface WeaponCueReadabilityStyle {
  lengthRadiusScale: number;
  thickness: number;
  tint: number;
  localAlpha: number;
  remoteAlpha: number;
  strokeWidth: number;
  strokeTint: number;
  localStrokeAlpha: number;
  remoteStrokeAlpha: number;
  stockLengthRadiusScale: number;
  stockThicknessScale: number;
  stockAlphaScale: number;
  muzzleRadius: number;
  muzzleAlphaScale: number;
}

const WEAPON_CUE_READABILITY_STYLES: Record<WeaponKind, WeaponCueReadabilityStyle> = {
  Pistol: {
    lengthRadiusScale: 0.68,
    thickness: 4,
    tint: 0xfff0c6,
    localAlpha: 0.68,
    remoteAlpha: 0.46,
    strokeWidth: 1,
    strokeTint: 0xfff7df,
    localStrokeAlpha: 0.24,
    remoteStrokeAlpha: 0.14,
    stockLengthRadiusScale: 0.34,
    stockThicknessScale: 1.25,
    stockAlphaScale: 0.68,
    muzzleRadius: 3,
    muzzleAlphaScale: 0.78
  },
  RocketLauncher: {
    lengthRadiusScale: 1.38,
    thickness: 9,
    tint: 0xff9b55,
    localAlpha: 0.86,
    remoteAlpha: 0.68,
    strokeWidth: 2,
    strokeTint: 0xffd2a8,
    localStrokeAlpha: 0.38,
    remoteStrokeAlpha: 0.24,
    stockLengthRadiusScale: 0.62,
    stockThicknessScale: 1.5,
    stockAlphaScale: 0.7,
    muzzleRadius: 7,
    muzzleAlphaScale: 0.82
  },
  Gatling: {
    lengthRadiusScale: 1.22,
    thickness: 5,
    tint: 0xffd86d,
    localAlpha: 0.84,
    remoteAlpha: 0.64,
    strokeWidth: 1,
    strokeTint: 0xffefaa,
    localStrokeAlpha: 0.34,
    remoteStrokeAlpha: 0.2,
    stockLengthRadiusScale: 0.5,
    stockThicknessScale: 1.15,
    stockAlphaScale: 0.66,
    muzzleRadius: 5,
    muzzleAlphaScale: 0.8
  },
  Shotgun: {
    lengthRadiusScale: 1.04,
    thickness: 11,
    tint: 0xffefb7,
    localAlpha: 0.82,
    remoteAlpha: 0.62,
    strokeWidth: 2,
    strokeTint: 0xfff7d6,
    localStrokeAlpha: 0.34,
    remoteStrokeAlpha: 0.22,
    stockLengthRadiusScale: 0.72,
    stockThicknessScale: 1.35,
    stockAlphaScale: 0.72,
    muzzleRadius: 5,
    muzzleAlphaScale: 0.78
  }
};

export function createWorldViewState(context: WorldViewFactoryContext): WorldViewState {
  const { scene, snapshot, getBaseHeroScale } = context;
  const heroViews = new Map<string, HeroView>();
  const remoteHeroInterpolationBuffers = new Map<string, RemoteHeroInterpolationBuffer>();
  const projectileInterpolationBuffers = new Map<string, ProjectileInterpolationBuffer>();
  const projectileViews = new Map<string, ProjectileView>();
  const slowFieldViews = new Map<string, SlowFieldView>();
  const pickupViews = new Map<string, PickupView>();
  const itemPickupViews = new Map<string, PickupView>();
  const scratchActiveRemoteHeroIds = new Set<string>();
  const scratchLiveProjectileIds = new Set<string>();
  const scratchLiveSlowFieldIds = new Set<string>();
  const scratchLiveWeaponPickupIds = new Set<string>();
  const scratchLiveItemPickupIds = new Set<string>();

  snapshot.heroes.forEach((hero) => {
    const visual = resolveHeroVisual(hero.heroId);
    const isPlayer = hero.heroId === snapshot.playerHeroId;
    const spriteDepth = isPlayer ? 50 : 42;
    const readabilityRadius = resolveHeroReadabilityRadius(hero.radius);
    const shadow = scene.add
      .circle(hero.position.x, hero.position.y, readabilityRadius * 1.08, 0x020711, isPlayer ? 0.3 : 0.22)
      .setDepth(HERO_READABILITY_SHADOW_DEPTH);
    const bodyDisc = scene.add
      .circle(hero.position.x, hero.position.y, readabilityRadius, visual.tint, isPlayer ? 0.15 : 0.1)
      .setDepth(HERO_READABILITY_BODY_DEPTH);
    const silhouetteRing = scene.add
      .circle(hero.position.x, hero.position.y, readabilityRadius + 1, 0x000000, 0)
      .setDepth(HERO_READABILITY_SILHOUETTE_DEPTH);
    silhouetteRing.setStrokeStyle(3, 0x06101b, isPlayer ? 0.58 : 0.42);
    const hitRing = scene.add.circle(hero.position.x, hero.position.y, readabilityRadius, visual.tint, 0).setDepth(HERO_READABILITY_HIT_RING_DEPTH);
    hitRing.setStrokeStyle(isPlayer ? 2 : 1, visual.tint, isPlayer ? 0.62 : 0.36);
    const statusRing = scene.add
      .circle(hero.position.x, hero.position.y, readabilityRadius + 6, HERO_SLOWED_STATUS_TINT, HERO_SLOWED_STATUS_FILL_ALPHA)
      .setDepth(HERO_READABILITY_STATUS_RING_DEPTH)
      .setVisible(false);
    statusRing.setStrokeStyle(2, HERO_SLOWED_STATUS_TINT, HERO_SLOWED_STATUS_STROKE_ALPHA);
    const weaponCueStyle = getWeaponCueReadabilityStyle(resolveHeroWeaponKind(hero));
    const weaponStock = scene.add
      .rectangle(
        hero.position.x,
        hero.position.y,
        readabilityRadius * weaponCueStyle.stockLengthRadiusScale,
        weaponCueStyle.thickness * weaponCueStyle.stockThicknessScale,
        weaponCueStyle.strokeTint,
        (isPlayer ? weaponCueStyle.localAlpha : weaponCueStyle.remoteAlpha) * weaponCueStyle.stockAlphaScale
      )
      .setOrigin(0.74, 0.5)
      .setDepth(HERO_READABILITY_WEAPON_STOCK_DEPTH);
    const weaponCue = scene.add
      .rectangle(
        hero.position.x,
        hero.position.y,
        readabilityRadius * weaponCueStyle.lengthRadiusScale,
        weaponCueStyle.thickness,
        weaponCueStyle.tint,
        isPlayer ? weaponCueStyle.localAlpha : weaponCueStyle.remoteAlpha
      )
      .setOrigin(0.08, 0.5)
      .setDepth(HERO_READABILITY_WEAPON_CUE_DEPTH);
    weaponCue.setStrokeStyle(weaponCueStyle.strokeWidth, weaponCueStyle.strokeTint, isPlayer ? weaponCueStyle.localStrokeAlpha : weaponCueStyle.remoteStrokeAlpha);
    const weaponMuzzle = scene.add
      .circle(
        hero.position.x,
        hero.position.y,
        weaponCueStyle.muzzleRadius,
        weaponCueStyle.strokeTint,
        (isPlayer ? weaponCueStyle.localAlpha : weaponCueStyle.remoteAlpha) * weaponCueStyle.muzzleAlphaScale
      )
      .setDepth(HERO_READABILITY_WEAPON_MUZZLE_DEPTH);
    weaponMuzzle.setStrokeStyle(1, weaponCueStyle.tint, isPlayer ? weaponCueStyle.localStrokeAlpha : weaponCueStyle.remoteStrokeAlpha);
    const weaponOverlay = createHeroWeaponOverlayView(scene, hero.position);
    const sprite = scene.add
      .image(hero.position.x, hero.position.y, visual.textureKey)
      .setScale(getBaseHeroScale(hero.heroId))
      .setTint(visual.tint)
      .setDepth(spriteDepth);

    const marker =
      isPlayer
        ? scene.add.circle(hero.position.x, hero.position.y, readabilityRadius + 8, 0x4ad9ff, 0.035).setDepth(HERO_READABILITY_MARKER_DEPTH)
        : null;

    if (marker) {
      marker.setStrokeStyle(2, 0x76e4ff, 0.55);
    }
    const localMotionStreaks = isPlayer ? createLocalHeroMotionStreakView(scene, hero.position) : null;

    const nameLabel = scene.add
      .text(hero.position.x, hero.position.y - 54, hero.displayName, {
        fontFamily: "Segoe UI",
        fontSize: hero.heroId === snapshot.playerHeroId ? "14px" : "13px",
        color: hero.heroId === snapshot.playerHeroId ? "#e8fbff" : "#d9e3ef"
      })
      .setOrigin(0.5, 1)
      .setDepth(58);

    const healthBackground = scene.add.rectangle(hero.position.x, hero.position.y - 38, 52, 8, 0x0d1014, 0.95).setDepth(56);
    const healthFill = scene
      .add
      .rectangle(hero.position.x - 25, hero.position.y - 38, 48, 6, visual.tint, 1)
      .setOrigin(0, 0.5)
      .setDepth(57);
    const actionBackground = scene.add.rectangle(hero.position.x, hero.position.y - 24, 52, 6, 0x10151d, 0.9).setDepth(55);
    actionBackground.setStrokeStyle(1, 0xffffff, 0.14);
    const actionFill = scene.add.rectangle(hero.position.x - 25, hero.position.y - 24, 0, 4, 0xe7edf5, 0.95).setOrigin(0, 0.5).setDepth(56);
    actionBackground.setVisible(false);
    actionFill.setVisible(false);

    heroViews.set(hero.heroId, {
      localMotionStreaks,
      shadow,
      bodyDisc,
      silhouetteRing,
      hitRing,
      statusRing,
      weaponStock,
      weaponCue,
      weaponMuzzle,
      weaponOverlay,
      sprite,
      nameLabel,
      healthBackground,
      healthFill,
      actionBackground,
      actionFill,
      marker
    });
  });

  snapshot.weaponPickups.forEach((pickup) => {
    pickupViews.set(pickup.weaponId, createWeaponPickupView(scene, pickup));
  });

  snapshot.itemPickups.forEach((pickup) => {
    itemPickupViews.set(pickup.pickupId, createItemPickupView(scene, pickup));
  });

  const rangeIndicator = scene.add.circle(0, 0, 1, 0x69d2ff, 0.05).setDepth(16).setVisible(false);
  rangeIndicator.setStrokeStyle(2, 0x69d2ff, 0.85);

  const targetIndicator = scene.add.circle(0, 0, 11, 0x69d2ff, 0.15).setDepth(17).setVisible(false);
  targetIndicator.setStrokeStyle(2, 0x69d2ff, 0.85);

  return {
    heroViews,
    remoteHeroInterpolationBuffers,
    projectileInterpolationBuffers,
    projectileViews,
    slowFieldViews,
    pickupViews,
    itemPickupViews,
    scratchActiveRemoteHeroIds,
    scratchLiveProjectileIds,
    scratchLiveSlowFieldIds,
    scratchLiveWeaponPickupIds,
    scratchLiveItemPickupIds,
    rangeIndicator,
    targetIndicator
  };
}

export function syncHeroViews({
  scene,
  snapshot,
  worldViews,
  deltaMs,
  weaponSwitchRemainingMs,
  weaponSwitchTotalMs,
  sharedAuthoritativeRuntime = false,
  remoteAuthoritativeHeroIds = EMPTY_REMOTE_AUTH_HERO_IDS,
  localHeroDisplayOverride
}: WorldViewSyncContext): void {
  cleanupRemoteHeroInterpolationBuffers({
    snapshot,
    worldViews,
    sharedAuthoritativeRuntime,
    remoteAuthoritativeHeroIds
  });

  snapshot.heroes.forEach((hero) => {
    const view = worldViews.heroViews.get(hero.heroId);
    if (!view) {
      return;
    }

    const isPlayer = hero.heroId === snapshot.playerHeroId;
    const isRemoteAuthoritativeHero =
      sharedAuthoritativeRuntime && !isPlayer && remoteAuthoritativeHeroIds.has(hero.heroId);

    if (!hero.alive) {
      worldViews.remoteHeroInterpolationBuffers.delete(hero.heroId);
      view.shadow.setVisible(false);
      view.bodyDisc.setVisible(false);
      view.silhouetteRing.setVisible(false);
      view.hitRing.setVisible(false);
      view.statusRing.setVisible(false);
      view.weaponStock.setVisible(false);
      view.weaponCue.setVisible(false);
      view.weaponMuzzle.setVisible(false);
      setHeroWeaponOverlayVisible(view.weaponOverlay, false);
      view.sprite.setVisible(false);
      view.nameLabel.setVisible(false);
      view.healthBackground.setVisible(false);
      view.healthFill.setVisible(false);
      view.actionBackground.setVisible(false);
      view.actionFill.setVisible(false);
      view.marker?.setVisible(false);
      hideLocalHeroMotionStreaks(view.localMotionStreaks, true);
      return;
    }

    view.shadow.setVisible(true);
    view.bodyDisc.setVisible(true);
    view.silhouetteRing.setVisible(true);
    view.hitRing.setVisible(true);
    view.statusRing.setVisible(true);
    view.weaponStock.setVisible(true);
    view.weaponCue.setVisible(true);
    view.weaponMuzzle.setVisible(true);
    view.sprite.setVisible(true);
    view.nameLabel.setVisible(true);
    view.healthBackground.setVisible(true);
    view.healthFill.setVisible(true);
    view.actionBackground.setVisible(false);
    view.actionFill.setVisible(false);
    view.marker?.setVisible(true);

    const displayState = isPlayer && localHeroDisplayOverride
      ? localHeroDisplayOverride
      : isRemoteAuthoritativeHero
      ? resolveRemoteHeroDisplayState({
          scene,
          worldViews,
          view,
          hero,
          deltaMs
        })
      : { position: hero.position, facing: hero.facing };
    const displayPosition = displayState.position;

    view.sprite.setPosition(displayPosition.x, displayPosition.y);
    view.sprite.setRotation(displayState.facing);
    syncLocalHeroMotionStreaks(view.localMotionStreaks, displayPosition, deltaMs);
    syncHeroReadabilityVisuals(view, hero, displayPosition, displayState.facing, isPlayer, snapshot.slowFields);
    if (view.nameLabel.text !== hero.displayName) {
      view.nameLabel.setText(hero.displayName);
    }
    view.nameLabel.setPosition(displayPosition.x, displayPosition.y - 52);
    view.healthBackground.setPosition(displayPosition.x, displayPosition.y - 36);
    view.healthFill.setPosition(displayPosition.x - 24, displayPosition.y - 36);
    syncHeroHealthVisuals(view, hero, snapshot.elapsedMs);

    const weapon = hero.weapons[hero.currentWeaponIndex];
    const showingSwitch = isPlayer && weaponSwitchRemainingMs > 0 && weaponSwitchTotalMs > 0;
    const showingReload = weapon.reloadRemaining > 0 && WEAPON_DEFINITIONS[weapon.weaponKind].reloadMs > 0;
    if (showingSwitch || showingReload) {
      const progress = showingSwitch
        ? 1 - weaponSwitchRemainingMs / weaponSwitchTotalMs
        : 1 - weapon.reloadRemaining / WEAPON_DEFINITIONS[weapon.weaponKind].reloadMs;
      view.actionBackground.setVisible(true);
      view.actionFill.setVisible(true);
      view.actionBackground.setPosition(displayPosition.x, displayPosition.y - 24);
      view.actionFill.setPosition(displayPosition.x - 25, displayPosition.y - 24);
      view.actionFill.displayWidth = 50 * Phaser.Math.Clamp(progress, 0, 1);
    }

    if (view.marker) {
      view.marker.setPosition(displayPosition.x, displayPosition.y);
    }

    if (isRemoteAuthoritativeHero) {
      recordRemoteHeroViewDiagnostics({
        heroId: hero.heroId,
        displayName: hero.displayName,
        displayPosition,
        targetPosition: hero.position,
        facing: displayState.facing,
        targetFacing: hero.facing
      });
    }
  });
}

function createLocalHeroMotionStreakView(scene: Phaser.Scene, position: Vec2): LocalHeroMotionStreakView {
  const streaks = Array.from({ length: LOCAL_HERO_MOTION_STREAK_COUNT }, (_unused, index) =>
    scene.add
      .rectangle(position.x, position.y, 18 + index * 8, 3, LOCAL_HERO_MOTION_TINT, 0)
      .setOrigin(1, 0.5)
      .setDepth(HERO_LOCAL_MOTION_STREAK_DEPTH)
      .setVisible(false)
  );

  return {
    streaks,
    lastPosition: null,
    lastAngle: 0,
    intensity: 0
  };
}

function syncLocalHeroMotionStreaks(
  view: LocalHeroMotionStreakView | null,
  displayPosition: Vec2,
  deltaMs: number
): void {
  if (!view || !isFiniteVec2(displayPosition)) {
    return;
  }

  const lastPosition = view.lastPosition;
  view.lastPosition = { x: displayPosition.x, y: displayPosition.y };

  if (!lastPosition || !isFiniteVec2(lastPosition)) {
    hideLocalHeroMotionStreaks(view, false);
    return;
  }

  const dx = displayPosition.x - lastPosition.x;
  const dy = displayPosition.y - lastPosition.y;
  const frameDistance = Math.hypot(dx, dy);
  const safeDeltaMs = Number.isFinite(deltaMs) ? Math.max(1, deltaMs) : 16.67;
  const speed = frameDistance * 1000 / safeDeltaMs;
  const speedIntensity = Phaser.Math.Clamp(
    (speed - LOCAL_HERO_MOTION_MIN_SPEED) / (LOCAL_HERO_MOTION_MAX_SPEED - LOCAL_HERO_MOTION_MIN_SPEED),
    0,
    1
  );

  if (speedIntensity > 0 && frameDistance > 0.05) {
    view.intensity = speedIntensity;
    view.lastAngle = Math.atan2(dy, dx);
  } else {
    view.intensity *= LOCAL_HERO_MOTION_DECAY;
  }

  if (view.intensity <= 0.04) {
    hideLocalHeroMotionStreaks(view, false);
    return;
  }

  const angle = view.lastAngle;
  const directionX = Math.cos(angle);
  const directionY = Math.sin(angle);
  view.streaks.forEach((streak, index) => {
    const falloff = 1 - index * 0.22;
    const offset = 14 + index * 11 + view.intensity * 10;
    const sideOffset = (index - 1) * 4;
    const alpha = 0.16 * view.intensity * falloff;
    streak.setVisible(true);
    streak.setPosition(
      displayPosition.x - directionX * offset - directionY * sideOffset,
      displayPosition.y - directionY * offset + directionX * sideOffset
    );
    streak.setRotation(angle);
    streak.setDisplaySize(20 + index * 9 + view.intensity * 14, 2 + view.intensity * 2);
    streak.setFillStyle(LOCAL_HERO_MOTION_TINT, alpha);
  });
}

function hideLocalHeroMotionStreaks(view: LocalHeroMotionStreakView | null, resetPosition: boolean): void {
  if (!view) {
    return;
  }

  view.intensity = 0;
  if (resetPosition) {
    view.lastPosition = null;
  }
  view.streaks.forEach((streak) => {
    streak.setVisible(false);
    streak.setFillStyle(LOCAL_HERO_MOTION_TINT, 0);
  });
}

function resolveHeroReadabilityRadius(radius: number): number {
  return Math.max(HERO_READABILITY_MIN_RADIUS, Number.isFinite(radius) ? radius : HERO_READABILITY_MIN_RADIUS);
}

function resolveHeroWeaponKind(hero: Hero): WeaponKind {
  return hero.weapons[hero.currentWeaponIndex]?.weaponKind ?? "Pistol";
}

function getWeaponCueReadabilityStyle(weaponKind: WeaponKind): WeaponCueReadabilityStyle {
  return WEAPON_CUE_READABILITY_STYLES[weaponKind];
}

function syncHeroReadabilityVisuals(
  view: HeroView,
  hero: Hero,
  displayPosition: Vec2,
  displayFacing: number,
  isPlayer: boolean,
  slowFields: readonly SlowField[]
): void {
  const radius = resolveHeroReadabilityRadius(hero.radius);
  view.shadow.setPosition(displayPosition.x, displayPosition.y);
  view.shadow.setRadius(radius * 1.08);
  view.bodyDisc.setPosition(displayPosition.x, displayPosition.y);
  view.bodyDisc.setRadius(radius);
  view.silhouetteRing.setPosition(displayPosition.x, displayPosition.y);
  view.silhouetteRing.setRadius(radius + 1);
  view.hitRing.setPosition(displayPosition.x, displayPosition.y);
  view.hitRing.setRadius(radius);
  const slowed = isHeroInsideSlowField(hero, slowFields);
  view.statusRing.setVisible(slowed);
  if (slowed) {
    view.statusRing.setPosition(displayPosition.x, displayPosition.y);
    view.statusRing.setRadius(radius + 6);
    view.statusRing.setFillStyle(HERO_SLOWED_STATUS_TINT, HERO_SLOWED_STATUS_FILL_ALPHA);
    view.statusRing.setStrokeStyle(2, HERO_SLOWED_STATUS_TINT, HERO_SLOWED_STATUS_STROKE_ALPHA);
  }
  const weaponKind = resolveHeroWeaponKind(hero);
  const weaponCueStyle = getWeaponCueReadabilityStyle(weaponKind);
  const directionX = Math.cos(displayFacing);
  const directionY = Math.sin(displayFacing);
  const cueLength = radius * weaponCueStyle.lengthRadiusScale;
  const cueOriginOffset = radius * 0.22;
  const alpha = isPlayer ? weaponCueStyle.localAlpha : weaponCueStyle.remoteAlpha;
  const strokeAlpha = isPlayer ? weaponCueStyle.localStrokeAlpha : weaponCueStyle.remoteStrokeAlpha;

  view.weaponStock.setPosition(displayPosition.x - directionX * radius * 0.05, displayPosition.y - directionY * radius * 0.05);
  view.weaponStock.setRotation(displayFacing);
  view.weaponStock.setDisplaySize(radius * weaponCueStyle.stockLengthRadiusScale, weaponCueStyle.thickness * weaponCueStyle.stockThicknessScale);
  view.weaponStock.setFillStyle(weaponCueStyle.strokeTint, alpha * weaponCueStyle.stockAlphaScale);
  view.weaponStock.setStrokeStyle(1, weaponCueStyle.tint, strokeAlpha * 0.72);

  view.weaponCue.setPosition(displayPosition.x + directionX * cueOriginOffset, displayPosition.y + directionY * cueOriginOffset);
  view.weaponCue.setRotation(displayFacing);
  view.weaponCue.setDisplaySize(cueLength, weaponCueStyle.thickness);
  view.weaponCue.setFillStyle(weaponCueStyle.tint, alpha);
  view.weaponCue.setStrokeStyle(
    weaponCueStyle.strokeWidth,
    weaponCueStyle.strokeTint,
    strokeAlpha
  );

  view.weaponMuzzle.setPosition(
    displayPosition.x + directionX * (cueOriginOffset + cueLength * 0.92),
    displayPosition.y + directionY * (cueOriginOffset + cueLength * 0.92)
  );
  view.weaponMuzzle.setRadius(weaponCueStyle.muzzleRadius);
  view.weaponMuzzle.setFillStyle(weaponCueStyle.strokeTint, alpha * weaponCueStyle.muzzleAlphaScale);
  view.weaponMuzzle.setStrokeStyle(1, weaponCueStyle.tint, strokeAlpha);
  syncHeroWeaponOverlayVisuals({
    view: view.weaponOverlay,
    weaponKind,
    displayPosition,
    displayFacing,
    radius,
    cueOriginOffset,
    cueLength,
    alpha,
    strokeAlpha
  });
}

function isHeroInsideSlowField(hero: Hero, slowFields: readonly SlowField[]): boolean {
  if (!isFiniteVec2(hero.position)) {
    return false;
  }

  return slowFields.some((field) => {
    if (!isFiniteVec2(field.position) || !Number.isFinite(field.radius) || field.radius <= 0) {
      return false;
    }

    return Phaser.Math.Distance.Between(hero.position.x, hero.position.y, field.position.x, field.position.y) <= field.radius + hero.radius;
  });
}

function syncHeroHealthVisuals(view: HeroView, hero: Hero, elapsedMs: number): void {
  const healthRatio = resolveHeroHealthRatio(hero);
  const visual = resolveHeroVisual(hero.heroId);
  const fillTint =
    healthRatio <= HERO_HEALTH_DANGER_RATIO
      ? HERO_HEALTH_DANGER_TINT
      : healthRatio <= HERO_HEALTH_WARNING_RATIO
      ? HERO_HEALTH_WARNING_TINT
      : visual.tint;
  const pulseElapsedMs = Number.isFinite(elapsedMs) ? elapsedMs : 0;
  const dangerPulse = healthRatio <= HERO_HEALTH_DANGER_RATIO ? 0.04 + Math.sin(pulseElapsedMs / 170) * 0.04 : 0;
  const backgroundAlpha = Phaser.Math.Clamp(
    HERO_HEALTH_BACKGROUND_NORMAL_ALPHA + dangerPulse,
    HERO_HEALTH_BACKGROUND_NORMAL_ALPHA,
    1
  );

  view.healthFill.displayWidth = 48 * healthRatio;
  view.healthFill.setFillStyle(fillTint, 1);
  view.healthBackground.setFillStyle(HERO_HEALTH_BACKGROUND_TINT, backgroundAlpha);
}

function resolveHeroHealthRatio(hero: Hero): number {
  if (!Number.isFinite(hero.hp) || !Number.isFinite(hero.maxHp) || hero.maxHp <= 0) {
    return 0;
  }

  return Phaser.Math.Clamp(hero.hp / hero.maxHp, 0, 1);
}

interface CleanupRemoteHeroInterpolationBuffersInput {
  snapshot: GameSnapshot;
  worldViews: WorldViewState;
  sharedAuthoritativeRuntime: boolean;
  remoteAuthoritativeHeroIds: ReadonlySet<string>;
}

function cleanupRemoteHeroInterpolationBuffers({
  snapshot,
  worldViews,
  sharedAuthoritativeRuntime,
  remoteAuthoritativeHeroIds
}: CleanupRemoteHeroInterpolationBuffersInput): void {
  if (!sharedAuthoritativeRuntime) {
    worldViews.remoteHeroInterpolationBuffers.clear();
    return;
  }

  const activeRemoteHeroIds = worldViews.scratchActiveRemoteHeroIds;
  activeRemoteHeroIds.clear();
  snapshot.heroes.forEach((hero) => {
    if (
      hero.alive &&
      hero.heroId !== snapshot.playerHeroId &&
      remoteAuthoritativeHeroIds.has(hero.heroId) &&
      worldViews.heroViews.has(hero.heroId)
    ) {
      activeRemoteHeroIds.add(hero.heroId);
    }
  });

  for (const heroId of worldViews.remoteHeroInterpolationBuffers.keys()) {
    if (!activeRemoteHeroIds.has(heroId)) {
      worldViews.remoteHeroInterpolationBuffers.delete(heroId);
    }
  }
}

interface RemoteHeroDisplayState {
  position: Vec2;
  facing: number;
}

interface ResolveRemoteHeroDisplayStateInput {
  scene: Phaser.Scene;
  worldViews: WorldViewState;
  view: HeroView;
  hero: Hero;
  deltaMs: number;
}

function resolveRemoteHeroDisplayState({
  scene,
  worldViews,
  view,
  hero,
  deltaMs
}: ResolveRemoteHeroDisplayStateInput): RemoteHeroDisplayState {
  const receivedAtMs = resolveRenderNowMs(scene);
  const sample = createRemoteHeroInterpolationSample(hero, receivedAtMs);

  if (!sample) {
    return resolveRemoteHeroFallbackDisplayState(view, hero, deltaMs);
  }

  const buffer = getRemoteHeroInterpolationBuffer(worldViews, hero.heroId);
  recordRemoteHeroInterpolationSample(buffer, sample);

  return resolveInterpolatedRemoteHeroDisplayState(buffer, receivedAtMs) ?? resolveRemoteHeroFallbackDisplayState(view, hero, deltaMs);
}

function getRemoteHeroInterpolationBuffer(worldViews: WorldViewState, heroId: string): RemoteHeroInterpolationBuffer {
  const existing = worldViews.remoteHeroInterpolationBuffers.get(heroId);
  if (existing) {
    return existing;
  }

  const created: RemoteHeroInterpolationBuffer = { samples: [] };
  worldViews.remoteHeroInterpolationBuffers.set(heroId, created);
  return created;
}

function createRemoteHeroInterpolationSample(hero: Hero, receivedAtMs: number): RemoteHeroInterpolationSample | null {
  if (!Number.isFinite(receivedAtMs) || !isFiniteVec2(hero.position) || !Number.isFinite(hero.facing)) {
    return null;
  }

  return {
    receivedAtMs,
    position: { x: hero.position.x, y: hero.position.y },
    facing: hero.facing
  };
}

function recordRemoteHeroInterpolationSample(
  buffer: RemoteHeroInterpolationBuffer,
  sample: RemoteHeroInterpolationSample
): void {
  const lastSample = buffer.samples[buffer.samples.length - 1];
  if (lastSample) {
    const distance = Phaser.Math.Distance.Between(lastSample.position.x, lastSample.position.y, sample.position.x, sample.position.y);
    if (!Number.isFinite(distance) || distance >= AUTHORITATIVE_REMOTE_HERO_SNAP_DISTANCE) {
      buffer.samples = [sample];
      return;
    }

    const facingDelta = Math.abs(Phaser.Math.Angle.Wrap(sample.facing - lastSample.facing));
    if (distance <= AUTHORITATIVE_REMOTE_HERO_POSITION_EPSILON && facingDelta <= AUTHORITATIVE_REMOTE_HERO_FACING_EPSILON) {
      return;
    }

    if (sample.receivedAtMs <= lastSample.receivedAtMs) {
      sample.receivedAtMs = lastSample.receivedAtMs + 0.001;
    }
  }

  buffer.samples.push(sample);
  if (buffer.samples.length > AUTHORITATIVE_REMOTE_HERO_INTERPOLATION_BUFFER_CAP) {
    buffer.samples.splice(0, buffer.samples.length - AUTHORITATIVE_REMOTE_HERO_INTERPOLATION_BUFFER_CAP);
  }
}

function resolveInterpolatedRemoteHeroDisplayState(
  buffer: RemoteHeroInterpolationBuffer,
  renderNowMs: number
): RemoteHeroDisplayState | null {
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

function resolveRemoteHeroFallbackDisplayState(view: HeroView, hero: Hero, deltaMs: number): RemoteHeroDisplayState {
  const currentPosition = resolveFinitePosition({ x: view.sprite.x, y: view.sprite.y });
  const targetPosition = isFiniteVec2(hero.position) ? hero.position : currentPosition;
  return {
    position: resolveSmoothedDisplayPosition({
      current: currentPosition,
      target: targetPosition,
      deltaMs,
      smoothingMs: AUTHORITATIVE_REMOTE_HERO_SMOOTHING_MS,
      snapDistance: AUTHORITATIVE_REMOTE_HERO_SNAP_DISTANCE
    }),
    facing: Number.isFinite(hero.facing) ? hero.facing : view.sprite.rotation
  };
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

export function getHeroDisplayPosition(worldViews: WorldViewState, heroId: string): Vec2 | null {
  const view = worldViews.heroViews.get(heroId);
  if (!view?.sprite.active || !view.sprite.visible) {
    return null;
  }

  return { x: view.sprite.x, y: view.sprite.y };
}

export function getProjectileDisplayPosition(worldViews: WorldViewState, projectileId: string): Vec2 | null {
  return getProjectileDisplayPositionFromViews(worldViews, projectileId);
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

export function syncPickupViews({ snapshot, worldViews }: WorldViewSyncContext): void {
  const liveWeaponPickupIds = worldViews.scratchLiveWeaponPickupIds;
  liveWeaponPickupIds.clear();
  snapshot.weaponPickups.forEach((pickup) => {
    liveWeaponPickupIds.add(pickup.weaponId);
  });

  for (const [weaponId, view] of worldViews.pickupViews.entries()) {
    if (liveWeaponPickupIds.has(weaponId)) {
      continue;
    }

    setPickupViewVisible(view, false);
  }

  const liveItemPickupIds = worldViews.scratchLiveItemPickupIds;
  liveItemPickupIds.clear();
  snapshot.itemPickups.forEach((pickup) => {
    liveItemPickupIds.add(pickup.pickupId);
  });

  for (const [pickupId, view] of worldViews.itemPickupViews.entries()) {
    if (liveItemPickupIds.has(pickupId)) {
      continue;
    }

    setPickupViewVisible(view, false);
  }

  snapshot.weaponPickups.forEach((pickup) => {
    const view = worldViews.pickupViews.get(pickup.weaponId);
    if (!view) {
      return;
    }

    syncWeaponPickupView(view, pickup, snapshot.elapsedMs);
  });

  snapshot.itemPickups.forEach((pickup) => {
    const view = worldViews.itemPickupViews.get(pickup.pickupId);
    if (!view) {
      return;
    }

    syncItemPickupView(view, pickup, snapshot.elapsedMs);
  });
}

export function syncIndicators({
  snapshot,
  worldViews,
  pointerWorld,
  isBlinkTargetValid,
  isPreparedTargetValid,
  sharedAuthoritativeRuntime = false,
  localHeroDisplayOverride
}: WorldViewSyncContext): void {
  const player = snapshot.heroes.find((hero) => hero.heroId === snapshot.playerHeroId);
  const preparedSkill = player?.preparedSkill ?? null;

  if (!player || !player.alive || !isPreparedTargetSkillKind(preparedSkill)) {
    worldViews.rangeIndicator.setVisible(false);
    worldViews.targetIndicator.setVisible(false);
    return;
  }

  const displayPosition = sharedAuthoritativeRuntime ? player.position : localHeroDisplayOverride?.position ?? player.position;
  const profile = getPreparedTargetSkillRuntimeProfile(preparedSkill);
  const skill = player.skills.find((entry) => entry.kind === preparedSkill) ?? null;
  const valid = Boolean(
    skill &&
      skill.cooldownMs <= 0 &&
      isPreparedIndicatorTargetValid(
        player,
        preparedSkill,
        pointerWorld,
        displayPosition,
        localHeroDisplayOverride,
        isBlinkTargetValid,
        isPreparedTargetValid
      )
  );
  const color = valid ? 0x69ff9f : 0xff6b6b;

  worldViews.rangeIndicator.setVisible(true);
  worldViews.rangeIndicator.setPosition(displayPosition.x, displayPosition.y);
  worldViews.rangeIndicator.setRadius(profile.target.range);
  worldViews.rangeIndicator.setFillStyle(color, 0.05);
  worldViews.rangeIndicator.setStrokeStyle(2, color, 0.88);

  worldViews.targetIndicator.setVisible(true);
  worldViews.targetIndicator.setPosition(pointerWorld.x, pointerWorld.y);
  worldViews.targetIndicator.setRadius(profile.target.indicatorRadius);
  worldViews.targetIndicator.setFillStyle(color, 0.16);
  worldViews.targetIndicator.setStrokeStyle(2, color, 0.88);
}

function isPreparedIndicatorTargetValid(
  player: Hero,
  preparedSkill: PreparedTargetSkillKind,
  target: Vec2,
  displayPosition: Vec2,
  localHeroDisplayOverride: LocalHeroDisplayOverride | undefined,
  isBlinkTargetValid: WorldViewSyncContext["isBlinkTargetValid"],
  isPreparedTargetValid: WorldViewSyncContext["isPreparedTargetValid"]
): boolean {
  if (isPreparedTargetValid) {
    return isPreparedTargetValid(player, preparedSkill, target);
  }

  switch (preparedSkill) {
    case "Blink":
      return isBlinkIndicatorTargetValid(player, target, localHeroDisplayOverride, isBlinkTargetValid);
    case "Freeze":
      return isFreezeIndicatorTargetValid(target, displayPosition);
  }
}

function isFreezeIndicatorTargetValid(
  target: Vec2,
  displayPosition: Vec2
): boolean {
  const profile = getPreparedTargetSkillRuntimeProfile("Freeze");
  return Phaser.Math.Distance.Between(displayPosition.x, displayPosition.y, target.x, target.y) <= profile.target.range;
}

function isBlinkIndicatorTargetValid(
  player: Hero,
  target: Vec2,
  localHeroDisplayOverride: LocalHeroDisplayOverride | undefined,
  isBlinkTargetValid: WorldViewSyncContext["isBlinkTargetValid"]
): boolean {
  return isBlinkTargetValid(
    localHeroDisplayOverride
      ? { ...player, position: localHeroDisplayOverride.position, facing: localHeroDisplayOverride.facing }
      : player,
    target
  );
}

export function syncWorldViews(context: WorldViewSyncContext): void {
  syncHeroViews(context);
  syncSlowFieldViews(context);
  syncProjectileViews(context);
  syncPickupViews(context);
  syncIndicators(context);
}
