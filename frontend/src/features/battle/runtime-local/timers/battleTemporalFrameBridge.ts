import type { GameEvent, GameSnapshot } from "../../../../domain/types";
import { FEED_EVENT_TTL_MS } from "../../../../game/constants";
import { advanceEventFeedClock } from "./eventFeedClock";
import { advanceFreezeFields } from "../skills/freezeFieldController";

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
