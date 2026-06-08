package services.battle.microservices.session.services

import scala.util.control.NonFatal

import cats.effect.{IO, Ref, Resource}
import cats.effect.std.Semaphore
import cats.syntax.all.*

import services.battle.microservices.runtime.services.{BattleDynamicRuleBook, BattleEngine}
import services.battle.microservices.actors.objects.player.BattlePlayerState
import services.battle.objects.BattlePhase
import services.battle.microservices.session.objects.command.{
  BattleCommandAccepted,
  BattleCommandAcceptPath,
  BattleCommandRequest,
  BattleCommandServerDiagnostics
}
import services.battle.objects.core.{BattleAggregateState, BattleId, DurationMillis, EpochMillis, PlayerId, RoomId}
import services.battle.microservices.queue.objects.queue.{BattleSessionDescriptor, TicketId}
import services.battle.microservices.results.objects.result.{
  BattleFinishProjectionOutcome,
  BattleFinishProjectionStatus,
  BattleFinishProjector,
  NoopBattleFinishProjector
}
final case class BattleCommandOwnership(
  playerId: PlayerId,
  ticketId: TicketId
)

final case class BattleSessionSeed(
  roomId: RoomId,
  descriptor: BattleSessionDescriptor,
  commandOwnership: Vector[BattleCommandOwnership]
)

trait BattleSessionLookup {
  /** 中文名：active战斗会话（activeBattleSession）。游戏职责：在后端会话域中管理战斗会话、命令受理和状态读写，维护服务端权威状态�?*/
  def activeBattleSession(battleId: BattleId): IO[Option[BattleSessionSeed]]
}

enum BattleStateReadError {
  case BattleNotFound
}

enum BattleCommandSubmitError {
  case BattleNotFound
  case PlayerNotFound
  case BotCommandsNotSupported
  case CommandNotAuthorized
}

trait BattleStateService {
  /** 中文名：当前状态（currentState）。游戏职责：在后端会话域中管理战斗会话、命令受理和状态读写，维护服务端权威状态�?*/
  def currentState(battleId: BattleId): IO[Either[BattleStateReadError, BattleAggregateState]]
  /** 中文名：受理命令（acceptCommand）。游戏职责：在后端会话域中管理战斗会话、命令受理和状态读写，维护服务端权威状态�?*/
  def acceptCommand(request: BattleCommandRequest): IO[Either[BattleCommandSubmitError, BattleCommandAccepted]]
}

trait BattleRoomLifecycleSink {
  /** 中文名：标记战斗已结束（markBattleFinished）。游戏职责：session 在权威战斗结束时通知等待房间生命周期，避�?session 反向依赖 queue 实现�?*/
  def markBattleFinished(roomId: RoomId, finishedAt: EpochMillis): IO[Unit]
}

object NoopBattleRoomLifecycleSink extends BattleRoomLifecycleSink {
  /** 中文名：空房间生命周期通知（markBattleFinished）。游戏职责：测试或无队列模式下忽略战斗结束通知�?*/
  override def markBattleFinished(roomId: RoomId, finishedAt: EpochMillis): IO[Unit] = IO.unit
}

private final case class BattleAdvanceGate(
  stateAdvanceLock: Semaphore[IO],
  pendingCommandAdvanceCount: Ref[IO, Int]
)

