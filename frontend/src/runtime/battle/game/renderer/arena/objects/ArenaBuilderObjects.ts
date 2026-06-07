import type Phaser from "phaser";
import type { BattleVector2 as Vec2 } from "../../../../../../objects/battle/objects/core/BattleCoreScalars";
import type { CollisionShape } from "../../../../microservices/world/services/BattleArenaCatalog";

export type OccludableSprite = Phaser.GameObjects.Image | Phaser.Physics.Arcade.Image;

export interface StaticMapViewSprite {
  visible: boolean;
  getBounds(): Phaser.Geom.Rectangle;
  setVisible(value: boolean): this;
}

export type OccludableTrigger =
  | { kind: "aabb"; position: Vec2; size: Vec2 }
  | { kind: "circle"; position: Vec2; radius: number };

export type OccludableMode = "local-probe" | "tree-leaves" | "building-roof";

export interface ObstacleBounds {
  position: Vec2;
  size: Vec2;
  shape?: CollisionShape;
}

export interface OccludableView {
  sprite: OccludableSprite;
  bounds: Phaser.Geom.Rectangle;
  baseAlpha: number;
  mode: OccludableMode;
  trigger?: OccludableTrigger;
  fadeAlpha?: number;
}

export interface StaticMapView {
  sprite: StaticMapViewSprite;
  bounds: Phaser.Geom.Rectangle;
}

export interface ArenaBuilderContext {
  scene: Phaser.Scene;
  wallBodies: Phaser.Physics.Arcade.StaticGroup;
  obstacleBounds: ObstacleBounds[];
  occludables: OccludableView[];
  staticMapViews: StaticMapView[];
}
