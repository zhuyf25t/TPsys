import type {
  Hero,
  ItemPickup,
  PlayerCommand,
  SkillState,
  SlowField,
  Vec2,
  WeaponPickup,
  WeaponState
} from "../../../battle/objects/types";
import { getBotProfileById, type BotProfile } from "../registry/botRegistry";

export interface BotVec2Observation {
  readonly x: number;
  readonly y: number;
}

export interface BotWeaponObservation {
  readonly weaponKind: WeaponState["weaponKind"];
  readonly ammoInMagazine: number;
  readonly magazineSize: number;
  readonly reserveAmmo: number | null;
  readonly cooldownRemaining: number;
  readonly reloadRemaining: number;
  readonly heat: number;
  readonly overheated: boolean;
  readonly overheatRemaining: number;
}

export interface BotSkillObservation {
  readonly kind: SkillState["kind"];
  readonly cooldownMs: number;
  readonly activeMs: number;
}

export interface BotHeroObservation {
  readonly heroId: string;
  readonly displayName: string;
  readonly team: Hero["team"];
  readonly hp: number;
  readonly maxHp: number;
  readonly stamina: number;
  readonly maxStamina: number;
  readonly position: BotVec2Observation;
  readonly facing: number;
  readonly radius: number;
  readonly alive: boolean;
  readonly lifeState: Hero["lifeState"];
  readonly score: number;
  readonly currentWeaponIndex: number;
  readonly weapons: readonly BotWeaponObservation[];
  readonly skills: readonly BotSkillObservation[];
  readonly preparedSkill: Hero["preparedSkill"];
  readonly velocity: BotVec2Observation;
  readonly respawnMs: number;
  readonly jumpCooldownMs: number;
  readonly eliminatedAtMs: number | null;
}

export interface BotWeaponPickupObservation {
  readonly weaponId: string;
  readonly weaponKind: WeaponPickup["weaponKind"];
  readonly position: BotVec2Observation;
  readonly available: boolean;
  readonly respawnMs: number;
}

export interface BotItemPickupObservation {
  readonly pickupId: string;
  readonly kind: ItemPickup["kind"];
  readonly position: BotVec2Observation;
  readonly available: boolean;
  readonly respawnMs: number;
}

export interface BotSlowFieldObservation {
  readonly fieldId: string;
  readonly ownerHeroId: string;
  readonly position: BotVec2Observation;
  readonly radius: number;
  readonly ttlMs: number;
  readonly durationMs: number;
}

export interface BotSkinObservation {
  readonly avatarKey: string;
  readonly textureKey: string;
  readonly label: string;
}

export interface BotProfileObservation {
  readonly botId: string;
  readonly handle: string;
  readonly displayName: string;
  readonly initialRating: number;
  readonly profileTone: string;
  readonly strategyLabel: string;
  readonly skin: BotSkinObservation;
}

export interface BotCommandObservation {
  readonly movement: BotVec2Observation;
  readonly aim: BotVec2Observation;
  readonly pointerWorld: BotVec2Observation;
  readonly primaryHeld: boolean;
  readonly primaryJustPressed: boolean;
  readonly secondaryJustPressed: boolean;
  readonly sprint: boolean;
  readonly switchWeaponDirection: PlayerCommand["switchWeaponDirection"];
  readonly switchWeaponIndex: PlayerCommand["switchWeaponIndex"];
  readonly toggleBlink: boolean;
  readonly toggleFreeze: boolean;
  readonly castDash: boolean;
  readonly reloadPressed: boolean;
}

export interface BotStrategyMetadata {
  readonly botId: string;
  readonly profileTone: string | null;
  readonly strategyLabel: string | null;
  readonly normalizedStrategyLabel: string | null;
  readonly candidateStrategyIds: readonly string[];
}

export interface BotDecisionContext {
  readonly bot: BotHeroObservation;
  readonly enemies: readonly BotHeroObservation[];
  readonly weaponPickups: readonly BotWeaponPickupObservation[];
  readonly itemPickups: readonly BotItemPickupObservation[];
  readonly slowFields: readonly BotSlowFieldObservation[];
  readonly worldSize: BotVec2Observation;
  readonly deltaMs: number;
  readonly elapsedMs: number;
  readonly currentWeapon: BotWeaponObservation;
  readonly profile: BotProfileObservation | null;
  readonly strategy: BotStrategyMetadata;
  readonly defaultCommand: BotCommandObservation;
}

