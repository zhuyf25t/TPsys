import type { BattleHeroViewState as Hero } from "../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { BattleWeaponInventoryState as WeaponInventory } from "../../../../../objects/battle/microservices/combat/objects/weapon/BattleWeaponState";
import type { BattleVector2 as Vec2 } from "../../../../../objects/battle/objects/core/BattleCoreScalars";

export interface AdvanceRespawnTimersInput {
  heroes: readonly Hero[];
  deltaMs: number;
}

export interface BattleRespawnTimerAdvanceResult {
  heroes: Hero[];
  readyHeroes: Hero[];
}

export interface RespawnHeroStateInput {
  hero: Hero;
  spawn: Vec2;
  starterInventory: WeaponInventory;
}

export interface RespawnHeroStateResult {
  hero: Hero;
  heroId: string;
  displayName: string;
}

export function advanceRespawnTimers(input: AdvanceRespawnTimersInput): BattleRespawnTimerAdvanceResult {
  const readyHeroes: Hero[] = [];
  const deltaMs = Math.max(0, input.deltaMs);
  const heroes = input.heroes.map((hero) => {
    if (hero.alive || hero.respawnMs <= 0) {
      return hero;
    }

    const advancedHero = {
      ...hero,
      respawnMs: Math.max(0, hero.respawnMs - deltaMs)
    };
    if (advancedHero.respawnMs === 0) {
      readyHeroes.push(advancedHero);
    }

    return advancedHero;
  });

  return {
    heroes,
    readyHeroes
  };
}

export function buildRespawnHeroState(input: RespawnHeroStateInput): RespawnHeroStateResult {
  const hero = {
    ...input.hero,
    position: { x: input.spawn.x, y: input.spawn.y },
    hp: input.hero.maxHp,
    stamina: input.hero.maxStamina,
    velocity: { x: 0, y: 0 },
    lifeState: "alive" as const,
    alive: true,
    respawnMs: 0,
    currentWeaponIndex: input.starterInventory.currentWeaponIndex,
    weapons: input.starterInventory.weapons,
    preparedSkill: null,
    jumpCooldownMs: 0,
    skills: input.hero.skills.map((skill) => ({
      ...skill,
      activeMs: 0,
      cooldownMs: Math.max(skill.cooldownMs, 0)
    }))
  };

  return {
    hero,
    heroId: hero.heroId,
    displayName: hero.displayName
  };
}