final class InMemoryBattleStateService private (
  sessionLookup: BattleSessionLookup,
  currentTimeMillis: () => Long,
  battleDuration: DurationMillis,
  battleRules: BattleDynamicRuleBook,
  finishProjector: BattleFinishProjector,
  roomLifecycleSink: BattleRoomLifecycleSink,
  battles: Ref[IO, Map[BattleId, StoredBattle]],
  advanceGates: Ref[IO, Map[BattleId, BattleAdvanceGate]]
) extends BattleStateService {
  private final case class AdvancedStoredBattle(
    storedBattle: StoredBattle,
    projectionCandidate: Option[BattleAggregateState],
    roomFinished: Option[BattleRoomFinishedNotification]
  )

  private final case class CommandSubmissionUpdate(
    storedBattle: StoredBattle,
    submission: CommandSubmission
  )

  private final case class FreshCommandSubmission(
    submission: CommandSubmission,
    commitRetryCount: Int
  )

  private final case class TimedCommandSubmission(
    submission: CommandSubmission,
    roomFinished: Option[BattleRoomFinishedNotification],
    advanceMs: Long,
    commitRetryCount: Int
  )

  private final case class SerializedStateAdvanceTiming[A](
    result: A,
    lockWaitMs: Long,
    lockHeldMs: Long
  )

  private val CommandAcceptDeferredAdvanceWindowMs = 66L

  /** 中文名：当前状态（currentState）。游戏职责：在后端会话域中管理战斗会话、命令受理和状态读写，维护服务端权威状态�?*/
  override def currentState(battleId: BattleId): IO[Either[BattleStateReadError, BattleAggregateState]] =
    for
      now <- IO.blocking(EpochMillis(currentTimeMillis()))
      freshState <- freshReadableState(battleId, now)
      result <- freshState match {
        case Some(state) => IO.pure(Right(state))
        case None        => currentStateAtOrLatestWhenAdvancing(battleId, now)
      }
    yield result

  private def currentStateAtOrLatestWhenAdvancing(
    battleId: BattleId,
    now: EpochMillis
  ): IO[Either[BattleStateReadError, BattleAggregateState]] =
    battleAdvanceGate(battleId).flatMap { gate =>
      gate.pendingCommandAdvanceCount.get.flatMap {
        case pending if pending > 0 =>
          latestStateOrSerializedAdvance(battleId, now)
        case _ =>
          gate.stateAdvanceLock.tryAcquire.flatMap {
            case true =>
              currentStateAt(battleId, now).guarantee(gate.stateAdvanceLock.release)
            case false =>
              latestStateOrSerializedAdvance(battleId, now)
          }
      }
    }

  private def latestStateOrSerializedAdvance(
    battleId: BattleId,
    now: EpochMillis
  ): IO[Either[BattleStateReadError, BattleAggregateState]] =
    latestFreshOrFinishedState(battleId, now).flatMap {
      case Some(state) => IO.pure(Right(state))
      case None        => withSerializedStateAdvance(battleId)(currentStateAt(battleId, now))
    }

  private def currentStateAt(
    battleId: BattleId,
    now: EpochMillis
  ): IO[Either[BattleStateReadError, BattleAggregateState]] =
    for
      maybeStoredBattle <- findOrInitialize(battleId, now)
      readAndNotification <- maybeStoredBattle.fold(
        battleNotFoundRead
      )(storedBattle => advanceAndCommitRead(battleId, storedBattle, now))
      (read, roomFinished) = readAndNotification
      _ <- notifyRoomFinished(roomFinished)
      result <- read.projectionCandidate match {
        case Some(candidate) =>
          completeProjectionIO(candidate.battleId, candidate).map(Right.apply)
        case None =>
          IO.pure(read.result)
      }
    yield result

  private def freshReadableState(
    battleId: BattleId,
    now: EpochMillis
  ): IO[Option[BattleAggregateState]] =
    BattleEngine.TickStep(battleRules).flatMap { tickStep =>
      battles.get.map(_.get(battleId).flatMap { storedBattle =>
        val ageMs = now.value - storedBattle.lastUpdatedAt.value
        val tickStepMs = math.max(1L, tickStep.value)
        val accumulatedMs = storedBattle.pendingStepMs + ageMs
        val projectionComplete =
          storedBattle.finishProjectionStatus == BattleFinishProjectionStatus.Ready ||
            storedBattle.finishProjectionStatus == BattleFinishProjectionStatus.NotConfigured
        Option.when(
          ageMs >= 0L &&
            (
              (storedBattle.state.phase == BattlePhase.Finished && projectionComplete) ||
                (storedBattle.state.phase != BattlePhase.Finished && accumulatedMs < tickStepMs)
            )
        )(storedBattle.state.copy(serverTime = now))
      })
    }

  /** 中文名：受理命令（acceptCommand）。游戏职责：在后端会话域中管理战斗会话、命令受理和状态读写，维护服务端权威状态�?*/
  override def acceptCommand(
    request: BattleCommandRequest
  ): IO[Either[BattleCommandSubmitError, BattleCommandAccepted]] =
    for
      now <- currentWallClockMs
      startedAtMs <- monotonicMs
      result <- withPendingCommandAdvance(request.battleId) {
        for
          freshSubmission <- acceptFreshCommandWithoutAdvance(request, now)
          result <- freshSubmission match
            case Some(submission) =>
              commandResultWithServerDiagnostics(
                request = request,
                result = submission.submission.result,
                receivedAt = now,
                startedAtMs = startedAtMs,
                path = BattleCommandAcceptPath.Fresh,
                lockWaitMs = 0L,
                lockHeldMs = 0L,
                advanceMs = 0L,
                commitRetryCount = submission.commitRetryCount
              )
            case None =>
              withSerializedStateAdvanceTimed(request.battleId) {
                for
                  maybeStoredBattle <- findOrInitialize(request.battleId, now)
                  timedSubmission <- maybeStoredBattle.fold(
                    battleNotFoundTimedSubmission
                  )(storedBattle => acceptAndCommitCommand(request, storedBattle, now))
                  submission = timedSubmission.submission
                  roomFinished = timedSubmission.roomFinished
                  _ <- notifyRoomFinished(roomFinished)
                  _ <- submission.projectionCandidate match {
                    case Some(candidate) => completeProjectionIO(candidate.battleId, candidate).void
                    case None            => IO.unit
                  }
                yield timedSubmission
              }.flatMap { timing =>
                commandResultWithServerDiagnostics(
                  request = request,
                  result = timing.result.submission.result,
                  receivedAt = now,
                  startedAtMs = startedAtMs,
                  path = BattleCommandAcceptPath.Serialized,
                  lockWaitMs = timing.lockWaitMs,
                  lockHeldMs = timing.lockHeldMs,
                  advanceMs = timing.result.advanceMs,
                  commitRetryCount = timing.result.commitRetryCount
                )
              }
        yield result
      }
    yield result

  private def currentWallClockMs: IO[EpochMillis] =
    IO.blocking(EpochMillis(currentTimeMillis()))

  private def monotonicMs: IO[Long] =
    IO.monotonic.map(_.toMillis)

  private def battleAdvanceGate(battleId: BattleId): IO[BattleAdvanceGate] =
    advanceGates.get.flatMap { currentGates =>
      currentGates.get(battleId) match {
        case Some(gate) =>
          IO.pure(gate)
        case None =>
          for
            lock <- Semaphore[IO](1L)
            pendingCount <- Ref.of[IO, Int](0)
            created = BattleAdvanceGate(lock, pendingCount)
            gate <- advanceGates.modify { latestGates =>
              latestGates.get(battleId) match {
                case Some(existing) =>
                  latestGates -> existing
                case None =>
                  latestGates.updated(battleId, created) -> created
              }
            }
          yield gate
      }
    }

  private def withSerializedStateAdvance[A](battleId: BattleId)(operation: IO[A]): IO[A] =
    battleAdvanceGate(battleId).flatMap(_.stateAdvanceLock.permit.use(_ => operation))

  private def withSerializedStateAdvanceTimed[A](
    battleId: BattleId
  )(operation: IO[A]): IO[SerializedStateAdvanceTiming[A]] =
    for
      gate <- battleAdvanceGate(battleId)
      waitingStartedAtMs <- monotonicMs
      timing <- gate.stateAdvanceLock.permit.use { _ =>
        for
          acquiredAtMs <- monotonicMs
          result <- operation
          releasedAtMs <- monotonicMs
        yield SerializedStateAdvanceTiming(
          result = result,
          lockWaitMs = math.max(0L, acquiredAtMs - waitingStartedAtMs),
          lockHeldMs = math.max(0L, releasedAtMs - acquiredAtMs)
        )
      }
    yield timing

  private def withPendingCommandAdvance[A](battleId: BattleId)(operation: IO[A]): IO[A] =
    battleAdvanceGate(battleId).flatMap { gate =>
      gate.pendingCommandAdvanceCount.update(_ + 1) *>
        operation.guarantee(gate.pendingCommandAdvanceCount.update(count => math.max(0, count - 1)))
    }

  private def timed[A](operation: IO[A]): IO[(A, Long)] =
    for
      startedAtMs <- monotonicMs
      result <- operation
      completedAtMs <- monotonicMs
    yield result -> math.max(0L, completedAtMs - startedAtMs)

  private def commandResultWithServerDiagnostics(
    request: BattleCommandRequest,
    result: Either[BattleCommandSubmitError, BattleCommandAccepted],
    receivedAt: EpochMillis,
    startedAtMs: Long,
    path: BattleCommandAcceptPath,
    lockWaitMs: Long,
    lockHeldMs: Long,
    advanceMs: Long,
    commitRetryCount: Int
  ): IO[Either[BattleCommandSubmitError, BattleCommandAccepted]] =
    for
      completedAt <- currentWallClockMs
      completedAtMs <- monotonicMs
    yield result.map { accepted =>
      accepted.copy(
        serverDiagnostics = Some(
          BattleCommandServerDiagnostics(
            path = path,
            receivedAt = receivedAt,
            completedAt = completedAt,
            durationMs = math.max(0L, completedAtMs - startedAtMs),
            lockWaitMs = math.max(0L, lockWaitMs),
            lockHeldMs = math.max(0L, lockHeldMs),
            advanceMs = math.max(0L, advanceMs),
            commitRetryCount = math.max(0, commitRetryCount),
            clientTick = request.clientTick,
            acceptedTick = accepted.acceptedTick,
            acceptedTickLag = accepted.acceptedTick.value - request.clientTick.value,
            clientCommandSeq = request.clientCommandSeq,
            acceptedCommandSeq = accepted.acceptedCommandSeq,
            acceptedCommandSeqLag = accepted.acceptedCommandSeq.value - request.clientCommandSeq.value
          )
        )
      )
    }

  private def findOrInitialize(battleId: BattleId, now: EpochMillis): IO[Option[StoredBattle]] =
    battles.get.map(_.get(battleId)).flatMap {
      case Some(storedBattle) =>
        IO.pure(Some(storedBattle))
      case None =>
        for
          maybeSeed <- sessionLookup.activeBattleSession(battleId)
          maybeStoredBattle <- maybeSeed match {
            case None =>
              IO.pure(None)
            case Some(seed) =>
              battles.get.map(_.get(battleId)).flatMap {
                case Some(storedBattle) =>
                  IO.pure(Some(storedBattle))
                case None =>
                  BattleStoredBattleInitializationRules.fromSeed(seed, battleDuration, now, battleRules).flatMap { initialized =>
                    battles.modify { currentBattles =>
                      currentBattles.get(battleId) match {
                        case Some(existing) =>
                          (currentBattles, Some(existing))
                        case None =>
                          (currentBattles.updated(battleId, initialized), Some(initialized))
                      }
                    }
                  }
              }
          }
        yield maybeStoredBattle
    }

  private def advanceStoredBattle(
    storedBattle: StoredBattle,
    now: EpochMillis
  ): IO[AdvancedStoredBattle] =
    for
      advanced <- BattleStoredBattleAdvanceRules.advance(storedBattle, now, battleRules)
      prepared <- prepareProjection(advanced.storedBattle)
    yield AdvancedStoredBattle(prepared.storedBattle, prepared.projectionCandidate, advanced.roomFinished)

  private def advanceAndCommitRead(
    battleId: BattleId,
    fallback: StoredBattle,
    now: EpochMillis
  ): IO[(StateRead, Option[BattleRoomFinishedNotification])] =
    for
      snapshot <- latestStoredBattle(battleId, fallback)
      advanced <- advanceStoredBattle(snapshot, now)
      committed <- commitStoredBattleIfCurrent(battleId, snapshot, advanced.storedBattle)
      result <-
        if committed then
          stateRead(advanced.storedBattle.state, advanced.projectionCandidate).map(_ -> advanced.roomFinished)
        else
          latestStoredBattle(battleId, fallback)
            .flatMap(latest => stateRead(latest.state, None))
            .map(_ -> None)
    yield result

  private def acceptAndCommitCommand(
    request: BattleCommandRequest,
    fallback: StoredBattle,
    now: EpochMillis
  ): IO[TimedCommandSubmission] = {
    def loop(commitRetryCount: Int, accumulatedAdvanceMs: Long): IO[TimedCommandSubmission] =
      for
        snapshot <- latestStoredBattle(request.battleId, fallback)
        preflight <- preflightCommandSubmission(request, snapshot, now)
        result <-
          preflight match {
            case Some(update) =>
              IO.pure(
                TimedCommandSubmission(
                  submission = update.submission,
                  roomFinished = None,
                  advanceMs = accumulatedAdvanceMs,
                  commitRetryCount = commitRetryCount
                )
              )
            case None =>
              for
                timedAdvance <- timed(advanceStoredBattle(snapshot, now))
                (advanced, advanceMs) = timedAdvance
                update <- buildCommandSubmission(request, advanced.storedBattle, advanced.projectionCandidate, now)
                committed <- commitStoredBattleIfCurrent(request.battleId, snapshot, update.storedBattle)
                totalAdvanceMs = accumulatedAdvanceMs + advanceMs
                result <-
                  if committed then
                    IO.pure(
                      TimedCommandSubmission(
                        submission = update.submission,
                        roomFinished = advanced.roomFinished,
                        advanceMs = totalAdvanceMs,
                        commitRetryCount = commitRetryCount
                      )
                    )
                  else loop(commitRetryCount + 1, totalAdvanceMs)
              yield result
          }
      yield result

    loop(commitRetryCount = 0, accumulatedAdvanceMs = 0L)
  }

  private def preflightCommandSubmission(
    request: BattleCommandRequest,
    storedBattle: StoredBattle,
    now: EpochMillis
  ): IO[Option[CommandSubmissionUpdate]] =
    storedBattle.state.players.find(_.playerId == request.playerId) match {
      case None =>
        commandSubmissionUpdate(storedBattle, Left(BattleCommandSubmitError.PlayerNotFound), None).map(Some(_))
      case Some(player) if player.isBot =>
        commandSubmissionUpdate(storedBattle, Left(BattleCommandSubmitError.BotCommandsNotSupported), None).map(Some(_))
      case Some(_) if storedBattle.commandOwnershipByPlayerId.get(request.playerId).forall(_ != request.ticketId) =>
        commandSubmissionUpdate(storedBattle, Left(BattleCommandSubmitError.CommandNotAuthorized), None).map(Some(_))
      case Some(player) if request.clientCommandSeq.value <= player.lastClientCommandSeq.value =>
        for
          ignored <- BattleCommandAcceptanceFactory.stale(storedBattle.state, player, now)
          update <- commandSubmissionUpdate(storedBattle, Right(ignored), None)
        yield Some(update)
      case Some(player) if storedBattle.state.phase != BattlePhase.Active || !player.alive =>
        for
          ignored <- BattleCommandAcceptanceFactory.ignored(storedBattle.state, player, now)
          update <- commandSubmissionUpdate(storedBattle, Right(ignored), None)
        yield Some(update)
      case Some(_) =>
        IO.pure(None)
    }

  private def acceptFreshCommandWithoutAdvance(
    request: BattleCommandRequest,
    now: EpochMillis
  ): IO[Option[FreshCommandSubmission]] =
    BattleEngine.TickStep(battleRules).flatMap { tickStep =>
      val tickStepMs = math.max(1L, tickStep.value)

      def loop(commitRetryCount: Int): IO[Option[FreshCommandSubmission]] =
        battles.get.flatMap { currentBattles =>
          currentBattles.get(request.battleId) match {
            case None =>
              IO.pure(None)
            case Some(snapshot) =>
              freshCommandStoredBattle(snapshot, now, tickStepMs, request) match {
                case None =>
                  IO.pure(None)
                case Some(freshSnapshot) =>
                  buildCommandSubmission(request, freshSnapshot, None, now).flatMap { update =>
                    commitStoredBattleIfCurrent(request.battleId, snapshot, update.storedBattle).flatMap { committed =>
                      if committed then
                        IO.pure(Some(FreshCommandSubmission(update.submission, commitRetryCount)))
                      else loop(commitRetryCount + 1)
                    }
                  }
              }
          }
        }

      loop(commitRetryCount = 0)
    }

  private def freshCommandStoredBattle(
    storedBattle: StoredBattle,
    now: EpochMillis,
    tickStepMs: Long,
    request: BattleCommandRequest
  ): Option[StoredBattle] = {
    val ageMs = now.value - storedBattle.lastUpdatedAt.value
    val safeAgeMs = math.max(0L, ageMs)
    val accumulatedMs = storedBattle.pendingStepMs + safeAgeMs
    val deferredAdvanceWindowMs = math.max(tickStepMs, math.min(CommandAcceptDeferredAdvanceWindowMs, tickStepMs * 2L))

    Option.when(
      ageMs >= 0L &&
        storedBattle.state.phase != BattlePhase.Finished &&
        accumulatedMs <= deferredAdvanceWindowMs &&
        canApplyFreshCommandWithoutRuntimeAdvance(storedBattle.state, request, accumulatedMs, tickStepMs)
    )(
      storedBattle.copy(
        state = storedBattle.state.copy(serverTime = now),
        lastUpdatedAt = now,
        pendingStepMs = accumulatedMs
      )
    )
  }

  private def canApplyFreshCommandWithoutRuntimeAdvance(
    state: BattleAggregateState,
    request: BattleCommandRequest,
    accumulatedMs: Long,
    tickStepMs: Long
  ): Boolean =
    accumulatedMs < tickStepMs ||
      state.players
        .find(_.playerId == request.playerId)
        .exists(player =>
          !playerHasRuntimeInput(player) &&
            requestStartsRuntimeInput(request) &&
            accumulatedMs <= CommandAcceptDeferredAdvanceWindowMs
        )

  private def playerHasRuntimeInput(player: BattlePlayerState): Boolean =
    math.hypot(player.movement.x, player.movement.y) > 0.0001 ||
      player.primaryHeld ||
      player.reloadPressed ||
      player.sprint

  private def requestStartsRuntimeInput(request: BattleCommandRequest): Boolean =
    math.hypot(request.movement.x, request.movement.y) > 0.0001 ||
      request.primaryHeld ||
      request.reloadPressed ||
      request.sprint

  private def latestStoredBattle(battleId: BattleId, fallback: StoredBattle): IO[StoredBattle] =
    battles.get.map(_.getOrElse(battleId, fallback))

  private def latestFreshOrFinishedState(battleId: BattleId, now: EpochMillis): IO[Option[BattleAggregateState]] =
    BattleEngine.TickStep(battleRules).flatMap { tickStep =>
      battles.get.map(_.get(battleId).flatMap { storedBattle =>
        val ageMs = now.value - storedBattle.lastUpdatedAt.value
        val accumulatedMs = storedBattle.pendingStepMs + math.max(0L, ageMs)
        Option.when(
          storedBattle.state.phase == BattlePhase.Finished ||
            (ageMs >= 0L && accumulatedMs < math.max(1L, tickStep.value))
        )(storedBattle.state.copy(serverTime = now))
      })
    }

  private def commitStoredBattleIfCurrent(
    battleId: BattleId,
    expected: StoredBattle,
    updated: StoredBattle
  ): IO[Boolean] =
    battles.modify { currentBattles =>
      currentBattles.get(battleId) match {
        case Some(current) if sameStoredBattleReference(current, expected) =>
          (currentBattles.updated(battleId, updated), true)
        case None =>
          (currentBattles.updated(battleId, updated), true)
        case _ =>
          (currentBattles, false)
      }
    }

  private def notifyRoomFinished(notification: Option[BattleRoomFinishedNotification]): IO[Unit] =
    notification match {
      case Some(value) => roomLifecycleSink.markBattleFinished(value.roomId, value.finishedAt)
      case None        => IO.unit
    }

  private def battleNotFoundRead: IO[(StateRead, Option[BattleRoomFinishedNotification])] =
    IO.pure((StateRead(Left(BattleStateReadError.BattleNotFound), None), None))

  private def battleNotFoundSubmission: IO[(CommandSubmission, Option[BattleRoomFinishedNotification])] =
    IO.pure((CommandSubmission(Left(BattleCommandSubmitError.BattleNotFound), None), None))

  private def battleNotFoundTimedSubmission: IO[TimedCommandSubmission] =
    IO.pure(
      TimedCommandSubmission(
        submission = CommandSubmission(Left(BattleCommandSubmitError.BattleNotFound), None),
        roomFinished = None,
        advanceMs = 0L,
        commitRetryCount = 0
      )
    )

  private def stateRead(
    state: BattleAggregateState,
    projectionCandidate: Option[BattleAggregateState]
  ): IO[StateRead] =
    IO.pure(StateRead(Right(state), projectionCandidate))

  private def prepareProjection(storedBattle: StoredBattle): IO[BattleFinishProjectionPreparation] =
    BattleFinishProjectionPreparationRules.prepare(storedBattle)

  private def buildCommandSubmission(
    request: BattleCommandRequest,
    storedBattle: StoredBattle,
    projectionCandidate: Option[BattleAggregateState],
    now: EpochMillis
  ): IO[CommandSubmissionUpdate] =
    storedBattle.state.players.find(_.playerId == request.playerId) match {
      case None =>
        commandSubmissionUpdate(storedBattle, Left(BattleCommandSubmitError.PlayerNotFound), projectionCandidate)
      case Some(player) if player.isBot =>
        commandSubmissionUpdate(storedBattle, Left(BattleCommandSubmitError.BotCommandsNotSupported), projectionCandidate)
      case Some(_) if storedBattle.commandOwnershipByPlayerId.get(request.playerId).forall(_ != request.ticketId) =>
        commandSubmissionUpdate(storedBattle, Left(BattleCommandSubmitError.CommandNotAuthorized), projectionCandidate)
      case Some(player) if request.clientCommandSeq.value <= player.lastClientCommandSeq.value =>
        for
          ignored <- BattleCommandAcceptanceFactory.stale(storedBattle.state, player, now)
          update <- commandSubmissionUpdate(storedBattle, Right(ignored), projectionCandidate)
        yield update
      case Some(player) if storedBattle.state.phase != BattlePhase.Active || !player.alive =>
        for
          ignored <- BattleCommandAcceptanceFactory.ignored(storedBattle.state, player, now)
          update <- commandSubmissionUpdate(storedBattle, Right(ignored), projectionCandidate)
        yield update
      case Some(player) =>
        for
          applied <- BattleEngine.applyCommand(storedBattle.state, player, request, battleRules)
          accepted <- BattleCommandAcceptanceFactory.applied(
            state = applied.state,
            playerId = request.playerId,
            serverTime = now,
            outcomes = applied.outcomes
          )
          update <- commandSubmissionUpdate(storedBattle.copy(state = applied.state), Right(accepted), projectionCandidate)
        yield update
    }

  private def commandSubmissionUpdate(
    storedBattle: StoredBattle,
    result: Either[BattleCommandSubmitError, BattleCommandAccepted],
    projectionCandidate: Option[BattleAggregateState]
  ): IO[CommandSubmissionUpdate] =
    IO.pure(CommandSubmissionUpdate(storedBattle, CommandSubmission(result, projectionCandidate)))

  private def completeProjectionIO(battleId: BattleId, candidate: BattleAggregateState): IO[BattleAggregateState] =
    for
      outcome <- projectFinishArtifacts(candidate)
      completed <- completeProjectionLoop(battleId, candidate, outcome)
    yield completed

  private def completeProjectionLoop(
    battleId: BattleId,
    candidate: BattleAggregateState,
    outcome: BattleFinishProjectionOutcome
  ): IO[BattleAggregateState] =
    battles.get.flatMap { currentBattles =>
      currentBattles.get(battleId) match {
        case None =>
          IO.pure(candidate)
        case Some(storedBattle) if storedBattle.finishProjectionStatus != BattleFinishProjectionStatus.InProgress =>
          IO.pure(storedBattle.state)
        case Some(storedBattle) =>
          for
            updated <- BattleFinishProjectionCompletionRules.complete(storedBattle, outcome)
            committed <- commitProjectionCompletion(battleId, storedBattle, updated, candidate)
            completed <- committed match {
              case Some(state) => IO.pure(state)
              case None        => completeProjectionLoop(battleId, candidate, outcome)
            }
          yield completed
      }
    }

  private def commitProjectionCompletion(
    battleId: BattleId,
    expected: StoredBattle,
    updated: StoredBattle,
    missingFallback: BattleAggregateState
  ): IO[Option[BattleAggregateState]] =
    battles.modify { currentBattles =>
      currentBattles.get(battleId) match {
        case None =>
          (currentBattles, Some(missingFallback))
        case Some(current) if sameStoredBattleReference(current, expected) =>
          (currentBattles.updated(battleId, updated), Some(updated.state))
        case Some(current) if current.finishProjectionStatus != BattleFinishProjectionStatus.InProgress =>
          (currentBattles, Some(current.state))
        case Some(_) =>
          (currentBattles, None)
      }
    }

  private def projectFinishArtifacts(candidate: BattleAggregateState): IO[BattleFinishProjectionOutcome] =
    finishProjector.project(candidate).handleErrorWith {
      case NonFatal(error) =>
        BattleFailureMessageFormatter.throwableMessage(error).map(BattleFinishProjectionOutcome.Failed(_))
    }

  private def sameStoredBattleReference(left: StoredBattle, right: StoredBattle): Boolean =
    left.asInstanceOf[AnyRef] eq right.asInstanceOf[AnyRef]

}
object InMemoryBattleStateService {
  def DefaultBattleDuration(battleRules: BattleDynamicRuleBook): IO[DurationMillis] =
    BattleEngine.DefaultBattleDuration(battleRules)

