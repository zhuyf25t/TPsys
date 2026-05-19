package slaydemo.backend.battle.services.runtime

import slaydemo.backend.battle.services.*

import slaydemo.backend.battle.objects.*
import slaydemo.backend.battle.services.actors.BattlePlayerLifecycleRules.*
import slaydemo.backend.battle.services.results.BattleReplayFrameRecorder.appendFrame

private[services] object BattleRuntimeFinishRules {
  /** 中文名：判断是否战斗已结束（isBattleFinished）。游戏职责：在后端运行时域中管理 tick 推进、时间、事件保留和结束判定，维持战斗循环。 */
  def isBattleFinished(state: BattleAggregateState, elapsed: Long): Boolean =
    state.phase == BattlePhase.Finished ||
      elapsed >= state.durationMs.value ||
      state.players.count(player => player.alive && player.hp.value > 0) <= 1

  /** 中文名：结束runtime状态（finishRuntimeState）。游戏职责：在后端运行时域中管理 tick 推进、时间、事件保留和结束判定，维持战斗循环。 */
  def finishRuntimeState(
    state: BattleAggregateState,
    elapsed: Long,
    now: EpochMillis
  ): BattleAggregateState = {
    val finishedPlayers = state.players.map(clearFinishedPlayerRuntime)
    val winner = winnerFor(finishedPlayers)
    state.copy(
      phase = BattlePhase.Finished,
      serverTime = now,
      elapsedMs = ElapsedMillis(elapsed),
      tick = BattleTick(elapsed / BattleRuntimeCatalog.TickStep.value),
      players = finishedPlayers,
      projectiles = Vector.empty,
      slowFields = state.slowFields,
      replayFrames = appendFrame(
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

  /** 中文名：已结束atfor房间（finishedAtForRoom）。游戏职责：在后端运行时域中管理 tick 推进、时间、事件保留和结束判定，维持战斗循环。 */
  def finishedAtForRoom(state: BattleAggregateState): EpochMillis =
    if state.elapsedMs.value >= state.durationMs.value then state.endsAt
    else state.serverTime
}
