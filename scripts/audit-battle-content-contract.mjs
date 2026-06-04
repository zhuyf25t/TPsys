import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT_DIR = join(fileURLToPath(new URL("..", import.meta.url)));
const PATHS = {
  frontendWeapons: join(
    ROOT_DIR,
    "frontend",
    "src",
    "objects",
    "battle",
    "microservices",
    "combat",
    "objects",
    "combat",
    "BattleCombatRuleDefinitions.ts"
  ),
  frontendSkills: join(
    ROOT_DIR,
    "frontend",
    "src",
    "objects",
    "battle",
    "microservices",
    "abilities",
    "objects",
    "abilities",
    "BattleAbilityRuleDefinitions.ts"
  ),
  backendDefaults: join(ROOT_DIR, "backend", "src", "test", "scala", "services", "battle", "BattleDynamicRuleTestDefaults.scala"),
  sharedMaps: join(ROOT_DIR, "shared", "battle", "maps")
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
  "projectileCount",
  "spreadRadians",
  "magazineSize",
  "reserveAmmo",
  "pickupAmmo",
  "recoilStrength",
  "usesHeat",
  "maxHeat",
  "heatPerShot",
  "coolRatePerSecond",
  "overheatLockMs"
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
  "speedMultiplier"
];

const PROJECTILE_KIND_WIRE = {
  PistolBullet: "pistol-bullet",
  Rocket: "rocket",
  GatlingBullet: "gatling-bullet",
  ShotgunPellet: "shotgun-pellet"
};

const sources = Object.fromEntries(
  Object.entries(PATHS)
    .filter(([, path]) => !path.endsWith("maps"))
    .map(([key, path]) => [key, readFileSync(path, "utf8")])
);

const frontend = {
  weaponDefinitions: parseFrontendDefinitions(sources.frontendWeapons, "WEAPON_DEFINITIONS", WEAPON_FIELDS),
  skillDefinitions: parseFrontendDefinitions(sources.frontendSkills, "SKILL_DEFINITIONS", SKILL_FIELDS)
};

const backend = {
  weaponDefinitions: parseBackendWeaponDefinitions(sources.backendDefaults),
  skillDefinitions: parseBackendSkillDefinitions(sources.backendDefaults)
};

const sharedMaps = loadSharedMaps(PATHS.sharedMaps);

const failures = [];
pushNonEmptyRecordFailure("frontend weaponDefinitions", frontend.weaponDefinitions, failures);
pushNonEmptyRecordFailure("backend weaponDefinitions", backend.weaponDefinitions, failures);
pushNonEmptyRecordFailure("frontend skillDefinitions", frontend.skillDefinitions, failures);
pushNonEmptyRecordFailure("backend skillDefinitions", backend.skillDefinitions, failures);
pushNonEmptyRecordFailure("shared battle maps", Object.fromEntries(sharedMaps.map((map) => [map.mapId, map])), failures);
compareValues("weaponDefinitions", frontend.weaponDefinitions, backend.weaponDefinitions, failures);
compareValues("skillDefinitions", frontend.skillDefinitions, backend.skillDefinitions, failures);
validateWeaponDefinitions("frontend.weaponDefinitions", frontend.weaponDefinitions, failures);
validateWeaponDefinitions("backend.weaponDefinitions", backend.weaponDefinitions, failures);
validateSkillDefinitions("frontend.skillDefinitions", frontend.skillDefinitions, failures);
validateSkillDefinitions("backend.skillDefinitions", backend.skillDefinitions, failures);
sharedMaps.forEach((map) => validateSharedMap(`sharedMaps.${map.mapId}`, map, frontend.weaponDefinitions, failures));

