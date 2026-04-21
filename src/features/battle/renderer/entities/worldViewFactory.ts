import Phaser from "phaser";
import type { GameSnapshot, Hero, Projectile, SlowField, Vec2 } from "../../../../domain/types";
import { BULLET_TEXTURE_KEY, CRATE_TEXTURE_KEY, ROCKET_TEXTURE_KEY, WEAPON_PICKUP_ICON_KEYS } from "../../../../game/constants";
import { resolveHeroVisual } from "../../../../game/spawn";
import { WEAPON_DEFINITIONS } from "../../../../game/weapons";
import { SKILL_DEFINITIONS } from "../../../../game/skills";
import { getItemPickupDisplayLabel, getWeaponDisplayLabel, getWeaponPickupTint } from "../../presenters/battleDisplayCatalog";

export interface HeroView {
  sprite: Phaser.GameObjects.Image;
  nameLabel: Phaser.GameObjects.Text;
  healthBackground: Phaser.GameObjects.Rectangle;
  healthFill: Phaser.GameObjects.Rectangle;
  actionBackground: Phaser.GameObjects.Rectangle;
  actionFill: Phaser.GameObjects.Rectangle;
  marker: Phaser.GameObjects.Arc | null;
}

export interface ProjectileView {
  sprite: Phaser.GameObjects.Image;
}

export interface PickupView {
  sprite: Phaser.GameObjects.Image;
  label: Phaser.GameObjects.Text;
}

export interface SlowFieldView {
  fill: Phaser.GameObjects.Arc;
  rim: Phaser.GameObjects.Arc;
}

export interface WorldViewState {
  heroViews: Map<string, HeroView>;
  projectileViews: Map<string, ProjectileView>;
  slowFieldViews: Map<string, SlowFieldView>;
  pickupViews: Map<string, PickupView>;
  itemPickupViews: Map<string, PickupView>;
  rangeIndicator: Phaser.GameObjects.Arc;
  targetIndicator: Phaser.GameObjects.Arc;
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
  weaponSwitchRemainingMs: number;
  weaponSwitchTotalMs: number;
  pointerWorld: Vec2;
  isBlinkTargetValid: (player: Hero, target: Vec2) => boolean;
}

export function createWorldViewState(context: WorldViewFactoryContext): WorldViewState {
  const { scene, snapshot, getBaseHeroScale } = context;
  const heroViews = new Map<string, HeroView>();
  const projectileViews = new Map<string, ProjectileView>();
  const slowFieldViews = new Map<string, SlowFieldView>();
  const pickupViews = new Map<string, PickupView>();
  const itemPickupViews = new Map<string, PickupView>();

  snapshot.heroes.forEach((hero) => {
    const visual = resolveHeroVisual(hero.heroId);
    const sprite = scene.add
      .image(hero.position.x, hero.position.y, visual.textureKey)
      .setScale(getBaseHeroScale(hero.heroId))
      .setTint(visual.tint)
      .setDepth(hero.heroId === snapshot.playerHeroId ? 50 : 42);

    const marker =
      hero.heroId === snapshot.playerHeroId
        ? scene.add.circle(hero.position.x, hero.position.y, 30, 0x4ad9ff, 0.06).setDepth(38)
        : null;

    if (marker) {
      marker.setStrokeStyle(2, 0x76e4ff, 0.85);
    }

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
    const sprite = scene.add
      .image(pickup.position.x, pickup.position.y, WEAPON_PICKUP_ICON_KEYS[pickup.weaponKind])
      .setScale(pickup.weaponKind === "RocketLauncher" ? 1.05 : 0.95)
      .setDepth(62);

    const label = scene.add
      .text(pickup.position.x, pickup.position.y + 26, getWeaponDisplayLabel(pickup.weaponKind), {
        fontFamily: "Segoe UI",
        fontSize: "12px",
        color: "#f4f8ff"
      })
      .setOrigin(0.5, 0)
      .setDepth(63);

    sprite.setTint(getWeaponPickupTint(pickup.weaponKind));
    pickupViews.set(pickup.weaponId, { sprite, label });
  });

  snapshot.itemPickups.forEach((pickup) => {
    const sprite = scene.add.image(pickup.position.x, pickup.position.y, CRATE_TEXTURE_KEY).setDepth(62);
    sprite.setScale(0.72);
    sprite.setTint(0x7bff9b);

    const label = scene.add
      .text(pickup.position.x, pickup.position.y + 26, getItemPickupDisplayLabel(pickup.kind), {
        fontFamily: "Segoe UI",
        fontSize: "12px",
        color: "#f4f8ff"
      })
      .setOrigin(0.5, 0)
      .setDepth(63);

    itemPickupViews.set(pickup.pickupId, { sprite, label });
  });

  const rangeIndicator = scene.add.circle(0, 0, 1, 0x69d2ff, 0.05).setDepth(16).setVisible(false);
  rangeIndicator.setStrokeStyle(2, 0x69d2ff, 0.85);

  const targetIndicator = scene.add.circle(0, 0, 11, 0x69d2ff, 0.15).setDepth(17).setVisible(false);
  targetIndicator.setStrokeStyle(2, 0x69d2ff, 0.85);

  return {
    heroViews,
    projectileViews,
    slowFieldViews,
    pickupViews,
    itemPickupViews,
    rangeIndicator,
    targetIndicator
  };
}

