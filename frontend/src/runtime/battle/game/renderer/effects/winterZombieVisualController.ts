import Phaser from "phaser";
import type { BattleGameSnapshot as GameSnapshot } from "../../../../../objects/battle/microservices/session/objects/state/BattleGameSnapshot";
import type { BattleHeroViewState as Hero } from "../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { BattleVector2 as Vec2 } from "../../../../../objects/battle/objects/core/BattleCoreScalars";
import { getActiveBattleMap } from "../../objects/BattleGameConstants";
import type { HeroView } from "../entities/worldViewFactory";

const SNOWFLAKE_COUNT = 42;
const FOOTPRINT_GAP = 48;
const FOOTPRINT_TTL_MS = 6000;
const BLOOD_TTL_MS = 12000;
const MAX_FOOTPRINTS = 80;
const MAX_BLOOD_DECALS = 32;
const HORDE_CYCLE_MS = 30000;
const HORDE_WARNING_MS = 7200;
const HORDE_BREACH_FLASH_MS = 1800;
const TRAIL_DECAL_UPDATE_INTERVAL_MS = 160;
const ZOMBIE_GLOW_UPDATE_INTERVAL_MS = 160;
const HORDE_WARNING_UPDATE_INTERVAL_MS = 200;

type SnowView = Phaser.GameObjects.Arc | Phaser.GameObjects.Rectangle;
type DecalView = Phaser.GameObjects.Ellipse | Phaser.GameObjects.Image;

interface Snowflake {
  view: SnowView;
  x: number;
  y: number;
  speedX: number;
  speedY: number;
  driftPhase: number;
}

interface HeroTrailState {
  lastFootprint: Vec2 | null;
  lastPosition: Vec2 | null;
  lastHp: number;
  wasAlive: boolean;
}

interface HordeWarningPlan {
  label: string;
  points: readonly Vec2[];
}

export class WinterZombieVisualController {
  private readonly snowflakes: Snowflake[] = [];
  private readonly heroTrailStates = new Map<string, HeroTrailState>();
  private readonly zombieGlows = new Map<string, Phaser.GameObjects.Arc>();
  private readonly footprints: Phaser.GameObjects.Ellipse[] = [];
  private readonly bloodDecals: DecalView[] = [];
  private readonly edgeStrips: Phaser.GameObjects.Rectangle[];
  private readonly spawnPings: Phaser.GameObjects.Arc[];
  private readonly wavePanel: Phaser.GameObjects.Rectangle;
  private readonly waveText: Phaser.GameObjects.Text;
  private readonly waveSubtext: Phaser.GameObjects.Text;
  private ambientVisible: boolean | null = null;
  private trailDecalUpdateElapsedMs = TRAIL_DECAL_UPDATE_INTERVAL_MS;
  private zombieGlowUpdateElapsedMs = ZOMBIE_GLOW_UPDATE_INTERVAL_MS;
  private hordeWarningUpdateElapsedMs = HORDE_WARNING_UPDATE_INTERVAL_MS;

  public constructor(private readonly scene: Phaser.Scene) {
    this.createBlizzardLayer();
    this.edgeStrips = this.createEdgeWarningStrips();
    this.spawnPings = this.createSpawnPings();
    this.wavePanel = scene.add
      .rectangle(0, 0, 292, 58, 0x06100a, 0.64)
      .setScrollFactor(0)
      .setDepth(2100)
      .setStrokeStyle(1, 0x9cff6f, 0.42);
    this.waveText = scene.add
      .text(0, 0, "HORDE IN 00:30", {
        fontFamily: "Segoe UI",
        fontSize: "18px",
        fontStyle: "bold",
        color: "#b7ff6a",
        stroke: "#061007",
        strokeThickness: 4
      })
      .setOrigin(0.5)
      .setScrollFactor(0)
      .setDepth(2101);
    this.waveSubtext = scene.add
      .text(0, 0, "EDGE ACTIVITY: NORTH", {
        fontFamily: "Segoe UI",
        fontSize: "11px",
        color: "#d9ffe8",
        stroke: "#061007",
        strokeThickness: 3
      })
      .setOrigin(0.5)
      .setScrollFactor(0)
      .setDepth(2101);
  }

