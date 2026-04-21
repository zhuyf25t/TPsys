import type { Hero, PlayerCommand, Vec2 } from "../../../../domain/types";
import type { HeroView } from "../entities/worldViewFactory";
import type { SceneGeometryObstacleBounds } from "../../runtime-local/geometry/sceneGeometry";
import { applyJumpAction, applySkillInputs } from "../../runtime-local/movement/playerMotionAbilityHandler";

export interface PlayerAbilitySceneBridgeOptions {
  getPlayerHero(): Hero;
  getWorldSize(): Vec2;
  getObstacleBounds(): readonly SceneGeometryObstacleBounds[];
  getHeroViews(): ReadonlyMap<string, HeroView>;
  getBaseHeroScale(heroId: string): number;
  isPlayerMotionActive(): boolean;
  isBlinkTargetValid(player: Hero, target: Vec2): boolean;
  startPlayerMotion(destination: Vec2, durationMs: number, motionType: "jump" | "dash" | "blink"): void;
  createAfterimage(
    position: Vec2,
    rotation: number,
    scale: number,
    textureKey: string,
    tint: number,
    alpha: number
  ): void;
  createPulse(position: Vec2, radius: number, color: number): void;
  createFloatingText(position: Vec2, text: string, color: string): void;
  showFloatingText(position: Vec2, text: string, tone: "neutral" | "warning" | "error" | "success"): void;
  addFreezeField(ownerHeroId: string, position: Vec2, radius: number, durationMs: number): void;
}

export class PlayerAbilitySceneBridge {
  public constructor(private readonly options: PlayerAbilitySceneBridgeOptions) {}

  public handleSkillInputs(command: PlayerCommand): void {
    const player = this.options.getPlayerHero();
    const playerTextureKey = this.options.getHeroViews().get(player.heroId)?.sprite.texture.key ?? "hero-player";

    applySkillInputs({
      player,
      command,
      worldSize: this.options.getWorldSize(),
      obstacleBounds: this.options.getObstacleBounds(),
      isPlayerMotionActive: this.options.isPlayerMotionActive(),
      isBlinkTargetValid: (target) => this.options.isBlinkTargetValid(player, target),
      playerBaseScale: this.options.getBaseHeroScale(player.heroId),
      playerTextureKey,
      callbacks: {
        startPlayerMotion: (destination, durationMs, motionType) =>
          this.options.startPlayerMotion(destination, durationMs, motionType),
        createPulse: (position, radius, color) => this.options.createPulse(position, radius, color),
        createAfterimage: (position, rotation, scale, textureKey, tint, alpha) =>
          this.options.createAfterimage(position, rotation, scale, textureKey, tint, alpha),
        createFloatingText: (position, text, color) => this.options.createFloatingText(position, text, color),
        showFloatingText: (position, text, tone) => this.options.showFloatingText(position, text, tone),
        addFreezeField: (ownerHeroId, position, radius, durationMs) =>
          this.options.addFreezeField(ownerHeroId, position, radius, durationMs)
      }
    });
  }

  public handleJumpAction(command: PlayerCommand, lastMoveDirection: Vec2): void {
    const player = this.options.getPlayerHero();

    applyJumpAction({
      player,
      command,
      lastMoveDirection,
      worldSize: this.options.getWorldSize(),
      obstacleBounds: this.options.getObstacleBounds(),
      isPlayerMotionActive: this.options.isPlayerMotionActive(),
      callbacks: {
        startPlayerMotion: (destination, durationMs, motionType) =>
          this.options.startPlayerMotion(destination, durationMs, motionType),
        showFloatingText: (position, text, tone) => this.options.showFloatingText(position, text, tone)
      }
    });
  }
}
