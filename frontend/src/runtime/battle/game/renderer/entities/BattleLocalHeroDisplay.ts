import type { BattleHeroViewState as Hero } from "../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { BattleVector2 as Vec2 } from "../../../../../objects/battle/objects/core/BattleCoreScalars";
import type {
  LocalHeroDisplayActor,
  LocalHeroDisplayPose,
  LocalHeroDisplayPoseStore
} from "./objects/BattleLocalHeroDisplayObjects";

export type {
  LocalHeroDisplayActor,
  LocalHeroDisplayPose,
  LocalHeroDisplayPositionStore,
  LocalHeroDisplayPoseReader,
  LocalHeroDisplayPoseStore
} from "./objects/BattleLocalHeroDisplayObjects";

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
