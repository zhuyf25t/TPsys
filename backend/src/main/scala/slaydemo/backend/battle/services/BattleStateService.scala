package slaydemo.backend.battle.services

import scala.util.control.NonFatal

import slaydemo.backend.battle.api.{BattleCommandAccepted, BattleCommandRequest}
import slaydemo.backend.battle.objects.*

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
  def activeBattleSession(battleId: BattleId): Option[BattleSessionSeed]
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
  def currentState(battleId: BattleId): Either[BattleStateReadError, BattleAggregateState]
  def acceptCommand(request: BattleCommandRequest): Either[BattleCommandSubmitError, BattleCommandAccepted]
}

final class InMemoryBattleStateService(
  sessionLookup: BattleSessionLookup,
  currentTimeMillis: () => Long,
  battleDuration: DurationMillis,
  finishProjector: BattleFinishProjector,
  roomLifecycleSink: BattleRoomLifecycleSink
) extends BattleStateService {
  private val lock = Object()
  private var battles: Map[BattleId, StoredBattle] = Map.empty

  override def currentState(battleId: BattleId): Either[BattleStateReadError, BattleAggregateState] = {
    val read = lock.synchronized {
      val now = EpochMillis(currentTimeMillis())
      findOrInitialize(battleId, now) match {
        case None =>
          StateRead(Left(BattleStateReadError.BattleNotFound), None)
        case Some(storedBattle) =>
          val (advanced, projectionCandidate) = prepareProjection(advanceStoredBattle(storedBattle, now))
          battles = battles.updated(battleId, advanced)
          StateRead(Right(advanced.state), projectionCandidate)
      }
    }

    read.projectionCandidate match {
      case Some(candidate) => Right(completeProjection(candidate.battleId, candidate))
      case None            => read.result
    }
  }

  override def acceptCommand(
    request: BattleCommandRequest
  ): Either[BattleCommandSubmitError, BattleCommandAccepted] = {
    val submission = lock.synchronized {
      val now = EpochMillis(currentTimeMillis())
      findOrInitialize(request.battleId, now) match {
        case None =>
          CommandSubmission(Left(BattleCommandSubmitError.BattleNotFound), None)
        case Some(storedBattle) =>
          val (advanced, projectionCandidate) = prepareProjection(advanceStoredBattle(storedBattle, now))
          advanced.state.players.find(_.playerId == request.playerId) match {
            case None =>
              storeCommandSubmission(request, advanced, Left(BattleCommandSubmitError.PlayerNotFound), projectionCandidate)
            case Some(player) if player.isBot =>
              storeCommandSubmission(request, advanced, Left(BattleCommandSubmitError.BotCommandsNotSupported), projectionCandidate)
            case Some(_) if advanced.commandOwnershipByPlayerId.get(request.playerId).forall(_ != request.ticketId) =>
              storeCommandSubmission(request, advanced, Left(BattleCommandSubmitError.CommandNotAuthorized), projectionCandidate)
            case Some(player) if advanced.state.phase != BattlePhase.Active || !player.alive =>
              val ignored = BattleCommandAcceptanceFactory.ignored(advanced.state, player, now)
              storeCommandSubmission(request, advanced, Right(ignored), projectionCandidate)
            case Some(player) =>
              val applied = BattleCommandApplicationRules.applyCommand(advanced.state, player, request)
              val nextState = applied.state
              val accepted = BattleCommandAcceptanceFactory.applied(
                state = nextState,
                playerId = request.playerId,
                serverTime = now,
                outcomes = applied.outcomes
              )
              storeCommandSubmission(request, advanced.copy(state = nextState), Right(accepted), projectionCandidate)
          }
      }
    }

    submission.projectionCandidate.foreach(candidate => completeProjection(candidate.battleId, candidate))
    submission.result
  }

  private def findOrInitialize(battleId: BattleId, now: EpochMillis): Option[StoredBattle] =
    battles.get(battleId).orElse {
      sessionLookup.activeBattleSession(battleId).map { seed =>
        val storedBattle = BattleStoredBattleInitializationRules.fromSeed(seed, battleDuration, now)
        battles = battles.updated(battleId, storedBattle)
        storedBattle
      }
    }

  private def advanceStoredBattle(storedBattle: StoredBattle, now: EpochMillis): StoredBattle = {
    val advanced = BattleStoredBattleAdvanceRules.advance(storedBattle, now)
    advanced.roomFinished.foreach(notification =>
      roomLifecycleSink.markBattleFinished(notification.roomId, notification.finishedAt)
    )
    advanced.storedBattle
  }

  private def prepareProjection(storedBattle: StoredBattle): (StoredBattle, Option[BattleAggregateState]) = {
    val preparation = BattleFinishProjectionPreparationRules.prepare(storedBattle)
    preparation.storedBattle -> preparation.projectionCandidate
  }

  private def storeCommandSubmission(
    request: BattleCommandRequest,
    storedBattle: StoredBattle,
    result: Either[BattleCommandSubmitError, BattleCommandAccepted],
    projectionCandidate: Option[BattleAggregateState]
  ): CommandSubmission = {
    battles = battles.updated(request.battleId, storedBattle)
    CommandSubmission(result, projectionCandidate)
  }

  private def completeProjection(battleId: BattleId, candidate: BattleAggregateState): BattleAggregateState = {
    val outcome = projectFinishArtifacts(candidate)
    lock.synchronized {
      battles.get(battleId) match {
        case None =>
          candidate
        case Some(storedBattle) if storedBattle.finishProjectionStatus != BattleFinishProjectionStatus.InProgress =>
          storedBattle.state
        case Some(storedBattle) =>
          val updated = BattleFinishProjectionCompletionRules.complete(storedBattle, outcome)
          battles = battles.updated(battleId, updated)
          updated.state
      }
    }
  }

  private def projectFinishArtifacts(candidate: BattleAggregateState): BattleFinishProjectionOutcome =
    try {
      finishProjector.project(candidate)
    } catch {
      case NonFatal(error) =>
        BattleFinishProjectionOutcome.Failed(BattleFailureMessageFormatter.throwableMessage(error))
    }

}
object InMemoryBattleStateService {
  val DefaultBattleDuration: DurationMillis = BattleRuntimeCatalog.DefaultBattleDuration

  def apply(sessionLookup: BattleSessionLookup): InMemoryBattleStateService =
    apply(sessionLookup, DefaultBattleDuration)

  def apply(sessionLookup: BattleSessionLookup, battleDuration: DurationMillis): InMemoryBattleStateService =
    apply(sessionLookup, battleDuration, NoopBattleFinishProjector)

  def apply(
    sessionLookup: BattleSessionLookup,
    battleDuration: DurationMillis,
    finishProjector: BattleFinishProjector
  ): InMemoryBattleStateService =
    apply(sessionLookup, battleDuration, finishProjector, NoopBattleRoomLifecycleSink)

  def apply(
    sessionLookup: BattleSessionLookup,
    battleDuration: DurationMillis,
    finishProjector: BattleFinishProjector,
    roomLifecycleSink: BattleRoomLifecycleSink
  ): InMemoryBattleStateService =
    new InMemoryBattleStateService(
      sessionLookup = sessionLookup,
      currentTimeMillis = () => System.currentTimeMillis(),
      battleDuration = battleDuration,
      finishProjector = finishProjector,
      roomLifecycleSink = roomLifecycleSink
    )
}