if (failures.length > 0) {
  console.error("Battle content contract audit failed:");
  for (const failure of failures) {
    console.error(`  - ${failure}`);
  }
  process.exitCode = 1;
} else {
  console.log("Battle content contract audit passed.");
  console.log(`Shared maps: ${sharedMaps.length} (${sharedMaps.map((map) => map.mapId).sort().join(", ")})`);
  console.log(`Weapon definitions: ${Object.keys(frontend.weaponDefinitions).length} (${Object.keys(frontend.weaponDefinitions).sort().join(", ")})`);
  console.log(`Skill definitions: ${Object.keys(frontend.skillDefinitions).length} (${Object.keys(frontend.skillDefinitions).sort().join(", ")})`);
}

function parseFrontendDefinitions(source, constName, fields) {
  const record = extractTsObjectRecord(extractTsConstObject(source, constName));
  const definitions = {};

  for (const [key, block] of record.entries()) {
    definitions[key] = Object.fromEntries(fields.map((fieldName) => [fieldName, readTsOptionalScalarField(block, fieldName)]));
  }

  return definitions;
}

function parseBackendWeaponDefinitions(source) {
  const vectorBody = extractScalaDeclaredVector(source, /\bprivate\s+val\s+weaponRules\b/, "weaponRules");
  const calls = extractScalaCalls(vectorBody, "weaponRule");
  const definitions = {};

  for (const call of calls) {
    const args = parseScalaNamedArgs(call);
    const weaponKind = readScalaEnum(args.get("weaponKind"), "WeaponKind");
    const heat = parseHeatDefinition(args.get("heat"));
    definitions[weaponKind] = {
      projectileKind: PROJECTILE_KIND_WIRE[readScalaEnum(args.get("projectileKind"), "ProjectileKind")],
      cooldownMs: readScalaNumberValue(args.get("cooldownMs")),
      reloadMs: readScalaNumberValue(args.get("reloadMs")),
      projectileSpeedPerSecond: readScalaNumberValue(args.get("speed")),
      projectileDamage: readScalaNumberValue(args.get("damage")),
      projectileLifetimeMs: readScalaNumberValue(args.get("lifetimeMs")),
      projectileRadius: readScalaNumberValue(args.get("projectileRadius")),
      splashRadius: readScalaNumberValue(args.get("splashRadius")),
      projectileCount: readScalaNumberValue(args.get("projectileCount")),
      spreadRadians: readScalaNumberValue(args.get("spread")),
      magazineSize: readScalaNumberValue(args.get("magazineSize")),
      reserveAmmo: readScalaOptionNumber(args.get("reserveAmmo")),
      pickupAmmo: readScalaNumberValue(args.get("pickupAmmo")),
      recoilStrength: readScalaNumberValue(args.get("recoilStrength")),
      usesHeat: heat !== null,
      maxHeat: heat?.maxHeat ?? 0,
      heatPerShot: heat?.heatPerShot ?? 0,
      coolRatePerSecond: heat?.coolRatePerSecond ?? 0,
      overheatLockMs: heat?.overheatLockMs ?? 0
    };
  }

  return definitions;
}

function parseBackendSkillDefinitions(source) {
  const skillRules = extractScalaCallAfter(source, "BattleSkillRuleSet", source.indexOf("private val skillRules"));
  const args = parseScalaNamedArgs(skillRules);
  const blink = parseSkillConfig(args.get("blink"), "BlinkConfig");
  const dash = parseSkillConfig(args.get("dash"), "DashConfig");
  const freeze = parseSkillConfig(args.get("freeze"), "FreezeConfig");
  const critical = parseSkillConfig(args.get("critical"), "CriticalConfig");
  const speedMultiplier = readScalaWrappedNumberAfter(source, "slowFieldMovementFactor", "BattleSlowFactor");

  return {
    Blink: {
      skillKind: "Blink",
      activationKind: "prepared-target",
      effectType: "teleport",
      cooldownMs: blink.cooldownMs,
      activeMs: blink.activeMs,
      range: blink.range,
      radius: null,
      durationMs: null,
      distance: null,
      speedMultiplier: null
    },
    Dash: {
      skillKind: "Dash",
      activationKind: "instant",
      effectType: "dash",
      cooldownMs: dash.cooldownMs,
      activeMs: dash.activeMs,
      range: null,
      radius: null,
      durationMs: null,
      distance: dash.distance,
      speedMultiplier: null
    },
    Freeze: {
      skillKind: "Freeze",
      activationKind: "prepared-target",
      effectType: "slow-field",
      cooldownMs: freeze.cooldownMs,
      activeMs: freeze.activeMs,
      range: freeze.castRange,
      radius: freeze.radius,
      durationMs: freeze.activeMs,
      distance: null,
      speedMultiplier
    },
    Critical: {
      skillKind: "Critical",
      activationKind: "instant",
      effectType: "damage-boost",
      cooldownMs: critical.cooldownMs,
      activeMs: critical.activeMs,
      range: null,
      radius: null,
      durationMs: critical.activeMs,
      distance: null,
      speedMultiplier: null
    }
  };
}

