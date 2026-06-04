import type { BattleGameSnapshot as GameSnapshot } from "../../../../../objects/battle/microservices/session/objects/state/BattleGameSnapshot";
import type { BattleHeroViewState as Hero } from "../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import { cloneBattleExtractionSnapshotFields } from "../../extraction/functions/cloneBattleExtractionState";

const DEFAULT_MIN_CLOSURE_DURATION_MS = 2400;
const DEFAULT_MAX_CLOSURE_DURATION_MS = 12000;
const CLOSURE_MS_PER_ELIMINATION = 900;
const CLOSURE_SETUP_RATIO = 0.25;
const CLOSURE_AFTERGLOW_RATIO = 0.86;

export interface BotOnlyBattleClosureOptions {
  maxElapsedMs: number;
  minClosureDurationMs?: number;
  maxClosureDurationMs?: number;
}

export interface BotOnlyBattleClosureElimination {
  heroId: string;
  elapsedMs: number;
}

export interface BotOnlyBattleClosure {
  startedAtMs: number;
  finishedAtMs: number;
  survivorHeroId: string;
  eliminations: BotOnlyBattleClosureElimination[];
  startSnapshot: GameSnapshot;
  snapshot: GameSnapshot;
}

/** 中文名：创建机器人only战斗closure（createBotOnlyBattleClosure）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function createBotOnlyBattleClosure(
  snapshot: GameSnapshot,
  options: BotOnlyBattleClosureOptions
): BotOnlyBattleClosure | null {
  const player = snapshot.heroes.find((hero) => hero.heroId === snapshot.playerHeroId);
  if (!player || isClosureAlive(player)) {
    return null;
  }

  const liveNonLocalHeroes = snapshot.heroes
    .filter((hero) => hero.heroId !== snapshot.playerHeroId && isClosureAlive(hero))
    .sort(compareClosureSurvivorCandidate);
  if (liveNonLocalHeroes.length === 0) {
    return null;
  }

  const maxElapsedMs = Math.max(0, options.maxElapsedMs);
  const sourceElapsedMs = clampElapsedMs(snapshot.elapsedMs, maxElapsedMs);
  const preferredDurationMs = getPreferredClosureDurationMs(liveNonLocalHeroes.length - 1, options);
  const finishedAtMs = sourceElapsedMs < maxElapsedMs
    ? Math.min(maxElapsedMs, sourceElapsedMs + preferredDurationMs)
    : sourceElapsedMs;
  const startedAtMs = finishedAtMs > sourceElapsedMs
    ? sourceElapsedMs
    : Math.max(0, finishedAtMs - Math.min(preferredDurationMs, maxElapsedMs));
  const survivorHeroId = liveNonLocalHeroes[0].heroId;
  const eliminations = buildClosureEliminations(
    liveNonLocalHeroes.slice(1),
    startedAtMs,
    finishedAtMs
  );
  const eliminationTimesByHeroId = new Map(eliminations.map((elimination) => [elimination.heroId, elimination.elapsedMs]));
  const startSnapshot = cloneSnapshot(snapshot);
  startSnapshot.elapsedMs = startedAtMs;
  const closedSnapshot = cloneSnapshot(snapshot);
  closedSnapshot.elapsedMs = finishedAtMs;
  closedSnapshot.heroes = closedSnapshot.heroes.map((hero) => {
    const eliminatedAtMs = eliminationTimesByHeroId.get(hero.heroId);
    return eliminatedAtMs === undefined ? hero : eliminateHero(hero, eliminatedAtMs);
  });

  const closure: BotOnlyBattleClosure = {
    startedAtMs,
    finishedAtMs,
    survivorHeroId,
    eliminations,
    startSnapshot,
    snapshot: closedSnapshot
  };

  closedSnapshot.events = buildClosureEventsAt(closure, finishedAtMs, closedSnapshot.events);

  return closure;
}

/** 中文名：构建机器人only战斗closuresnapshots（buildBotOnlyBattleClosureSnapshots）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function buildBotOnlyBattleClosureSnapshots(closure: BotOnlyBattleClosure): GameSnapshot[] {
  const frameTimes = buildClosureFrameTimes(closure);
  if (frameTimes.length === 0) {
    return [];
  }

  return frameTimes.map((elapsedMs) => buildClosureSnapshotAt(closure, elapsedMs));
}

function buildClosureEliminations(
  eliminatedHeroesByStrength: Hero[],
  startedAtMs: number,
  finishedAtMs: number
): BotOnlyBattleClosureElimination[] {
  const chronologicalHeroes = [...eliminatedHeroesByStrength].reverse();
  const spanMs = Math.max(0, finishedAtMs - startedAtMs);

  return chronologicalHeroes.map((hero, index) => ({
    heroId: hero.heroId,
    elapsedMs: startedAtMs + spanMs * ((index + 1) / (chronologicalHeroes.length + 1))
  }));
}

function buildClosureFrameTimes(closure: BotOnlyBattleClosure): number[] {
  const spanMs = closure.finishedAtMs - closure.startedAtMs;
  if (spanMs <= 0) {
    return [closure.finishedAtMs];
  }

  return uniqueSortedElapsedMs([
    closure.startedAtMs,
    interpolateElapsedMs(closure, CLOSURE_SETUP_RATIO),
    ...closure.eliminations.map((elimination) => elimination.elapsedMs),
    interpolateElapsedMs(closure, CLOSURE_AFTERGLOW_RATIO),
    closure.finishedAtMs
  ]);
}

function buildClosureSnapshotAt(closure: BotOnlyBattleClosure, elapsedMs: number): GameSnapshot {
  const elapsedSnapshot = cloneSnapshot(closure.startSnapshot);
  elapsedSnapshot.elapsedMs = elapsedMs;
  elapsedSnapshot.heroes = elapsedSnapshot.heroes.map((hero) => {
    const elimination = closure.eliminations.find((entry) => entry.heroId === hero.heroId);
    return elimination && elapsedMs >= elimination.elapsedMs ? eliminateHero(hero, elimination.elapsedMs) : hero;
  });
  elapsedSnapshot.events = buildClosureEventsAt(closure, elapsedMs, elapsedSnapshot.events);
  return elapsedSnapshot;
}

function buildClosureEventsAt(
  closure: BotOnlyBattleClosure,
  elapsedMs: number,
  sourceEvents: GameSnapshot["events"]
): GameSnapshot["events"] {
  const eliminationEvents = closure.eliminations
    .filter((elimination) => elapsedMs >= elimination.elapsedMs)
    .map((elimination) => {
      const hero = closure.startSnapshot.heroes.find((candidate) => candidate.heroId === elimination.heroId);
      const displayName = hero?.displayName ?? elimination.heroId;
      return {
        eventId: `bot-closure-${elimination.heroId}-${Math.round(elimination.elapsedMs)}`,
        type: "kill" as const,
        message: `${displayName} 在机器人收尾中被淘汰。`,
        ttlMs: 1200
      };
    });

  const survivor = closure.startSnapshot.heroes.find((hero) => hero.heroId === closure.survivorHeroId);
  const survivorEvent = elapsedMs >= closure.finishedAtMs && survivor
    ? [
        {
          eventId: `bot-closure-survivor-${closure.survivorHeroId}-${Math.round(closure.finishedAtMs)}`,
          type: "kill" as const,
          message: `${survivor.displayName} 成为最后幸存者。`,
          ttlMs: 1600
        }
      ]
    : [];

  return [...sourceEvents, ...eliminationEvents, ...survivorEvent].slice(-6);
}

function compareClosureSurvivorCandidate(left: Hero, right: Hero): number {
  return (
    compareDescending(left.score, right.score) ||
    compareDescending(getHpRatio(left), getHpRatio(right)) ||
    compareDescending(left.hp, right.hp) ||
    compareDescending(getStaminaRatio(left), getStaminaRatio(right)) ||
    compareDescending(left.stamina, right.stamina) ||
    left.heroId.localeCompare(right.heroId)
  );
}

function compareDescending(left: number, right: number): number {
  return readFiniteNumber(right) - readFiniteNumber(left);
}

function getHpRatio(hero: Hero): number {
  return safeRatio(hero.hp, hero.maxHp);
}

function getStaminaRatio(hero: Hero): number {
  return safeRatio(hero.stamina, hero.maxStamina);
}

function safeRatio(value: number, maxValue: number): number {
  const safeMaxValue = readFiniteNumber(maxValue);
  if (safeMaxValue <= 0) {
    return 0;
  }

  return readFiniteNumber(value) / safeMaxValue;
}

function readFiniteNumber(value: number): number {
  return Number.isFinite(value) ? value : 0;
}

function isClosureAlive(hero: Hero): boolean {
  return hero.alive && hero.lifeState === "alive" && hero.hp > 0;
}

function eliminateHero(hero: Hero, eliminatedAtMs: number): Hero {
  return {
    ...cloneHero(hero),
    alive: false,
    lifeState: "dead",
    hp: 0,
    preparedSkill: null,
    velocity: { x: 0, y: 0 },
    respawnMs: 0,
    eliminatedAtMs
  };
}

function getPreferredClosureDurationMs(
  eliminationCount: number,
  options: BotOnlyBattleClosureOptions
): number {
  const minDurationMs = Math.max(0, options.minClosureDurationMs ?? DEFAULT_MIN_CLOSURE_DURATION_MS);
  const maxDurationMs = Math.max(minDurationMs, options.maxClosureDurationMs ?? DEFAULT_MAX_CLOSURE_DURATION_MS);
  const durationMs = DEFAULT_MIN_CLOSURE_DURATION_MS + Math.max(0, eliminationCount) * CLOSURE_MS_PER_ELIMINATION;
  return Math.min(maxDurationMs, Math.max(minDurationMs, durationMs));
}

function clampElapsedMs(elapsedMs: number, maxElapsedMs: number): number {
  if (!Number.isFinite(elapsedMs)) {
    return 0;
  }

  return Math.min(Math.max(0, elapsedMs), maxElapsedMs);
}

function interpolateElapsedMs(closure: BotOnlyBattleClosure, ratio: number): number {
  return closure.startedAtMs + (closure.finishedAtMs - closure.startedAtMs) * ratio;
}

function uniqueSortedElapsedMs(values: number[]): number[] {
  return [...new Set(values.filter(Number.isFinite))].sort((left, right) => left - right);
}

function cloneSnapshot(snapshot: GameSnapshot): GameSnapshot {
  const extractionFields = cloneBattleExtractionSnapshotFields(snapshot);
  return {
    heroes: snapshot.heroes.map(cloneHero),
    projectiles: snapshot.projectiles.map((projectile) => ({
      ...projectile,
      position: { ...projectile.position },
      velocity: { ...projectile.velocity },
      hitTargets: [...projectile.hitTargets]
    })),
    slowFields: snapshot.slowFields.map((field) => ({
      ...field,
      position: { ...field.position }
    })),
    weaponPickups: snapshot.weaponPickups.map((pickup) => ({
      ...pickup,
      position: { ...pickup.position }
    })),
    itemPickups: snapshot.itemPickups.map((pickup) => ({
      ...pickup,
      position: { ...pickup.position }
    })),
    gasZone: extractionFields.gasZone,
    extraction: extractionFields.extraction,
    lootCaches: extractionFields.lootCaches,
    events: snapshot.events.map((event) => ({ ...event })),
    worldSize: { ...snapshot.worldSize },
    elapsedMs: snapshot.elapsedMs,
    playerHeroId: snapshot.playerHeroId
  };
}

function cloneHero(hero: Hero): Hero {
  return {
    ...hero,
    position: { ...hero.position },
    velocity: { ...hero.velocity },
    weapons: hero.weapons.map((weapon) => ({ ...weapon })),
    skills: hero.skills.map((skill) => ({ ...skill }))
  };
}
