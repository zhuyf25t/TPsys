import { useEffect, useMemo, useRef, useState } from "react";
import { FLOOR_TILE_SIZE, INNER_OBSTACLES, WORLD_SIZE } from "../../game/constants";
import { buildReplayRoomInsights, getReplayDisplayFrames, hasMeaningfulReplayFrames } from "./replayGateway";
import type { ReplayFrame, ReplayHeroFrame, ReplayPlayback } from "./replayTypes";

const PLAYBACK_SPEED = 6;

interface ReplayViewerProps {
  replay: ReplayPlayback;
  onTimelineChange?: (items: ReplayLiveTimelineItem[]) => void;
}

interface ReplayLiveTimelineItem {
  timeLabel: string;
  title: string;
  detail: string;
  tone: "neutral" | "success" | "warning" | "danger";
}

interface ReplayViewport {
  worldWidth: number;
  worldHeight: number;
  scale: number;
  offsetX: number;
  offsetY: number;
}

export function ReplayViewer({ replay, onTimelineChange }: ReplayViewerProps) {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const posterRef = useRef<HTMLImageElement | null>(null);
  const [playing, setPlaying] = useState(true);
  const [playheadMs, setPlayheadMs] = useState(0);
  const [posterReady, setPosterReady] = useState(false);

  const displayFrames = useMemo(() => getReplayDisplayFrames(replay), [replay]);
  const roomInsights = useMemo(() => buildReplayRoomInsights(replay), [replay]);
  const totalMs = useMemo(() => {
    const lastFrame = displayFrames[displayFrames.length - 1];
    return lastFrame ? Math.max(1, lastFrame.elapsedMs) : Math.max(1, replay.durationMs || 1);
  }, [displayFrames, replay.durationMs]);

  const hasReplayFrames = displayFrames.length > 0;
  const playableReplay = hasMeaningfulReplayFrames(displayFrames);
  const animatedReplay = playableReplay && displayFrames.length >= 2;
  const sparseReplay = playableReplay && displayFrames.length < 4;
  const capturedFrameCount = replay.frames.length;
  const visibleTimeline = useMemo(() => {
    if (!hasReplayFrames) {
      return roomInsights.timeline.slice(0, 5);
    }

    const activeMoments = roomInsights.timeline.filter((moment) => parseTimelineLabelMs(moment.timeLabel) <= playheadMs + 250);
    return (activeMoments.length > 0 ? activeMoments : roomInsights.timeline.slice(0, 1)).slice(-5);
  }, [hasReplayFrames, playheadMs, roomInsights.timeline]);

  useEffect(() => {
    onTimelineChange?.(visibleTimeline);
  }, [onTimelineChange, visibleTimeline]);

  useEffect(() => {
    setPlaying(true);
    setPlayheadMs(0);
  }, [replay.id]);

  useEffect(() => {
    if (!replay.thumbnailDataUrl) {
      setPosterReady(false);
      posterRef.current = null;
      return;
    }

    const image = new Image();
    posterRef.current = image;
    image.onload = () => setPosterReady(true);
    image.onerror = () => setPosterReady(false);
    image.src = replay.thumbnailDataUrl;

    return () => {
      posterRef.current = null;
    };
  }, [replay.thumbnailDataUrl, replay.id]);

  useEffect(() => {
    if (!playing || !animatedReplay) {
      return;
    }

    let frameId = 0;
    let last = performance.now();

    const tick = (now: number): void => {
      const delta = now - last;
      last = now;

      let reachedEnd = false;
      setPlayheadMs((current) => {
        const next = Math.min(totalMs, current + delta * PLAYBACK_SPEED);
        reachedEnd = next >= totalMs;
        return next;
      });

      if (reachedEnd) {
        setPlaying(false);
      }

      frameId = window.requestAnimationFrame(tick);
    };

    frameId = window.requestAnimationFrame(tick);

    return () => {
      window.cancelAnimationFrame(frameId);
    };
  }, [playing, animatedReplay, totalMs]);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas || !hasReplayFrames) {
      return;
    }

    drawReplayFrame(canvas, selectFrame(displayFrames, playheadMs), posterRef.current, posterReady);
  }, [displayFrames, hasReplayFrames, playheadMs, posterReady]);

  const resetPlayback = (): void => {
    setPlayheadMs(0);
    setPlaying(true);
  };

  const togglePlayback = (): void => {
    if (!animatedReplay) {
      return;
    }

    setPlaying((value) => !value);
  };

  return (
    <div className="replay-viewer">
      <div className={`replay-viewer__stage ${hasReplayFrames ? "replay-viewer__stage--playable" : "replay-viewer__stage--fallback"}`}>
        {hasReplayFrames ? (
          <canvas ref={canvasRef} className="replay-viewer__canvas" />
        ) : (
          <div className="replay-viewer__fallback">
            <div className="replay-viewer__fallback-card">
              <div className="replay-viewer__fallback-media">
                {posterReady && replay.thumbnailDataUrl ? (
                  <img className="replay-viewer__fallback-image" src={replay.thumbnailDataUrl} alt={replay.title} />
                ) : (
                  <div className="replay-viewer__fallback-poster">
                    <strong>{replay.resultLabel}</strong>
                    <span>{replay.title}</span>
                    <small>{replay.highlightLine}</small>
                  </div>
                )}
              </div>

              <div className="replay-viewer__fallback-copy">
                <div className="replay-viewer__eyebrow">{capturedFrameCount > 0 ? "摘要记录" : "战报封面"}</div>
                <p>{capturedFrameCount > 0 ? "当前只有少量帧或单帧，不能形成连续播放。" : "当前没有逐帧数据，保留客观战报信息。"}</p>
                <div className="replay-viewer__chips">
                  <span className="pill">{replay.mapLabel}</span>
                  <span className="pill">{replay.modeLabel}</span>
                  <span className="pill">{replay.resultLabel}</span>
                  <span className="pill">{roomInsights.frameCountLabel}</span>
                </div>
              </div>
            </div>
          </div>
        )}

        <div className="replay-viewer__badge-row">
          <span className={`pill replay-viewer__badge replay-viewer__badge--${playableReplay && !sparseReplay ? "full" : "summary"}`}>
            {roomInsights.modeLabel}
          </span>
          <span className="pill">{roomInsights.statusLabel}</span>
        </div>

        {playableReplay && sparseReplay ? (
          <div className="replay-viewer__sparse-banner">
            <strong>关键帧模式</strong>
            <span>帧数较少，画面会按现有帧插值播放。</span>
          </div>
        ) : null}
      </div>

      {hasReplayFrames ? (
        <div className="replay-viewer__controls">
          {animatedReplay ? (
            <button type="button" className="button-link button-link--primary" onClick={togglePlayback}>
              {playing ? "暂停" : "播放"}
            </button>
          ) : (
            <span className="pill">单帧预览</span>
          )}
          <button type="button" className="button-link" onClick={resetPlayback}>
            重播
          </button>
          <label className="replay-viewer__seek">
            <span>进度</span>
            <input
              type="range"
              min={0}
              max={totalMs}
              step={1}
              value={Math.min(playheadMs, totalMs)}
              onChange={(event) => {
                setPlayheadMs(Number(event.target.value));
                setPlaying(false);
              }}
            />
          </label>
          <span className="replay-viewer__time">
            {formatTime(playheadMs)} / {formatTime(totalMs)}
          </span>
          <span className="pill">帧数 {capturedFrameCount}</span>
        </div>
      ) : (
        <div className="replay-viewer__controls replay-viewer__controls--fallback">
          <span className="pill">{capturedFrameCount > 0 ? `摘要 · ${capturedFrameCount} 帧` : "仅摘要"}</span>
          <span className="pill">暂无可播放时间轴</span>
        </div>
      )}
    </div>
  );
}