  public update(snapshot: GameSnapshot, heroViews: ReadonlyMap<string, HeroView>, deltaMs: number): void {
    const enabled = getActiveBattleMap().themeId === "winter";
    this.setAmbientVisible(enabled);
    if (!enabled) {
      return;
    }

    const clampedDeltaMs = Number.isFinite(deltaMs) ? Math.min(100, Math.max(0, deltaMs)) : 0;
    this.updateBlizzard(clampedDeltaMs, snapshot.elapsedMs);
    if (this.shouldRunTrailDecalUpdate(clampedDeltaMs)) {
      this.updateTrailDecals(snapshot, heroViews);
    }
    if (this.shouldRunZombieGlowUpdate(clampedDeltaMs)) {
      this.updateZombieGlows(snapshot, heroViews);
    }
    if (this.shouldRunHordeWarningUpdate(clampedDeltaMs)) {
      this.updateHordeWarning(snapshot);
    }
  }

  public destroy(): void {
    [
      ...this.snowflakes.map((flake) => flake.view),
      ...this.edgeStrips,
      ...this.spawnPings,
      ...this.zombieGlows.values(),
      ...this.footprints,
      ...this.bloodDecals,
      this.wavePanel,
      this.waveText,
      this.waveSubtext
    ].forEach((view) => {
      this.scene.tweens.killTweensOf(view);
      view.destroy();
    });
    this.snowflakes.length = 0;
    this.edgeStrips.length = 0;
    this.spawnPings.length = 0;
    this.zombieGlows.clear();
    this.footprints.length = 0;
    this.bloodDecals.length = 0;
    this.heroTrailStates.clear();
  }

  private createBlizzardLayer(): void {
    for (let index = 0; index < SNOWFLAKE_COUNT; index += 1) {
      const fastStreak = index % 4 === 0;
      const view = fastStreak
        ? this.scene.add.rectangle(0, 0, this.randomBetween(14, 34), this.randomBetween(1, 3), 0xffffff, this.randomBetween(0.16, 0.34))
        : this.scene.add.circle(0, 0, this.randomBetween(0.9, 2.6), 0xffffff, this.randomBetween(0.24, 0.58));
      view
        .setScrollFactor(0)
        .setDepth(fastStreak ? 1880 : 1860)
        .setBlendMode(Phaser.BlendModes.ADD)
        .setRotation(-0.48);
      this.snowflakes.push({
        view,
        x: this.randomBetween(-80, this.scene.scale.width + 80),
        y: this.randomBetween(-80, this.scene.scale.height + 80),
        speedX: this.randomBetween(90, 260),
        speedY: this.randomBetween(120, 360),
        driftPhase: this.randomBetween(0, Math.PI * 2)
      });
    }
  }

  private createEdgeWarningStrips(): Phaser.GameObjects.Rectangle[] {
    return [0, 1, 2, 3].map(() =>
      this.scene.add
        .rectangle(0, 0, 10, 10, 0x7dff56, 0)
        .setScrollFactor(0)
        .setDepth(2090)
        .setBlendMode(Phaser.BlendModes.ADD)
    );
  }

  private createSpawnPings(): Phaser.GameObjects.Arc[] {
    return [0, 1, 2, 3].map(() => {
      const ping = this.scene.add.circle(0, 0, 34, 0x7dff56, 0.05).setDepth(82).setVisible(false);
      ping.setStrokeStyle(3, 0xb7ff6a, 0.72);
      return ping;
    });
  }

  private setAmbientVisible(visible: boolean): void {
    if (this.ambientVisible === visible) {
      return;
    }

    this.ambientVisible = visible;
    this.snowflakes.forEach((flake) => flake.view.setVisible(visible));
    this.edgeStrips.forEach((strip) => strip.setVisible(visible));
    this.spawnPings.forEach((ping) => ping.setVisible(visible && ping.visible));
    this.wavePanel.setVisible(visible);
    this.waveText.setVisible(visible);
    this.waveSubtext.setVisible(visible);
    if (visible) {
      this.trailDecalUpdateElapsedMs = TRAIL_DECAL_UPDATE_INTERVAL_MS;
      this.zombieGlowUpdateElapsedMs = ZOMBIE_GLOW_UPDATE_INTERVAL_MS;
      this.hordeWarningUpdateElapsedMs = HORDE_WARNING_UPDATE_INTERVAL_MS;
    }
  }

