import type { BattleGameSnapshot as GameSnapshot } from "../../../../objects/battle/microservices/session/objects/state/BattleGameSnapshot";
import type { BattleVector2 as Vec2 } from "../../../../objects/battle/objects/core/BattleCoreScalars";
import type { BattleHeroViewState as Hero } from "../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import { AUTO_PICKUP_RADIUS } from "../../game/objects/BattleGameConstants";
import { advancePickupLifecycle } from "./pickupLifecycle";
import { handleAutomaticPickupScene } from "./automaticPickupSceneHandler";
import type { ObstacleBounds, OccludableView } from "../../game/renderer/arena/objects/ArenaBuilderObjects";

export interface PickupFrameBridgeInput {
  getSnapshot(): GameSnapshot;
  getPlayerHero(): Hero;
  getObstacleBounds(): readonly ObstacleBounds[];
  getOccludables(): readonly OccludableView[];
  showFloatingText(position: Vec2, text: string, tone: "success" | "neutral" | "warning"): void;
  createPulse(position: Vec2, radius: number, color: number): void;
  pushEvent(type: "pickup" | "heal", message: string): void;
}

export class PickupFrameBridge {
  public constructor(private readonly input: PickupFrameBridgeInput) {}

  public updatePickupLifecycle(deltaMs: number): void {
    const snapshot = this.input.getSnapshot();
    advancePickupLifecycle({
      deltaMs,
      worldSize: snapshot.worldSize,
      obstacleBounds: this.input.getObstacleBounds(),
      occludables: this.input.getOccludables(),
      weaponPickups: snapshot.weaponPickups,
      itemPickups: snapshot.itemPickups
    });
  }

  public handleAutomaticPickups(): void {
    const snapshot = this.input.getSnapshot();
    handleAutomaticPickupScene({
      player: this.input.getPlayerHero(),
      weaponPickups: snapshot.weaponPickups,
      itemPickups: snapshot.itemPickups,
      autoPickupRadius: AUTO_PICKUP_RADIUS,
      callbacks: {
        showFloatingText: (position, text, tone) => this.input.showFloatingText(position, text, tone),
        createPulse: (position, radius, color) => this.input.createPulse(position, radius, color),
        pushEvent: (type, message) => this.input.pushEvent(type, message)
      }
    });
  }
}