function selectFrame(frames: ReplayFrame[], playheadMs: number): ReplayFrame | null {
  if (frames.length === 0) {
    return null;
  }

  if (frames.length === 1 || playheadMs <= frames[0].elapsedMs) {
    return frames[0];
  }

  for (let index = 1; index < frames.length; index += 1) {
    const current = frames[index];
    const previous = frames[index - 1];
    if (playheadMs <= current.elapsedMs) {
      const span = Math.max(1, current.elapsedMs - previous.elapsedMs);
      const progress = Math.max(0, Math.min(1, (playheadMs - previous.elapsedMs) / span));
      return interpolateFrames(previous, current, progress, playheadMs);
    }
  }

  return frames[frames.length - 1];
}

function interpolateFrames(left: ReplayFrame, right: ReplayFrame, progress: number, playheadMs: number): ReplayFrame {
  return {
    elapsedMs: playheadMs,
    worldSize: right.worldSize,
    heroes: left.heroes.map((hero) => {
      const nextHero = right.heroes.find((candidate) => candidate.heroId === hero.heroId) ?? hero;
      return interpolateHero(hero, nextHero, progress);
    }),
    projectiles: progress < 0.5 ? left.projectiles : right.projectiles,
    pickups: right.pickups,
    eventMessages: progress < 0.5 ? left.eventMessages : right.eventMessages
  };
}

