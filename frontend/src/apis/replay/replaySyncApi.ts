import { buildApiUrl, normalizeApiBase } from "../../system/api/apiUrl";
import { hasMeaningfulReplayFrames } from "../../objects/replay/replayRecorder";
import type { ReplayFrame, ReplayRecordApiRequestDto } from "../../objects/replay/replayTypes";

const REPLAY_API_BASE = normalizeApiBase(
  import.meta.env.VITE_REPLAY_API_BASE ?? import.meta.env.VITE_BATTLE_API_BASE ?? "",
  "/api"
);

export interface ReplaySyncPayload extends Omit<ReplayRecordApiRequestDto, "framesJson" | "frames"> {
  replayId: string;
  battleId: string;
  handle: string;
  displayName: string;
  finishedAt: number;
  finishedAtLabel: string;
  title: string;
  modeLabel: string;
  resultLabel: string;
  mapLabel: string;
  highlightLine: string;
  coverLabel: string;
  playersLine: string;
  timelineHint: string;
  score: number;
  placement: number | null;
  durationMs: number;
  aliveAtEnd: boolean;
  thumbnailDataUrl: string | null;
  currentLoadout: string | null;
  frameCount: number;
  playbackAvailable: boolean;
  frames: ReplayFrame[];
}

/** 中文名称：同步回放到后端。游戏职责：写入回放目录和帧数据，不触发本地 truth 回填。 */
export async function syncReplayToBackend(payload: ReplaySyncPayload): Promise<boolean> {
  if (typeof window === "undefined") {
    return false;
  }

  try {
    const frames = Array.isArray(payload.frames) ? payload.frames : [];
    const playbackAvailable = hasMeaningfulReplayFrames(frames);
    const response = await fetch(buildApiUrl(REPLAY_API_BASE, "/replay/catalog"), {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      keepalive: true,
      body: JSON.stringify({
        ...payload,
        frameCount: frames.length,
        playbackAvailable,
        frames,
        framesJson: JSON.stringify(frames)
      })
    });

    return response.ok;
  } catch {
    return false;
  }
}
