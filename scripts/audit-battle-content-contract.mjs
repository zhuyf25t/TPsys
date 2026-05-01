import { readFileSync } from "node:fs";
import { join } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT_DIR = join(fileURLToPath(new URL("..", import.meta.url)));
const PATHS = {
  frontendMap: join(ROOT_DIR, "frontend", "src", "game", "battleMapCatalog.ts"),
  frontendContent: join(ROOT_DIR, "frontend", "src", "game", "battleContentCatalog.ts"),
  backendRuntime: join(
    ROOT_DIR,
    "backend",
    "src",
    "main",
    "scala",
    "slaydemo",
    "backend",
    "battle",
    "services",
    "BattleStateService.scala"
  ),
  backendCatalog: join(
    ROOT_DIR,
    "backend",
    "src",
    "main",
    "scala",
    "slaydemo",
    "backend",
    "battle",
    "services",
    "InMemoryBattleStateCatalog.scala"
  ),
};

const WEAPON_FIELDS = [
  "projectileKind",
  "cooldownMs",
  "reloadMs",
  "projectileSpeedPerSecond",
  "projectileDamage",
  "projectileLifetimeMs",
  "projectileRadius",
  "splashRadius",
  "pellets",
  "spreadRadians",
  "magazineSize",
  "reserveAmmo",
  "pickupAmmo",
  "recoilStrength",
  "usesHeat",
  "maxHeat",
  "heatPerShot",
  "coolRatePerSecond",
  "overheatLockMs",
];
const SKILL_FIELDS = [
  "skillKind",
  "activationKind",
  "effectType",
  "cooldownMs",
  "activeMs",
  "range",
  "radius",
  "durationMs",
  "distance",
  "speedMultiplier",
];

const sources = Object.fromEntries(Object.entries(PATHS).map(([key, path]) => [key, readFileSync(path, "utf8")]));
const frontend = {
  defaultMap: parseFrontendMapCatalog(sources.frontendMap),
  weaponDefinitions: parseFrontendWeaponDefinitions(sources.frontendContent),
  skillDefinitions: parseFrontendSkillDefinitions(sources.frontendContent),
};
const backend = {
  defaultMap: parseBackendRuntimeMapCatalog(combinedBackendBattleSource()),
  weaponDefinitions: parseBackendRuntimeWeaponDefinitions(combinedBackendBattleSource()),
  skillDefinitions: parseBackendRuntimeSkillDefinitions(combinedBackendBattleSource()),
};

const failures = [];
pushNonEmptyRecordFailure("frontend weaponDefinitions", frontend.weaponDefinitions, failures);
pushNonEmptyRecordFailure("backend weaponDefinitions", backend.weaponDefinitions, failures);
pushNonEmptyRecordFailure("frontend skillDefinitions", frontend.skillDefinitions, failures);
pushNonEmptyRecordFailure("backend skillDefinitions", backend.skillDefinitions, failures);
compareValues("defaultMap", frontend.defaultMap, backend.defaultMap, failures);
compareValues("weaponDefinitions", frontend.weaponDefinitions, backend.weaponDefinitions, failures);
compareValues("skillDefinitions", frontend.skillDefinitions, backend.skillDefinitions, failures);
validateBattleContent("frontend", frontend, failures);
validateBattleContent("backend", backend, failures);

if (failures.length > 0) {
  console.error("Battle content contract audit failed:");
  for (const failure of failures) {
    console.error(`  - ${failure}`);
  }
  process.exitCode = 1;
} else {
  console.log("Battle content contract audit passed.");
  console.log(`Default map: ${frontend.defaultMap.mapId}`);
  console.log(`Hero spawn points: ${frontend.defaultMap.heroSpawnPoints.length}`);
  console.log(`Inner obstacles: ${frontend.defaultMap.innerObstacles.length}`);
  console.log(`Weapon pickups: ${frontend.defaultMap.weaponPickupDefinitions.length}`);
  console.log(`Item pickups: ${frontend.defaultMap.itemPickupDefinitions.length}`);
  console.log(`Weapon definitions: ${Object.keys(frontend.weaponDefinitions).length} (${Object.keys(frontend.weaponDefinitions).sort().join(", ")})`);
  console.log(`Skill definitions: ${Object.keys(frontend.skillDefinitions).length}`);
}

function pushNonEmptyRecordFailure(label, record, target) {
  if (Object.keys(record).length === 0) {
    target.push(`${label} must not be empty.`);
  }
}

function combinedBackendBattleSource() {
  return `${sources.backendCatalog}\n${sources.backendRuntime}`;
}

function validateBattleContent(prefix, content, target) {
  validateMap(`${prefix}.defaultMap`, content.defaultMap, content.weaponDefinitions, target);
  validateWeaponDefinitions(`${prefix}.weaponDefinitions`, content.weaponDefinitions, target);
  validateSkillDefinitions(`${prefix}.skillDefinitions`, content.skillDefinitions, target);
}

function validateMap(path, map, weaponDefinitions, target) {
  assertNonEmptyString(`${path}.mapId`, map.mapId, target);
  assertNonEmptyString(`${path}.themeId`, map.themeId, target);
  assertPositiveNumber(`${path}.worldSize.x`, map.worldSize?.x, target);
  assertPositiveNumber(`${path}.worldSize.y`, map.worldSize?.y, target);

  if (map.heroSpawnPoints.length < 2) {
    target.push(`${path}.heroSpawnPoints.length: expected at least 2, got ${map.heroSpawnPoints.length}`);
  }
  map.heroSpawnPoints.forEach((spawnPoint, index) => {
    assertPointInWorld(`${path}.heroSpawnPoints[${index}]`, spawnPoint, map.worldSize, target);
  });

  assertUniqueNonEmptyStrings(
    `${path}.innerObstacles`,
    map.innerObstacles.map((obstacle) => obstacle.obstacleId),
    "obstacleId",
    target
  );
  map.innerObstacles.forEach((obstacle, index) => {
    const obstaclePath = `${path}.innerObstacles[${index}]`;
    assertPositiveNumber(`${obstaclePath}.size.x`, obstacle.size?.x, target);
    assertPositiveNumber(`${obstaclePath}.size.y`, obstacle.size?.y, target);
    assertRectInWorld(obstaclePath, obstacle.position, obstacle.size, map.worldSize, target);
  });

  assertUniqueNonEmptyStrings(
    `${path}.weaponPickupDefinitions`,
    map.weaponPickupDefinitions.map((pickup) => pickup.pickupId),
    "pickupId",
    target
  );
  map.weaponPickupDefinitions.forEach((pickup, index) => {
    const pickupPath = `${path}.weaponPickupDefinitions[${index}]`;
    assertPointInWorld(`${pickupPath}.position`, pickup.position, map.worldSize, target);
    assertNonEmptyString(`${pickupPath}.weaponKind`, pickup.weaponKind, target);
    if (!Object.hasOwn(weaponDefinitions, pickup.weaponKind)) {
      target.push(`${pickupPath}.weaponKind: missing weapon definition for ${formatValue(pickup.weaponKind)}`);
    }
  });

  assertUniqueNonEmptyStrings(
    `${path}.itemPickupDefinitions`,
    map.itemPickupDefinitions.map((pickup) => pickup.pickupId),
    "pickupId",
    target
  );
  map.itemPickupDefinitions.forEach((pickup, index) => {
    const pickupPath = `${path}.itemPickupDefinitions[${index}]`;
    assertPointInWorld(`${pickupPath}.position`, pickup.position, map.worldSize, target);
    assertNonEmptyString(`${pickupPath}.kind`, pickup.kind, target);
    if (pickup.kind !== "Medkit") {
      target.push(`${pickupPath}.kind: unsupported item pickup kind ${formatValue(pickup.kind)}`);
    }
  });
}