  def apply(
    sessionLookup: BattleSessionLookup,
    battleRules: BattleDynamicRuleBook
  ): IO[InMemoryBattleStateService] =
    create(sessionLookup, battleRules)

  def apply(
    sessionLookup: BattleSessionLookup,
    battleDuration: DurationMillis,
    battleRules: BattleDynamicRuleBook
  ): IO[InMemoryBattleStateService] =
    create(sessionLookup, battleDuration, battleRules)

  def apply(
    sessionLookup: BattleSessionLookup,
    battleDuration: DurationMillis,
    battleRules: BattleDynamicRuleBook,
    finishProjector: BattleFinishProjector
  ): IO[InMemoryBattleStateService] =
    create(sessionLookup, battleDuration, battleRules, finishProjector)

  def apply(
    sessionLookup: BattleSessionLookup,
    battleDuration: DurationMillis,
    battleRules: BattleDynamicRuleBook,
    finishProjector: BattleFinishProjector,
    roomLifecycleSink: BattleRoomLifecycleSink
  ): IO[InMemoryBattleStateService] =
    create(sessionLookup, battleDuration, battleRules, finishProjector, roomLifecycleSink)

  def create(
    sessionLookup: BattleSessionLookup,
    battleRules: BattleDynamicRuleBook
  ): IO[InMemoryBattleStateService] =
    DefaultBattleDuration(battleRules).flatMap(create(sessionLookup, _, battleRules))

