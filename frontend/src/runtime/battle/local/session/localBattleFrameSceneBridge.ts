import type { BattleGameSnapshot as GameSnapshot } from "../../../../objects/battle/microservices/session/objects/state/BattleGameSnapshot";
import type { BattleVector2 as Vec2 } from "../../../../objects/battle/objects/core/BattleCoreScalars";
import type { BattleHeroViewState as Hero } from "../../../../objects/battle/microservices/actors/objects/player/BattleHeroViewState";
import type { BattlePlayerCommand as PlayerCommand } from "../../../../objects/battle/microservices/session/objects/command/BattlePlayerCommand";
import {
  BASE_MOVE_SPEED,
  SPRINT_MULTIPLIER,
  STAMINA_DRAIN_PER_SECOND,
  STAMINA_RECOVER_PER_SECOND
} from "../../game/objects/BattleGameConstants";
import type { PlayerAbilitySceneBridge } from "../../game/renderer/effects/playerAbilitySceneBridge";
import type { ProjectileFrameSceneBridge } from "../../game/renderer/effects/projectileFrameSceneBridge";
import type { WeaponActionSceneBridge } from "../../game/renderer/effects/weaponActionSceneBridge";
import type { BotFrameBridge } from "../../../bots/controller/botFrameBridge";
import { advanceMovement } from "../../microservices/actors/functions/BattlePlayerMovementRules";
import type { PickupFrameBridge } from "../pickups/pickupFrameBridge";
import { getFreezeSpeedMultiplier } from "../../microservices/abilities/functions/BattleSlowFieldRuntimeRules";
import { advanceHeroWeaponSkillTimers } from "../timers/heroWeaponSkillTimers";
import type { WeaponSwitchStateBridge } from "../weapons/weaponSwitchStateBridge";
import { resolveBattlePlayerCommandAfterSkillInput } from "../../microservices/actors/functions/BattlePlayerInputRules";

type FloatingTone = "success" | "neutral" | "warning" | "error";

export interface LocalBattleFrameSceneBridgeOptions {
  getSnapshot(): GameSnapshot;
  getPlayerHero(): Hero;
  syncPlayerHeroFromPhysics(): void;
  setPlayerActorVelocity(velocity: Vec2): void;
  isPlayerMotionActive(): boolean;
  showFloatingText(position: Vec2, text: string, tone: FloatingTone): void;
  pickupFrameBridge: PickupFrameBridge;
  playerAbilityBridge: PlayerAbilitySceneBridge;
  weaponActionBridge: WeaponActionSceneBridge;
  botFrameBridge: BotFrameBridge;
  projectileFrameBridge: ProjectileFrameSceneBridge;
  weaponSwitchStateBridge: WeaponSwitchStateBridge;
}

export class LocalBattleFrameSceneBridge {
  private lastMoveDirection: Vec2 = { x: 1, y: 0 };
  private readonly pickupNoticeCooldowns = new Map<string, number>();

  public constructor(private readonly options: LocalBattleFrameSceneBridgeOptions) {}

  public update(command: PlayerCommand, deltaMs: number): void {
    this.options.pickupFrameBridge.updatePickupLifecycle(deltaMs);
    this.updateHeroStateTimers(deltaMs);
    this.options.syncPlayerHeroFromPhysics();
    this.updatePlayerMovement(command, deltaMs);
    this.options.pickupFrameBridge.handleAutomaticPickups();
    const preparedSkillBeforeSkillInputs = this.options.getPlayerHero().preparedSkill;
    this.options.playerAbilityBridge.handleSkillInputs(command);
    this.options.playerAbilityBridge.handleJumpAction(command, this.lastMoveDirection);
    this.handleWeaponSwitchAction(command);
    this.options.weaponActionBridge.handleWeaponFireAction(
      resolveBattlePlayerCommandAfterSkillInput(command, preparedSkillBeforeSkillInputs)
    );
    this.options.botFrameBridge.updateBotActions(deltaMs);
    this.options.projectileFrameBridge.updateProjectiles(deltaMs);
    this.options.syncPlayerHeroFromPhysics();
  }

  private updateHeroStateTimers(deltaMs: number): void {
    const snapshot = this.options.getSnapshot();
    const weaponSwitchStateBridge = this.options.weaponSwitchStateBridge;
    const nextState = advanceHeroWeaponSkillTimers({
      deltaMs,
      playerHeroId: snapshot.playerHeroId,
      heroes: snapshot.heroes,
      pickupNoticeCooldowns: this.pickupNoticeCooldowns,
      weaponSwitchRemainingMs: weaponSwitchStateBridge.getWeaponSwitchRemainingMs(),
      weaponSwitchTotalMs: weaponSwitchStateBridge.getWeaponSwitchTotalMs(),
      pendingWeaponIndex: weaponSwitchStateBridge.getPendingWeaponIndex()
    });

    weaponSwitchStateBridge.syncState(nextState);
  }

  private updatePlayerMovement(command: PlayerCommand, deltaMs: number): void {
    const snapshot = this.options.getSnapshot();
    const player = this.options.getPlayerHero();

    if (!player.alive) {
      const stoppedVelocity = { x: 0, y: 0 };
      this.options.setPlayerActorVelocity(stoppedVelocity);
      player.velocity = stoppedVelocity;
      return;
    }

    player.facing = Math.atan2(command.aim.y, command.aim.x);

    const result = advanceMovement({
      alive: player.alive,
      motionActive: this.options.isPlayerMotionActive(),
      movement: command.movement,
      sprint: command.sprint,
      stamina: player.stamina,
      maxStamina: player.maxStamina,
      lastMoveDirection: this.lastMoveDirection,
      deltaMs,
      baseMoveSpeed: BASE_MOVE_SPEED,
      sprintMultiplier: SPRINT_MULTIPLIER,
      staminaDrainPerSecond: STAMINA_DRAIN_PER_SECOND,
      staminaRecoverPerSecond: STAMINA_RECOVER_PER_SECOND,
      speedMultiplier: getFreezeSpeedMultiplier(player.position, snapshot.slowFields)
    });

    this.lastMoveDirection = result.lastMoveDirection;
    this.options.setPlayerActorVelocity(result.velocity);
    player.velocity = { x: result.velocity.x, y: result.velocity.y };
    player.stamina = result.stamina;
  }

  private handleWeaponSwitchAction(command: PlayerCommand): void {
    const player = this.options.getPlayerHero();
    const switchResult = this.options.weaponSwitchStateBridge.handleWeaponSwitchAction({
      player,
      switchDirection: command.switchWeaponDirection,
      switchWeaponIndex: command.switchWeaponIndex
    });

    if (!switchResult.switched) { return; }
    if (switchResult.showNotice) {
      this.options.showFloatingText(player.position, "\u6b63\u5728\u5207\u67aa", "neutral");
    }
  }
}
