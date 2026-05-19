import type { Vec2 } from "../../../battle/objects/types";

export function heroSeed(heroId: string): number {
  let hash = 0;
  for (let index = 0; index < heroId.length; index += 1) {
    hash = (hash * 31 + heroId.charCodeAt(index)) % 997;
  }

  return hash / 997;
}

export function distanceBetween(left: Vec2, right: Vec2): number {
  return Math.hypot(left.x - right.x, left.y - right.y);
}
