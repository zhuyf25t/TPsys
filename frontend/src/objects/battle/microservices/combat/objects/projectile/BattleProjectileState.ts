import type { BattleTeamMode, BattleVector2 } from "../../../../objects/core/BattleCoreScalars";
import type { Damage } from "../combat/Damage";
import type { ProjectileKind } from "./ProjectileKind";
import type { ProjectileTerminalReason } from "./ProjectileTerminalReason";

export interface BattleProjectileState {
  projectileId: string;
  ownerHeroId: string;
  kind: ProjectileKind;
  team: BattleTeamMode;
  position: BattleVector2;
  velocity: BattleVector2;
  facing: number;
  radius: number;
  damage: Damage;
  ttlMs: number;
  maxLifetimeMs: number;
  splashRadius: number;
  alive: boolean;
  hitTargets: string[];
}

export interface BattleProjectileTerminalState {
  projectileId: string;
  kind: ProjectileKind;
  ownerPlayerId: string;
  ownerHeroId: string;
  reason: ProjectileTerminalReason;
  start: BattleVector2;
  end: BattleVector2;
  terminalPosition: BattleVector2;
  ttlBefore: number;
  ttlAfter: number;
  elapsedMs: number;
  targetPlayerId: string | null;
  targetHeroId: string | null;
  hpBefore: number | null;
  hpAfter: number | null;
  damage: Damage | null;
}
