import type { BattleGameEventState as GameEvent } from "../../../../objects/battle/microservices/runtime/objects/event/BattleGameEventState";
import type { BattleGameSnapshot as GameSnapshot } from "../../../../objects/battle/microservices/session/objects/state/BattleGameSnapshot";
import type { BattleItemPickupState as ItemPickup, BattleWeaponPickupState as WeaponPickup } from "../../../../objects/battle/microservices/abilities/objects/pickup/BattlePickupState";
import type { BattleWeaponState as WeaponState } from "../../../../objects/battle/microservices/combat/objects/weapon/BattleWeaponState";
import type { BattleHeroViewState as Hero } from "../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { SkillKind } from "../../../../objects/battle/microservices/abilities/objects/skill/SkillKind";
import { WEAPON_PICKUP_RADIUS } from "../objects/BattleGameConstants";
import { getSkillState, SKILL_DEFINITIONS } from "../../microservices/abilities/functions/BattleSkillStateRules";
import type {
  HudFeedEntry,
  HudLeaderboardEntry,
  HudMinimapRect,
  HudSkillEntry,
  HudStatusEntry,
  HudState,
  HudWeaponEntry
} from "../ui/Hud";
import { getItemPickupDisplayLabel, getWeaponDisplayLabel } from "./battleDisplayCatalog";
import { createMinimapData, type MinimapObstacleBounds } from "./minimapPresenter";

export type HudPresenterObstacleBounds = MinimapObstacleBounds;

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
  sharedAuthoritativeHud: boolean;
  nearbyWeaponPickup: WeaponPickup | null;
  nearbyItemPickup: ItemPickup | null;
  skillBindings: HudSkillBinding[];
  cameraRect: HudMinimapRect;
  obstacleBounds: readonly HudPresenterObstacleBounds[];
  mapExpanded: boolean;
}

/** ???:??hud??(createHudState)?????:????????????????????????,??????????????????*/
export function createHudState(input: HudPresenterInput): HudState {
  const {
    snapshot,
    playerHero,
    currentWeapon,
    fps,
    timer,
    weaponSwitchRemainingMs,
    sharedAuthoritativeHud,
    nearbyWeaponPickup,
    nearbyItemPickup,
    skillBindings,
    cameraRect,
    obstacleBounds,
    mapExpanded
  } = input;

  if (sharedAuthoritativeHud) {
    return {
      timer,
      fps,
      score: playerHero.score,
      playerName: playerHero.displayName,
      hp: playerHero.hp,
      maxHp: playerHero.maxHp,
      stamina: playerHero.stamina,
      maxStamina: playerHero.maxStamina,
      currentWeaponName: formatAuthoritativeWeaponName(playerHero, currentWeapon),
      currentWeaponAmmo: formatAuthoritativeWeaponAmmo(currentWeapon),
      currentWeaponState: formatAuthoritativeWeaponState(playerHero, currentWeapon),
      pickupHint: formatAuthoritativePickupHint(
        playerHero,
        snapshot.weaponPickups,
        snapshot.itemPickups,
        nearbyWeaponPickup,
        nearbyItemPickup
      ),
      weaponEntries: buildAuthoritativeWeaponEntries(playerHero),
      skillEntries: buildSkillEntries(playerHero, skillBindings),
      statusEntries: buildStatusEntries(playerHero, currentWeapon, weaponSwitchRemainingMs, sharedAuthoritativeHud, nearbyWeaponPickup, nearbyItemPickup),
      leaderboard: buildLeaderboard(snapshot.heroes, playerHero),
      feed: buildFeed(snapshot.events),
      minimap: createMinimapData({ snapshot, cameraRect, obstacleBounds }),
      mapExpanded,
      debugLines: []
    };
  }

  return {
    timer,
    fps,
    score: playerHero.score,
    playerName: playerHero.displayName,
    hp: playerHero.hp,
    maxHp: playerHero.maxHp,
    stamina: playerHero.stamina,
    maxStamina: playerHero.maxStamina,
    currentWeaponName: playerHero.alive ? getWeaponDisplayLabel(currentWeapon.weaponKind) : "???",
    currentWeaponAmmo: formatCurrentWeaponAmmo(currentWeapon),
    currentWeaponState: formatCurrentWeaponState(playerHero, currentWeapon, weaponSwitchRemainingMs),
    pickupHint: formatPickupHint(nearbyWeaponPickup, nearbyItemPickup),
    weaponEntries: buildWeaponEntries(playerHero),
    skillEntries: buildSkillEntries(playerHero, skillBindings),
    statusEntries: buildStatusEntries(playerHero, currentWeapon, weaponSwitchRemainingMs, sharedAuthoritativeHud, nearbyWeaponPickup, nearbyItemPickup),
    leaderboard: buildLeaderboard(snapshot.heroes, playerHero),
    feed: buildFeed(snapshot.events),
    minimap: createMinimapData({ snapshot, cameraRect, obstacleBounds }),
    mapExpanded,
    debugLines: []
  };
}

