import type { ProjectileKind } from "../../../combat/objects/projectile/ProjectileKind";
import type { ProjectileTerminalReason } from "../../../combat/objects/projectile/ProjectileTerminalReason";
import type { BattleApiVectorDto } from "./BattleStateSharedResponseApiTypes";

export interface BattleStateProjectileResponseDto {
  projectileId: string;
  ownerHeroId: string;
  kind: ProjectileKind;
  position: BattleApiVectorDto;
  velocity: BattleApiVectorDto;
  facing: number;
  radius: number;
  damage: number;
  ttlMs: number;
  maxLifetimeMs: number;
  splashRadius: number;
}

export interface BattleStateProjectileTerminalResponseDto {
  projectileId: string;
  kind: ProjectileKind;
  ownerPlayerId: string;
  ownerHeroId: string;
  reason: ProjectileTerminalReason;
  start: BattleApiVectorDto;
  end: BattleApiVectorDto;
  terminalPosition: BattleApiVectorDto;
  ttlBefore: number;
  ttlAfter: number;
  elapsedMs: number;
  targetPlayerId: string | null;
  targetHeroId: string | null;
  hpBefore: number | null;
  hpAfter: number | null;
  damage: number | null;
}

