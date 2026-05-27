import type { GameSnapshot } from "../../../../objects/battle/types";
import {
  buildReplayFrame,
  finalizeReplayFrames,
  REPLAY_SAMPLE_INTERVAL_MS
} from "../../../../objects/replay/replayRecorder";
import type { ReplayFrame } from "../../../../objects/replay/replayTypes";
import {
  buildBotOnlyBattleClosureSnapshots,
  type BotOnlyBattleClosure
} from "./botOnlyBattleClosure";

/** 中文名：finalize战斗回放frames（finalizeBattleReplayFrames）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function finalizeBattleReplayFrames(
  frames: ReplayFrame[],
  finalSnapshot: GameSnapshot,
  closure: BotOnlyBattleClosure | null = null
): ReplayFrame[] {
  const authoritativeFinalSnapshot = closure?.snapshot ?? finalSnapshot;
  const sourceFrames = closure ? appendBotOnlyBattleClosureReplayFrames(frames, closure) : frames;
  const finalizedFrames = finalizeReplayFrames(sourceFrames, authoritativeFinalSnapshot);
  if (finalizedFrames.length >= 2) {
    return finalizedFrames;
  }

  const finalFrame = finalizedFrames[0] ?? buildReplayFrame(authoritativeFinalSnapshot);
  const initialFrame = selectInitialReplayFrame(sourceFrames) ?? finalFrame;
  const initialElapsedMs = 0;
  const finalElapsedMs = Math.max(finalFrame.elapsedMs, REPLAY_SAMPLE_INTERVAL_MS);

  return [
    cloneReplayFrame(initialFrame, initialElapsedMs),
    cloneReplayFrame(finalFrame, finalElapsedMs)
  ];
}

/** 中文名：append机器人only战斗closure回放frames（appendBotOnlyBattleClosureReplayFrames）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function appendBotOnlyBattleClosureReplayFrames(
  frames: ReplayFrame[],
  closure: BotOnlyBattleClosure
): ReplayFrame[] {
  const closureFrames = buildBotOnlyBattleClosureSnapshots(closure).map(buildReplayFrame);
  if (closureFrames.length === 0) {
    return frames;
  }

  return [
    ...frames.filter((frame) => Number.isFinite(frame.elapsedMs) && frame.elapsedMs < closure.startedAtMs),
    ...closureFrames
  ];
}

function selectInitialReplayFrame(frames: ReplayFrame[]): ReplayFrame | null {
  const chronologicalFrames = frames
    .filter((frame) => Number.isFinite(frame.elapsedMs))
    .sort((left, right) => left.elapsedMs - right.elapsedMs);

  return chronologicalFrames[0] ?? null;
}

function cloneReplayFrame(frame: ReplayFrame, elapsedMs = frame.elapsedMs): ReplayFrame {
  return {
    elapsedMs,
    worldSize: { ...frame.worldSize },
    heroes: frame.heroes.map((hero) => ({
      ...hero,
      position: { ...hero.position }
    })),
    projectiles: frame.projectiles.map((projectile) => ({
      ...projectile,
      position: { ...projectile.position }
    })),
    pickups: frame.pickups.map((pickup) => ({
      ...pickup,
      position: { ...pickup.position }
    })),
    eventMessages: [...frame.eventMessages]
  };
}