function interpolateHero(left: ReplayHeroFrame, right: ReplayHeroFrame, progress: number): ReplayHeroFrame {
  return {
    ...left,
    position: {
      x: left.position.x + (right.position.x - left.position.x) * progress,
      y: left.position.y + (right.position.y - left.position.y) * progress
    },
    hp: left.hp + (right.hp - left.hp) * progress,
    score: left.score + (right.score - left.score) * progress,
    facing: left.facing + (right.facing - left.facing) * progress,
    alive: progress < 0.5 ? left.alive : right.alive,
    lifeState: progress < 0.5 ? left.lifeState : right.lifeState,
    currentWeaponKind: progress < 0.5 ? left.currentWeaponKind : right.currentWeaponKind,
    eliminatedAtMs: progress < 0.5 ? left.eliminatedAtMs : right.eliminatedAtMs
  };
}

function drawReplayFrame(canvas: HTMLCanvasElement, frame: ReplayFrame | null, poster: HTMLImageElement | null, posterReady: boolean): void {
  const context = canvas.getContext("2d");
  if (!context) {
    return;
  }

  const width = canvas.clientWidth || canvas.parentElement?.clientWidth || 1;
  const height = canvas.clientHeight || canvas.parentElement?.clientHeight || 1;
  const dpr = window.devicePixelRatio || 1;

  if (canvas.width !== Math.round(width * dpr) || canvas.height !== Math.round(height * dpr)) {
    canvas.width = Math.round(width * dpr);
    canvas.height = Math.round(height * dpr);
  }

  context.save();
  context.scale(dpr, dpr);
  context.clearRect(0, 0, width, height);
  drawBattleBackdrop(context, width, height, poster, posterReady);

  if (!frame) {
    drawEmptyReplayState(context, width, height);
    context.restore();
    return;
  }

  const viewport = createReplayViewport(WORLD_SIZE, width, height, 18);
  drawArena(context, viewport);
  drawPickups(context, frame, viewport);
  drawProjectiles(context, frame, viewport);
  drawHeroes(context, frame, viewport);
  drawOverlay(context, frame);
  context.restore();
}

function drawBattleBackdrop(
  context: CanvasRenderingContext2D,
  width: number,
  height: number,
  poster: HTMLImageElement | null,
  posterReady: boolean
): void {
  const gradient = context.createLinearGradient(0, 0, width, height);
  gradient.addColorStop(0, "#111510");
  gradient.addColorStop(0.45, "#1f2618");
  gradient.addColorStop(1, "#090b0c");
  context.fillStyle = gradient;
  context.fillRect(0, 0, width, height);

  if (poster && posterReady) {
    context.save();
    context.globalAlpha = 0.16;
    context.filter = "saturate(0.8) contrast(1.05)";
    context.drawImage(poster, 0, 0, width, height);
    context.restore();
  }

  context.save();
  context.strokeStyle = "rgba(238, 211, 137, 0.055)";
  context.lineWidth = 1;
  for (let x = -40; x <= width + 40; x += 44) {
    context.beginPath();
    context.moveTo(x, 0);
    context.lineTo(x + 90, height);
    context.stroke();
  }
  for (let y = 0; y <= height; y += 42) {
    context.beginPath();
    context.moveTo(0, y);
    context.lineTo(width, y);
    context.stroke();
  }
  context.restore();
}

