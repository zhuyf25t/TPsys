import type { Hero, ItemPickup, Vec2, WeaponPickup } from "../../../battle/objects/types";
import { distanceBetween } from "./botMath";

export interface BotTarget {
  key: string;
  kind: "enemy" | "pickup";
  position: Vec2;
  distance: number;
  score: number;
  heroId?: string;
}

export interface BotBrainState {
  targetKey: string | null;
  targetKind: BotTarget["kind"] | null;
  targetScore: number;
  targetHoldUntilMs: number;
  nextFireAtMs: number;
  patrolHeading: number;
  patrolUntilMs: number;
  fireBurstUntilMs: number;
  firePauseUntilMs: number;
  lastEnemyPosition: Vec2 | null;
  lastEnemySeenAtMs: number;
  lastMoveDirection: Vec2 | null;
  stuckUntilMs: number;
}

const TARGET_SWITCH_BUFFER = 96;
const ENEMY_HOLD_MS = 900;
const PICKUP_HOLD_MS = 540;
const OPPORTUNISTIC_WEAPON_PICKUP_DISTANCE = 380;

export function chooseSupportTarget(
  bot: Hero,
  weapon: Hero["weapons"][number],
  weaponPickups: readonly WeaponPickup[],
  itemPickups: readonly ItemPickup[]
): BotTarget | null {
  const healthLow = bot.hp <= bot.maxHp * 0.56;
  const healthCritical = bot.hp <= bot.maxHp * 0.38;
  const weaponScarce = isWeaponScarce(weapon);
  const candidates: BotTarget[] = [];

  if (healthLow || healthCritical) {
    const medkit = findNearestMedkit(bot.position, itemPickups);
    if (medkit) {
      candidates.push(medkit);
    }
  }

  if (weaponScarce) {
    const pickup = findNearestWeaponPickup(bot.position, weaponPickups);
    if (pickup) {
      candidates.push(pickup);
    }
  } else {
    const pickup = findNearestWeaponPickup(bot.position, weaponPickups);
    if (pickup && pickup.distance <= OPPORTUNISTIC_WEAPON_PICKUP_DISTANCE) {
      candidates.push(pickup);
    }
  }

  if (candidates.length === 0) {
    return null;
  }

  return candidates.sort((left, right) => right.score - left.score)[0] ?? null;
}

export function findNearestAliveEnemy(bot: Hero, heroes: readonly Hero[], weapon: Hero["weapons"][number]): BotTarget | null {
  let best: BotTarget | null = null;

  heroes.forEach((hero) => {
    if (!hero.alive || hero.heroId === bot.heroId) {
      return;
    }

    const distance = distanceBetween(bot.position, hero.position);
    const engageRange = getEngageRange(weapon.weaponKind);
    const threatBonus = distance <= engageRange * 1.1 ? 200 : distance <= engageRange * 1.35 ? 110 : 0;
    const score = 1800 - distance + threatBonus;
    if (!best || score > best.score) {
      best = {
        key: `enemy:${hero.heroId}`,
        kind: "enemy",
        position: hero.position,
        distance,
        score,
        heroId: hero.heroId
      };
    }
  });

  return best;
}

