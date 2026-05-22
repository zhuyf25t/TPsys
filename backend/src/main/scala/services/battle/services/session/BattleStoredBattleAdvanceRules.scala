package services.battle.services.session

import services.battle.services.*

import services.battle.objects.*
import services.battle.services.runtime.BattleRuntimeFinishRules.finishedAtForRoom
import services.battle.services.runtime.BattleRuntimeStepRules.advanceStateStep

private[battle] final case class BattleRoomFinishedNotification(
  roomId: RoomId,
  finishedAt: EpochMillis
)

private[battle] final case class BattleStoredBattleAdvanceResult(
  storedBattle: StoredBattle,
  roomFinished: Option[BattleRoomFinishedNotification]
)

private[battle] object BattleStoredBattleAdvanceRules {
  /** 中文名：推进（advance）。游戏职责：在后端会话域中管理战斗会话、命令受理和状态读写，维护服务端权威状态。 */
  def advance(storedBattle: StoredBattle, now: EpochMillis): BattleStoredBattleAdvanceResult = {
    val safeNow =
      if now.value >= storedBattle.lastUpdatedAt.value then now
      else storedBattle.lastUpdatedAt

    if storedBattle.state.phase == BattlePhase.Finished then
      BattleStoredBattleAdvanceResult(
        storedBattle = storedBattle.copy(
          state = storedBattle.state.copy(serverTime = safeNow),
          lastUpdatedAt = safeNow,
          pendingStepMs = 0L
        ),
        roomFinished = None
      )
    else {
      val elapsedSinceLastUpdate = math.max(0L, safeNow.value - storedBattle.lastUpdatedAt.value)
      val accumulatedMs = storedBattle.pendingStepMs + elapsedSinceLastUpdate
      val steps = accumulatedMs / BattleRuntimeCatalog.TickStep.value
      val remainderMs = accumulatedMs % BattleRuntimeCatalog.TickStep.value

      val advancedState =
        if steps <= 0L then advanceStateStep(storedBattle.state, 0L, safeNow)
        else {
          val steppedThroughAt = safeNow.value - remainderMs
          val steppedState = (0L until steps).foldLeft(storedBattle.state) { case (currentState, stepIndex) =>
            val stepNow = EpochMillis(steppedThroughAt - ((steps - stepIndex - 1L) * BattleRuntimeCatalog.TickStep.value))
            advanceStateStep(currentState, BattleRuntimeCatalog.TickStep.value, stepNow)
          }
          advanceStateStep(steppedState, 0L, safeNow)
        }

      BattleStoredBattleAdvanceResult(
        storedBattle = storedBattle.copy(
          state = advancedState,
          lastUpdatedAt = safeNow,
          pendingStepMs = if advancedState.phase == BattlePhase.Finished then 0L else remainderMs
        ),
        roomFinished = roomFinishedWhenTransitioned(storedBattle.state, advancedState)
      )
    }
  }

  private def roomFinishedWhenTransitioned(
    previousState: BattleAggregateState,
    nextState: BattleAggregateState
  ): Option[BattleRoomFinishedNotification] =
    Option.when(previousState.phase != BattlePhase.Finished && nextState.phase == BattlePhase.Finished) {
      BattleRoomFinishedNotification(nextState.roomId, finishedAtForRoom(nextState))
    }
}
