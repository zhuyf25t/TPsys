package services.battle.database.session

import scala.util.control.NonFatal

import services.battle.database.runtime.BattleEngine
import services.battle.objects.BattlePhase
import services.battle.objects.command.{BattleCommandAccepted, BattleCommandRequest}
import services.battle.objects.core.{BattleAggregateState, BattleId, DurationMillis, EpochMillis, PlayerId, RoomId, TicketId}
import services.battle.objects.queue.BattleSessionDescriptor
import services.battle.objects.result.{
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
  /** 中文名：当前状态（currentState）。游戏职责：在后端会话域中管理战斗会话、命令受理和状态读写，维护服务端权威状态�?*/
  def currentState(battleId: BattleId): Either[BattleStateReadError, BattleAggregateState]
  /** 中文名：受理命令（acceptCommand）。游戏职责：在后端会话域中管理战斗会话、命令受理和状态读写，维护服务端权威状态�?*/
  def acceptCommand(request: BattleCommandRequest): Either[BattleCommandSubmitError, BattleCommandAccepted]
}

trait BattleRoomLifecycleSink {
  /** 中文名：标记战斗已结束（markBattleFinished）。游戏职责：session 在权威战斗结束时通知等待房间生命周期，避免 session 反向依赖 queue 实现。 */
  def markBattleFinished(roomId: RoomId, finishedAt: EpochMillis): Unit
}

object NoopBattleRoomLifecycleSink extends BattleRoomLifecycleSink {
  /** 中文名：空房间生命周期通知（markBattleFinished）。游戏职责：测试或无队列模式下忽略战斗结束通知。 */
  override def markBattleFinished(roomId: RoomId, finishedAt: EpochMillis): Unit = ()
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

  /** 中文名：当前状态（currentState）。游戏职责：在后端会话域中管理战斗会话、命令受理和状态读写，维护服务端权威状态�?*/
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

  /** 中文名：受理命令（acceptCommand）。游戏职责：在后端会话域中管理战斗会话、命令受理和状态读写，维护服务端权威状态�?*/
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
              val applied = BattleEngine.applyCommand(advanced.state, player, request)
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
  val DefaultBattleDuration: DurationMillis = BattleEngine.DefaultBattleDuration

  /** 中文名：应用（apply）。游戏职责：在后端会话域中管理战斗会话、命令受理和状态读写，维护服务端权威状态�?*/
  def apply(sessionLookup: BattleSessionLookup): InMemoryBattleStateService =
    apply(sessionLookup, DefaultBattleDuration)

  /** 中文名：应用（apply）。游戏职责：在后端会话域中管理战斗会话、命令受理和状态读写，维护服务端权威状态�?*/
  def apply(sessionLookup: BattleSessionLookup, battleDuration: DurationMillis): InMemoryBattleStateService =
    apply(sessionLookup, battleDuration, NoopBattleFinishProjector)

  /** 中文名：应用（apply）。游戏职责：在后端会话域中管理战斗会话、命令受理和状态读写，维护服务端权威状态�?*/
  def apply(
    sessionLookup: BattleSessionLookup,
    battleDuration: DurationMillis,
    finishProjector: BattleFinishProjector
  ): InMemoryBattleStateService =
    apply(sessionLookup, battleDuration, finishProjector, NoopBattleRoomLifecycleSink)

  /** 中文名：应用（apply）。游戏职责：在后端会话域中管理战斗会话、命令受理和状态读写，维护服务端权威状态�?*/
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
