import Phaser from "phaser";
import { resolveStaticObstacleMetalSkinRectangles } from "./functions/ObstacleSkinRules";
import type { ArenaObstacle } from "../../objects/BattleGameConstants";
import type { ObstacleSkinRectanglePlan } from "./objects/ObstacleSkinObjects";

export function createStaticObstacleMetalSkin(scene: Phaser.Scene, obstacle: ArenaObstacle, imageDepth: number): void {
  resolveStaticObstacleMetalSkinRectangles(obstacle, imageDepth).forEach((rectangle) => {
    createSkinRectangle(scene, rectangle);
  });
}

function createSkinRectangle(scene: Phaser.Scene, plan: ObstacleSkinRectanglePlan): void {
  const rectangle = scene.add
    .rectangle(plan.position.x, plan.position.y, plan.size.x, plan.size.y, plan.color, plan.alpha)
    .setDepth(plan.depth);

  if (plan.stroke !== undefined) {
    rectangle.setStrokeStyle(plan.stroke.width, plan.stroke.color, plan.stroke.alpha);
  }
}
