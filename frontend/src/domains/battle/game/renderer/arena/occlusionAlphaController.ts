import Phaser from "phaser";
import type { Hero } from "../../../objects/types";
import type { OccludableTrigger, OccludableView } from "./arenaBuilder";

export interface OcclusionAlphaInput {
  player: Pick<Hero, "position" | "radius" | "alive">;
  heroes: ReadonlyArray<Pick<Hero, "position" | "radius" | "alive">>;
  occludables: readonly OccludableView[];
}

const OCCLUSION_PROBE_OFFSET_X = 54;
const OCCLUSION_PROBE_OFFSET_Y = 92;
const OCCLUSION_PROBE_WIDTH = 108;
const OCCLUSION_PROBE_HEIGHT = 124;
const OCCLUSION_FADE_ALPHA = 0.35;
const OCCLUSION_LERP = 0.28;

/** 中文名：更新occludablealpha（updateOccludableAlpha）。游戏职责：在前端战斗域中组织战斗界面、状态、输入或渲染数据，保持客户端玩法表达与后端契约一致。 */
export function updateOccludableAlpha(input: OcclusionAlphaInput): void {
  const probe = new Phaser.Geom.Rectangle(
    input.player.position.x - OCCLUSION_PROBE_OFFSET_X,
    input.player.position.y - OCCLUSION_PROBE_OFFSET_Y,
    OCCLUSION_PROBE_WIDTH,
    OCCLUSION_PROBE_HEIGHT
  );

  input.occludables.forEach((occludable) => {
    const shouldFade = shouldFadeOccludable(occludable, input, probe);
    const targetAlpha = shouldFade ? occludable.fadeAlpha ?? OCCLUSION_FADE_ALPHA : occludable.baseAlpha;
    occludable.sprite.setAlpha(Phaser.Math.Linear(occludable.sprite.alpha, targetAlpha, OCCLUSION_LERP));
  });
}

function shouldFadeOccludable(
  occludable: OccludableView,
  input: OcclusionAlphaInput,
  probe: Phaser.Geom.Rectangle
): boolean {
  if (occludable.mode === "tree-leaves") {
    return Boolean(occludable.trigger) && input.heroes.some((hero) => hero.alive && overlapsTrigger(hero, occludable.trigger));
  }

  if (occludable.mode === "building-roof") {
    return input.player.alive && Boolean(occludable.trigger) && overlapsTrigger(input.player, occludable.trigger);
  }

  if (!input.player.alive) {
    return false;
  }

  const bounds = occludable.bounds;
  const overlapsProbe = Phaser.Geom.Intersects.RectangleToRectangle(bounds, probe);
  const isAbovePlayer = bounds.centerY <= input.player.position.y + 18;
  const alignedX = Math.abs(bounds.centerX - input.player.position.x) <= bounds.width * 0.55 + input.player.radius + 18;
  const closeY = Math.abs(bounds.centerY - input.player.position.y) <= bounds.height + 86;
  return overlapsProbe || (isAbovePlayer && alignedX && closeY);
}

function overlapsTrigger(
  hero: Pick<Hero, "position" | "radius">,
  trigger: OccludableTrigger | undefined
): boolean {
  if (!trigger) {
    return false;
  }

  if (trigger.kind === "circle") {
    return Math.hypot(hero.position.x - trigger.position.x, hero.position.y - trigger.position.y) <= hero.radius + trigger.radius;
  }

  const dx = Math.abs(hero.position.x - trigger.position.x);
  const dy = Math.abs(hero.position.y - trigger.position.y);
  return dx <= hero.radius + trigger.size.x / 2 && dy <= hero.radius + trigger.size.y / 2;
}