function buildStatusEntries(
  playerHero: Hero,
  currentWeapon: WeaponState,
  weaponSwitchRemainingMs: number,
  sharedAuthoritativeHud: boolean,
  nearbyWeaponPickup: WeaponPickup | null,
  nearbyItemPickup: ItemPickup | null
): HudStatusEntry[] {
  const entries: HudStatusEntry[] = [];

  if (!playerHero.alive) {
    entries.push({
      label: "????:???",
      tone: "danger"
    });
    return entries;
  }

  const hpRatio = playerHero.hp / playerHero.maxHp;
  if (hpRatio <= 0.3) {
    entries.push({ label: "????", tone: "danger" });
  } else if (hpRatio <= 0.55) {
    entries.push({ label: "????", tone: "warning" });
  }

  if (nearbyWeaponPickup) {
    entries.push({ label: `??? ${getWeaponDisplayLabel(nearbyWeaponPickup.weaponKind)}`, tone: "success" });
  } else if (nearbyItemPickup) {
    entries.push({ label: `??? ${getItemPickupDisplayLabel(nearbyItemPickup.kind)}`, tone: "success" });
  }

  if (weaponSwitchRemainingMs > 0) {
    entries.push({ label: "???", tone: "info" });
  } else if (currentWeapon.reloadRemainingMs > 0) {
    entries.push({ label: "???", tone: "warning" });
  } else if (currentWeapon.weaponKind === "Gatling" && currentWeapon.overheated) {
    entries.push({ label: "????", tone: "danger" });
  } else if (currentWeapon.weaponKind !== "Gatling" && currentWeapon.ammoInMagazine <= 0) {
    entries.push({ label: (currentWeapon.reserveAmmo ?? 0) > 0 ? "????" : "????", tone: "danger" });
  } else {
    entries.push({ label: sharedAuthoritativeHud ? "?????" : "????", tone: "info" });
  }

  return entries.slice(0, 4);
}

function buildAuthoritativeWeaponEntries(playerHero: Hero): HudWeaponEntry[] {
  return playerHero.weapons.map((weapon, index) => ({
    label: `${index === playerHero.currentWeaponIndex ? ">" : " "} ???${getWeaponDisplayLabel(weapon.weaponKind)} | ${formatAuthoritativeWeaponAmmo(weapon)} | ${formatAuthoritativeWeaponStateText(weapon)}`,
    current: index === playerHero.currentWeaponIndex,
    warning: isWeaponWarning(weapon),
    tone: getHudWeaponTone(weapon.weaponKind)
  }));
}

function formatAuthoritativePickupHint(
  playerHero: Hero,
  weaponPickups: readonly WeaponPickup[],
  itemPickups: readonly ItemPickup[],
  nearbyWeaponPickup: WeaponPickup | null,
  nearbyItemPickup: ItemPickup | null
): string {
  const weaponPickup =
    nearbyWeaponPickup ?? findNearbyAuthoritativeWeaponPickup(playerHero.position, weaponPickups, WEAPON_PICKUP_RADIUS);
  if (weaponPickup) {
    return `??????${getWeaponDisplayLabel(weaponPickup.weaponKind)};????????,????`;
  }

  const pickup =
    nearbyItemPickup ?? findNearbyAuthoritativeMedkit(playerHero.position, itemPickups, WEAPON_PICKUP_RADIUS);
  if (pickup) {
    return "?????????;???????";
  }

  return "??????????????;????????????";
}