export function syncHeroViews({ snapshot, worldViews, weaponSwitchRemainingMs, weaponSwitchTotalMs }: WorldViewSyncContext): void {
  snapshot.heroes.forEach((hero) => {
    const view = worldViews.heroViews.get(hero.heroId);
    if (!view) {
      return;
    }

    if (!hero.alive) {
      view.sprite.setVisible(false);
      view.nameLabel.setVisible(false);
      view.healthBackground.setVisible(false);
      view.healthFill.setVisible(false);
      view.actionBackground.setVisible(false);
      view.actionFill.setVisible(false);
      view.marker?.setVisible(false);
      return;
    }

    view.sprite.setVisible(true);
    view.nameLabel.setVisible(true);
    view.healthBackground.setVisible(true);
    view.healthFill.setVisible(true);
    view.actionBackground.setVisible(false);
    view.actionFill.setVisible(false);
    view.marker?.setVisible(true);

    view.sprite.setPosition(hero.position.x, hero.position.y);
    view.sprite.setRotation(hero.facing);
    view.nameLabel.setPosition(hero.position.x, hero.position.y - 52);
    view.healthBackground.setPosition(hero.position.x, hero.position.y - 36);
    view.healthFill.setPosition(hero.position.x - 24, hero.position.y - 36);
    view.healthFill.displayWidth = 48 * (hero.hp / hero.maxHp);

    const weapon = hero.weapons[hero.currentWeaponIndex];
    const isPlayer = hero.heroId === snapshot.playerHeroId;
    const showingSwitch = isPlayer && weaponSwitchRemainingMs > 0 && weaponSwitchTotalMs > 0;
    const showingReload = weapon.reloadRemaining > 0 && WEAPON_DEFINITIONS[weapon.weaponKind].reloadMs > 0;
    if (showingSwitch || showingReload) {
      const progress = showingSwitch
        ? 1 - weaponSwitchRemainingMs / weaponSwitchTotalMs
        : 1 - weapon.reloadRemaining / WEAPON_DEFINITIONS[weapon.weaponKind].reloadMs;
      view.actionBackground.setVisible(true);
      view.actionFill.setVisible(true);
      view.actionBackground.setPosition(hero.position.x, hero.position.y - 24);
      view.actionFill.setPosition(hero.position.x - 25, hero.position.y - 24);
      view.actionFill.displayWidth = 50 * Phaser.Math.Clamp(progress, 0, 1);
    }

    if (view.marker) {
      view.marker.setPosition(hero.position.x, hero.position.y);
    }
  });
}

export function syncProjectileViews({ scene, snapshot, worldViews }: WorldViewSyncContext): void {
  const liveIds = new Set(snapshot.projectiles.map((projectile) => projectile.projectileId));

  snapshot.projectiles.forEach((projectile) => {
    const existing = worldViews.projectileViews.get(projectile.projectileId) ?? createProjectileView(scene, projectile);
    worldViews.projectileViews.set(projectile.projectileId, existing);
    existing.sprite.setPosition(projectile.position.x, projectile.position.y);
    existing.sprite.setRotation(projectile.facing);
  });

  for (const [projectileId, view] of worldViews.projectileViews.entries()) {
    if (liveIds.has(projectileId)) {
      continue;
    }

    view.sprite.destroy();
    worldViews.projectileViews.delete(projectileId);
  }
}

