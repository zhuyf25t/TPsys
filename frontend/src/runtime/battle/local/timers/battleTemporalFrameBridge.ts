import type { BattleGameEventState as GameEvent } from "../../../../objects/battle/microservices/runtime/objects/event/BattleGameEventState";
import type { BattleGameSnapshot as GameSnapshot } from "../../../../objects/battle/microservices/session/objects/state/BattleGameSnapshot";
import { FEED_EVENT_TTL_MS } from "../../game/objects/BattleGameConstants";
import { advanceEventFeedClock } from "./eventFeedClock";
import { advanceFreezeFields } from "../../microservices/abilities/functions/BattleSlowFieldRuntimeRules";
import { applyBattleGasDamageToHeroes } from "../../microservices/extraction/functions/BattleGasDamageRules";

export class BattleTemporalFrameBridge {
  private eventSequence = 0;

  public update(snapshot: GameSnapshot, deltaMs: number): void {
    snapshot.events = advanceEventFeedClock({
      events: snapshot.events,
      deltaMs
    }).events;
    snapshot.slowFields = advanceFreezeFields({
      fields: snapshot.slowFields,
      deltaMs
    });
    this.applyGasDamage(snapshot, deltaMs);
  }

  public applyGasDamage(snapshot: GameSnapshot, deltaMs: number): void {
    const gasDamage = applyBattleGasDamageToHeroes({
      heroes: snapshot.heroes,
      gasZone: snapshot.gasZone,
      elapsedMs: snapshot.elapsedMs,
      deltaMs
    });
    snapshot.heroes = gasDamage.heroes;
    gasDamage.eliminations.forEach((elimination) => {
      this.pushEvent(snapshot, "kill", `${elimination.displayName} \u88ab\u6bd2\u6c14\u541e\u6ca1`);
    });
  }

  public pushEvent(snapshot: GameSnapshot, type: GameEvent["type"], message: string): void {
    snapshot.events.push({
      eventId: `event-${this.eventSequence++}`,
      type,
      message,
      ttlMs: FEED_EVENT_TTL_MS
    });
  }
}
