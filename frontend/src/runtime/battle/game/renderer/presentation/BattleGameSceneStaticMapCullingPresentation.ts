import Phaser from "phaser";
import type { StaticMapView } from "../arena/objects/ArenaBuilderObjects";

interface UpdateGameSceneStaticMapCullingInput {
  camera: Phaser.Cameras.Scene2D.Camera;
  staticMapViews: readonly StaticMapView[];
  paddingWorldUnits?: number;
}

const DEFAULT_STATIC_MAP_CULL_PADDING_WORLD_UNITS = 768;

export function updateGameSceneStaticMapCulling({
  camera,
  staticMapViews,
  paddingWorldUnits = DEFAULT_STATIC_MAP_CULL_PADDING_WORLD_UNITS
}: UpdateGameSceneStaticMapCullingInput): void {
  if (staticMapViews.length === 0) {
    return;
  }

  const worldView = camera.worldView;
  const cullRect = new Phaser.Geom.Rectangle(
    worldView.x - paddingWorldUnits,
    worldView.y - paddingWorldUnits,
    worldView.width + paddingWorldUnits * 2,
    worldView.height + paddingWorldUnits * 2
  );

  staticMapViews.forEach((view) => {
    const shouldBeVisible = Phaser.Geom.Intersects.RectangleToRectangle(cullRect, view.bounds);
    if (view.sprite.visible !== shouldBeVisible) {
      view.sprite.setVisible(shouldBeVisible);
    }
  });
}
