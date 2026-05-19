import type { GameSnapshot, Hero, PlayerCommand, PreparedSkill, Vec2 } from "../../objects/types";
import type { MotionObstacleBounds } from "../../runtime/local/movement/motionController";
import { applyAuthoritativeFrameToSnapshot } from "./authoritativeFrameSnapshotApplier";
import type { BattleRuntimeAuthoritativeFrame } from "./authoritativeBattleStateBridge";
import type {
  AuthoritativeLocalHeroPendingBlinkPrediction,
  AuthoritativeLocalHeroPendingDashPrediction,
  AuthoritativeLocalHeroReplayCommandEntry
} from "./authoritativeLocalHeroReplay";
import { findDashSkillState, getPredictedDashCooldownMs } from "./authoritativeLocalHeroDashPrediction";
import { findBlinkSkillState, getPredictedBlinkCooldownMs } from "./authoritativeLocalHeroBlinkPrediction";
import { applyAuthoritativeLocalHeroDisplayMotion } from "./authoritativeLocalHeroMotion";
import { isSharedAuthoritativeTargetValid } from "./effects/sharedAuthoritativeTargetValidity";
import { LocalAuthoritativeHeroCorrectionController } from "./localAuthoritativeHeroCorrection";
import type { LocalHeroDisplay } from "./localHeroDisplayPose";

export type GameSceneAuthoritativeFrame = BattleRuntimeAuthoritativeFrame;

export interface GameSceneAuthoritativeFrameOptions {
  localCommandHistory?: readonly AuthoritativeLocalHeroReplayCommandEntry[];
  localLastClientCommandSeq?: number;
  nowMs?: number;
}

export interface ApplyAuthoritativeFrameSceneBridgeInput {
  snapshot: GameSnapshot;
  frame: BattleRuntimeAuthoritativeFrame;
  localPlayerMovementActive: boolean;
  obstacleBounds: readonly MotionObstacleBounds[];
  options?: GameSceneAuthoritativeFrameOptions;
}

export interface UpdateAuthoritativeLocalDisplayMotionInput {
  snapshot: GameSnapshot;
  player: Hero;
  command: PlayerCommand;
  deltaMs: number;
  obstacleBounds: readonly MotionObstacleBounds[];
  localPlayerMovementActive: boolean;
}

export interface SuppressInvalidAuthoritativePreparedConfirmInput {
  command: PlayerCommand;
  player: Hero;
  preparedSkill: PreparedSkill;
  worldSize: Vec2;
  obstacleBounds: readonly MotionObstacleBounds[];
}

export class AuthoritativeFrameSceneBridge {
  private readonly localHeroCorrection: LocalAuthoritativeHeroCorrectionController;
  private pendingLocalBlinkPrediction: AuthoritativeLocalHeroPendingBlinkPrediction | null = null;
  private pendingLocalDashPrediction: AuthoritativeLocalHeroPendingDashPrediction | null = null;
  private localBlinkCooldownUntilMs: number | null = null;
  private localDashCooldownUntilMs: number | null = null;

  public constructor(private readonly localHeroDisplay: LocalHeroDisplay) {
    this.localHeroCorrection = new LocalAuthoritativeHeroCorrectionController(localHeroDisplay);
  }

  public updateLocalDisplayMotion({
    snapshot,
    player,
    command,
    deltaMs,
    obstacleBounds,
    localPlayerMovementActive
  }: UpdateAuthoritativeLocalDisplayMotionInput): void {
    const nowMs = Date.now();
    const motionResult = applyAuthoritativeLocalHeroDisplayMotion({
      snapshot,
      player,
      command,
      deltaMs,
      displayPoseStore: this.localHeroDisplay,
      obstacleBounds,
      blinkCooldownMsOverride: this.resolveLocalBlinkCooldownMs(player, nowMs),
      dashCooldownMsOverride: this.resolveLocalDashCooldownMs(player, nowMs)
    });
    if (motionResult.predictedBlinkDestination) {
      this.pendingLocalBlinkPrediction = {
        destination: {
          x: motionResult.predictedBlinkDestination.x,
          y: motionResult.predictedBlinkDestination.y
        },
        expiresAtMs: nowMs + 900,
        mismatchAllowedUntilMs: nowMs + 180
      };
      this.localBlinkCooldownUntilMs = nowMs + getPredictedBlinkCooldownMs();
    }
    if (motionResult.predictedDashDestination) {
      this.pendingLocalDashPrediction = {
        destination: {
          x: motionResult.predictedDashDestination.x,
          y: motionResult.predictedDashDestination.y
        },
        expiresAtMs: nowMs + 900
      };
      this.localDashCooldownUntilMs = nowMs + getPredictedDashCooldownMs();
    }
    this.localHeroCorrection.update(deltaMs, { localMovementActive: localPlayerMovementActive });
  }

