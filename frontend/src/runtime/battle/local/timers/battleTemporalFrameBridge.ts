import type { BattleGameEventState as GameEvent } from "../../../../objects/battle/microservices/runtime/objects/event/BattleGameEventState";
import type { BattleGameSnapshot as GameSnapshot } from "../../../../objects/battle/microservices/session/objects/state/BattleGameSnapshot";
import { FEED_EVENT_TTL_MS } from "../../game/objects/BattleGameConstants";
import { advanceEventFeedClock } from "./eventFeedClock";
import { advanceFreezeFields } from "../../microservices/abilities/functions/BattleSlowFieldRuntimeRules";

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
