import type { BattleVector2 as Vec2 } from "../../../../../objects/battle/objects/core/BattleCoreScalars";
import type { BattleHeroViewState as Hero } from "../../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { BattlePlayerCommand as PlayerCommand } from "../../../../../objects/battle/microservices/session/objects/command/BattlePlayerCommand";
import { isBlinkTargetValid as resolveBlinkTargetValidity } from "../../../local/movement/blinkTargetResolver";
import { applyJumpAction, applySkillInputs } from "../../../local/movement/playerMotionAbilityHandler";
import { resolvePlayerAbilityTextureKey } from "./functions/PlayerAbilitySceneBridgeRules";
import type { PlayerAbilitySceneBridgeOptions } from "./objects/PlayerAbilitySceneBridgeObjects";

export class PlayerAbilitySceneBridge {
  public constructor(private readonly options: PlayerAbilitySceneBridgeOptions) {}

  public isBlinkTargetValid(player: Hero, target: Vec2): boolean {
    return resolveBlinkTargetValidity({
      player,
      target,
      worldSize: this.options.getWorldSize(),
      obstacleBounds: this.options.getObstacleBounds()
    });
  }

  public handleSkillInputs(command: PlayerCommand): void {
    const player = this.options.getPlayerHero();
    const playerTextureKey = resolvePlayerAbilityTextureKey({
      heroId: player.heroId,
      heroViews: this.options.getHeroViews()
    });

    applySkillInputs({
      player,
      command,
      worldSize: this.options.getWorldSize(),
      obstacleBounds: this.options.getObstacleBounds(),
      isPlayerMotionActive: this.options.isPlayerMotionActive(),
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
