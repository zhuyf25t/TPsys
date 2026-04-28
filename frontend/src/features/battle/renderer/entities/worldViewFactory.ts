import Phaser from "phaser";
import type { GameSnapshot, Hero, PreparedSkill, Vec2 } from "../../../../domain/types";
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
  type HeroWeaponOverlayView
} from "./heroWeaponOverlayView";
import {
  createHeroReadabilityView,
  syncHeroHealthVisuals,
  syncHeroReadabilityVisuals,
  type HeroHealthView,
  type HeroReadabilitySyncView
} from "./heroReadabilityView";
import {
  createLocalHeroMotionStreakView,
  hideLocalHeroMotionStreaks,
  syncLocalHeroMotionStreaks,
  type LocalHeroMotionStreakView
} from "./localHeroMotionStreakView";
import {
  createItemPickupView,
  createWeaponPickupView,
  type PickupView
} from "./pickupViewPresentation";
import { syncPickupViews } from "./pickupViewSync";
import {
  getProjectileDisplayPositionFromViews,
  syncProjectileViews,
  syncSlowFieldViews,
  type ProjectileInterpolationBuffer,
  type ProjectileView,
  type SlowFieldView
} from "./projectileAndFieldViewPresentation";
import {
  cleanupRemoteHeroInterpolationBuffers,
  resolveRemoteHeroDisplayState,
  type RemoteHeroInterpolationBuffer
} from "./remoteHeroInterpolationView";

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

const EMPTY_REMOTE_AUTH_HERO_IDS: ReadonlySet<string> = new Set<string>();

export { syncPickupViews };

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
    const readabilityView = createHeroReadabilityView(scene, hero, isPlayer);
    const weaponOverlay = createHeroWeaponOverlayView(scene, hero.position);
    const sprite = scene.add
      .image(hero.position.x, hero.position.y, visual.textureKey)
      .setScale(getBaseHeroScale(hero.heroId))
      .setTint(visual.tint)
      .setDepth(spriteDepth);

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
      ...readabilityView,
      weaponOverlay,
      sprite,
      nameLabel,
      healthBackground,
      healthFill,
      actionBackground,
      actionFill,
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