function validateWeaponDefinitions(path, weaponDefinitions, target) {
  for (const [weaponKind, definition] of Object.entries(weaponDefinitions)) {
    const definitionPath = `${path}.${weaponKind}`;
    assertNonEmptyString(`${definitionPath}.projectileKind`, definition.projectileKind, target);
    assertNonNegativeNumber(`${definitionPath}.cooldownMs`, definition.cooldownMs, target);
    assertNonNegativeNumber(`${definitionPath}.reloadMs`, definition.reloadMs, target);
    assertPositiveNumber(`${definitionPath}.projectileSpeedPerSecond`, definition.projectileSpeedPerSecond, target);
    assertPositiveNumber(`${definitionPath}.projectileDamage`, definition.projectileDamage, target);
    assertPositiveNumber(`${definitionPath}.projectileLifetimeMs`, definition.projectileLifetimeMs, target);
    assertPositiveNumber(`${definitionPath}.projectileRadius`, definition.projectileRadius, target);
    assertNonNegativeNumber(`${definitionPath}.splashRadius`, definition.splashRadius, target);
    assertPositiveNumber(`${definitionPath}.pellets`, definition.pellets, target);
    assertNonNegativeNumber(`${definitionPath}.spreadRadians`, definition.spreadRadians, target);
    assertNonNegativeNumber(`${definitionPath}.magazineSize`, definition.magazineSize, target);
    assertNonNegativeNumber(`${definitionPath}.reserveAmmo`, definition.reserveAmmo, target);
    assertNonNegativeNumber(`${definitionPath}.pickupAmmo`, definition.pickupAmmo, target);
    assertNonNegativeNumber(`${definitionPath}.recoilStrength`, definition.recoilStrength, target);
    assertBoolean(`${definitionPath}.usesHeat`, definition.usesHeat, target);
    assertNonNegativeNumber(`${definitionPath}.maxHeat`, definition.maxHeat, target);
    assertNonNegativeNumber(`${definitionPath}.heatPerShot`, definition.heatPerShot, target);
    assertNonNegativeNumber(`${definitionPath}.coolRatePerSecond`, definition.coolRatePerSecond, target);
    assertNonNegativeNumber(`${definitionPath}.overheatLockMs`, definition.overheatLockMs, target);

    if (definition.usesHeat) {
      assertPositiveNumber(`${definitionPath}.maxHeat`, definition.maxHeat, target);
      assertPositiveNumber(`${definitionPath}.heatPerShot`, definition.heatPerShot, target);
      assertPositiveNumber(`${definitionPath}.coolRatePerSecond`, definition.coolRatePerSecond, target);
      assertPositiveNumber(`${definitionPath}.overheatLockMs`, definition.overheatLockMs, target);
    }
  }
}

function validateSkillDefinitions(path, skillDefinitions, target) {
  for (const [skillKind, definition] of Object.entries(skillDefinitions)) {
    const definitionPath = `${path}.${skillKind}`;
    if (definition.skillKind !== skillKind) {
      target.push(`${definitionPath}.skillKind: expected ${formatValue(skillKind)}, got ${formatValue(definition.skillKind)}`);
    }
    assertNonEmptyString(`${definitionPath}.activationKind`, definition.activationKind, target);
    assertNonEmptyString(`${definitionPath}.effectType`, definition.effectType, target);
    assertNonNegativeNumber(`${definitionPath}.cooldownMs`, definition.cooldownMs, target);
    assertNonNegativeNumber(`${definitionPath}.activeMs`, definition.activeMs, target);

    if (definition.effectType === "teleport") {
      assertEquals(`${definitionPath}.activationKind`, definition.activationKind, "prepared-target", target);
      assertPositiveNumber(`${definitionPath}.range`, definition.range, target);
    } else if (definition.effectType === "dash") {
      assertEquals(`${definitionPath}.activationKind`, definition.activationKind, "instant", target);
      assertPositiveNumber(`${definitionPath}.distance`, definition.distance, target);
    } else if (definition.effectType === "slow-field") {
      assertEquals(`${definitionPath}.activationKind`, definition.activationKind, "prepared-target", target);
      assertPositiveNumber(`${definitionPath}.range`, definition.range, target);
      assertPositiveNumber(`${definitionPath}.radius`, definition.radius, target);
      assertPositiveNumber(`${definitionPath}.durationMs`, definition.durationMs, target);
      assertPositiveNumber(`${definitionPath}.speedMultiplier`, definition.speedMultiplier, target);
    } else {
      target.push(`${definitionPath}.effectType: unsupported effect type ${formatValue(definition.effectType)}`);
    }
  }
}

function assertUniqueNonEmptyStrings(path, values, fieldName, target) {
  const seen = new Map();
  values.forEach((value, index) => {
    const valuePath = `${path}[${index}].${fieldName}`;
    assertNonEmptyString(valuePath, value, target);
    if (typeof value !== "string" || value.trim() === "") {
      return;
    }
    if (seen.has(value)) {
      target.push(`${valuePath}: duplicate value ${formatValue(value)} first seen at ${path}[${seen.get(value)}].${fieldName}`);
    } else {
      seen.set(value, index);
    }
  });
}