function findNearbyAuthoritativeWeaponPickup(
  position: Hero["position"],
  pickups: readonly WeaponPickup[],
  radius: number
): WeaponPickup | null {
  let closest: WeaponPickup | null = null;
  let closestDistance = radius;

  pickups.forEach((pickup) => {
    if (!pickup.available) {
      return;
    }

    const distance = Math.hypot(position.x - pickup.position.x, position.y - pickup.position.y);
    if (distance <= closestDistance) {
      closest = pickup;
      closestDistance = distance;
    }
  });

  return closest;
}

function findNearbyAuthoritativeMedkit(
  position: Hero["position"],
  pickups: readonly ItemPickup[],
  radius: number
): ItemPickup | null {
  let closest: ItemPickup | null = null;
  let closestDistance = radius;

  pickups.forEach((pickup) => {
    if (!pickup.available || pickup.kind !== "Medkit") {
      return;
    }

    const distance = Math.hypot(position.x - pickup.position.x, position.y - pickup.position.y);
    if (distance <= closestDistance) {
      closest = pickup;
      closestDistance = distance;
    }
  });

  return closest;
}

function formatAuthoritativeWeaponName(playerHero: Hero, currentWeapon: WeaponState): string {
  return playerHero.alive ? `???${getWeaponDisplayLabel(currentWeapon.weaponKind)}` : "???";
}

function formatAuthoritativeWeaponAmmo(currentWeapon: WeaponState): string {
  if (currentWeapon.weaponKind === "Gatling") {
    return `?? ${Math.round(currentWeapon.heat)} / 100`;
  }

  return `${currentWeapon.ammoInMagazine} / ${currentWeapon.reserveAmmo ?? 0}`;
}

function formatAuthoritativeWeaponState(playerHero: Hero, currentWeapon: WeaponState): string {
  if (!playerHero.alive) {
    return "???";
  }

  return formatAuthoritativeWeaponStateText(currentWeapon);
}

function formatAuthoritativeWeaponStateText(currentWeapon: WeaponState): string {
  if (currentWeapon.weaponKind === "Gatling") {
    if (currentWeapon.overheated) {
      return "??";
    }

    if (currentWeapon.overheatRemainingMs > 0) {
      return `?? ${(Math.max(0, currentWeapon.overheatRemainingMs) / 1000).toFixed(1)} ?`;
    }
  }

  if (currentWeapon.reloadRemainingMs > 0) {
    return `?? ${(Math.max(0, currentWeapon.reloadRemainingMs) / 1000).toFixed(1)} ?`;
  }

  return `?? ${(Math.max(0, currentWeapon.fireCooldownMs) / 1000).toFixed(1)} ?`;
}

function isWeaponWarning(weapon: WeaponState): boolean {
  return weapon.weaponKind === "Gatling"
    ? weapon.overheated || weapon.overheatRemainingMs > 0
    : weapon.ammoInMagazine <= 0 && (weapon.reserveAmmo ?? 0) <= 0;
}

function buildWeaponEntries(playerHero: Hero): HudWeaponEntry[] {
  return playerHero.weapons.map((weapon, index) => ({
    label:
      weapon.weaponKind === "Gatling"
        ? `${index === playerHero.currentWeaponIndex ? ">" : " "} ${getWeaponDisplayLabel(weapon.weaponKind)} ??? ${Math.round(weapon.heat)} / 100`
        : `${index === playerHero.currentWeaponIndex ? ">" : " "} ${getWeaponDisplayLabel(weapon.weaponKind)} ?${weapon.ammoInMagazine} / ${weapon.reserveAmmo ?? 0}`,
    current: index === playerHero.currentWeaponIndex,
    warning:
      weapon.weaponKind === "Gatling"
        ? weapon.overheated
        : weapon.ammoInMagazine <= 0 && (weapon.reserveAmmo ?? 0) <= 0,
    tone: getHudWeaponTone(weapon.weaponKind)
  }));
}

