import type { GameSnapshot } from "../../../../domain/types";
import { advanceBotActions } from "./botController";
import type { ObstacleBounds } from "../../renderer/arena/arenaBuilder";

export interface BotFrameBridgeOptions {
  getSnapshot(): GameSnapshot;
  getObstacleBounds(): readonly ObstacleBounds[];
  getProjectileSequence(): number;
  setProjectileSequence(nextSequence: number): void;
  getAuthoritativeHeroIds(): ReadonlySet<string>;
}

export class BotFrameBridge {
  public constructor(private readonly options: BotFrameBridgeOptions) {}

  public updateBotActions(deltaMs: number): void {
    const snapshot = this.options.getSnapshot();
    const result = advanceBotActions({
      heroes: snapshot.heroes,
      playerHeroId: snapshot.playerHeroId,
      worldSize: snapshot.worldSize,
      obstacleBounds: this.options.getObstacleBounds(),
      weaponPickups: snapshot.weaponPickups,
      itemPickups: snapshot.itemPickups,
      slowFields: snapshot.slowFields,
      deltaMs,
      elapsedMs: snapshot.elapsedMs,
      projectileSequence: this.options.getProjectileSequence(),
      authoritativeHeroIds: this.options.getAuthoritativeHeroIds()
    });

    this.options.setProjectileSequence(result.projectileSequence);
    result.projectiles.forEach((projectile) => {
      snapshot.projectiles.push(projectile);
    });
  }
}
