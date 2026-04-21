import type { GameEvent, GameSnapshot, Hero, ItemPickup, SkillKind, WeaponPickup, WeaponState } from "../../../domain/types";
import { getSkillState } from "../../../game/skills";
import type {
  HudFeedEntry,
  HudLeaderboardEntry,
  HudMinimapRect,
  HudSkillEntry,
  HudState,
  HudWeaponEntry
} from "../../../ui/Hud";
import { getItemPickupDisplayLabel, getWeaponDisplayLabel } from "./battleDisplayCatalog";
import { createMinimapData } from "./minimapPresenter";

export interface HudSkillBinding {
  key: string;
  skillId: SkillKind;
  label: string;
}

export interface HudPresenterInput {
  snapshot: GameSnapshot;
  playerHero: Hero;
  currentWeapon: WeaponState;
  fps: number;
  timer: string;
  weaponSwitchRemainingMs: number;
  nearbyWeaponPickup: WeaponPickup | null;
  nearbyItemPickup: ItemPickup | null;
  skillBindings: HudSkillBinding[];
  cameraRect: HudMinimapRect;
}

export function createHudState(input: HudPresenterInput): HudState {
  const {
    snapshot,
    playerHero,
    currentWeapon,
    fps,
    timer,
    weaponSwitchRemainingMs,
    nearbyWeaponPickup,
    nearbyItemPickup,
    skillBindings,
    cameraRect
  } = input;

  return {
    timer,
    fps,
    score: playerHero.score,
    playerName: playerHero.displayName,
    hp: playerHero.hp,
    maxHp: playerHero.maxHp,
    stamina: playerHero.stamina,
    maxStamina: playerHero.maxStamina,
    currentWeaponName: playerHero.alive ? getWeaponDisplayLabel(currentWeapon.weaponKind) : "已出局",
    currentWeaponAmmo: formatCurrentWeaponAmmo(currentWeapon),
    currentWeaponState: formatCurrentWeaponState(playerHero, currentWeapon, weaponSwitchRemainingMs),
    pickupHint: formatPickupHint(nearbyWeaponPickup, nearbyItemPickup),
    weaponEntries: buildWeaponEntries(playerHero),
    skillEntries: buildSkillEntries(playerHero, skillBindings),
    leaderboard: buildLeaderboard(snapshot.heroes, playerHero),
    feed: buildFeed(snapshot.events),
    minimap: createMinimapData({ snapshot, cameraRect }),
    debugLines: []
  };
}

function buildWeaponEntries(playerHero: Hero): HudWeaponEntry[] {
  return playerHero.weapons.map((weapon, index) => ({
    label:
      weapon.weaponKind === "Gatling"
        ? `${index === playerHero.currentWeaponIndex ? ">" : " "} ${getWeaponDisplayLabel(weapon.weaponKind)} · 热量 ${Math.round(weapon.heat)} / 100`
        : `${index === playerHero.currentWeaponIndex ? ">" : " "} ${getWeaponDisplayLabel(weapon.weaponKind)} · ${weapon.ammoInMagazine} / ${weapon.reserveAmmo ?? 0}`,
    current: index === playerHero.currentWeaponIndex,
    warning:
      weapon.weaponKind === "Gatling"
        ? weapon.overheated
        : weapon.ammoInMagazine <= 0 && (weapon.reserveAmmo ?? 0) <= 0
  }));
}

function buildSkillEntries(playerHero: Hero, skillBindings: HudSkillBinding[]): HudSkillEntry[] {
  return skillBindings.map((binding) => {
    const skill = getSkillState(playerHero, binding.skillId);

    return {
      key: binding.key,
      name: binding.label,
      state: formatSkillState(playerHero, binding.skillId, skill.cooldownMs),
      ready: skill.cooldownMs <= 0,
      prepared: playerHero.preparedSkill === binding.skillId
    };
  });
}

function formatSkillState(playerHero: Hero, skillId: SkillKind, cooldownMs: number): string {
  if (cooldownMs > 0) {
    return `${(cooldownMs / 1000).toFixed(1)}秒`;
  }

  if (playerHero.preparedSkill === skillId) {
    return "准备中";
  }

  return "可用";
}

function buildLeaderboard(heroes: GameSnapshot["heroes"], playerHero: Hero): HudLeaderboardEntry[] {
  return [...heroes]
    .sort((left, right) => {
      if (left.alive !== right.alive) {
        return left.alive ? -1 : 1;
      }
      if (right.score !== left.score) {
        return right.score - left.score;
      }
      return left.displayName.localeCompare(right.displayName);
    })
    .slice(0, 6)
    .map((hero, index) => ({
      rank: index + 1,
      name: hero.displayName,
      score: hero.score,
      current: hero.heroId === playerHero.heroId,
      alive: hero.alive
    }));
}

function buildFeed(events: GameEvent[]): HudFeedEntry[] {
  return events.slice(-4).reverse().map((event) => ({
    message: event.message,
    tone:
      event.type === "kill"
        ? "kill"
        : event.type === "pickup"
          ? "pickup"
          : event.type === "respawn"
            ? "respawn"
            : event.type === "heal"
              ? "heal"
              : "info",
    alpha: clamp(event.ttlMs / 3000, 0, 1)
  }));
}

function formatCurrentWeaponAmmo(currentWeapon: WeaponState): string {
  return currentWeapon.weaponKind === "Gatling"
    ? `热量 ${Math.round(currentWeapon.heat)} / 100`
    : `${currentWeapon.ammoInMagazine} / ${currentWeapon.reserveAmmo ?? 0}`;
}

function formatCurrentWeaponState(playerHero: Hero, currentWeapon: WeaponState, weaponSwitchRemainingMs: number): string {
  if (!playerHero.alive) {
    return "已出局";
  }

  if (weaponSwitchRemainingMs > 0) {
    return `正在切枪 ${(weaponSwitchRemainingMs / 1000).toFixed(1)}秒`;
  }

  if (currentWeapon.weaponKind === "Gatling") {
    return currentWeapon.overheated ? `过热 ${(currentWeapon.overheatRemaining / 1000).toFixed(1)}秒` : "自动射击";
  }

  if (currentWeapon.reloadRemaining > 0) {
    return `正在换弹 ${(currentWeapon.reloadRemaining / 1000).toFixed(1)}秒`;
  }

  if (currentWeapon.cooldownRemaining > 0) {
    return `冷却 ${(currentWeapon.cooldownRemaining / 1000).toFixed(1)}秒`;
  }

  return "就绪";
}

function formatPickupHint(nearbyWeaponPickup: WeaponPickup | null, nearbyItemPickup: ItemPickup | null): string {
  if (nearbyWeaponPickup) {
    return `附近武器：${getWeaponDisplayLabel(nearbyWeaponPickup.weaponKind)} · 自动拾取`;
  }

  if (nearbyItemPickup) {
    return `附近补给：${getItemPickupDisplayLabel(nearbyItemPickup.kind)} · 自动拾取`;
  }

  return "滚轮切换武器 · T 换弹";
}

function clamp(value: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, value));
}