function drawArena(context: CanvasRenderingContext2D, viewport: ReplayViewport): void {
  context.save();
  drawWorldRect(context, viewport, { x: WORLD_SIZE.x / 2, y: WORLD_SIZE.y / 2 }, WORLD_SIZE, "#4f5b49", "rgba(199, 146, 56, 0.38)", 4);
  drawWorldGrid(context, viewport, { x: WORLD_SIZE.x / 2, y: WORLD_SIZE.y / 2 }, WORLD_SIZE, FLOOR_TILE_SIZE, "rgba(255, 255, 255, 0.035)");
  drawWorldRect(context, viewport, { x: WORLD_SIZE.x / 2, y: WORLD_SIZE.y / 2 }, { x: WORLD_SIZE.x - 192, y: WORLD_SIZE.y - 192 }, "rgba(103, 130, 79, 0.32)");
  drawWorldRect(context, viewport, { x: WORLD_SIZE.x / 2, y: WORLD_SIZE.y / 2 }, { x: 1536, y: 928 }, "#7f8075", "rgba(32, 34, 30, 0.34)", 2);
  drawWorldGrid(context, viewport, { x: WORLD_SIZE.x / 2, y: WORLD_SIZE.y / 2 }, { x: 1536, y: 928 }, FLOOR_TILE_SIZE, "rgba(25, 27, 24, 0.16)");
  drawWorldRect(context, viewport, { x: WORLD_SIZE.x / 2, y: WORLD_SIZE.y / 2 }, { x: 1376, y: 768 }, "rgba(178, 174, 150, 0.42)", "rgba(245, 230, 191, 0.24)", 2);
  drawWorldRect(context, viewport, { x: WORLD_SIZE.x / 2, y: WORLD_SIZE.y / 2 }, { x: 1184, y: 576 }, "rgba(74, 91, 65, 0.36)");
  drawWorldRect(context, viewport, { x: WORLD_SIZE.x / 2, y: 320 }, { x: 544, y: 160 }, "rgba(142, 108, 60, 0.72)", "rgba(245, 230, 191, 0.24)", 2);
  drawWorldRect(context, viewport, { x: WORLD_SIZE.x / 2, y: WORLD_SIZE.y - 320 }, { x: 544, y: 160 }, "rgba(142, 108, 60, 0.72)", "rgba(245, 230, 191, 0.24)", 2);
  drawWorldRect(context, viewport, { x: 640, y: WORLD_SIZE.y / 2 }, { x: 416, y: 224 }, "rgba(111, 80, 46, 0.64)", "rgba(232, 183, 110, 0.18)", 2);
  drawWorldRect(context, viewport, { x: WORLD_SIZE.x - 640, y: WORLD_SIZE.y / 2 }, { x: 416, y: 224 }, "rgba(111, 80, 46, 0.64)", "rgba(232, 183, 110, 0.18)", 2);
  drawWaterPatches(context, viewport);
  drawWorldRect(context, viewport, { x: WORLD_SIZE.x / 2, y: WORLD_SIZE.y / 2 }, { x: 1504, y: 864 }, "rgba(16, 21, 26, 0.08)");
  drawArenaDecorations(context, viewport);
  drawBorderWalls(context, viewport);
  drawInnerObstacles(context, viewport);
  context.restore();
}

function drawWorldRect(
  context: CanvasRenderingContext2D,
  viewport: ReplayViewport,
  center: { x: number; y: number },
  size: { x: number; y: number },
  fillStyle: string,
  strokeStyle?: string,
  lineWidth = 1
): void {
  const rect = toCanvasRect(center, size, viewport);
  context.save();
  context.fillStyle = fillStyle;
  context.fillRect(rect.x, rect.y, rect.width, rect.height);
  if (strokeStyle) {
    context.strokeStyle = strokeStyle;
    context.lineWidth = lineWidth;
    context.strokeRect(rect.x, rect.y, rect.width, rect.height);
  }
  context.restore();
}

