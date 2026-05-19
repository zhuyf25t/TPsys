import type { Hero, Vec2 } from "../../../objects/types";
import type { SceneGeometryObstacleBounds } from "./sceneGeometry";
import { resolveKnockbackDestination, resolveRecoilDestination } from "./displacementResolver";

export interface HeroDisplacementInput {
  hero: Hero;
  direction: Vec2;
  strength: number;
  worldSize: Vec2;
  obstacleBounds: readonly SceneGeometryObstacleBounds[];
  setHeroPosition(position: Vec2): void;
}

function applyHeroDisplacement(
  resolveDestination: (input: {
    position: Vec2;
    radius: number;
    direction: Vec2;
    strength: number;
    worldSize: Vec2;
    obstacleBounds: readonly SceneGeometryObstacleBounds[];
  }) => Vec2 | null,
  input: HeroDisplacementInput
): void {
  const destination = resolveDestination({
    position: input.hero.position,
    radius: input.hero.radius,
    direction: input.direction,
    strength: input.strength,
    worldSize: input.worldSize,
    obstacleBounds: input.obstacleBounds
  });

  if (destination) {
    input.setHeroPosition(destination);
  }
}

export function applyRecoilDisplacement(input: HeroDisplacementInput): void {
  applyHeroDisplacement(resolveRecoilDestination, input);
}

export function applyKnockbackDisplacement(input: HeroDisplacementInput): void {
  applyHeroDisplacement(resolveKnockbackDestination, input);
}
