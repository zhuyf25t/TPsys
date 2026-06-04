import type { BattleVector2 } from "../../../../objects/core/BattleCoreScalars";

export type PickupSpawnPointKind = "weapon" | "medkit";

export interface PickupSpawnPoint {
  id: string;
  kind: PickupSpawnPointKind;
  position: BattleVector2;
  occupied: boolean;
}

