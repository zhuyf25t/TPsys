import type { Hero, WeaponInventory, Vec2 } from "../../../../domain/types";

export interface AdvanceRespawnTimersInput {
  heroes: readonly Hero[];
  deltaMs: number;
}

export function advanceRespawnTimers(input: AdvanceRespawnTimersInput): Hero[] {
  const readyHeroes: Hero[] = [];
  const deltaMs = Math.max(0, input.deltaMs);

  input.heroes.forEach((hero) => {
    if (hero.alive || hero.respawnMs <= 0) {
      return;
    }

    hero.respawnMs = Math.max(0, hero.respawnMs - deltaMs);
    if (hero.respawnMs === 0) {
      readyHeroes.push(hero);
    }
  });

  return readyHeroes;
}

export interface RespawnHeroStateInput {
  hero: Hero;
  spawn: Vec2;
  starterInventory: WeaponInventory;
}

export interface RespawnHeroStateResult {
  heroId: string;
  displayName: string;
}

export function buildRespawnHeroState(input: RespawnHeroStateInput): RespawnHeroStateResult {
  input.hero.position = { x: input.spawn.x, y: input.spawn.y };
  input.hero.hp = input.hero.maxHp;
  input.hero.stamina = input.hero.maxStamina;
  input.hero.velocity = { x: 0, y: 0 };
  input.hero.lifeState = "alive";
  input.hero.alive = true;
  input.hero.respawnMs = 0;
  input.hero.currentWeaponIndex = input.starterInventory.currentWeaponIndex;
  input.hero.weapons = input.starterInventory.weapons;
  input.hero.preparedSkill = null;
  input.hero.jumpCooldownMs = 0;
  input.hero.skills.forEach((skill) => {
    skill.activeMs = 0;
    skill.cooldownMs = Math.max(skill.cooldownMs, 0);
  });

  return {
    heroId: input.hero.heroId,
    displayName: input.hero.displayName
  };
}
