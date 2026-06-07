package services.battle.microservices.session.services

import cats.effect.IO

import services.battle.microservices.runtime.services.{BattleDynamicRuleBook, BattleEngine}
import services.battle.objects.BattlePhase
import services.battle.objects.core.{BattleAggregateState, EpochMillis, RoomId}

private[battle] final case class BattleRoomFinishedNotification(
  roomId: RoomId,
  finishedAt: EpochMillis
)

private[battle] final case class BattleStoredBattleAdvanceResult(
  storedBattle: StoredBattle,
  roomFinished: Option[BattleRoomFinishedNotification]
)

private[battle] object BattleStoredBattleAdvanceRules {
  private val StandardExactCatchUpStepLimit = 128L
  private val HighPopulationExactCatchUpStepLimit = 3L
  private val HighPopulationPlayerCount = 8
  private val StandardCoalescedCatchUpStepLimit = 32
  private val HighPopulationCoalescedCatchUpStepLimit = 8
  private val MinCoalescedCatchUpStepMs = 250L

  private final case class AdvancedStateFrame(
    state: BattleAggregateState,
    pendingStepMs: Long
  )

  /** 中文名：推进（advance）。游戏职责：在后端会话域中管理战斗会话、命令受理和状态读写，维护服务端权威状态�?*/
  def advance(
    storedBattle: StoredBattle,
    now: EpochMillis,
    battleRules: BattleDynamicRuleBook
  ): IO[BattleStoredBattleAdvanceResult] =
    for
      safeNow <- safeNowFor(storedBattle, now)
      result <-
        if storedBattle.state.phase == BattlePhase.Finished then
          finishedStoredBattle(storedBattle, safeNow)
        else
          for
            elapsedSinceLastUpdate <- elapsedSinceLastUpdate(storedBattle, safeNow)
            tickStep <- BattleEngine.TickStep(battleRules)
            accumulatedMs <- accumulatedMs(storedBattle, elapsedSinceLastUpdate)
            steps <- stepCount(accumulatedMs, tickStep.value)
            remainderMs <- stepRemainder(accumulatedMs, tickStep.value)
            advancedFrame <- advanceState(storedBattle.state, safeNow, steps, remainderMs, tickStep.value, battleRules)
            result <- advancedResult(storedBattle, advancedFrame.state, safeNow, advancedFrame.pendingStepMs, battleRules)
          yield result
    yield result

  private def safeNowFor(storedBattle: StoredBattle, now: EpochMillis): IO[EpochMillis] =
    IO.pure {
      if now.value >= storedBattle.lastUpdatedAt.value then now
      else storedBattle.lastUpdatedAt
    }

  private def finishedStoredBattle(
    storedBattle: StoredBattle,
    safeNow: EpochMillis
  ): IO[BattleStoredBattleAdvanceResult] =
    IO.pure(
      BattleStoredBattleAdvanceResult(
        storedBattle = storedBattle.copy(
          state = storedBattle.state.copy(serverTime = safeNow),
          lastUpdatedAt = safeNow,
          pendingStepMs = 0L
        ),
        roomFinished = None
      )
    )

  private def elapsedSinceLastUpdate(storedBattle: StoredBattle, safeNow: EpochMillis): IO[Long] =
    IO.pure(math.max(0L, safeNow.value - storedBattle.lastUpdatedAt.value))

  private def accumulatedMs(storedBattle: StoredBattle, elapsedSinceLastUpdate: Long): IO[Long] =
    IO.pure(storedBattle.pendingStepMs + elapsedSinceLastUpdate)

  private def stepCount(accumulatedMs: Long, tickStepMs: Long): IO[Long] =
    IO.pure(accumulatedMs / tickStepMs)

  private def stepRemainder(accumulatedMs: Long, tickStepMs: Long): IO[Long] =
    IO.pure(accumulatedMs % tickStepMs)

  private def advanceState(
    state: BattleAggregateState,
    safeNow: EpochMillis,
    steps: Long,
    remainderMs: Long,
    tickStepMs: Long,
    battleRules: BattleDynamicRuleBook
  ): IO[AdvancedStateFrame] =
    if steps <= 0L then
      BattleEngine
        .advanceStateStep(state, 0L, safeNow, battleRules)
        .map(AdvancedStateFrame(_, remainderMs))
    else if steps <= exactCatchUpStepLimitFor(state) then
      advanceExactState(state, safeNow, steps, remainderMs, tickStepMs, battleRules)
        .map(AdvancedStateFrame(_, remainderMs))
    else
      advanceCoalescedState(state, safeNow, steps, remainderMs, tickStepMs, battleRules)
        .map(AdvancedStateFrame(_, 0L))

  private def exactCatchUpStepLimitFor(state: BattleAggregateState): Long =
    if state.players.length >= HighPopulationPlayerCount then HighPopulationExactCatchUpStepLimit
    else StandardExactCatchUpStepLimit

  private def advanceExactState(
    state: BattleAggregateState,
    safeNow: EpochMillis,
    steps: Long,
    remainderMs: Long,
    tickStepMs: Long,
    battleRules: BattleDynamicRuleBook
  ): IO[BattleAggregateState] = {
    val steppedThroughAt = safeNow.value - remainderMs
    val steppedStateIO = (0L until steps).foldLeft(IO.pure(state)) { case (currentStateIO, stepIndex) =>
      val stepNow = EpochMillis(steppedThroughAt - ((steps - stepIndex - 1L) * tickStepMs))
      currentStateIO.flatMap(currentState => advanceStateStepYielding(currentState, tickStepMs, stepNow, battleRules))
    }
    steppedStateIO.flatMap(steppedState => advanceStateStepYielding(steppedState, 0L, safeNow, battleRules))
  }

  private def advanceCoalescedState(
    state: BattleAggregateState,
    safeNow: EpochMillis,
    steps: Long,
    remainderMs: Long,
    tickStepMs: Long,
    battleRules: BattleDynamicRuleBook
  ): IO[BattleAggregateState] = {
    val accumulatedMs = steps * tickStepMs + remainderMs
    val startedAt = safeNow.value - accumulatedMs
    val stepPlans = coalescedStepDurations(accumulatedMs, coalescedCatchUpStepLimitFor(state)).scanLeft(startedAt -> 0L) {
      case ((previousNow, _), deltaMs) => (previousNow + deltaMs) -> deltaMs
    }.tail

    stepPlans.foldLeft(IO.pure(state)) { case (currentStateIO, (stepNow, deltaMs)) =>
      currentStateIO.flatMap(currentState => advanceStateStepYielding(currentState, deltaMs, EpochMillis(stepNow), battleRules))
    }
  }

  private def advanceStateStepYielding(
    state: BattleAggregateState,
    deltaMs: Long,
    now: EpochMillis,
    battleRules: BattleDynamicRuleBook
  ): IO[BattleAggregateState] =
    IO.cede *> BattleEngine.advanceStateStep(state, deltaMs, now, battleRules)

  private def coalescedCatchUpStepLimitFor(state: BattleAggregateState): Int =
    if state.players.length >= HighPopulationPlayerCount then HighPopulationCoalescedCatchUpStepLimit
    else StandardCoalescedCatchUpStepLimit

  private def coalescedStepDurations(totalMs: Long, stepLimit: Int): Vector[Long] = {
    val boundedTotalMs = math.max(0L, totalMs)
    val boundedStepLimit = math.max(1, stepLimit)
    if boundedTotalMs <= 0L then Vector.empty
    else {
      val adaptiveStepMs = math.max(
        MinCoalescedCatchUpStepMs,
        math.ceil(boundedTotalMs.toDouble / boundedStepLimit.toDouble).toLong
      )
      val fullStepCount = (boundedTotalMs / adaptiveStepMs).toInt
      val remainderMs = boundedTotalMs % adaptiveStepMs
      Vector.fill(fullStepCount)(adaptiveStepMs) ++ (if remainderMs > 0L then Vector(remainderMs) else Vector.empty)
    }
  }

  private def advancedResult(
    storedBattle: StoredBattle,
    advancedState: BattleAggregateState,
    safeNow: EpochMillis,
    pendingStepMs: Long,
    battleRules: BattleDynamicRuleBook
  ): IO[BattleStoredBattleAdvanceResult] =
    roomFinishedWhenTransitioned(storedBattle.state, advancedState, battleRules).map { roomFinished =>
      BattleStoredBattleAdvanceResult(
        storedBattle = storedBattle.copy(
          state = advancedState,
          lastUpdatedAt = safeNow,
          pendingStepMs = if advancedState.phase == BattlePhase.Finished then 0L else pendingStepMs
        ),
        roomFinished = roomFinished
      )
    }

  private def roomFinishedWhenTransitioned(
    previousState: BattleAggregateState,
    nextState: BattleAggregateState,
    battleRules: BattleDynamicRuleBook
  ): IO[Option[BattleRoomFinishedNotification]] =
    if previousState.phase != BattlePhase.Finished && nextState.phase == BattlePhase.Finished then
      BattleEngine.finishedAtForRoom(nextState, battleRules).map(finishedAt =>
        Some(BattleRoomFinishedNotification(nextState.roomId, finishedAt))
      )
    else IO.pure(None)
}
