import type { GameSnapshot } from "../../../objects/types";

export function cloneGameSnapshot(snapshot: GameSnapshot): GameSnapshot {
  return {
    heroes: snapshot.heroes.map((hero) => ({
      ...hero,
      position: { ...hero.position },
      weapons: hero.weapons.map((weapon) => ({ ...weapon })),
      skills: hero.skills.map((skill) => ({ ...skill })),
      velocity: { ...hero.velocity }
    })),
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
    events: snapshot.events.map((event) => ({ ...event })),
    worldSize: { ...snapshot.worldSize },
    elapsedMs: snapshot.elapsedMs,
    playerHeroId: snapshot.playerHeroId
  };
}
