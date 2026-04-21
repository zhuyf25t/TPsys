import type { GameEvent } from "../../../../domain/types";

export interface EventFeedClockInput {
  events: GameEvent[];
  deltaMs: number;
}

export interface EventFeedClockResult {
  events: GameEvent[];
}

export function advanceEventFeedClock(input: EventFeedClockInput): EventFeedClockResult {
  return {
    events: input.events
      .map((event) => ({
        ...event,
        ttlMs: event.ttlMs - input.deltaMs
      }))
      .filter((event) => event.ttlMs > 0)
  };
}