function drawWorldGrid(
  context: CanvasRenderingContext2D,
  viewport: ReplayViewport,
  center: { x: number; y: number },
  size: { x: number; y: number },
  stepWorld: number,
  strokeStyle: string
): void {
  const rect = toCanvasRect(center, size, viewport);
  const step = Math.max(4, stepWorld * viewport.scale);

  context.save();
  context.beginPath();
  context.rect(rect.x, rect.y, rect.width, rect.height);
  context.clip();
  context.strokeStyle = strokeStyle;
  context.lineWidth = 1;

  for (let x = rect.x; x <= rect.x + rect.width; x += step) {
    context.beginPath();
    context.moveTo(x, rect.y);
    context.lineTo(x, rect.y + rect.height);
    context.stroke();
  }

  for (let y = rect.y; y <= rect.y + rect.height; y += step) {
    context.beginPath();
    context.moveTo(rect.x, y);
    context.lineTo(rect.x + rect.width, y);
    context.stroke();
  }

  context.restore();
}

function drawWaterPatches(context: CanvasRenderingContext2D, viewport: ReplayViewport): void {
  [
    { x: 448, y: 304 },
    { x: WORLD_SIZE.x - 448, y: 304 },
    { x: 448, y: WORLD_SIZE.y - 304 },
    { x: WORLD_SIZE.x - 448, y: WORLD_SIZE.y - 304 }
  ].forEach((center) => {
    drawWorldRect(context, viewport, center, { x: 224, y: 160 }, "rgba(66, 128, 145, 0.18)", "rgba(117, 232, 255, 0.14)", 2);
  });
}

function drawArenaDecorations(context: CanvasRenderingContext2D, viewport: ReplayViewport): void {
  [
    { x: 320, y: 320 },
    { x: 2240, y: 320 },
    { x: 320, y: 1280 },
    { x: 2240, y: 1280 },
    { x: 736, y: 224 },
    { x: 1824, y: 224 },
    { x: 736, y: 1376 },
    { x: 1824, y: 1376 }
  ].forEach((position) => drawTree(context, viewport, position));

  [
    { x: 896, y: 448 },
    { x: 1664, y: 448 },
    { x: 896, y: 1152 },
    { x: 1664, y: 1152 },
    { x: 640, y: 640 },
    { x: 1920, y: 640 },
    { x: 640, y: 960 },
    { x: 1920, y: 960 }
  ].forEach((position) => drawRock(context, viewport, position));

  [
    { x: 544, y: 576 },
    { x: 2016, y: 576 },
    { x: 544, y: 1024 },
    { x: 2016, y: 1024 },
    { x: 1280, y: 224 },
    { x: 1280, y: 1376 }
  ].forEach((position) => drawBush(context, viewport, position));
}

function drawBorderWalls(context: CanvasRenderingContext2D, viewport: ReplayViewport): void {
  for (let x = FLOOR_TILE_SIZE / 2; x < WORLD_SIZE.x; x += FLOOR_TILE_SIZE) {
    drawObstacleBlock(context, viewport, { kind: "wall", position: { x, y: FLOOR_TILE_SIZE / 2 }, size: { x: FLOOR_TILE_SIZE, y: FLOOR_TILE_SIZE } });
    drawObstacleBlock(context, viewport, { kind: "wall", position: { x, y: WORLD_SIZE.y - FLOOR_TILE_SIZE / 2 }, size: { x: FLOOR_TILE_SIZE, y: FLOOR_TILE_SIZE } });
  }

  for (let y = FLOOR_TILE_SIZE * 1.5; y < WORLD_SIZE.y - FLOOR_TILE_SIZE / 2; y += FLOOR_TILE_SIZE) {
    drawObstacleBlock(context, viewport, { kind: "wall", position: { x: FLOOR_TILE_SIZE / 2, y }, size: { x: FLOOR_TILE_SIZE, y: FLOOR_TILE_SIZE } });
    drawObstacleBlock(context, viewport, { kind: "wall", position: { x: WORLD_SIZE.x - FLOOR_TILE_SIZE / 2, y }, size: { x: FLOOR_TILE_SIZE, y: FLOOR_TILE_SIZE } });
  }
}