function assertRectInWorld(path, position, size, worldSize, target) {
  assertPointInWorld(`${path}.position`, position, worldSize, target);
  if (
    !isFiniteNumber(position?.x) ||
    !isFiniteNumber(position?.y) ||
    !isFiniteNumber(size?.x) ||
    !isFiniteNumber(size?.y) ||
    !isFiniteNumber(worldSize?.x) ||
    !isFiniteNumber(worldSize?.y)
  ) {
    return;
  }

  const minX = position.x - size.x / 2;
  const maxX = position.x + size.x / 2;
  const minY = position.y - size.y / 2;
  const maxY = position.y + size.y / 2;
  if (minX < 0 || maxX > worldSize.x || minY < 0 || maxY > worldSize.y) {
    target.push(`${path}: obstacle bounds ${formatValue({ minX, maxX, minY, maxY })} exceed worldSize ${formatValue(worldSize)}`);
  }
}

function assertPointInWorld(path, point, worldSize, target) {
  assertFiniteNumber(`${path}.x`, point?.x, target);
  assertFiniteNumber(`${path}.y`, point?.y, target);
  if (!isFiniteNumber(point?.x) || !isFiniteNumber(point?.y) || !isFiniteNumber(worldSize?.x) || !isFiniteNumber(worldSize?.y)) {
    return;
  }
  if (point.x < 0 || point.x > worldSize.x) {
    target.push(`${path}.x: expected within [0, ${worldSize.x}], got ${formatValue(point.x)}`);
  }
  if (point.y < 0 || point.y > worldSize.y) {
    target.push(`${path}.y: expected within [0, ${worldSize.y}], got ${formatValue(point.y)}`);
  }
}

function assertEquals(path, actual, expected, target) {
  if (!scalarEquals(actual, expected)) {
    target.push(`${path}: expected ${formatValue(expected)}, got ${formatValue(actual)}`);
  }
}

function assertBoolean(path, value, target) {
  if (typeof value !== "boolean") {
    target.push(`${path}: expected boolean, got ${formatValue(value)}`);
  }
}

function assertNonEmptyString(path, value, target) {
  if (typeof value !== "string" || value.trim() === "") {
    target.push(`${path}: expected non-empty string, got ${formatValue(value)}`);
  }
}

function assertPositiveNumber(path, value, target) {
  assertFiniteNumber(path, value, target);
  if (isFiniteNumber(value) && value <= 0) {
    target.push(`${path}: expected positive number, got ${formatValue(value)}`);
  }
}

function assertNonNegativeNumber(path, value, target) {
  assertFiniteNumber(path, value, target);
  if (isFiniteNumber(value) && value < 0) {
    target.push(`${path}: expected non-negative number, got ${formatValue(value)}`);
  }
}

function assertFiniteNumber(path, value, target) {
  if (!isFiniteNumber(value)) {
    target.push(`${path}: expected finite number, got ${formatValue(value)}`);
  }
}

function isFiniteNumber(value) {
  return typeof value === "number" && Number.isFinite(value);
}

function parseFrontendMapCatalog(source) {
  const defaultMap = extractTsConstObject(source, "DEFAULT_BATTLE_MAP");
  const heroSpawnPoints = parseTsVecArray(extractTsConstArray(source, "HERO_SPAWN_POINTS"));
  const innerObstacles = extractTopLevelObjectBlocks(extractTsConstArray(source, "INNER_OBSTACLES")).map((block) => ({
    obstacleId: readTsStringField(block, "obstacleId"),
    kind: readTsStringField(block, "kind"),
    position: readTsVecField(block, "position"),
    size: readTsVecField(block, "size"),
  }));
  const weaponPickupDefinitions = extractTopLevelObjectBlocks(extractTsConstArray(source, "WEAPON_PICKUP_DEFINITIONS")).map(
    (block) => ({
      pickupId: readTsStringField(block, "pickupId"),
      weaponKind: readTsStringField(block, "weaponKind"),
      position: readTsVecField(block, "position"),
    })
  );
  const itemPickupDefinitions = extractTopLevelObjectBlocks(extractTsConstArray(source, "ITEM_PICKUP_DEFINITIONS")).map(
    (block) => ({
      pickupId: readTsStringField(block, "pickupId"),
      kind: readTsStringField(block, "kind"),
      position: readTsVecField(block, "position"),
    })
  );

  return {
    mapId: readTsStringField(defaultMap, "mapId"),
    themeId: readTsStringField(defaultMap, "themeId"),
    worldSize: readTsVecField(defaultMap, "worldSize"),
    heroSpawnPoints,
    innerObstacles,
    weaponPickupDefinitions,
    itemPickupDefinitions,
  };
}

function parseFrontendWeaponDefinitions(source) {
  const record = extractTsObjectRecord(extractTsConstObject(source, "WEAPON_DEFINITIONS"));
  const definitions = {};

  for (const [weaponKind, block] of record.entries()) {
    definitions[weaponKind] = Object.fromEntries(
      WEAPON_FIELDS.map((fieldName) => [fieldName, readTsScalarField(block, fieldName)])
    );
  }

  return definitions;
}

function parseFrontendSkillDefinitions(source) {
  const record = extractTsObjectRecord(extractTsConstObject(source, "SKILL_DEFINITIONS"));
  const definitions = {};

  for (const [skillKind, block] of record.entries()) {
    definitions[skillKind] = Object.fromEntries(
      SKILL_FIELDS.map((fieldName) => [fieldName, readTsOptionalScalarField(block, fieldName)])
    );
  }

  return definitions;
}

function parseBackendRuntimeMapCatalog(source) {
  const constants = parseScalaConstants(source, "");
  const pickups = parseBackendRuntimePickupDefinitions(source);

  return {
    mapId: constants.get("MapId") ?? null,
    themeId: constants.get("ThemeId") ?? null,
    worldSize: readScalaValVec(source, "WorldSize"),
    heroSpawnPoints: parseScalaVecArray(extractScalaValVector(source, "SpawnPoints")),
    innerObstacles: parseBackendRuntimeInnerObstacles(source),
    weaponPickupDefinitions: pickups.weaponPickupDefinitions,
    itemPickupDefinitions: pickups.itemPickupDefinitions,
  };
}

