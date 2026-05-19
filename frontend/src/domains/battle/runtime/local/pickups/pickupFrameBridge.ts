import type { GameSnapshot, Hero, Vec2 } from "../../../objects/types";
import { AUTO_PICKUP_RADIUS } from "../../../game/constants";
import { advancePickupLifecycle } from "./pickupLifecycle";
import { handleAutomaticPickupScene } from "./automaticPickupSceneHandler";
import type { ObstacleBounds, OccludableView } from "../../../game/renderer/arena/arenaBuilder";

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