export function chooseTarget(
  bot: Hero,
  weapon: Hero["weapons"][number],
  brain: BotBrainState,
  enemyTarget: BotTarget | null,
  pickupTarget: BotTarget | null,
  elapsedMs: number
): BotTarget | null {
  const candidates = [enemyTarget, pickupTarget].filter((value): value is BotTarget => Boolean(value));

  if (candidates.length === 0) {
    brain.targetKey = null;
    brain.targetKind = null;
    brain.targetScore = 0;
    brain.targetHoldUntilMs = 0;
    return null;
  }

  const currentTarget = brain.targetKey ? candidates.find((candidate) => candidate.key === brain.targetKey) ?? null : null;
  const bestTarget = candidates.sort((left, right) => right.score - left.score)[0];
  const engageRange = getEngageRange(weapon.weaponKind);
  const enemyPressure = enemyTarget && enemyTarget.distance <= engageRange * 1.9;
  const enemyClose = enemyTarget && enemyTarget.distance <= engageRange * 1.08;
  const enemyFar = enemyTarget && enemyTarget.distance >= engageRange * 1.45;
  const healthCritical = bot.hp <= bot.maxHp * 0.38;
  const enemyNearby = enemyTarget && enemyTarget.distance <= engageRange * 1.28;
  const pickupIsMedkit = Boolean(pickupTarget && pickupTarget.key.startsWith("medkit:"));
  const supportUrgent = Boolean(
    pickupTarget &&
      ((pickupIsMedkit && (healthCritical || (bot.hp <= bot.maxHp * 0.52 && !enemyNearby))) ||
        (!pickupIsMedkit && isWeaponScarce(weapon) && !enemyNearby))
  );
  const supportCompelling = Boolean(
    pickupTarget &&
      ((pickupIsMedkit && (healthCritical || (bot.hp <= bot.maxHp * 0.72 && !enemyClose))) ||
        (!pickupIsMedkit && (isWeaponScarce(weapon) || pickupTarget.distance <= OPPORTUNISTIC_WEAPON_PICKUP_DISTANCE)))
  );

  if (enemyTarget && currentTarget?.kind === "pickup" && !supportUrgent && (enemyPressure || enemyClose || currentTarget.score <= enemyTarget.score)) {
    brain.targetKey = enemyTarget.key;
    brain.targetKind = enemyTarget.kind;
    brain.targetScore = enemyTarget.score;
    brain.targetHoldUntilMs = elapsedMs + ENEMY_HOLD_MS + (enemyPressure ? 160 : 0);
    brain.lastEnemyPosition = { x: enemyTarget.position.x, y: enemyTarget.position.y };
    brain.lastEnemySeenAtMs = elapsedMs;
    return enemyTarget;
  }

  if (currentTarget && elapsedMs < brain.targetHoldUntilMs) {
    const switchBuffer =
      currentTarget.kind === "pickup" ? (enemyPressure ? 80 : 180) : supportUrgent ? 60 : TARGET_SWITCH_BUFFER;
    if (bestTarget && bestTarget.score <= currentTarget.score + switchBuffer) {
      return currentTarget;
    }
  }

  if (
    pickupTarget &&
    (supportUrgent || (supportCompelling && !enemyPressure && (!enemyTarget || enemyFar || pickupTarget.score >= enemyTarget.score - 120)))
  ) {
    brain.targetKey = pickupTarget.key;
    brain.targetKind = pickupTarget.kind;
    brain.targetScore = pickupTarget.score;
    brain.targetHoldUntilMs = elapsedMs + PICKUP_HOLD_MS + (supportUrgent ? 160 : 0);
    return pickupTarget;
  }

  if (enemyTarget) {
    brain.targetKey = enemyTarget.key;
    brain.targetKind = enemyTarget.kind;
    brain.targetScore = enemyTarget.score;
    brain.targetHoldUntilMs = elapsedMs + ENEMY_HOLD_MS + (enemyPressure ? 240 : 0);
    brain.lastEnemyPosition = { x: enemyTarget.position.x, y: enemyTarget.position.y };
    brain.lastEnemySeenAtMs = elapsedMs;
    return enemyTarget;
  }

  if (currentTarget && isTargetStillValid(weapon, currentTarget, enemyTarget, pickupTarget)) {
    const switchBuffer = currentTarget.kind === "pickup" ? 80 : TARGET_SWITCH_BUFFER;
    if (!bestTarget || bestTarget.score <= currentTarget.score + switchBuffer) {
      return currentTarget;
    }
  }

  const nextTarget = bestTarget ?? currentTarget;
  if (!nextTarget) {
    return null;
  }

  brain.targetKey = nextTarget.key;
  brain.targetKind = nextTarget.kind;
  brain.targetScore = nextTarget.score;
  brain.targetHoldUntilMs = elapsedMs + (nextTarget.kind === "pickup" ? PICKUP_HOLD_MS : ENEMY_HOLD_MS);
  return nextTarget;
}

export function getEngageRange(weaponKind: Hero["weapons"][number]["weaponKind"]): number {
  switch (weaponKind) {
    case "Shotgun":
      return 340;
    case "RocketLauncher":
      return 780;
    case "Gatling":
      return 720;
    default:
      return 620;
  }
}

export function isWeaponScarce(weapon: Hero["weapons"][number]): boolean {
  if (weapon.weaponKind === "Gatling") {
    return weapon.overheated || weapon.heat >= 72;
  }

  const reserve = weapon.reserveAmmo ?? 0;
  const magazineThreshold = Math.max(1, Math.ceil(weapon.magazineSize * 0.33));
  return weapon.ammoInMagazine <= magazineThreshold || reserve <= weapon.magazineSize;
}

export function shouldAttackTarget(weaponKind: Hero["weapons"][number]["weaponKind"], distance: number): boolean {
  return distance <= getEngageRange(weaponKind) * 1.08;
}

export function getBotFireDelayMs(weaponKind: Hero["weapons"][number]["weaponKind"], cooldownMs: number): number {
  if (weaponKind === "Gatling") {
    return Math.max(72, cooldownMs);
  }

  return Math.max(cooldownMs, 120);
}

function findNearestWeaponPickup(position: Vec2, pickups: readonly WeaponPickup[]): BotTarget | null {
  let closest: BotTarget | null = null;

  pickups.forEach((pickup) => {
    if (!pickup.available) {
      return;
    }

    const distance = distanceBetween(position, pickup.position);
    const score = 700 - distance;
    if (!closest || distance < closest.distance) {
      closest = {
        key: `weapon:${pickup.weaponId}`,
        kind: "pickup",
        position: pickup.position,
        distance,
        score
      };
    }
  });

  return closest;
}

function findNearestMedkit(position: Vec2, pickups: readonly ItemPickup[]): BotTarget | null {
  let closest: BotTarget | null = null;

  pickups.forEach((pickup) => {
    if (!pickup.available || pickup.kind !== "Medkit") {
      return;
    }

    const distance = distanceBetween(position, pickup.position);
    const score = 1080 - distance;
    if (!closest || distance < closest.distance) {
      closest = {
        key: `medkit:${pickup.pickupId}`,
        kind: "pickup",
        position: pickup.position,
        distance,
        score
      };
    }
  });

  return closest;
}

function isTargetStillValid(
  weapon: Hero["weapons"][number],
  target: BotTarget,
  enemyTarget: BotTarget | null,
  pickupTarget: BotTarget | null
): boolean {
  if (target.kind === "enemy") {
    return Boolean(enemyTarget && enemyTarget.heroId === target.heroId);
  }

  if (!pickupTarget) {
    return false;
  }

  const scarcity = isWeaponScarce(weapon);
  if (target.key.startsWith("medkit:")) {
    return scarcity ? pickupTarget.key === target.key || pickupTarget.score >= target.score - 140 : pickupTarget.key === target.key;
  }

  return scarcity && pickupTarget.key === target.key;
}