function parseSkillConfig(rawValue, callName) {
  const body = extractScalaCall(rawValue, callName);
  const args = parseScalaNamedArgs(body);
  const runtime = parseRuntime(args.get("runtime"));
  return {
    range: args.has("range") ? readScalaWrappedNumber(args.get("range"), "SkillDistance") : null,
    distance: args.has("distance") ? readScalaWrappedNumber(args.get("distance"), "SkillDistance") : null,
    radius: args.has("radius") ? readScalaWrappedNumber(args.get("radius"), "Radius") : null,
    castRange: args.has("castRange") ? readScalaWrappedNumber(args.get("castRange"), "SkillDistance") : null,
    ...runtime
  };
}

function parseRuntime(rawValue) {
  const body = extractScalaCall(rawValue, "BattleSkillRuntime");
  const args = splitTopLevel(body, ",");
  return {
    cooldownMs: readScalaWrappedNumber(args[0], "CooldownMillis"),
    activeMs: readScalaWrappedNumber(args[1], "DurationMillis")
  };
}

function parseHeatDefinition(rawValue) {
  const value = (rawValue ?? "None").trim();
  if (value === "None") {
    return null;
  }

  const body = extractScalaCall(value.slice("Some(".length, -1), "BattleWeaponHeatDefinition");
  const args = parseScalaNamedArgs(body);
  return {
    maxHeat: readScalaWrappedNumber(args.get("maxHeat"), "BattleWeaponHeat"),
    heatPerShot: readScalaWrappedNumber(args.get("heatPerShot"), "BattleWeaponHeat"),
    coolRatePerSecond: readScalaWrappedNumber(args.get("coolRatePerSecond"), "BattleWeaponHeatRatePerSecond"),
    overheatLockMs: readScalaWrappedNumber(args.get("overheatLockMs"), "CooldownMillis")
  };
}

function loadSharedMaps(directory) {
  return readdirSync(directory)
    .filter((fileName) => fileName.endsWith(".json"))
    .sort()
    .map((fileName) => JSON.parse(readFileSync(join(directory, fileName), "utf8")));
}

