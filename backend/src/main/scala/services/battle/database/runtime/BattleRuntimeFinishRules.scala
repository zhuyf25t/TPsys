package services.battle.database.runtime

import services.battle.database.actors.BattlePlayerLifecycleRules
import services.battle.objects.{BattleAggregateState, BattlePhase, BattleTick, ElapsedMillis, EpochMillis}

private[services] object BattleRuntimeFinishRules {
  /** 中文名：判断战斗是否结束（isBattleFinished）。游戏职责：根据时长和存活玩家数量判断权威战斗是否进入结束阶段。 */
  def isBattleFinished(state: BattleAggregateState, elapsed: Long): Boolean =
    state.phase == BattlePhase.Finished ||
      elapsed >= state.durationMs.value ||
      state.players.count(player => player.alive && player.hp.value > 0) <= 1

  /** 中文名：结束运行时状态（finishRuntimeState）。游戏职责：清理结束时玩家运行态、写入胜者并追加最终回放帧。 */
  def finishRuntimeState(
    state: BattleAggregateState,
    elapsed: Long,
    now: EpochMillis
  ): BattleAggregateState = {
    val finishedPlayers = state.players.map(BattlePlayerLifecycleRules.clearFinishedPlayerRuntime)
    val winner = BattlePlayerLifecycleRules.winnerFor(finishedPlayers)
    state.copy(
      phase = BattlePhase.Finished,
      serverTime = now,
      elapsedMs = ElapsedMillis(elapsed),
      tick = BattleTick(elapsed / BattleRuntimeCatalog.TickStep.value),
      players = finishedPlayers,
      projectiles = Vector.empty,
      slowFields = state.slowFields,
      replayFrames = BattleReplayFrameRecorder.appendFrame(
        state.replayFrames,
        ElapsedMillis(elapsed),
        finishedPlayers,
        Vector.empty,
        state.pickups
      ),
      winnerPlayerId = winner.map(_.playerId),
      winnerHeroId = winner.map(_.heroId)
    )
  }

  /** 中文名：房间结束时间（finishedAtForRoom）。游戏职责：给等待房间生命周期选择战斗完成时间。 */
  def finishedAtForRoom(state: BattleAggregateState): EpochMillis =
    if state.elapsedMs.value >= state.durationMs.value then state.endsAt
    else state.serverTime
}
