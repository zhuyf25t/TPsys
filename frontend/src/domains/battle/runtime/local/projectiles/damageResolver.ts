import type { GameEvent, Hero, Projectile } from "../../../objects/types";

export type DamageProjectileKind = Projectile["kind"] | "rocket-explosion";
export type DamageNoDamageReason = "projectile-dead" | "invalid-target" | "self-hit" | "duplicate-hit";

export interface DamageApplicationInput {
  heroes: Hero[];
  target: Hero;
  ownerHeroId: string;
  projectileId: string;
  projectileKind: DamageProjectileKind;
  damage: number;
  elapsedMs: number;
}

export interface DamageApplicationResult {
  applied: boolean;
  reason: DamageNoDamageReason | null;
  hpBefore: number;
  hpAfter: number;
  killed: boolean;
  killerHeroId: string | null;
  killerDisplayName: string | null;
  targetDisplayName: string;
  event: Pick<GameEvent, "type" | "message"> | null;
}

export function applyDamageToHero(input: DamageApplicationInput): DamageApplicationResult {
  if (!input.target.alive) {
    return buildNoDamageResult("invalid-target", input.target.displayName);
  }

  const hpBefore = input.target.hp;
  const hpAfter = Math.max(0, input.target.hp - input.damage);
  if (hpAfter === hpBefore) {
    return buildNoDamageResult("invalid-target", input.target.displayName);
  }

  input.target.hp = hpAfter;

  if (hpAfter > 0) {
    return {
      applied: true,
      reason: null,
      hpBefore,
      hpAfter,
      killed: false,
      killerHeroId: null,
      killerDisplayName: null,
      targetDisplayName: input.target.displayName,
      event: null
    };
  }

  input.target.alive = false;
  input.target.lifeState = "dead";
  input.target.respawnMs = 0;
  input.target.velocity = { x: 0, y: 0 };
  input.target.eliminatedAtMs = input.elapsedMs;

  const killer = input.heroes.find((hero) => hero.heroId === input.ownerHeroId);
  if (killer && killer.heroId !== input.target.heroId) {
    killer.score += 1;
  }

  return {
    applied: true,
    reason: null,
    hpBefore,
    hpAfter,
    killed: true,
    killerHeroId: killer && killer.heroId !== input.target.heroId ? killer.heroId : null,
    killerDisplayName: killer && killer.heroId !== input.target.heroId ? killer.displayName : null,
    targetDisplayName: input.target.displayName,
    event:
      killer && killer.heroId !== input.target.heroId
        ? {
            type: "kill",
            message: `${killer.displayName} 击败 ${input.target.displayName}`
          }
        : null
  };
}

function buildNoDamageResult(reason: DamageNoDamageReason, targetDisplayName: string): DamageApplicationResult {
  return {
    applied: false,
    reason,
    hpBefore: 0,
    hpAfter: 0,
    killed: false,
    killerHeroId: null,
    killerDisplayName: null,
    targetDisplayName,
    event: null
  };
}
