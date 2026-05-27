import type { GameEvent } from "../../../../objects/battle/types";

export interface EventFeedClockInput {
  events: GameEvent[];
  deltaMs: number;
}

export interface EventFeedClockResult {
  events: GameEvent[];
}

/** 中文名：推进事件feedclock（advanceEventFeedClock）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
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