  private shouldRunTrailDecalUpdate(deltaMs: number): boolean {
    this.trailDecalUpdateElapsedMs += deltaMs;
    if (this.trailDecalUpdateElapsedMs < TRAIL_DECAL_UPDATE_INTERVAL_MS) {
      return false;
    }

    this.trailDecalUpdateElapsedMs = 0;
    return true;
  }

  private shouldRunZombieGlowUpdate(deltaMs: number): boolean {
    this.zombieGlowUpdateElapsedMs += deltaMs;
    if (this.zombieGlowUpdateElapsedMs < ZOMBIE_GLOW_UPDATE_INTERVAL_MS) {
      return false;
    }

    this.zombieGlowUpdateElapsedMs = 0;
    return true;
  }

  private shouldRunHordeWarningUpdate(deltaMs: number): boolean {
    this.hordeWarningUpdateElapsedMs += deltaMs;
    if (this.hordeWarningUpdateElapsedMs < HORDE_WARNING_UPDATE_INTERVAL_MS) {
      return false;
    }

    this.hordeWarningUpdateElapsedMs = 0;
    return true;
  }

  private updateBlizzard(deltaMs: number, elapsedMs: number): void {
    const width = Math.max(1, this.scene.scale.width);
    const height = Math.max(1, this.scene.scale.height);
    const deltaSeconds = Math.min(0.05, Math.max(0, deltaMs) / 1000);
    this.snowflakes.forEach((flake) => {
      flake.x += (flake.speedX + Math.sin(elapsedMs / 360 + flake.driftPhase) * 34) * deltaSeconds;
      flake.y += flake.speedY * deltaSeconds;
      if (flake.x > width + 90 || flake.y > height + 90) {
        flake.x = this.randomBetween(-140, width * 0.88);
        flake.y = this.randomBetween(-110, -18);
      }
      flake.view.setPosition(flake.x, flake.y);
    });
  }

  private updateTrailDecals(snapshot: GameSnapshot, heroViews: ReadonlyMap<string, HeroView>): void {
    snapshot.heroes.forEach((hero) => {
      const state = this.resolveTrailState(hero);
      const view = heroViews.get(hero.heroId);
      const position = this.resolveHeroDisplayPosition(hero, view, state);
      const zombie = isZombieHero(hero);

      if (hero.alive) {
        if (!state.lastFootprint || distanceBetween(state.lastFootprint, position) >= FOOTPRINT_GAP) {
          this.createFootprintPair(position, hero.facing, zombie);
          state.lastFootprint = { x: position.x, y: position.y };
        }

        if (hero.hp < state.lastHp - 0.5) {
          this.createBloodDecal(position, zombie, 0.75);
        }
      } else if (state.wasAlive) {
        this.createBloodDecal(position, zombie, 1.25);
      }

      state.lastPosition = { x: position.x, y: position.y };
      state.lastHp = hero.hp;
      state.wasAlive = hero.alive;
    });
  }

  private resolveTrailState(hero: Hero): HeroTrailState {
    const current = this.heroTrailStates.get(hero.heroId);
    if (current) {
      return current;
    }

    const created = {
      lastFootprint: null,
      lastPosition: { x: hero.position.x, y: hero.position.y },
      lastHp: hero.hp,
      wasAlive: hero.alive
    };
    this.heroTrailStates.set(hero.heroId, created);
    return created;
  }

  private resolveHeroDisplayPosition(hero: Hero, view: HeroView | undefined, state: HeroTrailState): Vec2 {
    if (view?.sprite.visible) {
      return { x: view.sprite.x, y: view.sprite.y };
    }

    return state.lastPosition ?? hero.position;
  }

  private createFootprintPair(position: Vec2, facing: number, zombie: boolean): void {
    const forward = { x: Math.cos(facing), y: Math.sin(facing) };
    const side = { x: Math.cos(facing + Math.PI / 2), y: Math.sin(facing + Math.PI / 2) };
    const color = zombie ? 0x5b8d46 : 0x637f88;
    [-1, 1].forEach((direction) => {
      const foot = this.scene.add
        .ellipse(
          position.x + side.x * direction * 6 - forward.x * 5,
          position.y + side.y * direction * 6 - forward.y * 5,
          zombie ? 9 : 7,
          zombie ? 18 : 15,
          color,
          zombie ? 0.3 : 0.24
        )
        .setRotation(facing)
        .setDepth(31);
      this.footprints.push(foot);
      this.scene.tweens.add({
        targets: foot,
        alpha: 0,
        duration: FOOTPRINT_TTL_MS,
        ease: "Sine.easeIn",
        onComplete: () => this.removeFootprint(foot)
      });
    });

    while (this.footprints.length > MAX_FOOTPRINTS) {
      this.removeFootprint(this.footprints[0]);
    }
  }