function drawInnerObstacles(context: CanvasRenderingContext2D, viewport: ReplayViewport): void {
  INNER_OBSTACLES.forEach((obstacle) => {
    drawObstacleBlock(context, viewport, obstacle);
  });
}

function drawObstacleBlock(
  context: CanvasRenderingContext2D,
  viewport: ReplayViewport,
  obstacle: { kind: "wall" | "crate"; position: { x: number; y: number }; size: { x: number; y: number } }
): void {
  const rect = toCanvasRect(obstacle.position, obstacle.size, viewport);
  context.save();
  context.fillStyle = obstacle.kind === "crate" ? "#8a5c32" : "#777a70";
  context.strokeStyle = obstacle.kind === "crate" ? "rgba(42, 24, 12, 0.82)" : "rgba(26, 29, 25, 0.86)";
  context.lineWidth = 2;
  context.fillRect(rect.x, rect.y, rect.width, rect.height);
  context.strokeRect(rect.x, rect.y, rect.width, rect.height);
  context.fillStyle = obstacle.kind === "crate" ? "rgba(242, 190, 110, 0.24)" : "rgba(245, 230, 191, 0.18)";
  context.fillRect(rect.x + 2, rect.y + 2, Math.max(0, rect.width - 4), Math.max(0, rect.height * 0.28));

  if (obstacle.kind === "crate") {
    context.strokeStyle = "rgba(42, 24, 12, 0.58)";
    context.beginPath();
    context.moveTo(rect.x + 4, rect.y + 4);
    context.lineTo(rect.x + rect.width - 4, rect.y + rect.height - 4);
    context.moveTo(rect.x + rect.width - 4, rect.y + 4);
    context.lineTo(rect.x + 4, rect.y + rect.height - 4);
    context.stroke();
  }

  context.restore();
}

function drawTree(context: CanvasRenderingContext2D, viewport: ReplayViewport, position: { x: number; y: number }): void {
  const point = toCanvasPoint(position, viewport);
  const radius = Math.max(5, 26 * viewport.scale);
  context.save();
  context.fillStyle = "rgba(35, 76, 42, 0.88)";
  context.strokeStyle = "rgba(154, 206, 115, 0.18)";
  context.lineWidth = 2;
  context.beginPath();
  context.arc(point.x, point.y, radius, 0, Math.PI * 2);
  context.fill();
  context.stroke();
  context.fillStyle = "rgba(90, 57, 30, 0.75)";
  context.fillRect(point.x - radius * 0.18, point.y + radius * 0.24, radius * 0.36, radius * 0.58);
  context.restore();
}

function drawRock(context: CanvasRenderingContext2D, viewport: ReplayViewport, position: { x: number; y: number }): void {
  const point = toCanvasPoint(position, viewport);
  context.save();
  context.fillStyle = "rgba(134, 137, 130, 0.84)";
  context.strokeStyle = "rgba(28, 31, 29, 0.72)";
  context.lineWidth = 2;
  context.beginPath();
  context.ellipse(point.x, point.y, Math.max(5, 22 * viewport.scale), Math.max(4, 16 * viewport.scale), -0.3, 0, Math.PI * 2);
  context.fill();
  context.stroke();
  context.restore();
}

function drawBush(context: CanvasRenderingContext2D, viewport: ReplayViewport, position: { x: number; y: number }): void {
  const point = toCanvasPoint(position, viewport);
  context.save();
  context.fillStyle = "rgba(75, 132, 60, 0.72)";
  context.beginPath();
  context.ellipse(point.x, point.y, Math.max(6, 24 * viewport.scale), Math.max(4, 13 * viewport.scale), 0.18, 0, Math.PI * 2);
  context.fill();
  context.restore();
}