function parseBackendRuntimeInnerObstacles(source) {
  return [
    ...extractScalaDefVector(source, "innerObstacles").matchAll(
      /ArenaObstacle\(\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*BattleVector2\(\s*([^)]+?)\s*,\s*([^)]+?)\s*\)\s*,\s*BattleVector2\(\s*([^)]+?)\s*,\s*([^)]+?)\s*\)\s*\)/g
    ),
  ].map((match) => ({
    obstacleId: match[1],
    kind: match[2],
    position: vec(Number(match[3]), Number(match[4])),
    size: vec(Number(match[5]), Number(match[6])),
  }));
}

function parseBackendRuntimePickupDefinitions(source) {
  const calls = extractScalaCalls(extractScalaDefVector(source, "initialPickups"), "BattlePickupState");
  const weaponPickupDefinitions = [];
  const itemPickupDefinitions = [];

  for (const call of calls) {
    const args = parseScalaNamedArgs(call);
    const pickupId = readScalaWrapperString(args.get("pickupId"), "PickupId");
    const pickupKind = readScalaEnum(args.get("pickupKind"), "PickupKind");
    const position = parseScalaVecExpression(args.get("position"));

    if (pickupKind === "Weapon") {
      weaponPickupDefinitions.push({
        pickupId,
        weaponKind: readScalaOptionalEnum(args.get("weaponKind"), "WeaponKind"),
        position,
      });
    } else if (pickupKind === "Medkit") {
      itemPickupDefinitions.push({
        pickupId,
        kind: "Medkit",
        position,
      });
    }
  }

  return { weaponPickupDefinitions, itemPickupDefinitions };
}

function parseBackendRuntimeWeaponDefinitions(source) {
  const constants = parseScalaConstants(source, "");

  return {
    Pistol: {
      projectileKind: "pistol-bullet",
      cooldownMs: constants.get("PistolFireCooldownMs"),
      reloadMs: constants.get("PistolReloadMs"),
      projectileSpeedPerSecond: constants.get("PistolProjectileSpeed"),
      projectileDamage: constants.get("PistolDamage"),
      projectileLifetimeMs: constants.get("PistolProjectileLifetimeMs"),
      projectileRadius: constants.get("PistolProjectileRadius"),
      splashRadius: 0,
      pellets: 1,
      spreadRadians: 0,
      magazineSize: constants.get("PistolMagazineSize"),
      reserveAmmo: constants.get("InitialPistolReserveAmmo"),
      pickupAmmo: constants.get("PistolPickupAmmo"),
      recoilStrength: constants.get("PistolRecoilStrength"),
      usesHeat: false,
      maxHeat: 0,
      heatPerShot: 0,
      coolRatePerSecond: 0,
      overheatLockMs: 0,
    },
    RocketLauncher: {
      projectileKind: "rocket",
      cooldownMs: constants.get("RocketCooldownMs"),
      reloadMs: constants.get("RocketReloadMs"),
      projectileSpeedPerSecond: constants.get("RocketProjectileSpeed"),
      projectileDamage: constants.get("RocketDamage"),
      projectileLifetimeMs: constants.get("RocketProjectileLifetimeMs"),
      projectileRadius: constants.get("RocketProjectileRadius"),
      splashRadius: constants.get("RocketSplashRadius"),
      pellets: 1,
      spreadRadians: 0,
      magazineSize: constants.get("RocketMagazineSize"),
      reserveAmmo: constants.get("RocketReserveAmmo"),
      pickupAmmo: constants.get("RocketPickupAmmo"),
      recoilStrength: constants.get("RocketRecoilStrength"),
      usesHeat: false,
      maxHeat: 0,
      heatPerShot: 0,
      coolRatePerSecond: 0,
      overheatLockMs: 0,
    },
    Gatling: {
      projectileKind: "gatling-bullet",
      cooldownMs: constants.get("GatlingCooldownMs"),
      reloadMs: constants.get("GatlingReloadMs"),
      projectileSpeedPerSecond: constants.get("GatlingProjectileSpeed"),
      projectileDamage: constants.get("GatlingDamage"),
      projectileLifetimeMs: constants.get("GatlingProjectileLifetimeMs"),
      projectileRadius: constants.get("GatlingProjectileRadius"),
      splashRadius: 0,
      pellets: 1,
      spreadRadians: constants.get("GatlingSpreadRadians"),
      magazineSize: constants.get("GatlingMagazineSize"),
      reserveAmmo: 0,
      pickupAmmo: constants.get("GatlingPickupAmmo"),
      recoilStrength: constants.get("GatlingRecoilStrength"),
      usesHeat: true,
      maxHeat: constants.get("GatlingMaxHeat"),
      heatPerShot: constants.get("GatlingHeatPerShot"),
      coolRatePerSecond: constants.get("GatlingCoolRatePerSecond"),
      overheatLockMs: constants.get("GatlingOverheatLockMs"),
    },
    Shotgun: {
      projectileKind: "shotgun-pellet",
      cooldownMs: constants.get("ShotgunCooldownMs"),
      reloadMs: constants.get("ShotgunReloadMs"),
      projectileSpeedPerSecond: constants.get("ShotgunProjectileSpeed"),
      projectileDamage: constants.get("ShotgunDamage"),
      projectileLifetimeMs: constants.get("ShotgunProjectileLifetimeMs"),
      projectileRadius: constants.get("ShotgunProjectileRadius"),
      splashRadius: 0,
      pellets: constants.get("ShotgunPellets"),
      spreadRadians: constants.get("ShotgunSpreadRadians"),
      magazineSize: constants.get("ShotgunMagazineSize"),
      reserveAmmo: constants.get("ShotgunReserveAmmo"),
      pickupAmmo: constants.get("ShotgunPickupAmmo"),
      recoilStrength: constants.get("ShotgunRecoilStrength"),
      usesHeat: false,
      maxHeat: 0,
      heatPerShot: 0,
      coolRatePerSecond: 0,
      overheatLockMs: 0,
    },
  };
}

function parseBackendRuntimeSkillDefinitions(source) {
  const constants = parseScalaConstants(source, "");

  return {
    Blink: {
      skillKind: "Blink",
      activationKind: "prepared-target",
      effectType: "teleport",
      cooldownMs: constants.get("BlinkCooldownMs"),
      activeMs: constants.get("BlinkActiveMs"),
      range: constants.get("BlinkRange"),
      radius: null,
      durationMs: null,
      distance: null,
      speedMultiplier: null,
    },
    Dash: {
      skillKind: "Dash",
      activationKind: "instant",
      effectType: "dash",
      cooldownMs: constants.get("DashCooldownMs"),
      activeMs: constants.get("DashActiveMs"),
      range: null,
      radius: null,
      durationMs: null,
      distance: constants.get("DashDistance"),
      speedMultiplier: null,
    },
    Freeze: {
      skillKind: "Freeze",
      activationKind: "prepared-target",
      effectType: "slow-field",
      cooldownMs: constants.get("FreezeCooldownMs"),
      activeMs: constants.get("FreezeDurationMs"),
      range: constants.get("FreezeCastRange"),
      radius: constants.get("FreezeRadius"),
      durationMs: constants.get("FreezeDurationMs"),
      distance: null,
      speedMultiplier: constants.get("SlowFieldMovementFactor"),
    },
  };
}