function validateSharedMap(path, map, weaponDefinitions, target) {
  assertNonEmptyString(`${path}.mapId`, map.mapId, target);
  assertNonEmptyString(`${path}.themeId`, map.themeId, target);
  assertPositiveNumber(`${path}.worldSize.x`, map.worldSize?.x, target);
  assertPositiveNumber(`${path}.worldSize.y`, map.worldSize?.y, target);
  assertArray(`${path}.heroDefinitions`, map.heroDefinitions, target);
  assertArray(`${path}.weaponPickups`, map.weaponPickups, target);
  assertArray(`${path}.itemPickups`, map.itemPickups, target);

  for (const [index, hero] of (map.heroDefinitions ?? []).entries()) {
    assertPointInWorld(`${path}.heroDefinitions[${index}].position`, hero.position, map.worldSize, target);
  }

  for (const [index, pickup] of (map.weaponPickups ?? []).entries()) {
    assertNonEmptyString(`${path}.weaponPickups[${index}].pickupId`, pickup.pickupId, target);
    assertPointInWorld(`${path}.weaponPickups[${index}].position`, pickup.position, map.worldSize, target);
    if (!Object.hasOwn(weaponDefinitions, pickup.weaponKind)) {
      target.push(`${path}.weaponPickups[${index}].weaponKind: missing weapon definition for ${formatValue(pickup.weaponKind)}`);
    }
  }

  for (const [index, pickup] of (map.itemPickups ?? []).entries()) {
    assertNonEmptyString(`${path}.itemPickups[${index}].pickupId`, pickup.pickupId, target);
    assertPointInWorld(`${path}.itemPickups[${index}].position`, pickup.position, map.worldSize, target);
    if (pickup.kind !== "Medkit") {
      target.push(`${path}.itemPickups[${index}].kind: unsupported item pickup kind ${formatValue(pickup.kind)}`);
    }
  }
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
    assertPositiveNumber(`${definitionPath}.projectileCount`, definition.projectileCount, target);
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
  }
}

function pushNonEmptyRecordFailure(label, record, target) {
  if (Object.keys(record).length === 0) {
    target.push(`${label} must not be empty.`);
  }
}