  public applyFrame({
    snapshot,
    frame,
    localPlayerMovementActive,
    obstacleBounds,
    options = {}
  }: ApplyAuthoritativeFrameSceneBridgeInput): Set<string> {
    const nowMs = options.nowMs ?? Date.now();
    applyAuthoritativeFrameToSnapshot({
      snapshot,
      frame,
      localPlayerMovementActive,
      localPlayerReplay: {
        commandHistory: options.localCommandHistory ?? [],
        lastClientCommandSeq: options.localLastClientCommandSeq ?? 0,
        nowMs,
        obstacleBounds,
        pendingBlinkPrediction: this.resolvePendingLocalBlinkPrediction(nowMs),
        pendingDashPrediction: this.resolvePendingLocalDashPrediction(nowMs),
        blinkCooldownMsOverride: this.resolveLocalBlinkCooldownMs(
          snapshot.heroes.find((hero) => hero.heroId === snapshot.playerHeroId) ?? null,
          nowMs
        ),
        dashCooldownMsOverride: this.resolveLocalDashCooldownMs(
          snapshot.heroes.find((hero) => hero.heroId === snapshot.playerHeroId) ?? null,
          nowMs
        )
      },
      applyLocalPlayerAuthoritativeCorrection: ({ authoritativePosition, localMovementActive, forceHardSnap }) => {
        this.localHeroCorrection.observeAuthoritativePosition(authoritativePosition, {
          localMovementActive,
          forceHardSnap
        });
      }
    });
    this.prunePendingLocalBlinkPrediction(snapshot, nowMs);
    this.prunePendingLocalDashPrediction(snapshot, nowMs);
    this.syncLocalBlinkCooldown(snapshot, nowMs);
    this.syncLocalDashCooldown(snapshot, nowMs);

    return new Set(frame.remoteAuthoritativeHeroIds);
  }

  private resolvePendingLocalBlinkPrediction(nowMs: number): AuthoritativeLocalHeroPendingBlinkPrediction | null {
    if (!this.pendingLocalBlinkPrediction || this.pendingLocalBlinkPrediction.expiresAtMs < nowMs) {
      return null;
    }

    return this.pendingLocalBlinkPrediction;
  }

  private resolvePendingLocalDashPrediction(nowMs: number): AuthoritativeLocalHeroPendingDashPrediction | null {
    if (!this.pendingLocalDashPrediction || this.pendingLocalDashPrediction.expiresAtMs < nowMs) {
      return null;
    }

    return this.pendingLocalDashPrediction;
  }

  private prunePendingLocalBlinkPrediction(snapshot: GameSnapshot, nowMs: number): void {
    if (!this.pendingLocalBlinkPrediction || this.pendingLocalBlinkPrediction.expiresAtMs < nowMs) {
      this.pendingLocalBlinkPrediction = null;
      this.clearLocalBlinkCooldownIfAuthoritativeReady(snapshot);
      return;
    }

    const player = snapshot.heroes.find((hero) => hero.heroId === snapshot.playerHeroId);
    if (!player || !player.alive) {
      this.pendingLocalBlinkPrediction = null;
      this.localBlinkCooldownUntilMs = null;
      return;
    }

    if (distanceBetween(player.position, this.pendingLocalBlinkPrediction.destination) <= 48) {
      this.pendingLocalBlinkPrediction = null;
      return;
    }

    if (this.pendingLocalBlinkPrediction.mismatchAllowedUntilMs < nowMs) {
      this.pendingLocalBlinkPrediction = null;
      this.clearLocalBlinkCooldownIfAuthoritativeReady(snapshot);
    }
  }