function drawPickups(context: CanvasRenderingContext2D, frame: ReplayFrame, viewport: ReplayViewport): void {
  frame.pickups.forEach((pickup) => {
    drawPickupPad(context, pickup, viewport);
  });

  frame.pickups
    .filter((pickup) => pickup.available)
    .forEach((pickup) => {
      const { x, y } = toCanvasPoint(pickup.position, viewport);
      context.save();
      context.translate(x, y);
      context.rotate(Math.PI / 4);
      context.fillStyle = pickup.kind === "weapon" ? "rgba(249, 186, 84, 0.9)" : "rgba(87, 223, 124, 0.9)";
      context.strokeStyle = "rgba(8, 10, 8, 0.78)";
      context.lineWidth = 2;
      context.fillRect(-5, -5, 10, 10);
      context.strokeRect(-5, -5, 10, 10);
      context.restore();
    });
}

function drawPickupPad(context: CanvasRenderingContext2D, pickup: ReplayFrame["pickups"][number], viewport: ReplayViewport): void {
  const { x, y } = toCanvasPoint({ x: pickup.position.x, y: pickup.position.y + 8 }, viewport);
  const width = (pickup.kind === "weapon" ? 112 : 100) * viewport.scale;
  const height = (pickup.kind === "weapon" ? 84 : 78) * viewport.scale;

  context.save();
  context.fillStyle = pickup.kind === "weapon" ? "rgba(142, 108, 60, 0.22)" : "rgba(59, 168, 92, 0.22)";
  context.strokeStyle = pickup.kind === "weapon" ? "rgba(209, 178, 124, 0.72)" : "rgba(157, 245, 181, 0.72)";
  context.lineWidth = 2;
  context.beginPath();
  context.roundRect(x - width / 2, y - height / 2, width, height, Math.max(4, 8 * viewport.scale));
  context.fill();
  context.stroke();
  context.restore();
}

function drawProjectiles(context: CanvasRenderingContext2D, frame: ReplayFrame, viewport: ReplayViewport): void {
  frame.projectiles
    .filter((projectile) => projectile.alive)
    .forEach((projectile) => {
      const { x, y } = toCanvasPoint(projectile.position, viewport);
      context.save();
      context.translate(x, y);
      context.rotate(projectile.facing);
      context.fillStyle = projectile.kind.includes("rocket") ? "#ff9e3d" : "#79e2ff";
      context.shadowColor = context.fillStyle;
      context.shadowBlur = 12;
      context.beginPath();
      context.roundRect(-8, -2, 16, 4, 2);
      context.fill();
      context.restore();
    });
}

function drawHeroes(context: CanvasRenderingContext2D, frame: ReplayFrame, viewport: ReplayViewport): void {
  const palette = ["#7ee7ff", "#ffb24d", "#ff6f91", "#8c9eff", "#b0ff7c", "#f3d48b"];

  frame.heroes.forEach((hero, index) => {
    const { x, y } = toCanvasPoint(hero.position, viewport);
    const color = palette[index % palette.length];

    context.save();
    context.translate(x, y);
    context.rotate(hero.facing);
    context.globalAlpha = hero.alive ? 1 : 0.38;
    context.shadowColor = color;
    context.shadowBlur = hero.alive ? 10 : 0;
    context.fillStyle = color;
    context.strokeStyle = "rgba(3, 6, 7, 0.82)";
    context.lineWidth = 2;
    context.beginPath();
    context.moveTo(16, 0);
    context.lineTo(-10, -10);
    context.lineTo(-6, 0);
    context.lineTo(-10, 10);
    context.closePath();
    context.fill();
    context.stroke();
    context.restore();

    drawHeroBar(context, x - 20, y - 24, hero.hp, hero.maxHp);
    context.save();
    context.fillStyle = "rgba(255, 248, 226, 0.9)";
    context.font = "600 11px ui-sans-serif, system-ui";
    context.textAlign = "center";
    context.fillText(hero.displayName, x, y - 31);
    context.restore();
  });
}

