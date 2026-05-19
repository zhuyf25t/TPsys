import type { GameSnapshot } from "../../../objects/types";

/** 中文名：克隆game快照（cloneGameSnapshot）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
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