  private prunePendingLocalDashPrediction(snapshot: GameSnapshot, nowMs: number): void {
    if (!this.pendingLocalDashPrediction || this.pendingLocalDashPrediction.expiresAtMs < nowMs) {
      this.pendingLocalDashPrediction = null;
      return;
    }

    const player = snapshot.heroes.find((hero) => hero.heroId === snapshot.playerHeroId);
    if (!player || !player.alive || distanceBetween(player.position, this.pendingLocalDashPrediction.destination) <= 48) {
      this.pendingLocalDashPrediction = null;
    }
  }

  private resolveLocalBlinkCooldownMs(player: Hero | null, nowMs: number): number {
    const authoritativeBlinkCooldownMs = player ? findBlinkSkillState(player.skills)?.cooldownMs ?? 0 : 0;
    const predictedBlinkCooldownMs =
      this.localBlinkCooldownUntilMs !== null ? Math.max(0, this.localBlinkCooldownUntilMs - nowMs) : 0;
    return Math.max(authoritativeBlinkCooldownMs, predictedBlinkCooldownMs);
  }

  private resolveLocalDashCooldownMs(player: Hero | null, nowMs: number): number {
    const authoritativeDashCooldownMs = player ? findDashSkillState(player.skills)?.cooldownMs ?? 0 : 0;
    const predictedDashCooldownMs =
      this.localDashCooldownUntilMs !== null ? Math.max(0, this.localDashCooldownUntilMs - nowMs) : 0;
    return Math.max(authoritativeDashCooldownMs, predictedDashCooldownMs);
  }

  private syncLocalBlinkCooldown(snapshot: GameSnapshot, nowMs: number): void {
    const player = snapshot.heroes.find((hero) => hero.heroId === snapshot.playerHeroId);
    const authoritativeBlinkCooldownMs = player ? findBlinkSkillState(player.skills)?.cooldownMs ?? 0 : 0;

    if (authoritativeBlinkCooldownMs > 0) {
      this.localBlinkCooldownUntilMs = Math.max(
        this.localBlinkCooldownUntilMs ?? 0,
        nowMs + authoritativeBlinkCooldownMs
      );
      return;
    }

    if (this.localBlinkCooldownUntilMs !== null && this.localBlinkCooldownUntilMs <= nowMs) {
      this.localBlinkCooldownUntilMs = null;
    }
  }

  private syncLocalDashCooldown(snapshot: GameSnapshot, nowMs: number): void {
    const player = snapshot.heroes.find((hero) => hero.heroId === snapshot.playerHeroId);
    const authoritativeDashCooldownMs = player ? findDashSkillState(player.skills)?.cooldownMs ?? 0 : 0;

    if (authoritativeDashCooldownMs > 0) {
      this.localDashCooldownUntilMs = Math.max(
        this.localDashCooldownUntilMs ?? 0,
        nowMs + authoritativeDashCooldownMs
      );
      return;
    }

    if (this.localDashCooldownUntilMs !== null && this.localDashCooldownUntilMs <= nowMs) {
      this.localDashCooldownUntilMs = null;
    }
  }

  private clearLocalBlinkCooldownIfAuthoritativeReady(snapshot: GameSnapshot): void {
    const player = snapshot.heroes.find((hero) => hero.heroId === snapshot.playerHeroId);
    const authoritativeBlinkCooldownMs = player ? findBlinkSkillState(player.skills)?.cooldownMs ?? 0 : 0;
    if (authoritativeBlinkCooldownMs <= 0) {
      this.localBlinkCooldownUntilMs = null;
    }
  }
}

function distanceBetween(left: Vec2, right: Vec2): number {
  return Math.hypot(right.x - left.x, right.y - left.y);
}

export function suppressInvalidAuthoritativePreparedConfirm({
  command,
  player,
  preparedSkill,
  worldSize,
  obstacleBounds
}: SuppressInvalidAuthoritativePreparedConfirmInput): PlayerCommand {
  if (!command.primaryJustPressed || preparedSkill === null) {
    return command;
  }

  if (
    isSharedAuthoritativeTargetValid({
      player,
      preparedSkill,
      target: command.pointerWorld,
      worldSize,
      obstacleBounds
    })
  ) {
    return command;
  }

  return {
    ...command,
    primaryJustPressed: false
  };
}