function getHudWeaponTone(weaponKind: WeaponState["weaponKind"]): HudWeaponEntry["tone"] {
  switch (weaponKind) {
    case "RocketLauncher":
      return "rocket";
    case "Gatling":
      return "gatling";
    case "Shotgun":
      return "shotgun";
    case "Pistol":
    default:
      return "pistol";
  }
}

function buildSkillEntries(playerHero: Hero, skillBindings: HudSkillBinding[]): HudSkillEntry[] {
  return skillBindings.map((binding) => {
    const skill = getSkillState(playerHero, binding.skillId);

    return {
      key: binding.key,
      icon: getSkillIcon(binding.skillId),
      name: binding.label,
      state: formatSkillState(playerHero, binding.skillId, skill.cooldownMs, skill.activeMs),
      ready: skill.cooldownMs <= 0 && skill.activeMs <= 0,
      prepared: playerHero.preparedSkill === binding.skillId,
      cooldownProgress: skillProgress(skill.cooldownMs, SKILL_DEFINITIONS[binding.skillId].cooldownMs),
      activeProgress: skillProgress(skill.activeMs, SKILL_DEFINITIONS[binding.skillId].activeMs)
    };
  });
}

function formatSkillState(playerHero: Hero, skillId: SkillKind, cooldownMs: number, activeMs: number): string {
  if (activeMs > 0) {
    return `持续 ${(activeMs / 1000).toFixed(1)}s`;
  }

  if (cooldownMs > 0) {
    return `CD ${(cooldownMs / 1000).toFixed(1)}s`;
  }

  if (playerHero.preparedSkill === skillId) {
    return "已瞄准";
  }

  return "就绪";
}

function skillProgress(remainingMs: number, totalMs: number): number {
  if (!Number.isFinite(remainingMs) || !Number.isFinite(totalMs) || totalMs <= 0 || remainingMs <= 0) {
    return 0;
  }

  return clamp(remainingMs / totalMs, 0, 1);
}

function getSkillIcon(kind: SkillKind): string {
  switch (kind) {
    case "Blink":
      return "闪";
    case "Dash":
      return "冲";
    case "Freeze":
      return "冰";
    case "Critical":
      return "暴";
    default:
      return "?";
  }
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
    ? `?? ${Math.round(currentWeapon.heat)} / 100`
    : `${currentWeapon.ammoInMagazine} / ${currentWeapon.reserveAmmo ?? 0}`;
}

function formatCurrentWeaponState(playerHero: Hero, currentWeapon: WeaponState, weaponSwitchRemainingMs: number): string {
  if (!playerHero.alive) {
    return "???";
  }

  if (weaponSwitchRemainingMs > 0) {
    return `???? ${(weaponSwitchRemainingMs / 1000).toFixed(1)}?`;
  }

  if (currentWeapon.weaponKind === "Gatling") {
    return currentWeapon.overheated ? `?? ${(currentWeapon.overheatRemainingMs / 1000).toFixed(1)}?` : "????";
  }

  if (currentWeapon.reloadRemainingMs > 0) {
    return `???? ${(currentWeapon.reloadRemainingMs / 1000).toFixed(1)}?`;
  }

  if (currentWeapon.fireCooldownMs > 0) {
    return `?? ${(currentWeapon.fireCooldownMs / 1000).toFixed(1)}?`;
  }

  return "??";
}

function formatPickupHint(nearbyWeaponPickup: WeaponPickup | null, nearbyItemPickup: ItemPickup | null): string {
  if (nearbyWeaponPickup) {
    return `??????{getWeaponDisplayLabel(nearbyWeaponPickup.weaponKind)} ??????`;
  }

  if (nearbyItemPickup) {
    return `??????{getItemPickupDisplayLabel(nearbyItemPickup.kind)} ?????`;
  }

  return "?????? ?T ??";
}

function clamp(value: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, value));
}