export function syncSlowFieldViews({ scene, snapshot, worldViews }: WorldViewSyncContext): void {
  const liveIds = new Set(snapshot.slowFields.map((field) => field.fieldId));

  snapshot.slowFields.forEach((field) => {
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

  return { sprite };
}

export function syncPickupViews({ snapshot, worldViews }: WorldViewSyncContext): void {
  snapshot.weaponPickups.forEach((pickup) => {
    const view = worldViews.pickupViews.get(pickup.weaponId);
    if (!view) {
      return;
    }

    if (!pickup.available) {
      view.sprite.setVisible(false);
      view.label.setVisible(false);
      return;
    }

    const bob = Math.sin((snapshot.elapsedMs + pickup.position.x) / 240) * 4;
    view.sprite.setVisible(true);
    view.label.setVisible(true);
    view.sprite.setPosition(pickup.position.x, pickup.position.y + bob);
    view.label.setPosition(pickup.position.x, pickup.position.y + 28);
  });

  snapshot.itemPickups.forEach((pickup) => {
    const view = worldViews.itemPickupViews.get(pickup.pickupId);
    if (!view) {
      return;
    }

    if (!pickup.available) {
      view.sprite.setVisible(false);
      view.label.setVisible(false);
      return;
    }

    const bob = Math.sin((snapshot.elapsedMs + pickup.position.x) / 260) * 3;
    view.sprite.setVisible(true);
    view.label.setVisible(true);
    view.sprite.setPosition(pickup.position.x, pickup.position.y + bob);
    view.label.setPosition(pickup.position.x, pickup.position.y + 28);
  });
}

export function syncIndicators({ snapshot, worldViews, pointerWorld, isBlinkTargetValid }: WorldViewSyncContext): void {
  const player = snapshot.heroes.find((hero) => hero.heroId === snapshot.playerHeroId);
  const blink = player ? player.skills.find((skill) => skill.kind === "Blink") : null;
  const freeze = player ? player.skills.find((skill) => skill.kind === "Freeze") : null;

  if (!player || !player.alive || (player.preparedSkill !== "Blink" && player.preparedSkill !== "Freeze")) {
    worldViews.rangeIndicator.setVisible(false);
    worldViews.targetIndicator.setVisible(false);
    return;
  }

  const isFreeze = player.preparedSkill === "Freeze";
  const definition = isFreeze ? SKILL_DEFINITIONS.Freeze : SKILL_DEFINITIONS.Blink;
  const valid = isFreeze
    ? Boolean(freeze && freeze.cooldownMs <= 0 && Phaser.Math.Distance.Between(player.position.x, player.position.y, pointerWorld.x, pointerWorld.y) <= definition.range)
    : Boolean(blink && blink.cooldownMs <= 0 && isBlinkTargetValid(player, pointerWorld));
  const color = valid ? 0x69ff9f : 0xff6b6b;

  worldViews.rangeIndicator.setVisible(true);
  worldViews.rangeIndicator.setPosition(player.position.x, player.position.y);
  worldViews.rangeIndicator.setRadius(definition.range);
  worldViews.rangeIndicator.setFillStyle(color, 0.05);
  worldViews.rangeIndicator.setStrokeStyle(2, color, 0.88);

  worldViews.targetIndicator.setVisible(true);
  worldViews.targetIndicator.setPosition(pointerWorld.x, pointerWorld.y);
  worldViews.targetIndicator.setRadius(isFreeze ? SKILL_DEFINITIONS.Freeze.radius : 11);
  worldViews.targetIndicator.setFillStyle(color, 0.16);
  worldViews.targetIndicator.setStrokeStyle(2, color, 0.88);
}

export function syncWorldViews(context: WorldViewSyncContext): void {
  syncHeroViews(context);
  syncSlowFieldViews(context);
  syncProjectileViews(context);
  syncPickupViews(context);
  syncIndicators(context);
}
