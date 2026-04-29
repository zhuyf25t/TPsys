/**
 * External bot strategy template.
 *
 * This file intentionally does not import repo-internal TypeScript types.
 * It is a plain ESM module that can be copied outside the repository.
 *
 * Context summary:
 * - context.bot: the controlled bot observation.
 * - context.enemies: copied enemy observations.
 * - context.itemPickups / context.weaponPickups: copied pickup observations.
 * - context.currentWeapon: copied active weapon observation.
 * - context.defaultCommand: built-in command for this frame.
 *
 * Decision summary:
 * - Return a PlayerCommand-like object.
 * - movement and aim are vectors with finite x/y numbers.
 * - boolean command fields should be booleans when provided.
 * - Treat context as read-only.
 */

export const communityDistanceKeeperStrategy = {
  strategyId: "community-distance-keeper",

  decide(context) {
    const bot = context.bot;
    const weapon = context.currentWeapon;
    const nearestEnemy = findNearestAliveEnemy(bot, context.enemies);
    const lowHealth = bot.hp <= bot.maxHp * 0.42;
    const nearestMedkit = lowHealth ? findNearestAvailableItem(bot.position, context.itemPickups, ["medkit", "health"]) : null;

    const movement = chooseMovement(bot, nearestEnemy, nearestMedkit, context.worldSize);
    const aimTarget = nearestEnemy?.position ?? add(bot.position, context.defaultCommand.aim);
    const aim = normalize(subtract(aimTarget, bot.position));
    const enemyDistance = nearestEnemy ? distance(bot.position, nearestEnemy.position) : Infinity;
    const weaponReady =
      weapon.cooldownRemaining <= 0 &&
      weapon.reloadRemaining <= 0 &&
      !weapon.overheated &&
      weapon.ammoInMagazine > 0;

    return {
      movement,
      aim,
      pointerWorld: aimTarget,
      primaryHeld: Boolean(nearestEnemy && weaponReady && enemyDistance <= 720),
      primaryJustPressed: false,
      secondaryJustPressed: false,
      sprint: Boolean((nearestMedkit || enemyDistance > 760) && bot.stamina > bot.maxStamina * 0.35),
      switchWeaponDirection: 0,
      switchWeaponIndex: null,
      toggleBlink: false,
      toggleFreeze: false,
      castDash: false,
      reloadPressed: weapon.ammoInMagazine <= 0 && weapon.reloadRemaining <= 0 && weapon.reserveAmmo !== 0
    };
  }
};

export default communityDistanceKeeperStrategy;

function chooseMovement(bot, nearestEnemy, nearestMedkit, worldSize) {
  if (nearestMedkit) {
    return normalize(subtract(nearestMedkit.position, bot.position));
  }

  if (!nearestEnemy) {
    return normalize(subtract({ x: worldSize.x * 0.5, y: worldSize.y * 0.5 }, bot.position));
  }

  const toEnemy = subtract(nearestEnemy.position, bot.position);
  const enemyDistance = magnitude(toEnemy);
  const desiredMin = 420;
  const desiredMax = 620;

  if (enemyDistance < desiredMin) {
    return normalize(scale(toEnemy, -1));
  }

  if (enemyDistance > desiredMax) {
    return normalize(toEnemy);
  }

  const side = stableSide(bot.heroId);
  return normalize({ x: -toEnemy.y * side, y: toEnemy.x * side });
}

function findNearestAliveEnemy(bot, enemies) {
  return findNearest(bot.position, enemies.filter((enemy) => enemy.alive && enemy.team !== bot.team));
}

function findNearestAvailableItem(origin, itemPickups, acceptedKinds) {
  return findNearest(
    origin,
    itemPickups.filter((pickup) => pickup.available && acceptedKinds.includes(String(pickup.kind).toLowerCase()))
  );
}

function findNearest(origin, candidates) {
  let best = null;
  let bestDistance = Infinity;

  for (const candidate of candidates) {
    const candidateDistance = distance(origin, candidate.position);
    if (candidateDistance < bestDistance) {
      best = candidate;
      bestDistance = candidateDistance;
    }
  }

  return best;
}

function stableSide(value) {
  let hash = 0;
  for (let index = 0; index < value.length; index += 1) {
    hash = (hash * 31 + value.charCodeAt(index)) | 0;
  }

  return hash % 2 === 0 ? 1 : -1;
}

function subtract(a, b) {
  return { x: a.x - b.x, y: a.y - b.y };
}

function add(a, b) {
  return { x: a.x + b.x, y: a.y + b.y };
}

function scale(vector, factor) {
  return { x: vector.x * factor, y: vector.y * factor };
}

function normalize(vector) {
  const length = magnitude(vector);
  if (length === 0 || !Number.isFinite(length)) {
    return { x: 0, y: 0 };
  }

  return { x: vector.x / length, y: vector.y / length };
}

function distance(a, b) {
  return magnitude(subtract(a, b));
}

function magnitude(vector) {
  return Math.hypot(vector.x, vector.y);
}
