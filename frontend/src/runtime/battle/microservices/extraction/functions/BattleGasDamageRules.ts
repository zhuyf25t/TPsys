import type { BattleHeroViewState as Hero } from "../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { BattleGasZoneState } from "../../../../../objects/battle/microservices/extraction/objects/extraction/BattleExtractionDefinitions";

export interface BattleGasDamageInput {
  heroes: readonly Hero[];
  gasZone: BattleGasZoneState | null;
  elapsedMs: number;
  deltaMs: number;
}

export interface BattleGasElimination {
  heroId: string;
  displayName: string;
}

export interface BattleGasDamageResult {
  heroes: Hero[];
  eliminations: BattleGasElimination[];
}

export function applyBattleGasDamageToHeroes(input: BattleGasDamageInput): BattleGasDamageResult {
  const zone = input.gasZone;
  const deltaMs = Math.max(0, input.deltaMs);
  if (!zone || zone.damagePerSecond <= 0 || deltaMs <= 0) {
    return { heroes: input.heroes.map(cloneHero), eliminations: [] };
  }

  const damage = resolveGasDamageTick(zone.damagePerSecond, input.elapsedMs, deltaMs);
  if (damage <= 0) {
    return { heroes: input.heroes.map(cloneHero), eliminations: [] };
  }

  const eliminations: BattleGasElimination[] = [];
  const heroes = input.heroes.map((hero) => {
    if (!isAliveHero(hero) || isInsideGas(hero, zone)) {
      return cloneHero(hero);
    }

    const hp = Math.max(0, hero.hp - damage);
    if (hp > 0) {
      return { ...cloneHero(hero), hp };
    }

    eliminations.push({
      heroId: hero.heroId,
      displayName: hero.displayName
    });

    return {
      ...cloneHero(hero),
      hp: 0,
      alive: false,
      lifeState: "dead" as const,
      preparedSkill: null,
      velocity: { x: 0, y: 0 },
      respawnMs: 0,
      eliminatedAtMs: Math.max(0, input.elapsedMs)
    };
  });

  return { heroes, eliminations };
}

function resolveGasDamageTick(damagePerSecond: number, elapsedMs: number, deltaMs: number): number {
  const currentElapsedMs = Math.max(0, elapsedMs);
  const previousElapsedMs = Math.max(0, currentElapsedMs - deltaMs);
  const currentDamage = Math.floor((Math.max(0, damagePerSecond) * currentElapsedMs) / 1000);
  const previousDamage = Math.floor((Math.max(0, damagePerSecond) * previousElapsedMs) / 1000);
  return Math.max(0, currentDamage - previousDamage);
}

function isAliveHero(hero: Hero): boolean {
  return hero.alive && hero.lifeState === "alive" && hero.hp > 0;
}

function isInsideGas(hero: Hero, zone: BattleGasZoneState): boolean {
  const dx = hero.position.x - zone.center.x;
  const dy = hero.position.y - zone.center.y;
  return dx * dx + dy * dy <= zone.radius * zone.radius;
}

function cloneHero(hero: Hero): Hero {
  return {
    ...hero,
    position: { x: hero.position.x, y: hero.position.y },
    velocity: { x: hero.velocity.x, y: hero.velocity.y },
    weapons: hero.weapons.map((weapon) => ({ ...weapon })),
    skills: hero.skills.map((skill) => ({ ...skill }))
  };
}