function parseBackendMapCatalog(source) {
  const defaultMapStart = source.indexOf("val defaultMap");
  if (defaultMapStart < 0) {
    throw new Error("Could not find backend defaultMap.");
  }

  const defaultMap = extractScalaCall(source, "BattleMapConfig", defaultMapStart);
  const heroSpawnPoints = parseScalaVecArray(extractScalaNamedVector(defaultMap, "heroSpawnPoints"));
  const innerObstacles = [
    ...extractScalaNamedVector(defaultMap, "innerObstacles").matchAll(
      /ArenaObstacleDefinition\(\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*BattleVector2\(\s*([^)]+?)\s*,\s*([^)]+?)\s*\)\s*,\s*BattleVector2\(\s*([^)]+?)\s*,\s*([^)]+?)\s*\)\s*\)/g
    ),
  ].map((match) => ({
    obstacleId: match[1],
    kind: match[2],
    position: vec(Number(match[3]), Number(match[4])),
    size: vec(Number(match[5]), Number(match[6])),
  }));
  const weaponPickupDefinitions = [
    ...extractScalaNamedVector(defaultMap, "weaponPickupDefinitions").matchAll(
      /WeaponPickupDefinition\(\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*BattleVector2\(\s*([^)]+?)\s*,\s*([^)]+?)\s*\)\s*\)/g
    ),
  ].map((match) => ({
    pickupId: match[1],
    weaponKind: match[2],
    position: vec(Number(match[3]), Number(match[4])),
  }));
  const itemPickupDefinitions = [
    ...extractScalaNamedVector(defaultMap, "itemPickupDefinitions").matchAll(
      /ItemPickupDefinition\(\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*BattleVector2\(\s*([^)]+?)\s*,\s*([^)]+?)\s*\)\s*\)/g
    ),
  ].map((match) => ({
    pickupId: match[1],
    kind: match[2],
    position: vec(Number(match[3]), Number(match[4])),
  }));

  return {
    mapId: readScalaNamedString(defaultMap, "mapId"),
    themeId: readScalaNamedString(defaultMap, "themeId"),
    worldSize: readScalaNamedVec(defaultMap, "worldSize"),
    heroSpawnPoints,
    innerObstacles,
    weaponPickupDefinitions,
    itemPickupDefinitions,
  };
}

function parseBackendWeaponDefinitions(source, geometrySource) {
  const constants = parseScalaConstants(source, geometrySource);
  const definitions = {};
  const entries = extractScalaMapEntries(extractScalaNamedMap(source, "weaponDefinitions"), "WeaponDefinition", constants);
  const defaults = {
    usesHeat: false,
    maxHeat: 0,
    heatPerShot: 0,
    coolRatePerSecond: 0,
    overheatLockMs: 0,
  };

  for (const [weaponKind, args] of entries.entries()) {
    definitions[weaponKind] = {};
    for (const fieldName of WEAPON_FIELDS) {
      definitions[weaponKind][fieldName] =
        args.has(fieldName) ? resolveScalaValue(args.get(fieldName), constants) : defaults[fieldName];
    }
  }

  return definitions;
}

function parseBackendSkillDefinitions(source) {
  const constants = parseScalaConstants(source, "");
  const definitions = {};
  const entries = extractScalaMapEntries(extractScalaNamedMap(source, "skillDefinitions"), "SkillDefinition", constants);
  const defaults = {
    range: null,
    radius: null,
    durationMs: null,
    distance: null,
    speedMultiplier: null,
  };

  for (const [skillKind, args] of entries.entries()) {
    definitions[skillKind] = {};
    for (const fieldName of SKILL_FIELDS) {
      definitions[skillKind][fieldName] =
        args.has(fieldName) ? resolveScalaValue(args.get(fieldName), constants) : defaults[fieldName];
    }
  }

  return definitions;
}

function parseScalaConstants(source, geometrySource) {
  const constants = new Map();

  for (const match of geometrySource.matchAll(/\bval\s+(\w+)\s*:\s*(?:Double|Int|Long)\s*=\s*([-+]?\d+(?:\.\d+)?L?)/g)) {
    constants.set(`AuthoritativeArenaGeometry.${match[1]}`, parseScalaNumber(match[2]));
  }

  for (const match of source.matchAll(/\bval\s+(\w+)\s*:\s*String\s*=\s*"([^"]+)"/g)) {
    constants.set(match[1], match[2]);
  }

  for (const match of source.matchAll(/\bval\s+(\w+)\s*:\s*(?:Double|Int|Long)\s*=\s*([A-Za-z0-9_.]+|[-+]?\d+(?:\.\d+)?L?)/g)) {
    const resolved = tryResolveScalaValue(match[2], constants);
    if (resolved.resolved) {
      constants.set(match[1], resolved.value);
    }
  }

  return constants;
}

function tryResolveScalaValue(rawValue, constants) {
  try {
    return {
      resolved: true,
      value: resolveScalaValue(rawValue, constants),
    };
  } catch {
    return {
      resolved: false,
      value: null,
    };
  }
}

function resolveScalaValue(rawValue, constants) {
  const value = rawValue.trim();

  if (value.startsWith("Some(") && value.endsWith(")")) {
    return resolveScalaValue(value.slice("Some(".length, -1), constants);
  }

  if (value === "None") {
    return null;
  }

  if (value === "true") {
    return true;
  }

  if (value === "false") {
    return false;
  }

  const stringMatch = /^"([^"]*)"$/.exec(value);
  if (stringMatch) {
    return stringMatch[1];
  }

  if (/^[-+]?\d+(?:\.\d+)?L?$/.test(value)) {
    return parseScalaNumber(value);
  }

  if (constants.has(value)) {
    return constants.get(value);
  }

  throw new Error(`Could not resolve Scala value: ${value}`);
}

