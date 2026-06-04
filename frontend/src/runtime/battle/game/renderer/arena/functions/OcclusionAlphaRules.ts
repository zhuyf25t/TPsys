import type { OccludableTrigger, OccludableView } from "../objects/ArenaBuilderObjects";
import type {
  OcclusionAlphaHero,
  OcclusionAlphaInput,
  OcclusionAlphaPlan,
  OcclusionProbePlan,
  OcclusionProbeShape,
  ResolveOcclusionAlphaPlansInput
} from "../objects/OcclusionAlphaObjects";

const OCCLUSION_PROBE_OFFSET_X = 54;
const OCCLUSION_PROBE_OFFSET_Y = 92;
const OCCLUSION_PROBE_WIDTH = 108;
const OCCLUSION_PROBE_HEIGHT = 124;
const OCCLUSION_FADE_ALPHA = 0.35;
const OCCLUSION_LERP = 0.28;

export function resolveOcclusionProbePlan(input: Pick<OcclusionAlphaInput, "player">): OcclusionProbePlan {
  return {
    x: input.player.position.x - OCCLUSION_PROBE_OFFSET_X,
    y: input.player.position.y - OCCLUSION_PROBE_OFFSET_Y,
    width: OCCLUSION_PROBE_WIDTH,
    height: OCCLUSION_PROBE_HEIGHT
  };
}

export function resolveOcclusionAlphaPlans(input: ResolveOcclusionAlphaPlansInput): OcclusionAlphaPlan[] {
  return input.occludables.map((occludable) => {
    const shouldFade = shouldFadeOccludable(occludable, input, input.probe, input.intersectsProbe);
    const targetAlpha = shouldFade ? occludable.fadeAlpha ?? OCCLUSION_FADE_ALPHA : occludable.baseAlpha;
    return {
      occludable,
      alpha: input.lerpAlpha(occludable.sprite.alpha, targetAlpha, OCCLUSION_LERP)
    };
  });
}

function shouldFadeOccludable(
  occludable: OccludableView,
  input: OcclusionAlphaInput,
  probe: OcclusionProbeShape,
  intersectsProbe: (bounds: OcclusionProbeShape, probe: OcclusionProbeShape) => boolean
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
  const overlapsProbe = intersectsProbe(bounds, probe);
  const isAbovePlayer = bounds.centerY <= input.player.position.y + 18;
  const alignedX = Math.abs(bounds.centerX - input.player.position.x) <= bounds.width * 0.55 + input.player.radius + 18;
  const closeY = Math.abs(bounds.centerY - input.player.position.y) <= bounds.height + 86;
  return overlapsProbe || (isAbovePlayer && alignedX && closeY);
}

function overlapsTrigger(hero: Pick<OcclusionAlphaHero, "position" | "radius">, trigger: OccludableTrigger | undefined): boolean {
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