  def create(
    sessionLookup: BattleSessionLookup,
    battleDuration: DurationMillis,
    battleRules: BattleDynamicRuleBook
  ): IO[InMemoryBattleStateService] =
    create(sessionLookup, battleDuration, battleRules, NoopBattleFinishProjector)

  def create(
    sessionLookup: BattleSessionLookup,
    battleDuration: DurationMillis,
    battleRules: BattleDynamicRuleBook,
    finishProjector: BattleFinishProjector
  ): IO[InMemoryBattleStateService] =
    create(sessionLookup, battleDuration, battleRules, finishProjector, NoopBattleRoomLifecycleSink)

  def create(
    sessionLookup: BattleSessionLookup,
    battleDuration: DurationMillis,
    battleRules: BattleDynamicRuleBook,
    finishProjector: BattleFinishProjector,
    roomLifecycleSink: BattleRoomLifecycleSink
  ): IO[InMemoryBattleStateService] =
    createWithClock(
      sessionLookup = sessionLookup,
      currentTimeMillis = () => System.currentTimeMillis(),
      battleDuration = battleDuration,
      battleRules = battleRules,
      finishProjector = finishProjector,
      roomLifecycleSink = roomLifecycleSink
    )

  def resource(
    sessionLookup: BattleSessionLookup,
    battleDuration: DurationMillis,
    battleRules: BattleDynamicRuleBook,
    finishProjector: BattleFinishProjector,
    roomLifecycleSink: BattleRoomLifecycleSink
  ): Resource[IO, InMemoryBattleStateService] =
    Resource.eval(create(sessionLookup, battleDuration, battleRules, finishProjector, roomLifecycleSink))

  def createWithClock(
    sessionLookup: BattleSessionLookup,
    currentTimeMillis: () => Long,
    battleDuration: DurationMillis,
    battleRules: BattleDynamicRuleBook,
    finishProjector: BattleFinishProjector,
    roomLifecycleSink: BattleRoomLifecycleSink
  ): IO[InMemoryBattleStateService] =
    for
      battleRef <- Ref.of[IO, Map[BattleId, StoredBattle]](Map.empty)
      advanceGates <- Ref.of[IO, Map[BattleId, BattleAdvanceGate]](Map.empty)
    yield
      new InMemoryBattleStateService(
        sessionLookup = sessionLookup,
        currentTimeMillis = currentTimeMillis,
        battleDuration = battleDuration,
        battleRules = battleRules,
        finishProjector = finishProjector,
        roomLifecycleSink = roomLifecycleSink,
        battles = battleRef,
        advanceGates = advanceGates
      )
}