  private createBloodDecal(position: Vec2, zombie: boolean, scale: number): void {
    const tint = zombie ? 0x65ff59 : 0x7d1d26;
    const decal = this.scene.textures.exists("suroi-zombie-blood-decal")
      ? this.scene.add
          .image(position.x, position.y, "suroi-zombie-blood-decal")
          .setDisplaySize(this.randomBetween(46, 76) * scale, this.randomBetween(34, 62) * scale)
          .setTint(tint)
          .setAlpha(zombie ? 0.7 : 0.78)
      : this.scene.add.ellipse(position.x, position.y, 54 * scale, 32 * scale, tint, 0.48);

    decal.setDepth(32).setRotation(this.randomBetween(-Math.PI, Math.PI));
    this.bloodDecals.push(decal);
    this.scene.tweens.add({
      targets: decal,
      alpha: 0,
      delay: BLOOD_TTL_MS,
      duration: 3200,
      ease: "Sine.easeIn",
      onComplete: () => this.removeBloodDecal(decal)
    });

    while (this.bloodDecals.length > MAX_BLOOD_DECALS) {
      this.removeBloodDecal(this.bloodDecals[0]);
    }
  }

  private updateZombieGlows(snapshot: GameSnapshot, heroViews: ReadonlyMap<string, HeroView>): void {
    const activeIds = new Set<string>();
    snapshot.heroes.forEach((hero, index) => {
      if (!isZombieHero(hero)) {
        return;
      }

      activeIds.add(hero.heroId);
      const view = heroViews.get(hero.heroId);
      const glow = this.resolveZombieGlow(hero.heroId);
      const visible = hero.alive && Boolean(view?.sprite.visible);
      glow.setVisible(visible);
      if (!visible || !view) {
        return;
      }

      const pulse = 0.5 + Math.sin(snapshot.elapsedMs / 240 + index * 0.72) * 0.5;
      const radius = Math.max(30, hero.radius + 12 + pulse * 8);
      glow.setPosition(view.sprite.x, view.sprite.y);
      glow.setRadius(radius);
      glow.setFillStyle(0x74ff51, 0.06 + pulse * 0.05);
      glow.setStrokeStyle(3, 0xb7ff6a, 0.32 + pulse * 0.28);
    });

    [...this.zombieGlows.keys()].forEach((heroId) => {
      if (!activeIds.has(heroId)) {
        this.zombieGlows.get(heroId)?.destroy();
        this.zombieGlows.delete(heroId);
      }
    });
  }

  private resolveZombieGlow(heroId: string): Phaser.GameObjects.Arc {
    const existing = this.zombieGlows.get(heroId);
    if (existing) {
      return existing;
    }

    const created = this.scene.add
      .circle(0, 0, 30, 0x74ff51, 0.08)
      .setDepth(41)
      .setBlendMode(Phaser.BlendModes.ADD)
      .setVisible(false);
    created.setStrokeStyle(3, 0xb7ff6a, 0.5);
    this.zombieGlows.set(heroId, created);
    return created;
  }

