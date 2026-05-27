import type { Vec2 } from "../../../objects/battle/types";
import { findMotionDestination, type MotionObstacleBounds } from "../../battle/local/movement/motionController";
import { normalizeVector } from "../../battle/local/geometry/sceneGeometry";
import { distanceBetween } from "./botMath";

/** 中文名：steer机器人destination（steerBotDestination）。游戏职责：在前端 bot 域中组织机器人策略、目标选择和战术决策，辅助本地或演示战斗体验。 */
export function steerBotDestination(input: {
  position: Vec2;
  direction: Vec2;
  distance: number;
  radius: number;
  worldSize: Vec2;
  preferStrafe: boolean;
  obstacleBounds: readonly MotionObstacleBounds[];
}): { destination: Vec2; blocked: boolean } {
  const normalized = normalizeVector(input.direction);
  const candidates: Vec2[] = [normalized];
  const orthogonal = { x: -normalized.y, y: normalized.x };
  const angleOffsets = input.preferStrafe ? [0.18, -0.18, 0.36, -0.36, 0.62, -0.62] : [0.12, -0.12, 0.28, -0.28, 0.48, -0.48];

  angleOffsets.forEach((angle) => {
    candidates.push(rotateVector(normalized, angle));
  });
  candidates.push(orthogonal, { x: -orthogonal.x, y: -orthogonal.y }, { x: -normalized.x, y: -normalized.y });

  return chooseBestDestination(input, candidates);
}

function chooseBestDestination(
  input: {
    position: Vec2;
    direction: Vec2;
    distance: number;
    radius: number;
    worldSize: Vec2;
    obstacleBounds: readonly MotionObstacleBounds[];
  },
  candidates: readonly Vec2[]
): { destination: Vec2; blocked: boolean } {
  const distances = [input.distance, input.distance * 0.82, input.distance * 0.58];
  let best = evaluateCandidates(input, candidates, distances[0]);
  let bestScore = scoreDestination(input.position, normalizeVector(input.direction), best.destination);

  for (let index = 1; index < distances.length; index += 1) {
    const trial = evaluateCandidates(input, candidates, distances[index]);
    const score = scoreDestination(input.position, normalizeVector(input.direction), trial.destination);
    if (score > bestScore + 0.001) {
      best = trial;
      bestScore = score;
    }
  }

  if (best.blocked || distanceBetween(input.position, best.destination) < 3) {
    const expandedCandidates = [...candidates, rotateVector(input.direction, 0.9), rotateVector(input.direction, -0.9)];
    const fallback = evaluateCandidates(input, expandedCandidates, input.distance * 0.46);
    if (distanceBetween(input.position, fallback.destination) > distanceBetween(input.position, best.destination)) {
      best = fallback;
    }
  }

  return best;
}

function evaluateCandidates(
  input: {
    position: Vec2;
    direction: Vec2;
    radius: number;
    worldSize: Vec2;
    obstacleBounds: readonly MotionObstacleBounds[];
  },
  candidates: readonly Vec2[],
  distance: number
): { destination: Vec2; blocked: boolean } {
  const normalized = normalizeVector(input.direction);
  let best = findMotionDestination({
    position: input.position,
    direction: normalized,
    distance,
    radius: input.radius,
    worldSize: input.worldSize,
    obstacleBounds: input.obstacleBounds
  });
  let bestScore = scoreDestination(input.position, normalized, best.destination);

  candidates.forEach((candidate) => {
    const result = findMotionDestination({
      position: input.position,
      direction: candidate,
      distance,
      radius: input.radius,
      worldSize: input.worldSize,
      obstacleBounds: input.obstacleBounds
    });
    const score = scoreDestination(input.position, candidate, result.destination);
    if (score > bestScore + 0.001) {
      best = result;
      bestScore = score;
    }
  });

  return best;
}

function scoreDestination(origin: Vec2, direction: Vec2, destination: Vec2): number {
  const movement = distanceBetween(origin, destination);
  const alignment = normalizeVector(direction);
  const displacement = normalizeVector({
    x: destination.x - origin.x,
    y: destination.y - origin.y
  });
  return movement * 1000 + (alignment.x * displacement.x + alignment.y * displacement.y) * 150;
}

function rotateVector(vector: Vec2, radians: number): Vec2 {
  const cos = Math.cos(radians);
  const sin = Math.sin(radians);
  return normalizeVector({
    x: vector.x * cos - vector.y * sin,
    y: vector.x * sin + vector.y * cos
  });
}
