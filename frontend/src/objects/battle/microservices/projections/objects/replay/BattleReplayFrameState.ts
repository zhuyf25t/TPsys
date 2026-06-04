import type { ProjectileKind } from "../../../combat/objects/projectile/ProjectileKind";
import type { WeaponKind } from "../../../combat/objects/weapon/WeaponKind";
import type { BattleVector2 } from "../../../../objects/core/BattleCoreScalars";
import type { BattleReplayHeroLifeState } from "./BattleReplayHeroLifeState";

export type BattleReplayPickupKind = "weapon" | "medkit";

export type BattleReplayHeroFrameState = {
  readonly heroId: string;
  readonly displayName: string;
  readonly position: BattleVector2;
  readonly hp: number;
  readonly maxHp: number;
  readonly alive: boolean;
  readonly lifeState: BattleReplayHeroLifeState;
  readonly score: number;
  readonly facing: number;
  readonly currentWeaponKind: WeaponKind | null;
  readonly eliminatedAtMs: number | null;
};

export type BattleReplayProjectileFrameState = {
  readonly projectileId: string;
  readonly kind: ProjectileKind;
  readonly position: BattleVector2;
  readonly facing: number;
  readonly alive: boolean;
  readonly ttlMs: number;
  readonly splashRadius: number;
};

export type BattleReplayPickupFrameState = {
  readonly id: string;
  readonly kind: BattleReplayPickupKind;
  readonly position: BattleVector2;
  readonly available: boolean;
};

export type BattleReplayFrameState = {
  readonly elapsedMs: number;
  readonly worldSize: BattleVector2;
  readonly heroes: BattleReplayHeroFrameState[];
  readonly projectiles: BattleReplayProjectileFrameState[];
  readonly pickups: BattleReplayPickupFrameState[];
  readonly eventMessages: string[];
};