  private updateHordeWarning(snapshot: GameSnapshot): void {
    const elapsedMs = Math.max(0, snapshot.elapsedMs);
    const cycleElapsed = elapsedMs % HORDE_CYCLE_MS;
    const remainingMs = HORDE_CYCLE_MS - cycleElapsed;
    const warning = remainingMs <= HORDE_WARNING_MS || cycleElapsed <= HORDE_BREACH_FLASH_MS;
    const breaching = cycleElapsed <= HORDE_BREACH_FLASH_MS;
    const waveIndex = Math.floor(elapsedMs / HORDE_CYCLE_MS) + 1;
    const plan = this.resolveHordeWarningPlan(snapshot, waveIndex);
    const pulse = 0.5 + Math.sin(elapsedMs / 120) * 0.5;
    const width = Math.max(1, this.scene.scale.width);
    const height = Math.max(1, this.scene.scale.height);

    this.wavePanel.setPosition(width / 2, 42);
    this.waveText.setPosition(width / 2, 34);
    this.waveSubtext.setPosition(width / 2, 56);
    this.waveText.setText(breaching ? `HORDE ${waveIndex} BREACHING` : `HORDE ${waveIndex} IN ${formatRemaining(remainingMs)}`);
    this.waveText.setColor(warning ? "#fffb9a" : "#b7ff6a");
    this.waveSubtext.setText(`EDGE ACTIVITY: ${plan.label}`);
    this.wavePanel.setStrokeStyle(1, warning ? 0xff4f45 : 0x9cff6f, warning ? 0.78 : 0.42);

    this.updateEdgeStrips(width, height, warning ? 0.08 + pulse * 0.12 : 0.018);
    this.spawnPings.forEach((ping, index) => {
      const point = plan.points[index];
      const visible = warning && Boolean(point);
      ping.setVisible(visible);
      if (!point) {
        return;
      }

      ping.setPosition(point.x, point.y);
      ping.setRadius(28 + pulse * 28 + index * 4);
      ping.setFillStyle(0x7dff56, 0.04 + pulse * 0.04);
      ping.setStrokeStyle(3, breaching ? 0xff4f45 : 0xb7ff6a, 0.34 + pulse * 0.48);
    });
  }

  private updateEdgeStrips(width: number, height: number, alpha: number): void {
    const [top, right, bottom, left] = this.edgeStrips;
    top.setPosition(width / 2, 6).setDisplaySize(width, 14).setAlpha(alpha);
    right.setPosition(width - 6, height / 2).setDisplaySize(14, height).setAlpha(alpha);
    bottom.setPosition(width / 2, height - 6).setDisplaySize(width, 14).setAlpha(alpha);
    left.setPosition(6, height / 2).setDisplaySize(14, height).setAlpha(alpha);
  }

  private resolveHordeWarningPlan(snapshot: GameSnapshot, waveIndex: number): HordeWarningPlan {
    const width = snapshot.worldSize.x;
    const height = snapshot.worldSize.y;
    const inset = 34;
    switch (waveIndex % 4) {
      case 0:
        return {
          label: "NORTH RIDGE",
          points: [0.22, 0.5, 0.78].map((ratio) => ({ x: width * ratio, y: inset }))
        };
      case 1:
        return {
          label: "EAST WHITEOUT",
          points: [0.24, 0.52, 0.8].map((ratio) => ({ x: width - inset, y: height * ratio }))
        };
      case 2:
        return {
          label: "SOUTH TREE LINE",
          points: [0.2, 0.48, 0.76].map((ratio) => ({ x: width * ratio, y: height - inset }))
        };
      default:
        return {
          label: "WEST LAB ROAD",
          points: [0.2, 0.5, 0.78].map((ratio) => ({ x: inset, y: height * ratio }))
        };
    }
  }

  private removeFootprint(footprint: Phaser.GameObjects.Ellipse): void {
    removeViewFromList(footprint, this.footprints, this.scene);
  }

  private removeBloodDecal(decal: DecalView): void {
    removeViewFromList(decal, this.bloodDecals, this.scene);
  }

  private randomBetween(min: number, max: number): number {
    return Phaser.Math.FloatBetween(min, max);
  }
}

function isZombieHero(hero: Hero): boolean {
  const normalized = hero.displayName.toLowerCase();
  return normalized.includes("zombie") || hero.displayName.includes("丧尸");
}

function distanceBetween(a: Vec2, b: Vec2): number {
  return Math.hypot(a.x - b.x, a.y - b.y);
}

function formatRemaining(ms: number): string {
  const seconds = Math.max(0, Math.ceil(ms / 1000));
  const minutesPart = Math.floor(seconds / 60).toString().padStart(2, "0");
  const secondsPart = (seconds % 60).toString().padStart(2, "0");
  return `${minutesPart}:${secondsPart}`;
}

function removeViewFromList<T extends Phaser.GameObjects.GameObject>(
  view: T,
  list: T[],
  scene: Phaser.Scene
): void {
  const index = list.indexOf(view);
  if (index >= 0) {
    list.splice(index, 1);
  }

  scene.tweens.killTweensOf(view);
  if (view.active) {
    view.destroy();
  }
}
