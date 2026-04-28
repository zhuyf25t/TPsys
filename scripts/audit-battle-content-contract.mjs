import { readFileSync } from "node:fs";
import { join } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT_DIR = join(fileURLToPath(new URL("..", import.meta.url)));
const PATHS = {
  frontendMap: join(ROOT_DIR, "frontend", "src", "game", "battleMapCatalog.ts"),
  frontendContent: join(ROOT_DIR, "frontend", "src", "game", "battleContentCatalog.ts"),
  backendMap: join(ROOT_DIR, "backend", "src", "main", "scala", "battle", "runtime", "BattleMapCatalog.scala"),
  backendContent: join(ROOT_DIR, "backend", "src", "main", "scala", "battle", "runtime", "BattleContentCatalog.scala"),
  backendGeometry: join(ROOT_DIR, "backend", "src", "main", "scala", "battle", "runtime", "AuthoritativeArenaGeometry.scala"),
};

const WEAPON_KINDS = ["Pistol", "RocketLauncher", "Gatling", "Shotgun"];
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
  defaultMap: parseBackendMapCatalog(sources.backendMap),
  weaponDefinitions: parseBackendWeaponDefinitions(sources.backendContent, sources.backendGeometry),
  skillDefinitions: parseBackendSkillDefinitions(sources.backendContent),
};

const failures = [];
compareValues("defaultMap", frontend.defaultMap, backend.defaultMap, failures);
compareValues("weaponDefinitions", frontend.weaponDefinitions, backend.weaponDefinitions, failures);
compareValues("skillDefinitions", frontend.skillDefinitions, backend.skillDefinitions, failures);

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
  console.log(`Weapon definitions: ${Object.keys(frontend.weaponDefinitions).length} (${WEAPON_KINDS.join(", ")})`);
  console.log(`Skill definitions: ${Object.keys(frontend.skillDefinitions).length}`);
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

  for (const weaponKind of WEAPON_KINDS) {
    const block = record.get(weaponKind);
    if (!block) {
      throw new Error(`Missing frontend weapon definition for ${weaponKind}.`);
    }

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

  for (const weaponKind of WEAPON_KINDS) {
    const args = entries.get(weaponKind);
    if (!args) {
      throw new Error(`Missing backend weapon definition for ${weaponKind}.`);
    }

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