export interface BuildBotDecisionContextInput {
  readonly bot: Hero;
  readonly heroes: readonly Hero[];
  readonly weaponPickups: readonly WeaponPickup[];
  readonly itemPickups: readonly ItemPickup[];
  readonly slowFields: readonly SlowField[];
  readonly worldSize: Vec2;
  readonly deltaMs: number;
  readonly elapsedMs: number;
  readonly currentWeapon: WeaponState;
  readonly defaultCommand: PlayerCommand;
  readonly profile?: BotProfile | null;
}

export type BotCommandStrategyDecision = Readonly<Partial<PlayerCommand>> | null | undefined;

export interface BotCommandStrategy {
  readonly strategyId: string;
  decide(context: BotDecisionContext): BotCommandStrategyDecision;
}

type CommandFieldMap = Partial<Record<keyof PlayerCommand, unknown>>;

const registeredBotStrategies = new Map<string, BotCommandStrategy>();
const warnedStrategyFailures = new Set<string>();

export function buildBotDecisionContext(input: BuildBotDecisionContextInput): BotDecisionContext {
  const profile = input.profile ?? getBotProfileById(input.bot.heroId) ?? null;
  const profileObservation = profile ? copyBotProfile(profile) : null;
  const strategyLabel = profileObservation?.strategyLabel ?? null;
  const normalizedStrategyLabel = normalizeBotStrategyKey(strategyLabel);

  return {
    bot: copyHero(input.bot),
    enemies: input.heroes.filter((hero) => hero.heroId !== input.bot.heroId).map(copyHero),
    weaponPickups: input.weaponPickups.map(copyWeaponPickup),
    itemPickups: input.itemPickups.map(copyItemPickup),
    slowFields: input.slowFields.map(copySlowField),
    worldSize: copyVec2(input.worldSize),
    deltaMs: Math.max(0, input.deltaMs),
    elapsedMs: Math.max(0, input.elapsedMs),
    currentWeapon: copyWeapon(input.currentWeapon),
    profile: profileObservation,
    strategy: {
      botId: input.bot.heroId,
      profileTone: profileObservation?.profileTone ?? null,
      strategyLabel,
      normalizedStrategyLabel,
      candidateStrategyIds: buildStrategyCandidateIds(input.bot.heroId, profileObservation)
    },
    defaultCommand: copyCommandObservation(input.defaultCommand)
  };
}

export function registerBotStrategy(strategy: BotCommandStrategy): void {
  const strategyId = normalizeBotStrategyKey(strategy.strategyId);
  if (!strategyId) {
    throw new Error("Bot strategy requires a non-empty strategyId.");
  }

  const existing = registeredBotStrategies.get(strategyId);
  if (existing && existing !== strategy) {
    throw new Error(`Bot strategy '${strategyId}' is already registered.`);
  }

  registeredBotStrategies.set(strategyId, strategy);
}

export function unregisterBotStrategy(strategyId: string): boolean {
  const normalizedStrategyId = normalizeBotStrategyKey(strategyId);
  return normalizedStrategyId ? registeredBotStrategies.delete(normalizedStrategyId) : false;
}

export function listBotStrategyIds(): readonly string[] {
  return [...registeredBotStrategies.keys()].sort();
}

export function resolveBotStrategyCommand(context: BotDecisionContext, fallbackCommand?: PlayerCommand): PlayerCommand {
  const fallback = fallbackCommand ?? commandObservationToPlayerCommand(context.defaultCommand);
  const strategy = resolveRegisteredBotStrategy(context);

  if (!strategy) {
    return fallback;
  }

  try {
    return normalizeBotCommand(strategy.decide(context), fallback);
  } catch (error) {
    if (!warnedStrategyFailures.has(strategy.strategyId)) {
      warnedStrategyFailures.add(strategy.strategyId);
      console.warn(`[bot-sdk] Strategy '${strategy.strategyId}' failed; using built-in bot command.`, error);
    }
    return fallback;
  }
}

