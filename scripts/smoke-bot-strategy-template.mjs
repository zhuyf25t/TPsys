import assert from "node:assert/strict";
import { fileURLToPath, pathToFileURL } from "node:url";
import { join } from "node:path";

const ROOT_DIR = fileURLToPath(new URL("..", import.meta.url));
const TEMPLATE_PATH = join(ROOT_DIR, "examples", "bots", "community-distance-keeper.mjs");
const BOOL_FIELDS = [
  "primaryHeld",
  "primaryJustPressed",
  "secondaryJustPressed",
  "sprint",
  "toggleBlink",
  "toggleFreeze",
  "castDash",
  "reloadPressed"
];

const module = await import(pathToFileURL(TEMPLATE_PATH).href);
const strategy = module.default ?? module.communityDistanceKeeperStrategy;
assert.equal(typeof strategy, "object", "template must export a strategy object");
assert.equal(typeof strategy.strategyId, "string", "strategyId must be a string");
assert.ok(strategy.strategyId.trim().length > 0, "strategyId must be non-empty");
assert.equal(typeof strategy.decide, "function", "strategy must expose decide(context)");

const scenarios = [
  ["standard engagement", buildMockContext()],
  ["low health medkit", buildMockContext({ botHp: 32 })],
  ["no enemies", buildMockContext({ enemies: [], itemPickups: [] })]
];

for (const [scenarioName, context] of scenarios) {
  const before = stableStringify(context);
  deepFreeze(context);
  const decision = strategy.decide(context);

  assertDecisionShape(decision, scenarioName);
  assert.equal(stableStringify(context), before, `${scenarioName}: strategy must not mutate the provided context`);
}

console.log("Bot strategy template smoke passed.");
console.log(`Template: ${TEMPLATE_PATH}`);
console.log(`Strategy ID: ${strategy.strategyId}`);

function assertDecisionShape(decision, scenarioName) {
  assert.equal(typeof decision, "object", `${scenarioName}: decide(context) must return an object`);
  assert.notEqual(decision, null, `${scenarioName}: decide(context) must not return null in the template smoke`);
  assertVec2(decision.movement, `${scenarioName}: movement`);
  assertVec2(decision.aim, `${scenarioName}: aim`);
  assertVec2(decision.pointerWorld, `${scenarioName}: pointerWorld`);
  assert.ok(magnitude(decision.movement) <= 1.000001, `${scenarioName}: movement should be normalized or zero`);

  for (const fieldName of BOOL_FIELDS) {
    if (fieldName in decision) {
      assert.equal(typeof decision[fieldName], "boolean", `${scenarioName}: ${fieldName} must be boolean when present`);
    }
  }

  if ("switchWeaponDirection" in decision) {
    assert.ok([-1, 0, 1].includes(decision.switchWeaponDirection), `${scenarioName}: switchWeaponDirection must be -1, 0, or 1`);
  }

  if ("switchWeaponIndex" in decision && decision.switchWeaponIndex !== null) {
    assert.equal(typeof decision.switchWeaponIndex, "number", `${scenarioName}: switchWeaponIndex must be number or null when present`);
    assert.ok(Number.isInteger(decision.switchWeaponIndex), `${scenarioName}: switchWeaponIndex must be an integer when present`);
    assert.ok(decision.switchWeaponIndex >= 0, `${scenarioName}: switchWeaponIndex must be non-negative when present`);
  }
}

function assertVec2(value, label) {
  assert.equal(typeof value, "object", `${label} must be an object`);
  assert.notEqual(value, null, `${label} must not be null`);
  assert.equal(typeof value.x, "number", `${label}.x must be a number`);
  assert.equal(typeof value.y, "number", `${label}.y must be a number`);
  assert.ok(Number.isFinite(value.x), `${label}.x must be finite`);
  assert.ok(Number.isFinite(value.y), `${label}.y must be finite`);
}

