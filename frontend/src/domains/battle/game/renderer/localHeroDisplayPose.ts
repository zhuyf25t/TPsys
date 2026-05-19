import type { Hero, Vec2 } from "../../objects/types";

export interface LocalHeroDisplayPose {
  position: Vec2;
  facing: number;
}

export interface LocalHeroDisplayPoseReader {
  read(): LocalHeroDisplayPose;
}

export interface LocalHeroDisplayPoseStore extends LocalHeroDisplayPoseReader {
  write(displayPose: LocalHeroDisplayPose): void;
}

export interface LocalHeroDisplayPositionStore extends LocalHeroDisplayPoseStore {
  writePosition(position: Vec2): void;
}

interface LocalHeroDisplayActor {
  x: number;
  y: number;
  rotation: number;
  setPosition(x: number, y: number): unknown;
  setRotation(rotation: number): unknown;
}

export class LocalHeroDisplay implements LocalHeroDisplayPoseStore {
  public constructor(private readonly actor: LocalHeroDisplayActor) {}

  public read(): LocalHeroDisplayPose {
    return {
      position: { x: this.actor.x, y: this.actor.y },
      facing: this.actor.rotation
    };
  }

  public write(displayPose: LocalHeroDisplayPose): void {
    this.writePosition(displayPose.position);
    this.actor.setRotation(displayPose.facing);
  }

  public writePosition(position: Vec2): void {
    this.actor.setPosition(position.x, position.y);
  }

  public positionFor(player: Hero, useDisplayPose: boolean): Vec2 {
    return useDisplayPose ? this.read().position : player.position;
  }

  public heroFor(player: Hero, useDisplayPose: boolean): Hero {
    if (!useDisplayPose) {
      return player;
    }

    const displayPose = this.read();
    return {
      ...player,
      position: displayPose.position,
      facing: displayPose.facing
    };
  }
}
