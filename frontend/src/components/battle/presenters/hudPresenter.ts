import type {
  GameEvent,
  GameSnapshot,
  Hero,
  ItemPickup,
  SkillKind,
  SkillState,
  WeaponPickup,
  WeaponState
} from "../../../objects/battle/types";
import { WEAPON_PICKUP_RADIUS } from "../../../runtime/battle/game/constants";
import { getSkillState } from "../../../runtime/battle/game/skills";
import type {
  HudFeedEntry,
  HudLeaderboardEntry,
  HudMinimapRect,
  HudSkillEntry,
  HudStatusEntry,
  HudState,
  HudWeaponEntry
} from "../../../runtime/battle/game/ui/Hud";
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
}

/** 中文名：创建hud状态（createHudState）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
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
    obstacleBounds
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
      skillEntries: buildAuthoritativeSkillEntries(playerHero),
      statusEntries: buildStatusEntries(playerHero, currentWeapon, weaponSwitchRemainingMs, sharedAuthoritativeHud, nearbyWeaponPickup, nearbyItemPickup),
      leaderboard: buildLeaderboard(snapshot.heroes, playerHero),
      feed: buildFeed(snapshot.events),
      minimap: createMinimapData({ snapshot, cameraRect, obstacleBounds }),
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
    currentWeaponName: playerHero.alive ? getWeaponDisplayLabel(currentWeapon.weaponKind) : "已出局",
    currentWeaponAmmo: formatCurrentWeaponAmmo(currentWeapon),
    currentWeaponState: formatCurrentWeaponState(playerHero, currentWeapon, weaponSwitchRemainingMs),
    pickupHint: formatPickupHint(nearbyWeaponPickup, nearbyItemPickup),
    weaponEntries: buildWeaponEntries(playerHero),
    skillEntries: buildSkillEntries(playerHero, skillBindings),
    statusEntries: buildStatusEntries(playerHero, currentWeapon, weaponSwitchRemainingMs, sharedAuthoritativeHud, nearbyWeaponPickup, nearbyItemPickup),
    leaderboard: buildLeaderboard(snapshot.heroes, playerHero),
    feed: buildFeed(snapshot.events),
    minimap: createMinimapData({ snapshot, cameraRect, obstacleBounds }),
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
      label: "一命模式：已淘汰",
      tone: "danger"
    });
    return entries;
  }

  const hpRatio = playerHero.hp / playerHero.maxHp;
  if (hpRatio <= 0.3) {
    entries.push({ label: "生命危险", tone: "danger" });
  } else if (hpRatio <= 0.55) {
    entries.push({ label: "注意受击", tone: "warning" });
  }

  if (nearbyWeaponPickup) {
    entries.push({ label: `可拾取 ${getWeaponDisplayLabel(nearbyWeaponPickup.weaponKind)}`, tone: "success" });
  } else if (nearbyItemPickup) {
    entries.push({ label: `可拾取 ${getItemPickupDisplayLabel(nearbyItemPickup.kind)}`, tone: "success" });
  }

  if (weaponSwitchRemainingMs > 0) {
    entries.push({ label: "切枪中", tone: "info" });
  } else if (currentWeapon.reloadRemaining > 0) {
    entries.push({ label: "换弹中", tone: "warning" });
  } else if (currentWeapon.weaponKind === "Gatling" && currentWeapon.overheated) {
    entries.push({ label: "武器过热", tone: "danger" });
  } else if (currentWeapon.weaponKind !== "Gatling" && currentWeapon.ammoInMagazine <= 0) {
    entries.push({ label: (currentWeapon.reserveAmmo ?? 0) > 0 ? "弹匣已空" : "弹药耗尽", tone: "danger" });
  } else {
    entries.push({ label: sharedAuthoritativeHud ? "服务器同步" : "本地战斗", tone: "info" });
  }

  return entries.slice(0, 4);
}

function buildAuthoritativeWeaponEntries(playerHero: Hero): HudWeaponEntry[] {
  return playerHero.weapons.map((weapon, index) => ({
    label: `${index === playerHero.currentWeaponIndex ? ">" : " "} 服务器${getWeaponDisplayLabel(weapon.weaponKind)} | ${formatAuthoritativeWeaponAmmo(weapon)} | ${formatAuthoritativeWeaponStateText(weapon)}`,
    current: index === playerHero.currentWeaponIndex,
    warning: isWeaponWarning(weapon),
    tone: getHudWeaponTone(weapon.weaponKind)
  }));
}

function buildAuthoritativeSkillEntries(playerHero: Hero): HudSkillEntry[] {
  return (["Blink", "Dash", "Freeze"] as const).map((kind) => {
    const skill = playerHero.skills.find((entry) => entry.kind === kind) ?? { kind, cooldownMs: 0, activeMs: 0 };
    const prepared = (kind === "Blink" || kind === "Freeze") && playerHero.preparedSkill === kind;
    return {
      key: kind === "Blink" ? "Q" : kind === "Dash" ? "E" : "R",
      name: `服务器${getSkillDisplayLabel(kind)}`,
      state: formatAuthoritativeSkillState(skill, prepared),
      ready: skill.cooldownMs <= 0 && skill.activeMs <= 0,
      prepared
    };
  });
}

function formatAuthoritativeSkillState(skill: SkillState, prepared: boolean): string {
  if (skill.activeMs > 0) {
    return `生效中 ${(skill.activeMs / 1000).toFixed(1)} 秒`;
  }

  if (skill.cooldownMs > 0) {
    return `冷却 ${(skill.cooldownMs / 1000).toFixed(1)} 秒`;
  }

  if (prepared) {
    return "准备中：左键释放";
  }

  return "就绪";
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
    return `附近有服务器${getWeaponDisplayLabel(weaponPickup.weaponKind)}；接触后加入武器栏，滚轮切换`;
  }

  const pickup =
    nearbyItemPickup ?? findNearbyAuthoritativeMedkit(playerHero.position, itemPickups, WEAPON_PICKUP_RADIUS);
  if (pickup) {
    return "附近有服务器医疗包；接触后自动拾取";
  }

  return "服务器医疗包和武器补给已显示；接触武器后会切换当前装备";
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
  return playerHero.alive ? `服务器${getWeaponDisplayLabel(currentWeapon.weaponKind)}` : "已淘汰";
}

function formatAuthoritativeWeaponAmmo(currentWeapon: WeaponState): string {
  if (currentWeapon.weaponKind === "Gatling") {
    return `热量 ${Math.round(currentWeapon.heat)} / 100`;
  }

  return `${currentWeapon.ammoInMagazine} / ${currentWeapon.reserveAmmo ?? 0}`;
}

function formatAuthoritativeWeaponState(playerHero: Hero, currentWeapon: WeaponState): string {
  if (!playerHero.alive) {
    return "已淘汰";
  }

  return formatAuthoritativeWeaponStateText(currentWeapon);
}

function formatAuthoritativeWeaponStateText(currentWeapon: WeaponState): string {
  if (currentWeapon.weaponKind === "Gatling") {
    if (currentWeapon.overheated) {
      return "过热";
    }

    if (currentWeapon.overheatRemaining > 0) {
      return `散热 ${(Math.max(0, currentWeapon.overheatRemaining) / 1000).toFixed(1)} 秒`;
    }
  }

  if (currentWeapon.reloadRemaining > 0) {
    return `换弹 ${(Math.max(0, currentWeapon.reloadRemaining) / 1000).toFixed(1)} 秒`;
  }

  return `冷却 ${(Math.max(0, currentWeapon.cooldownRemaining) / 1000).toFixed(1)} 秒`;
}

function isWeaponWarning(weapon: WeaponState): boolean {
  return weapon.weaponKind === "Gatling"
    ? weapon.overheated || weapon.overheatRemaining > 0
    : weapon.ammoInMagazine <= 0 && (weapon.reserveAmmo ?? 0) <= 0;
}

function getSkillDisplayLabel(kind: SkillKind): string {
  switch (kind) {
    case "Blink":
      return "闪现";
    case "Dash":
      return "冲刺";
    case "Freeze":
      return "冻结";
    default:
      return kind;
  }
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
    return `附近武器：${getWeaponDisplayLabel(nearbyWeaponPickup.weaponKind)} · 加入武器栏`;
  }

  if (nearbyItemPickup) {
    return `附近补给：${getItemPickupDisplayLabel(nearbyItemPickup.kind)} · 自动拾取`;
  }

  return "滚轮切换武器 · T 换弹";
}

function clamp(value: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, value));
}