function extractScalaMapEntries(mapBody, callName, constants) {
  const entries = new Map();
  let index = 0;

  while (index < mapBody.length) {
    index = skipWhitespaceAndCommas(mapBody, index);
    if (index >= mapBody.length) {
      break;
    }

    const arrowIndex = findTopLevelArrow(mapBody, index);
    if (arrowIndex < 0) {
      break;
    }

    const keyExpression = mapBody.slice(index, arrowIndex).trim();
    const callIndex = mapBody.indexOf(`${callName}(`, arrowIndex + 2);
    if (callIndex < 0) {
      throw new Error(`Could not find ${callName} call for ${keyExpression}.`);
    }

    const openIndex = mapBody.indexOf("(", callIndex);
    const closeIndex = findMatchingBracket(mapBody, openIndex, "(", ")");
    if (closeIndex < 0) {
      throw new Error(`Could not find closing parenthesis for ${callName} ${keyExpression}.`);
    }

    entries.set(resolveScalaValue(keyExpression, constants), parseScalaNamedArgs(mapBody.slice(openIndex + 1, closeIndex)));
    index = closeIndex + 1;
  }

  return entries;
}

function parseScalaNamedArgs(argsBody) {
  const args = new Map();

  for (const part of splitTopLevel(argsBody, ",")) {
    const equalIndex = part.indexOf("=");
    if (equalIndex < 0) {
      continue;
    }

    args.set(part.slice(0, equalIndex).trim(), part.slice(equalIndex + 1).trim());
  }

  return args;
}

function extractTsObjectRecord(objectSource) {
  const record = new Map();
  const openIndex = objectSource.indexOf("{");
  const closeIndex = objectSource.lastIndexOf("}");
  let index = openIndex + 1;

  while (index < closeIndex) {
    index = skipWhitespaceAndCommas(objectSource, index);
    if (index >= closeIndex) {
      break;
    }

    const key = readTsObjectKey(objectSource, index);
    index = key.nextIndex;
    index = skipWhitespace(objectSource, index);

    if (objectSource[index] !== ":") {
      throw new Error(`Expected ':' after TS object key ${key.value}.`);
    }

    index = skipWhitespace(objectSource, index + 1);
    if (objectSource[index] !== "{") {
      throw new Error(`Expected object value for TS key ${key.value}.`);
    }

    const valueEnd = findMatchingBracket(objectSource, index, "{", "}");
    if (valueEnd < 0) {
      throw new Error(`Could not find object value end for TS key ${key.value}.`);
    }

    record.set(key.value, objectSource.slice(index, valueEnd + 1));
    index = valueEnd + 1;
  }

  return record;
}

function readTsObjectKey(source, startIndex) {
  const quote = source[startIndex];

  if (quote === "\"" || quote === "'") {
    let index = startIndex + 1;
    let escaped = false;

    while (index < source.length) {
      const char = source[index];
      if (escaped) {
        escaped = false;
      } else if (char === "\\") {
        escaped = true;
      } else if (char === quote) {
        return {
          value: source.slice(startIndex + 1, index),
          nextIndex: index + 1,
        };
      }
      index += 1;
    }
  }

  const match = /^[A-Za-z_$][\w$-]*/.exec(source.slice(startIndex));
  if (!match) {
    throw new Error(`Could not read TS object key near: ${source.slice(startIndex, startIndex + 40)}`);
  }

  return {
    value: match[0],
    nextIndex: startIndex + match[0].length,
  };
}

function extractTsConstObject(source, constName) {
  const declarationIndex = findTsConstDeclaration(source, constName);
  const objectStart = source.indexOf("{", declarationIndex);
  const objectEnd = findMatchingBracket(source, objectStart, "{", "}");

  if (objectStart < 0 || objectEnd < 0) {
    throw new Error(`Could not find object initializer for ${constName}.`);
  }

  return source.slice(objectStart, objectEnd + 1);
}

function extractTsConstArray(source, constName) {
  const declarationIndex = findTsConstDeclaration(source, constName);
  const arrayStart = source.indexOf("[", declarationIndex);
  const arrayEnd = findMatchingBracket(source, arrayStart, "[", "]");

  if (arrayStart < 0 || arrayEnd < 0) {
    throw new Error(`Could not find array initializer for ${constName}.`);
  }

  return source.slice(arrayStart, arrayEnd + 1);
}

function findTsConstDeclaration(source, constName) {
  const match = new RegExp(`\\bconst\\s+${constName}\\b`).exec(source);
  if (!match) {
    throw new Error(`Could not find const ${constName}.`);
  }

  return match.index;
}

function extractTopLevelObjectBlocks(arraySource) {
  const blocks = [];
  let objectStart = -1;
  let objectDepth = 0;
  let arrayDepth = 0;
  let quote = null;
  let escaped = false;

  for (let index = 0; index < arraySource.length; index += 1) {
    const char = arraySource[index];

    if (quote) {
      if (escaped) {
        escaped = false;
      } else if (char === "\\") {
        escaped = true;
      } else if (char === quote) {
        quote = null;
      }
      continue;
    }

    if (char === "\"" || char === "'" || char === "`") {
      quote = char;
      continue;
    }

    if (char === "[") {
      arrayDepth += 1;
      continue;
    }

    if (char === "]") {
      arrayDepth -= 1;
      continue;
    }

    if (char === "{" && arrayDepth === 1) {
      if (objectDepth === 0) {
        objectStart = index;
      }
      objectDepth += 1;
      continue;
    }

    if (char === "}" && objectDepth > 0) {
      objectDepth -= 1;
      if (objectDepth === 0 && objectStart >= 0) {
        blocks.push(arraySource.slice(objectStart, index + 1));
        objectStart = -1;
      }
    }
  }

  return blocks;
}

function parseTsVecArray(arraySource) {
  return [...arraySource.matchAll(/\{\s*x\s*:\s*([-+]?\d+(?:\.\d+)?)\s*,\s*y\s*:\s*([-+]?\d+(?:\.\d+)?)\s*\}/g)].map(
    (match) => vec(Number(match[1]), Number(match[2]))
  );
}

function readTsStringField(block, fieldName) {
  const match = new RegExp(`\\b${fieldName}\\s*:\\s*"([^"]*)"`).exec(block);
  if (!match) {
    throw new Error(`Missing TS string field ${fieldName}.`);
  }

  return match[1];
}

