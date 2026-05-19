import Phaser from "phaser";
import type { Hero } from "../../../objects/types";
import type { OccludableView } from "./arenaBuilder";

export interface OcclusionAlphaInput {
  player: Pick<Hero, "position" | "radius" | "alive">;
  occludables: readonly OccludableView[];
}

const OCCLUSION_PROBE_OFFSET_X = 54;
const OCCLUSION_PROBE_OFFSET_Y = 92;
const OCCLUSION_PROBE_WIDTH = 108;
const OCCLUSION_PROBE_HEIGHT = 124;
const OCCLUSION_FADE_ALPHA = 0.35;
const OCCLUSION_LERP = 0.28;

export function updateOccludableAlpha(input: OcclusionAlphaInput): void {
  if (!input.player.alive) {
    input.occludables.forEach((occludable) => {
      occludable.sprite.setAlpha(occludable.baseAlpha);
    });
    return;
  }

  const probe = new Phaser.Geom.Rectangle(
    input.player.position.x - OCCLUSION_PROBE_OFFSET_X,
    input.player.position.y - OCCLUSION_PROBE_OFFSET_Y,
    OCCLUSION_PROBE_WIDTH,
    OCCLUSION_PROBE_HEIGHT
  );

  input.occludables.forEach((occludable) => {
    const bounds = occludable.bounds;
    const overlapsProbe = Phaser.Geom.Intersects.RectangleToRectangle(bounds, probe);
    const isAbovePlayer = bounds.centerY <= input.player.position.y + 18;
    const alignedX = Math.abs(bounds.centerX - input.player.position.x) <= bounds.width * 0.55 + input.player.radius + 18;
    const closeY = Math.abs(bounds.centerY - input.player.position.y) <= bounds.height + 86;
    const shouldFade = overlapsProbe || (isAbovePlayer && alignedX && closeY);
    const targetAlpha = shouldFade ? OCCLUSION_FADE_ALPHA : occludable.baseAlpha;
    occludable.sprite.setAlpha(Phaser.Math.Linear(occludable.sprite.alpha, targetAlpha, OCCLUSION_LERP));
  });
}