export function normalizeBotCommand(command: BotCommandStrategyDecision, fallbackCommand: PlayerCommand): PlayerCommand {
  const fallback = copyPlayerCommand(fallbackCommand);
  const source = isRecord(command) ? (command as CommandFieldMap) : null;

  if (!source) {
    return fallback;
  }

  return {
    movement: normalizeMovement(source.movement, fallback.movement),
    aim: normalizeVec2(source.aim, fallback.aim),
    pointerWorld: normalizeVec2(source.pointerWorld, fallback.pointerWorld),
    primaryHeld: normalizeBoolean(source.primaryHeld, fallback.primaryHeld),
    primaryJustPressed: normalizeBoolean(source.primaryJustPressed, fallback.primaryJustPressed),
    secondaryJustPressed: normalizeBoolean(source.secondaryJustPressed, fallback.secondaryJustPressed),
    sprint: normalizeBoolean(source.sprint, fallback.sprint),
    switchWeaponDirection: normalizeSwitchDirection(source.switchWeaponDirection, fallback.switchWeaponDirection),
    switchWeaponIndex: normalizeSwitchWeaponIndex(source.switchWeaponIndex, fallback.switchWeaponIndex),
    toggleBlink: normalizeBoolean(source.toggleBlink, fallback.toggleBlink),
    toggleFreeze: normalizeBoolean(source.toggleFreeze, fallback.toggleFreeze),
    castDash: normalizeBoolean(source.castDash, fallback.castDash),
    reloadPressed: normalizeBoolean(source.reloadPressed, fallback.reloadPressed)
  };
}

export function normalizeBotStrategyKey(value: string | null | undefined): string | null {
  if (!value) {
    return null;
  }

  const normalized = value.trim().toLowerCase().replace(/\s+/g, "-");
  return normalized.length > 0 ? normalized : null;
}

function resolveRegisteredBotStrategy(context: BotDecisionContext): BotCommandStrategy | null {
  for (const candidateStrategyId of context.strategy.candidateStrategyIds) {
    const strategy = registeredBotStrategies.get(candidateStrategyId);
    if (strategy) {
      return strategy;
    }
  }

  return null;
}

function buildStrategyCandidateIds(botId: string, profile: BotProfileObservation | null): readonly string[] {
  const ids = [profile?.strategyLabel, profile?.botId, botId]
    .map((candidate) => normalizeBotStrategyKey(candidate))
    .filter((candidate): candidate is string => Boolean(candidate));

  return [...new Set(ids)];
}

function copyBotProfile(profile: BotProfile): BotProfileObservation {
  return {
    botId: profile.botId,
    handle: profile.handle,
    displayName: profile.displayName,
    initialRating: profile.initialRating,
    profileTone: profile.profileTone,
    strategyLabel: profile.strategyLabel,
    skin: {
      avatarKey: profile.skin.avatarKey,
      textureKey: profile.skin.textureKey,
      label: profile.skin.label
    }
  };
}

function copyHero(hero: Hero): BotHeroObservation {
  return {
    heroId: hero.heroId,
    displayName: hero.displayName,
    team: hero.team,
    hp: hero.hp,
    maxHp: hero.maxHp,
    stamina: hero.stamina,
    maxStamina: hero.maxStamina,
    position: copyVec2(hero.position),
    facing: hero.facing,
    radius: hero.radius,
    alive: hero.alive,
    lifeState: hero.lifeState,
    score: hero.score,
    currentWeaponIndex: hero.currentWeaponIndex,
    weapons: hero.weapons.map(copyWeapon),
    skills: hero.skills.map(copySkill),
    preparedSkill: hero.preparedSkill,
    velocity: copyVec2(hero.velocity),
    respawnMs: hero.respawnMs,
    jumpCooldownMs: hero.jumpCooldownMs,
    eliminatedAtMs: hero.eliminatedAtMs
  };
}

function copyWeapon(weapon: WeaponState): BotWeaponObservation {
  return {
    weaponKind: weapon.weaponKind,
    ammoInMagazine: weapon.ammoInMagazine,
    magazineSize: weapon.magazineSize,
    reserveAmmo: weapon.reserveAmmo,
    cooldownRemaining: weapon.cooldownRemaining,
    reloadRemaining: weapon.reloadRemaining,
    heat: weapon.heat,
    overheated: weapon.overheated,
    overheatRemaining: weapon.overheatRemaining
  };
}

function copySkill(skill: SkillState): BotSkillObservation {
  return {
    kind: skill.kind,
    cooldownMs: skill.cooldownMs,
    activeMs: skill.activeMs
  };
}