function readTsVecField(block, fieldName) {
  const match = new RegExp(
    `\\b${fieldName}\\s*:\\s*\\{\\s*x\\s*:\\s*([-+]?\\d+(?:\\.\\d+)?)\\s*,\\s*y\\s*:\\s*([-+]?\\d+(?:\\.\\d+)?)\\s*\\}`
  ).exec(block);
  if (!match) {
    throw new Error(`Missing TS vec field ${fieldName}.`);
  }

  return vec(Number(match[1]), Number(match[2]));
}

function readTsOptionalScalarField(block, fieldName) {
  return hasTsField(block, fieldName) ? readTsScalarField(block, fieldName) : null;
}

function readTsScalarField(block, fieldName) {
  const match = new RegExp(`\\b${fieldName}\\s*:\\s*("[^"]*"|true|false|[-+]?\\d+(?:\\.\\d+)?)`).exec(block);
  if (!match) {
    throw new Error(`Missing TS scalar field ${fieldName}.`);
  }

  const rawValue = match[1];
  if (rawValue === "true") {
    return true;
  }
  if (rawValue === "false") {
    return false;
  }
  if (rawValue.startsWith("\"")) {
    return rawValue.slice(1, -1);
  }
  return Number(rawValue);
}

function hasTsField(block, fieldName) {
  return new RegExp(`\\b${fieldName}\\s*:`).test(block);
}

function extractScalaCall(source, callName, fromIndex = 0) {
  const callIndex = source.indexOf(`${callName}(`, fromIndex);
  if (callIndex < 0) {
    throw new Error(`Could not find Scala call ${callName}.`);
  }

  const openIndex = source.indexOf("(", callIndex);
  const closeIndex = findMatchingBracket(source, openIndex, "(", ")");
  if (openIndex < 0 || closeIndex < 0) {
    throw new Error(`Could not find Scala call body for ${callName}.`);
  }

  return source.slice(openIndex + 1, closeIndex);
}

function extractScalaNamedMap(source, valName) {
  const valIndex = source.indexOf(`val ${valName}`);
  if (valIndex < 0) {
    throw new Error(`Could not find Scala val ${valName}.`);
  }

  const mapIndex = source.indexOf("Map(", valIndex);
  const openIndex = source.indexOf("(", mapIndex);
  const closeIndex = findMatchingBracket(source, openIndex, "(", ")");
  if (mapIndex < 0 || closeIndex < 0) {
    throw new Error(`Could not find Scala Map for ${valName}.`);
  }

  return source.slice(openIndex + 1, closeIndex);
}

function extractScalaNamedVector(block, fieldName) {
  const fieldMatch = new RegExp(`\\b${fieldName}\\s*=\\s*Vector\\s*\\(`).exec(block);
  if (!fieldMatch) {
    throw new Error(`Could not find Scala Vector field ${fieldName}.`);
  }

  const openIndex = block.indexOf("(", fieldMatch.index);
  const closeIndex = findMatchingBracket(block, openIndex, "(", ")");
  if (openIndex < 0 || closeIndex < 0) {
    throw new Error(`Could not find Scala Vector body for ${fieldName}.`);
  }

  return block.slice(openIndex + 1, closeIndex);
}

function parseScalaVecArray(vectorBody) {
  return [...vectorBody.matchAll(/BattleVector2\(\s*([-+]?\d+(?:\.\d+)?)\s*,\s*([-+]?\d+(?:\.\d+)?)\s*\)/g)].map(
    (match) => vec(Number(match[1]), Number(match[2]))
  );
}

function readScalaNamedString(block, fieldName) {
  const match = new RegExp(`\\b${fieldName}\\s*=\\s*"([^"]*)"`).exec(block);
  if (!match) {
    throw new Error(`Missing Scala string field ${fieldName}.`);
  }

  return match[1];
}

function readScalaNamedVec(block, fieldName) {
  const match = new RegExp(
    `\\b${fieldName}\\s*=\\s*BattleVector2\\(\\s*([-+]?\\d+(?:\\.\\d+)?)\\s*,\\s*([-+]?\\d+(?:\\.\\d+)?)\\s*\\)`
  ).exec(block);
  if (!match) {
    throw new Error(`Missing Scala vec field ${fieldName}.`);
  }

  return vec(Number(match[1]), Number(match[2]));
}

function readScalaValVec(source, valName) {
  const declaration = new RegExp(`\\bval\\s+${valName}\\b`).exec(source);
  if (!declaration) {
    throw new Error(`Could not find Scala val ${valName}.`);
  }

  const callStart = source.indexOf("BattleVector2(", declaration.index);
  if (callStart < 0) {
    throw new Error(`Could not find BattleVector2 initializer for ${valName}.`);
  }

  return parseScalaVecExpression(source.slice(callStart, findMatchingBracket(source, source.indexOf("(", callStart), "(", ")") + 1));
}

function parseScalaVecExpression(expression) {
  const match = /BattleVector2\(\s*([-+]?\d+(?:\.\d+)?)\s*,\s*([-+]?\d+(?:\.\d+)?)\s*\)/.exec(expression ?? "");
  if (!match) {
    throw new Error(`Could not parse Scala BattleVector2 expression: ${expression}`);
  }

  return vec(Number(match[1]), Number(match[2]));
}

function extractScalaVectorAfter(source, marker) {
  const markerIndex = source.indexOf(marker);
  if (markerIndex < 0) {
    throw new Error(`Could not find Scala marker ${marker}.`);
  }

  const vectorIndex = source.indexOf("Vector(", markerIndex);
  const openIndex = source.indexOf("(", vectorIndex);
  const closeIndex = findMatchingBracket(source, openIndex, "(", ")");
  if (vectorIndex < 0 || closeIndex < 0) {
    throw new Error(`Could not find Scala Vector after ${marker}.`);
  }

  return source.slice(openIndex + 1, closeIndex);
}

function extractScalaValVector(source, valName) {
  return extractScalaDeclaredVector(source, new RegExp(`\\bval\\s+${valName}\\b`), `val ${valName}`);
}

function extractScalaDefVector(source, defName) {
  return extractScalaDeclaredVector(source, new RegExp(`\\bdef\\s+${defName}\\b`), `def ${defName}`);
}

function extractScalaDeclaredVector(source, declarationPattern, label) {
  const declaration = declarationPattern.exec(source);
  if (!declaration) {
    throw new Error(`Could not find Scala ${label}.`);
  }

  const vectorIndex = source.indexOf("Vector(", declaration.index);
  const openIndex = source.indexOf("(", vectorIndex);
  const closeIndex = findMatchingBracket(source, openIndex, "(", ")");
  if (vectorIndex < 0 || closeIndex < 0) {
    throw new Error(`Could not find Scala Vector for ${label}.`);
  }

  return source.slice(openIndex + 1, closeIndex);
}

