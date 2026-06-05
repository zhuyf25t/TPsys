package services.battle.microservices.session.services

import scala.util.control.NonFatal

import cats.effect.{IO, Ref, Resource}
import cats.effect.std.Semaphore
import cats.syntax.all.*

import services.battle.microservices.runtime.services.{BattleDynamicRuleBook, BattleEngine}
import services.battle.objects.BattlePhase
import services.battle.microservices.session.objects.command.{BattleCommandAccepted, BattleCommandRequest}
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

final class InMemoryBattleStateService private (
  sessionLookup: BattleSessionLookup,
  currentTimeMillis: () => Long,
  battleDuration: DurationMillis,
  battleRules: BattleDynamicRuleBook,
  finishProjector: BattleFinishProjector,
  roomLifecycleSink: BattleRoomLifecycleSink,
  battles: Ref[IO, Map[BattleId, StoredBattle]],
  stateAdvanceLock: Semaphore[IO]
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

  private val FreshReadWindowMs = 750L

  /** 中文名：当前状态（currentState）。游戏职责：在后端会话域中管理战斗会话、命令受理和状态读写，维护服务端权威状态�?*/
  override def currentState(battleId: BattleId): IO[Either[BattleStateReadError, BattleAggregateState]] =
    for
      now <- IO.blocking(EpochMillis(currentTimeMillis()))
      freshState <- freshReadableState(battleId, now)
      result <- freshState match {
        case Some(state) => IO.pure(Right(state))
        case None        => withSerializedStateAdvance(currentStateAt(battleId, now))
      }
    yield result

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
    battles.get.map(_.get(battleId).flatMap { storedBattle =>
      val ageMs = now.value - storedBattle.lastUpdatedAt.value
      Option.when(ageMs >= 0L && ageMs <= FreshReadWindowMs)(storedBattle.state.copy(serverTime = now))
    })

  /** 中文名：受理命令（acceptCommand）。游戏职责：在后端会话域中管理战斗会话、命令受理和状态读写，维护服务端权威状态�?*/
  override def acceptCommand(
    request: BattleCommandRequest
  ): IO[Either[BattleCommandSubmitError, BattleCommandAccepted]] =
    withSerializedStateAdvance {
      for
        now <- IO.blocking(EpochMillis(currentTimeMillis()))
        maybeStoredBattle <- findOrInitialize(request.battleId, now)
        submissionAndNotification <- maybeStoredBattle.fold(
          battleNotFoundSubmission
        )(storedBattle => acceptAndCommitCommand(request, storedBattle, now))
        (submission, roomFinished) = submissionAndNotification
        _ <- notifyRoomFinished(roomFinished)
        _ <- submission.projectionCandidate match {
          case Some(candidate) => completeProjectionIO(candidate.battleId, candidate).void
          case None            => IO.unit
        }
      yield submission.result
    }

  private def withSerializedStateAdvance[A](operation: IO[A]): IO[A] =
    stateAdvanceLock.permit.use(_ => operation)

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
  ): IO[(CommandSubmission, Option[BattleRoomFinishedNotification])] = {
    def loop: IO[(CommandSubmission, Option[BattleRoomFinishedNotification])] =
      for
        snapshot <- latestStoredBattle(request.battleId, fallback)
        advanced <- advanceStoredBattle(snapshot, now)
        update <- buildCommandSubmission(request, advanced.storedBattle, advanced.projectionCandidate, now)
        committed <- commitStoredBattleIfCurrent(request.battleId, snapshot, update.storedBattle)
        result <- if committed then IO.pure((update.submission, advanced.roomFinished)) else loop
      yield result

    loop
  }

  private def latestStoredBattle(battleId: BattleId, fallback: StoredBattle): IO[StoredBattle] =
    battles.get.map(_.getOrElse(battleId, fallback))

  private def commitStoredBattleIfCurrent(
    battleId: BattleId,
    expected: StoredBattle,
    updated: StoredBattle
  ): IO[Boolean] =
    battles.modify { currentBattles =>
      currentBattles.get(battleId) match {
        case Some(current) if current == expected =>
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
        case Some(current) if current == expected =>
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
      stateAdvanceLock <- Semaphore[IO](1L)
    yield
      new InMemoryBattleStateService(
        sessionLookup = sessionLookup,
        currentTimeMillis = currentTimeMillis,
        battleDuration = battleDuration,
        battleRules = battleRules,
        finishProjector = finishProjector,
        roomLifecycleSink = roomLifecycleSink,
        battles = battleRef,
        stateAdvanceLock = stateAdvanceLock
      )
}