function copyWeaponPickup(pickup: WeaponPickup): BotWeaponPickupObservation {
  return {
    weaponId: pickup.weaponId,
    weaponKind: pickup.weaponKind,
    position: copyVec2(pickup.position),
    available: pickup.available,
    respawnMs: pickup.respawnMs
  };
}

function copyItemPickup(pickup: ItemPickup): BotItemPickupObservation {
  return {
    pickupId: pickup.pickupId,
    kind: pickup.kind,
    position: copyVec2(pickup.position),
    available: pickup.available,
    respawnMs: pickup.respawnMs
  };
}

function copySlowField(field: SlowField): BotSlowFieldObservation {
  return {
    fieldId: field.fieldId,
    ownerHeroId: field.ownerHeroId,
    position: copyVec2(field.position),
    radius: field.radius,
    ttlMs: field.ttlMs,
    durationMs: field.durationMs
  };
}

function copyCommandObservation(command: PlayerCommand): BotCommandObservation {
  return copyPlayerCommand(command);
}

function commandObservationToPlayerCommand(command: BotCommandObservation): PlayerCommand {
  return {
    movement: copyVec2(command.movement),
    aim: copyVec2(command.aim),
    pointerWorld: copyVec2(command.pointerWorld),
    primaryHeld: command.primaryHeld,
    primaryJustPressed: command.primaryJustPressed,
    secondaryJustPressed: command.secondaryJustPressed,
    sprint: command.sprint,
    switchWeaponDirection: command.switchWeaponDirection,
    switchWeaponIndex: command.switchWeaponIndex,
    toggleBlink: command.toggleBlink,
    toggleFreeze: command.toggleFreeze,
    castDash: command.castDash,
    reloadPressed: command.reloadPressed
  };
}

function copyPlayerCommand(command: PlayerCommand): PlayerCommand {
  return {
    movement: copyVec2(command.movement),
    aim: copyVec2(command.aim),
    pointerWorld: copyVec2(command.pointerWorld),
    primaryHeld: command.primaryHeld,
    primaryJustPressed: command.primaryJustPressed,
    secondaryJustPressed: command.secondaryJustPressed,
    sprint: command.sprint,
    switchWeaponDirection: command.switchWeaponDirection,
    switchWeaponIndex: command.switchWeaponIndex,
    toggleBlink: command.toggleBlink,
    toggleFreeze: command.toggleFreeze,
    castDash: command.castDash,
    reloadPressed: command.reloadPressed
  };
}

function normalizeMovement(value: unknown, fallback: Vec2): Vec2 {
  if (!isRecord(value)) {
    return copyVec2(fallback);
  }

  const vector = normalizeVec2(value, fallback);
  const magnitude = Math.hypot(vector.x, vector.y);
  if (magnitude <= 1 || magnitude === 0) {
    return vector;
  }

  return {
    x: vector.x / magnitude,
    y: vector.y / magnitude
  };
}

function normalizeVec2(value: unknown, fallback: Vec2): Vec2 {
  if (!isRecord(value)) {
    return copyVec2(fallback);
  }

  const x = Number(value.x);
  const y = Number(value.y);
  if (!Number.isFinite(x) || !Number.isFinite(y)) {
    return copyVec2(fallback);
  }

  return { x, y };
}

function normalizeBoolean(value: unknown, fallback: boolean): boolean {
  return typeof value === "boolean" ? value : fallback;
}

function normalizeSwitchDirection(value: unknown, fallback: PlayerCommand["switchWeaponDirection"]): PlayerCommand["switchWeaponDirection"] {
  if (value === -1 || value === 0 || value === 1) {
    return value;
  }

  if (typeof value === "number" && Number.isFinite(value)) {
    return value < 0 ? -1 : value > 0 ? 1 : 0;
  }

  return fallback;
}

function normalizeSwitchWeaponIndex(value: unknown, fallback: PlayerCommand["switchWeaponIndex"]): PlayerCommand["switchWeaponIndex"] {
  if (value === null || value === undefined) {
    return fallback;
  }

  if (typeof value !== "number" || !Number.isFinite(value)) {
    return fallback;
  }

  return value >= 0 ? Math.trunc(value) : fallback;
}

function copyVec2(value: Vec2): Vec2 {
  return {
    x: value.x,
    y: value.y
  };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}
