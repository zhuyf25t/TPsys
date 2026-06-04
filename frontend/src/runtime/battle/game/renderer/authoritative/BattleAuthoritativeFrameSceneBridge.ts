import { BattleAuthoritativeLocalSkillPredictionTracker } from "../../../local/skills/BattleAuthoritativeLocalSkillPredictionTracker";
import { applyAuthoritativeFrameToSnapshot } from "./BattleAuthoritativeFrameSnapshotApplier";
import { applyAuthoritativeLocalHeroDisplayMotion } from "./BattleAuthoritativeLocalHeroMotion";
import { buildPhaserAuthoritativeRenderPipelineFrame } from "./BattleAuthoritativeRenderPipeline";
import { LocalAuthoritativeHeroCorrectionController } from "./BattleLocalAuthoritativeHeroCorrectionController";
import type { LocalHeroDisplay } from "../entities/BattleLocalHeroDisplay";
import type {
  ApplyAuthoritativeFrameSceneBridgeInput,
  UpdateAuthoritativeLocalDisplayMotionInput
} from "./objects/BattleAuthoritativeFrameSceneBridgeObjects";

export type {
  ApplyAuthoritativeFrameSceneBridgeInput,
  GameSceneAuthoritativeFrame,
  GameSceneAuthoritativeFrameOptions,
  UpdateAuthoritativeLocalDisplayMotionInput
} from "./objects/BattleAuthoritativeFrameSceneBridgeObjects";

export class AuthoritativeFrameSceneBridge {
  private readonly localHeroCorrection: LocalAuthoritativeHeroCorrectionController;
  private readonly localSkillPrediction = new BattleAuthoritativeLocalSkillPredictionTracker();

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
      blinkCooldownMsOverride: this.localSkillPrediction.resolveBlinkCooldownMs(player, nowMs),
      dashCooldownMsOverride: this.localSkillPrediction.resolveDashCooldownMs(player, nowMs)
    });
    this.localSkillPrediction.recordPredictedMotion(motionResult, nowMs);
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
    const localPlayer = snapshot.heroes.find((hero) => hero.heroId === snapshot.playerHeroId) ?? null;
    const pipelineFrame = buildPhaserAuthoritativeRenderPipelineFrame({
      frame,
      nowMs,
      localPlayerMovementActive,
      obstacleBounds,
      commandHistory: options.localCommandHistory ?? [],
      lastClientCommandSeq: options.localLastClientCommandSeq ?? 0,
      pendingBlinkPrediction: this.localSkillPrediction.resolvePendingBlinkPrediction(nowMs),
      pendingDashPrediction: this.localSkillPrediction.resolvePendingDashPrediction(nowMs),
      blinkCooldownMsOverride: this.localSkillPrediction.resolveBlinkCooldownMs(localPlayer, nowMs),
      dashCooldownMsOverride: this.localSkillPrediction.resolveDashCooldownMs(localPlayer, nowMs),
      applyLocalPlayerAuthoritativeCorrection: ({ authoritativePosition, localMovementActive, forceHardSnap }) => {
        this.localHeroCorrection.observeAuthoritativePosition(authoritativePosition, {
          localMovementActive,
          forceHardSnap
        });
      }
    });

    applyAuthoritativeFrameToSnapshot({
      snapshot,
      frame: pipelineFrame.frame,
      receivedAtMs: nowMs,
      localPlayerMovementActive: pipelineFrame.localPlayerMovementActive,
      localPlayerReplay: pipelineFrame.localPlayerReplay,
      applyLocalPlayerAuthoritativeCorrection: pipelineFrame.applyLocalPlayerAuthoritativeCorrection
    });
    this.localSkillPrediction.syncFromAuthoritativeSnapshot(snapshot, nowMs);

    return pipelineFrame.remoteAuthoritativeHeroIds;
  }
}