function assertArray(path, value, target) {
  if (!Array.isArray(value) || value.length === 0) {
    target.push(`${path}: expected non-empty array, got ${formatValue(value)}`);
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

function readTsOptionalScalarField(block, fieldName) {
  return hasTsField(block, fieldName) ? readTsScalarField(block, fieldName) : null;
}

function readTsScalarField(block, fieldName) {
  const match = new RegExp(`\\b${fieldName}\\s*:\\s*("[^"]*"|true|false|[-+]?\\d+(?:\\.\\d+)?)`).exec(block);
  if (!match) {
    throw new Error(`Missing TS scalar field ${fieldName}.`);
  }

  const rawValue = match[1];
  if (rawValue === "true") return true;
  if (rawValue === "false") return false;
  if (rawValue.startsWith("\"")) return rawValue.slice(1, -1);
  return Number(rawValue);
}

function hasTsField(block, fieldName) {
  return new RegExp(`\\b${fieldName}\\s*:`).test(block);
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

function findTsConstDeclaration(source, constName) {
  const match = new RegExp(`\\bconst\\s+${constName}\\b`).exec(source);
  if (!match) {
    throw new Error(`Could not find const ${constName}.`);
  }

  return match.index;
}

function extractTsObjectRecord(objectSource) {
  const record = new Map();
  const openIndex = objectSource.indexOf("{");
  const closeIndex = objectSource.lastIndexOf("}");
  let index = openIndex + 1;

  while (index < closeIndex) {
    index = skipWhitespaceAndCommas(objectSource, index);
    if (index >= closeIndex) break;

    const key = readTsObjectKey(objectSource, index);
    index = skipWhitespace(objectSource, key.nextIndex);

    if (objectSource[index] !== ":") {
      throw new Error(`Expected ':' after TS object key ${key.value}.`);
    }

    index = skipWhitespace(objectSource, index + 1);
    const valueEnd = findMatchingBracket(objectSource, index, "{", "}");
    if (objectSource[index] !== "{" || valueEnd < 0) {
      throw new Error(`Expected object value for TS key ${key.value}.`);
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
      if (escaped) escaped = false;
      else if (char === "\\") escaped = true;
      else if (char === quote) return { value: source.slice(startIndex + 1, index), nextIndex: index + 1 };
      index += 1;
    }
  }

  const match = /^[A-Za-z_$][\w$-]*/.exec(source.slice(startIndex));
  if (!match) {
    throw new Error(`Could not read TS object key near: ${source.slice(startIndex, startIndex + 40)}`);
  }

  return { value: match[0], nextIndex: startIndex + match[0].length };
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

function extractScalaCallAfter(source, callName, fromIndex = 0) {
  const callIndex = source.indexOf(`${callName}(`, fromIndex);
  if (callIndex < 0) {
    throw new Error(`Could not find Scala call ${callName}.`);
  }
  return extractScalaCall(source.slice(callIndex), callName);
}

function extractScalaCall(source, callName) {
  const callIndex = source.indexOf(`${callName}(`);
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

function extractScalaCalls(source, callName) {
  const calls = [];
  let index = 0;

  while (index < source.length) {
    const callIndex = source.indexOf(`${callName}(`, index);
    if (callIndex < 0) break;

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

function parseScalaNamedArgs(argsBody) {
  const args = new Map();

  for (const part of splitTopLevel(argsBody, ",")) {
    const equalIndex = part.indexOf("=");
    if (equalIndex < 0) continue;
    args.set(part.slice(0, equalIndex).trim(), part.slice(equalIndex + 1).trim());
  }

  return args;
}

function readScalaEnum(rawValue, enumName) {
  const match = new RegExp(`^${enumName}\\.([A-Za-z0-9_]+)$`).exec((rawValue ?? "").trim());
  if (!match) {
    throw new Error(`Could not parse ${enumName} enum value: ${rawValue}`);
  }

  return match[1];
}

function readScalaOptionNumber(rawValue) {
  const value = (rawValue ?? "").trim();
  if (value === "None") return null;
  if (value.startsWith("Some(") && value.endsWith(")")) {
    return readScalaNumberValue(value.slice("Some(".length, -1));
  }
  return readScalaNumberValue(value);
}

function readScalaWrappedNumberAfter(source, fieldName, wrapperName) {
  const match = new RegExp(`\\b${fieldName}\\s*=\\s*${wrapperName}\\(([^)]+)\\)`).exec(source);
  if (!match) {
    throw new Error(`Could not find wrapped Scala number ${fieldName}.`);
  }
  return readScalaNumberValue(match[1]);
}

function readScalaWrappedNumber(rawValue, wrapperName) {
  const match = new RegExp(`^${wrapperName}\\(([^)]+)\\)$`).exec((rawValue ?? "").trim());
  if (!match) {
    throw new Error(`Could not parse ${wrapperName} number: ${rawValue}`);
  }
  return readScalaNumberValue(match[1]);
}

function readScalaNumberValue(rawValue) {
  const value = String(rawValue ?? "").trim().replace(/L$/, "");
  if (!/^[-+]?\d+(?:\.\d+)?$/.test(value)) {
    throw new Error(`Could not parse Scala number: ${rawValue}`);
  }
  return Number(value);
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
      if (escaped) escaped = false;
      else if (char === "\\") escaped = true;
      else if (char === quote) quote = null;
      continue;
    }

    if (char === "\"" || char === "'") {
      quote = char;
      continue;
    }

    if (char === "(" || char === "{" || char === "[") depth += 1;
    else if (char === ")" || char === "}" || char === "]") depth -= 1;
    else if (depth === 0 && char === delimiter) {
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
      if (escaped) escaped = false;
      else if (char === "\\") escaped = true;
      else if (char === quote) quote = null;
      continue;
    }

    if (char === "\"" || char === "'" || char === "`") {
      quote = char;
      continue;
    }

    if (char === openChar) depth += 1;
    else if (char === closeChar) {
      depth -= 1;
      if (depth === 0) return index;
    }
  }

  return -1;
}

function skipWhitespaceAndCommas(source, index) {
  while (index < source.length && /[\s,]/.test(source[index])) index += 1;
  return index;
}

function skipWhitespace(source, index) {
  while (index < source.length && /\s/.test(source[index])) index += 1;
  return index;
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

    for (let index = 0; index < Math.max(frontendValue.length, backendValue.length); index += 1) {
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
