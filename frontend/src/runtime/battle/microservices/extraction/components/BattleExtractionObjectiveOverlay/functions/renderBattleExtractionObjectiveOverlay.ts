import type Phaser from "phaser";
import type { BattleExtractionObjectiveOverlaySnapshot } from "../objects/BattleExtractionObjectiveOverlaySnapshot";

const GAS_COLOR = 0xff4a00;
const GAS_EDGE_COLOR = 0xffd27a;
const GAS_DANGER_FIELD_CURVE_SEGMENTS = 256;
const EXTRACTION_AVAILABLE_COLOR = 0xf7d35c;
const EXTRACTION_EXTRACTED_COLOR = 0x69f0ae;
const LOOT_CACHE_COLOR = 0x8bd3ff;

export function renderBattleExtractionObjectiveOverlay(
  graphics: Phaser.GameObjects.Graphics,
  snapshot: BattleExtractionObjectiveOverlaySnapshot
): void {
  graphics.clear();
  renderBattleGasZoneOverlay(graphics, snapshot);
  renderBattleExtractionZoneOverlay(graphics, snapshot);
  renderBattleLootCacheOverlay(graphics, snapshot);
}

function renderBattleGasZoneOverlay(
  graphics: Phaser.GameObjects.Graphics,
  snapshot: BattleExtractionObjectiveOverlaySnapshot
): void {
  const gas = snapshot.gasZone;
  if (!gas) {
    return;
  }

  renderGasDangerField(graphics, snapshot);
  graphics.lineStyle(gas.phase === "waiting" ? 5 : 8, GAS_COLOR, gas.phase === "waiting" ? 0.82 : 0.96);
  graphics.strokeCircle(gas.center.x, gas.center.y, gas.radius);
  if (gas.phase !== "waiting") {
    graphics.lineStyle(2, GAS_EDGE_COLOR, 0.58);
    graphics.strokeCircle(gas.center.x, gas.center.y, Math.max(0, gas.radius - 16));
  }
}

function renderGasDangerField(
  graphics: Phaser.GameObjects.Graphics,
  snapshot: BattleExtractionObjectiveOverlaySnapshot
): void {
  const gas = snapshot.gasZone;
  const worldSize = snapshot.worldSize;
  if (!gas || !worldSize) {
    return;
  }

  const alpha = gas.phase === "waiting" ? 0.12 : 0.5;
  const radius = Math.max(0, gas.radius);
  graphics.fillStyle(GAS_COLOR, alpha);

  if (
    radius <= 0 ||
    gas.center.x + radius <= 0 ||
    gas.center.x - radius >= worldSize.x ||
    gas.center.y + radius <= 0 ||
    gas.center.y - radius >= worldSize.y
  ) {
    graphics.fillRect(0, 0, worldSize.x, worldSize.y);
    return;
  }

  const left = clamp(gas.center.x - radius, 0, worldSize.x);
  const right = clamp(gas.center.x + radius, 0, worldSize.x);
  graphics.fillRect(0, 0, left, worldSize.y);
  graphics.fillRect(right, 0, Math.max(0, worldSize.x - right), worldSize.y);

  if (right <= left) {
    return;
  }

  graphics.fillPoints(buildGasDangerTopPolygon(left, right, worldSize.y, gas.center.x, gas.center.y, radius), true, true);
  graphics.fillPoints(buildGasDangerBottomPolygon(left, right, worldSize.y, gas.center.x, gas.center.y, radius), true, true);
}

function buildGasDangerTopPolygon(
  left: number,
  right: number,
  worldHeight: number,
  centerX: number,
  centerY: number,
  radius: number
): Phaser.Types.Math.Vector2Like[] {
  const points: Phaser.Types.Math.Vector2Like[] = [
    { x: left, y: 0 },
    { x: right, y: 0 }
  ];

  for (let index = 0; index <= GAS_DANGER_FIELD_CURVE_SEGMENTS; index += 1) {
    const x = right - ((right - left) * index) / GAS_DANGER_FIELD_CURVE_SEGMENTS;
    points.push({ x, y: clamp(resolveGasSafeTopY(x, centerX, centerY, radius), 0, worldHeight) });
  }

  return points;
}

function buildGasDangerBottomPolygon(
  left: number,
  right: number,
  worldHeight: number,
  centerX: number,
  centerY: number,
  radius: number
): Phaser.Types.Math.Vector2Like[] {
  const points: Phaser.Types.Math.Vector2Like[] = [
    { x: left, y: worldHeight },
    { x: right, y: worldHeight }
  ];

  for (let index = 0; index <= GAS_DANGER_FIELD_CURVE_SEGMENTS; index += 1) {
    const x = right - ((right - left) * index) / GAS_DANGER_FIELD_CURVE_SEGMENTS;
    points.push({ x, y: clamp(resolveGasSafeBottomY(x, centerX, centerY, radius), 0, worldHeight) });
  }

  return points;
}

function resolveGasSafeTopY(x: number, centerX: number, centerY: number, radius: number): number {
  return centerY - resolveGasHalfHeight(x, centerX, radius);
}

function resolveGasSafeBottomY(x: number, centerX: number, centerY: number, radius: number): number {
  return centerY + resolveGasHalfHeight(x, centerX, radius);
}

function resolveGasHalfHeight(x: number, centerX: number, radius: number): number {
  const dx = x - centerX;
  return Math.sqrt(Math.max(0, radius * radius - dx * dx));
}

function clamp(value: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, value));
}

function renderBattleExtractionZoneOverlay(
  graphics: Phaser.GameObjects.Graphics,
  snapshot: BattleExtractionObjectiveOverlaySnapshot
): void {
  snapshot.extraction?.zones.forEach((zone) => {
    const active = snapshot.extraction?.status.status === "extracting" &&
      snapshot.extraction.status.zoneId === zone.zoneId;
    const extracted = snapshot.extraction?.status.status === "extracted" &&
      snapshot.extraction.status.zoneId === zone.zoneId;

    graphics.lineStyle(active ? 6 : 3, extracted ? EXTRACTION_EXTRACTED_COLOR : EXTRACTION_AVAILABLE_COLOR, active ? 0.95 : 0.68);
    graphics.strokeCircle(zone.position.x, zone.position.y, zone.radius);
    graphics.fillStyle(extracted ? EXTRACTION_EXTRACTED_COLOR : EXTRACTION_AVAILABLE_COLOR, active ? 0.14 : 0.07);
    graphics.fillCircle(zone.position.x, zone.position.y, zone.radius);
  });
}

function renderBattleLootCacheOverlay(
  graphics: Phaser.GameObjects.Graphics,
  snapshot: BattleExtractionObjectiveOverlaySnapshot
): void {
  snapshot.lootCaches.forEach((cache) => {
    if (cache.status.status === "searched") {
      return;
    }

    graphics.lineStyle(cache.status.status === "searching" ? 4 : 2, LOOT_CACHE_COLOR, 0.78);
    graphics.strokeCircle(cache.position.x, cache.position.y, cache.radius);
    graphics.fillStyle(LOOT_CACHE_COLOR, cache.status.status === "searching" ? 0.13 : 0.06);
    graphics.fillCircle(cache.position.x, cache.position.y, cache.radius);
  });
}