function extractScalaCalls(source, callName) {
  const calls = [];
  let index = 0;

  while (index < source.length) {
    const callIndex = source.indexOf(`${callName}(`, index);
    if (callIndex < 0) {
      break;
    }

    const openIndex = source.indexOf("(", callIndex);
    const closeIndex = findMatchingBracket(source, openIndex, "(", ")");
    if (closeIndex < 0) {
      throw new Error(`Could not find closing parenthesis for ${callName}.`);
    }

    calls.push(source.slice(openIndex + 1, closeIndex));
    index = closeIndex + 1;
  }

  return calls;
}

function readScalaWrapperString(rawValue, wrapperName) {
  const match = new RegExp(`^${wrapperName}\\(\\s*"([^"]+)"\\s*\\)$`).exec((rawValue ?? "").trim());
  if (!match) {
    throw new Error(`Could not parse ${wrapperName} string value: ${rawValue}`);
  }

  return match[1];
}

function readScalaEnum(rawValue, enumName) {
  const match = new RegExp(`^${enumName}\\.([A-Za-z0-9_]+)$`).exec((rawValue ?? "").trim());
  if (!match) {
    throw new Error(`Could not parse ${enumName} enum value: ${rawValue}`);
  }

  return match[1];
}

function readScalaOptionalEnum(rawValue, enumName) {
  const value = (rawValue ?? "").trim();
  if (value === "None") {
    return null;
  }

  const somePrefix = "Some(";
  if (value.startsWith(somePrefix) && value.endsWith(")")) {
    return readScalaEnum(value.slice(somePrefix.length, -1), enumName);
  }

  return readScalaEnum(value, enumName);
}

function findTopLevelArrow(source, startIndex) {
  let depth = 0;
  let quote = null;
  let escaped = false;

  for (let index = startIndex; index < source.length - 1; index += 1) {
    const char = source[index];

    if (quote) {
      if (escaped) {
        escaped = false;
      } else if (char === "\\") {
        escaped = true;
      } else if (char === quote) {
        quote = null;
      }
      continue;
    }

    if (char === "\"" || char === "'") {
      quote = char;
      continue;
    }

    if (char === "(" || char === "{" || char === "[") {
      depth += 1;
      continue;
    }

    if (char === ")" || char === "}" || char === "]") {
      depth -= 1;
      continue;
    }

    if (depth === 0 && source[index] === "-" && source[index + 1] === ">") {
      return index;
    }
  }

  return -1;
}

function splitTopLevel(source, delimiter) {
  const parts = [];
  let start = 0;
  let depth = 0;
  let quote = null;
  let escaped = false;

  for (let index = 0; index < source.length; index += 1) {
    const char = source[index];

    if (quote) {
      if (escaped) {
        escaped = false;
      } else if (char === "\\") {
        escaped = true;
      } else if (char === quote) {
        quote = null;
      }
      continue;
    }

    if (char === "\"" || char === "'") {
      quote = char;
      continue;
    }

    if (char === "(" || char === "{" || char === "[") {
      depth += 1;
      continue;
    }

    if (char === ")" || char === "}" || char === "]") {
      depth -= 1;
      continue;
    }

    if (depth === 0 && char === delimiter) {
      parts.push(source.slice(start, index).trim());
      start = index + 1;
    }
  }

  parts.push(source.slice(start).trim());
  return parts.filter(Boolean);
}

function findMatchingBracket(value, startIndex, openChar, closeChar) {
  let depth = 0;
  let quote = null;
  let escaped = false;

  for (let index = startIndex; index < value.length; index += 1) {
    const char = value[index];

    if (quote) {
      if (escaped) {
        escaped = false;
      } else if (char === "\\") {
        escaped = true;
      } else if (char === quote) {
        quote = null;
      }
      continue;
    }

    if (char === "\"" || char === "'" || char === "`") {
      quote = char;
      continue;
    }

    if (char === openChar) {
      depth += 1;
    } else if (char === closeChar) {
      depth -= 1;
      if (depth === 0) {
        return index;
      }
    }
  }

  return -1;
}

function skipWhitespaceAndCommas(source, index) {
  while (index < source.length && /[\s,]/.test(source[index])) {
    index += 1;
  }
  return index;
}

function skipWhitespace(source, index) {
  while (index < source.length && /\s/.test(source[index])) {
    index += 1;
  }
  return index;
}

function parseScalaNumber(value) {
  return Number(String(value).replace(/L$/, ""));
}

function vec(x, y) {
  return { x, y };
}

function compareValues(path, frontendValue, backendValue, target) {
  if (Array.isArray(frontendValue) || Array.isArray(backendValue)) {
    if (!Array.isArray(frontendValue) || !Array.isArray(backendValue)) {
      target.push(`${path}: frontend=${formatValue(frontendValue)} backend=${formatValue(backendValue)}`);
      return;
    }

    if (frontendValue.length !== backendValue.length) {
      target.push(`${path}.length: frontend=${frontendValue.length} backend=${backendValue.length}`);
    }

    const maxLength = Math.max(frontendValue.length, backendValue.length);
    for (let index = 0; index < maxLength; index += 1) {
      compareValues(`${path}[${index}]`, frontendValue[index], backendValue[index], target);
    }
    return;
  }

  if (isPlainObject(frontendValue) || isPlainObject(backendValue)) {
    if (!isPlainObject(frontendValue) || !isPlainObject(backendValue)) {
      target.push(`${path}: frontend=${formatValue(frontendValue)} backend=${formatValue(backendValue)}`);
      return;
    }

    const keys = [...new Set([...Object.keys(frontendValue), ...Object.keys(backendValue)])].sort();
    for (const key of keys) {
      compareValues(`${path}.${key}`, frontendValue[key], backendValue[key], target);
    }
    return;
  }

  if (!scalarEquals(frontendValue, backendValue)) {
    target.push(`${path}: frontend=${formatValue(frontendValue)} backend=${formatValue(backendValue)}`);
  }
}

function scalarEquals(frontendValue, backendValue) {
  if (typeof frontendValue === "number" && typeof backendValue === "number") {
    return Math.abs(frontendValue - backendValue) < 1e-9;
  }

  return Object.is(frontendValue, backendValue);
}

function isPlainObject(value) {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

function formatValue(value) {
  return JSON.stringify(value);
}