function buildMockContext(overrides = {}) {
  const defaultCommand = {
    movement: { x: 0, y: 0 },
    aim: { x: 1, y: 0 },
    pointerWorld: { x: 620, y: 360 },
    primaryHeld: false,
    primaryJustPressed: false,
    secondaryJustPressed: false,
    sprint: false,
    switchWeaponDirection: 0,
    switchWeaponIndex: null,
    toggleBlink: false,
    toggleFreeze: false,
    castDash: false,
    reloadPressed: false
  };

  const currentWeapon = {
    weaponKind: "Pistol",
    ammoInMagazine: 12,
    magazineSize: 30,
    reserveAmmo: 60,
    cooldownRemaining: 0,
    reloadRemaining: 0,
    heat: 0,
    overheated: false,
    overheatRemaining: 0
  };

  return {
    bot: {
      heroId: "bot-template",
      displayName: "Template Bot",
      team: "blue",
      hp: overrides.botHp ?? 64,
      maxHp: 100,
      stamina: 72,
      maxStamina: 100,
      position: { x: 400, y: 320 },
      facing: 0,
      radius: 22,
      alive: true,
      lifeState: "alive",
      score: 0,
      currentWeaponIndex: 0,
      weapons: [currentWeapon],
      skills: [{ kind: "dash", cooldownMs: 0, activeMs: 0 }],
      preparedSkill: null,
      velocity: { x: 0, y: 0 },
      respawnMs: 0,
      jumpCooldownMs: 0,
      eliminatedAtMs: null
    },
    enemies: overrides.enemies ?? [
      {
        heroId: "enemy-near",
        displayName: "Enemy Near",
        team: "red",
        hp: 80,
        maxHp: 100,
        stamina: 50,
        maxStamina: 100,
        position: { x: 780, y: 340 },
        facing: 3.14,
        radius: 22,
        alive: true,
        lifeState: "alive",
        score: 0,
        currentWeaponIndex: 0,
        weapons: [currentWeapon],
        skills: [],
        preparedSkill: null,
        velocity: { x: 0, y: 0 },
        respawnMs: 0,
        jumpCooldownMs: 0,
        eliminatedAtMs: null
      },
      {
        heroId: "enemy-down",
        displayName: "Enemy Down",
        team: "red",
        hp: 0,
        maxHp: 100,
        stamina: 0,
        maxStamina: 100,
        position: { x: 200, y: 200 },
        facing: 0,
        radius: 22,
        alive: false,
        lifeState: "eliminated",
        score: 0,
        currentWeaponIndex: 0,
        weapons: [currentWeapon],
        skills: [],
        preparedSkill: null,
        velocity: { x: 0, y: 0 },
        respawnMs: 1800,
        jumpCooldownMs: 0,
        eliminatedAtMs: 1000
      }
    ],
    weaponPickups: [
      {
        weaponId: "pickup-rifle",
        weaponKind: "Pistol",
        position: { x: 640, y: 420 },
        available: true,
        respawnMs: 0
      }
    ],
    itemPickups: overrides.itemPickups ?? [
      {
        pickupId: "pickup-medkit",
        kind: "medkit",
        position: { x: 260, y: 310 },
        available: true,
        respawnMs: 0
      }
    ],
    slowFields: [
      {
        fieldId: "slow-1",
        ownerHeroId: "enemy-near",
        position: { x: 540, y: 330 },
        radius: 90,
        ttlMs: 800,
        durationMs: 1500
      }
    ],
    worldSize: { x: 1280, y: 720 },
    deltaMs: 16.67,
    elapsedMs: 12345,
    currentWeapon,
    profile: {
      botId: "bot-template",
      handle: "template",
      displayName: "Template Bot",
      initialRating: 1000,
      profileTone: "safe community example",
      strategyLabel: "community-distance-keeper",
      skin: {
        avatarKey: "bot-template",
        textureKey: "bot-template",
        label: "Template"
      }
    },
    strategy: {
      botId: "bot-template",
      profileTone: "safe community example",
      strategyLabel: "community-distance-keeper",
      normalizedStrategyLabel: "community-distance-keeper",
      candidateStrategyIds: ["community-distance-keeper", "bot-template"]
    },
    defaultCommand
  };
}

function deepFreeze(value) {
  if (!value || typeof value !== "object" || Object.isFrozen(value)) {
    return value;
  }

  Object.freeze(value);

  for (const nested of Object.values(value)) {
    deepFreeze(nested);
  }

  return value;
}

function stableStringify(value) {
  return JSON.stringify(sortKeys(value));
}

function sortKeys(value) {
  if (Array.isArray(value)) {
    return value.map(sortKeys);
  }

  if (!value || typeof value !== "object") {
    return value;
  }

  return Object.fromEntries(Object.keys(value).sort().map((key) => [key, sortKeys(value[key])]));
}

function magnitude(vector) {
  return Math.hypot(vector.x, vector.y);
}
