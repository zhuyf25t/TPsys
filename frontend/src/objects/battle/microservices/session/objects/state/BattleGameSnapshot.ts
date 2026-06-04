import type { BattleItemPickupState, BattleWeaponPickupState } from "../../../abilities/objects/pickup/BattlePickupState";
import type { BattleSlowFieldState } from "../../../abilities/objects/skill/BattleSlowFieldState";
import type { BattleHeroViewState } from "../../../actors/objects/player/BattleHeroViewState";
import type { BattleProjectileState } from "../../../combat/objects/projectile/BattleProjectileState";
import type {
  BattleExtractionState,
  BattleGasZoneState,
  BattleLootCacheState
} from "../../../extraction/objects/extraction/BattleExtractionDefinitions";
import type { BattleGameEventState } from "../../../runtime/objects/event/BattleGameEventState";
import type { BattleVector2 } from "../../../../objects/core/BattleCoreScalars";

export interface BattleGameSnapshot {
  heroes: BattleHeroViewState[];
  projectiles: BattleProjectileState[];
  slowFields: BattleSlowFieldState[];
  weaponPickups: BattleWeaponPickupState[];
  itemPickups: BattleItemPickupState[];
  gasZone: BattleGasZoneState | null;
  extraction: BattleExtractionState | null;
  lootCaches: BattleLootCacheState[];
  events: BattleGameEventState[];
  worldSize: BattleVector2;
  elapsedMs: number;
  playerHeroId: string;
}