function drawHeroBar(context: CanvasRenderingContext2D, x: number, y: number, hp: number, maxHp: number): void {
  const ratio = Math.max(0, Math.min(1, hp / Math.max(1, maxHp)));
  context.save();
  context.fillStyle = "rgba(4, 6, 7, 0.72)";
  context.beginPath();
  context.roundRect(x, y, 40, 5, 3);
  context.fill();
  context.fillStyle = ratio > 0.5 ? "#58e27c" : "#ff6c5e";
  context.beginPath();
  context.roundRect(x + 1, y + 1, Math.max(2, 38 * ratio), 3, 2);
  context.fill();
  context.restore();
}

function drawOverlay(context: CanvasRenderingContext2D, frame: ReplayFrame): void {
  context.save();
  context.fillStyle = "rgba(0, 0, 0, 0.58)";
  context.beginPath();
  context.roundRect(14, 14, 88, 30, 10);
  context.fill();
  context.fillStyle = "#f5e3b3";
  context.font = "700 13px ui-sans-serif, system-ui";
  context.fillText(formatClock(frame.elapsedMs), 28, 34);
  context.restore();
}

function drawEmptyReplayState(context: CanvasRenderingContext2D, width: number, height: number): void {
  context.save();
  context.fillStyle = "rgba(0, 0, 0, 0.42)";
  context.fillRect(0, 0, width, height);
  context.fillStyle = "#f5e3b3";
  context.font = "700 16px ui-sans-serif, system-ui";
  context.fillText("暂无逐帧画面", 28, height - 58);
  context.font = "12px ui-sans-serif, system-ui";
  context.fillStyle = "rgba(244, 231, 193, 0.78)";
  context.fillText("保留战报结果、评论和本地反馈。", 28, height - 34);
  context.restore();
}

function toCanvasPoint(position: { x: number; y: number }, viewport: ReplayViewport): { x: number; y: number } {
  return {
    x: viewport.offsetX + Math.max(0, Math.min(viewport.worldWidth, position.x)) * viewport.scale,
    y: viewport.offsetY + Math.max(0, Math.min(viewport.worldHeight, position.y)) * viewport.scale
  };
}

function toCanvasRect(
  center: { x: number; y: number },
  size: { x: number; y: number },
  viewport: ReplayViewport
): { x: number; y: number; width: number; height: number } {
  return {
    x: viewport.offsetX + (center.x - size.x / 2) * viewport.scale,
    y: viewport.offsetY + (center.y - size.y / 2) * viewport.scale,
    width: size.x * viewport.scale,
    height: size.y * viewport.scale
  };
}

function createReplayViewport(world: { x: number; y: number }, width: number, height: number, padding: number): ReplayViewport {
  const safeWidth = Math.max(1, width - padding * 2);
  const safeHeight = Math.max(1, height - padding * 2);
  const worldWidth = Math.max(1, world.x);
  const worldHeight = Math.max(1, world.y);
  const scale = Math.min(safeWidth / worldWidth, safeHeight / worldHeight);
  const contentWidth = worldWidth * scale;
  const contentHeight = worldHeight * scale;

  return {
    worldWidth,
    worldHeight,
    scale,
    offsetX: padding + Math.max(0, (safeWidth - contentWidth) * 0.5),
    offsetY: padding + Math.max(0, (safeHeight - contentHeight) * 0.5)
  };
}

function formatClock(elapsedMs: number): string {
  const totalSeconds = Math.max(0, Math.floor(elapsedMs / 1000));
  return `${Math.floor(totalSeconds / 60).toString().padStart(2, "0")}:${(totalSeconds % 60).toString().padStart(2, "0")}`;
}

function formatTime(durationMs: number): string {
  return formatClock(durationMs);
}

function parseTimelineLabelMs(label: string): number {
  const match = label.match(/^(\d{1,2}):(\d{2})$/);
  if (!match) {
    return 0;
  }

  return (Number(match[1]) * 60 + Number(match[2])) * 1000;
}
