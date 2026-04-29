package slaydemo.backend.battle.services

import scala.collection.concurrent.TrieMap

import slaydemo.backend.battle.api.{BattleCommandAccepted, BattleCommandRequest}
import slaydemo.backend.battle.objects.{BattleAggregateState, BattleSessionDescriptor}
import slaydemo.backend.battle.runtime.{BattleRuntime, InMemoryAuthoritativeBattleRuntime}
import slaydemo.backend.shared.objects.BattleId

final class InMemoryAuthoritativeBattleService(
  runtime: BattleRuntime = new InMemoryAuthoritativeBattleRuntime(),
  finishProjector: Option[AuthoritativeBattleFinishProjector] = None
) extends BattleService {
  private final case class StoredBattle(
    roomId: String,
    state: BattleAggregateState,
    lastUpdatedAt: Long,
    pendingStepMs: Long,
    commandOwnershipByPlayerId: Map[String, String],
    finishedAt: Option[Long]
  )

  private val battles = TrieMap.empty[String, StoredBattle]
  private val battleIdsByRoom = TrieMap.empty[String, String]
  private val tickStepMs = 33L
  private val lock = new AnyRef

  override def initializeRoomBattle(
    roomId: String,
    descriptor: BattleSessionDescriptor,
    commandOwnership: Seq[BattleCommandOwnership]
  ): BattleAggregateState =
    lock.synchronized {
      val ownershipByPlayerId = normalizeCommandOwnership(commandOwnership)
      battleIdsByRoom
        .get(roomId)
        .flatMap(battleId => battles.get(battleId))
        .map { storedBattle =>
          val advancedBattle = advanceToNow(storedBattle, System.currentTimeMillis()).copy(
            commandOwnershipByPlayerId = storedBattle.commandOwnershipByPlayerId ++ ownershipByPlayerId
          )
          battles.put(advancedBattle.state.battleId.value, advancedBattle)
          advancedBattle.state
        }
        .getOrElse {
          val createdAt = System.currentTimeMillis()
          val state = runtime.createBattle(roomId, descriptor, createdAt)
          val battleId = state.battleId.value
          val storedBattle = StoredBattle(
            roomId = roomId,
            state = state,
            lastUpdatedAt = createdAt,
            pendingStepMs = 0L,
            commandOwnershipByPlayerId = ownershipByPlayerId,
            finishedAt = resolveFinishedAt(state)
          )
          battleIdsByRoom.put(roomId, battleId)
          battles.put(battleId, storedBattle)
          state
        }
    }

  override def currentState(battleId: BattleId): Option[BattleAggregateState] = {
    val state = lock.synchronized {
      battles.get(battleId.value).map { storedBattle =>
        val advancedBattle = advanceToNow(storedBattle, System.currentTimeMillis())
        battles.put(battleId.value, advancedBattle)
        advancedBattle.state
      }
    }
    state.foreach(projectFinishedBattle)
    state
  }

  override def isResultReady(battleId: BattleId): Boolean =
    finishProjector.exists(_.isResultReady(battleId))

  override def isReplayReady(battleId: BattleId): Boolean =
    finishProjector.exists(_.isReplayReady(battleId))

  override def roomStatus(roomId: String, now: Long): Option[BattleRoomStatus] = {
    val normalizedRoomId = roomId.trim
    if (normalizedRoomId.isEmpty) {
      None
    } else {
      val (status, finishedState) = lock.synchronized {
        val nextStatus = battleIdsByRoom.get(normalizedRoomId).flatMap(battleId => battles.get(battleId)).map {
          storedBattle =>
            val advancedBattle = advanceToNow(storedBattle, now)
            battles.put(advancedBattle.state.battleId.value, advancedBattle)
            val finishedState = Option.when(advancedBattle.state.phase == "finished")(advancedBattle.state)
            val status = BattleRoomStatus(
              phase = advancedBattle.state.phase,
              finishedAt = advancedBattle.finishedAt
            )
            status -> finishedState
        }
        nextStatus match {
          case Some((status, finishedState)) => Some(status) -> finishedState
          case None                         => None -> None
        }
      }
      finishedState.foreach(projectFinishedBattle)
      status
    }
  }

  override def acceptCommand(request: BattleCommandRequest): Either[String, BattleCommandAccepted] = {
    val (accepted, finishedState) = lock.synchronized {
      battles.get(request.battleId.value) match {
        case None =>
          Left("battle_not_found") -> None
        case Some(storedBattle) =>
          validateCommandOwnership(storedBattle, request) match {
            case Left(error) =>
              Left(error) -> None
            case Right(()) =>
              acceptAuthorizedCommand(storedBattle, request)
          }
      }
    }
    finishedState.foreach(projectFinishedBattle)
    accepted
  }

  override def releaseRoom(roomId: String): Unit =
    lock.synchronized {
      battleIdsByRoom.remove(roomId).foreach(battleId => battles.remove(battleId))
    }

  private def acceptAuthorizedCommand(
    storedBattle: StoredBattle,
    request: BattleCommandRequest
  ): (Either[String, BattleCommandAccepted], Option[BattleAggregateState]) = {
    val now = System.currentTimeMillis()
    if (storedBattle.state.phase == "finished") {
      val finishedState = storedBattle.state.copy(serverTime = now)
      Right(
        BattleCommandAccepted(
          battleId = storedBattle.state.battleId,
          acceptedTick = storedBattle.state.tick,
          acceptedCommandSeq = lastClientCommandSeq(storedBattle.state, request),
          serverTime = now,
          commandStatus = "ignored",
          commandReason = Some("battle_finished")
        )
      ) -> Some(finishedState)
    } else {
      val advancedBattle = advanceToNow(storedBattle, now)
      if (advancedBattle.state.phase == "finished") {
        battles.put(request.battleId.value, advancedBattle)
        Right(
          BattleCommandAccepted(
            battleId = advancedBattle.state.battleId,
            acceptedTick = advancedBattle.state.tick,
            acceptedCommandSeq = lastClientCommandSeq(advancedBattle.state, request),
            serverTime = advancedBattle.state.serverTime,
            commandStatus = "ignored",
            commandReason = Some("battle_finished")
          )
        ) -> Some(advancedBattle.state)
      } else {
        runtime.applyCommand(advancedBattle.state, request, now) match {
          case Left(error) =>
            Left(error) -> None
          case Right(commandApplication) =>
            val nextState = commandApplication.state
            val nextStoredBattle = advancedBattle.copy(
              state = nextState,
              lastUpdatedAt = now
            )
            val storedNextBattle = rememberFinishedAt(nextStoredBattle)
            battles.put(request.battleId.value, storedNextBattle)
            Right(
              BattleCommandAccepted(
                battleId = nextState.battleId,
                acceptedTick = nextState.tick,
                acceptedCommandSeq = lastClientCommandSeq(nextState, request),
                serverTime = nextState.serverTime,
                commandStatus = commandApplication.commandStatus,
                commandReason = commandApplication.commandReason,
                outcomes = commandApplication.outcomes
              )
            ) -> Option.when(nextState.phase == "finished")(nextState)
        }
      }
    }
  }

  private def advanceToNow(storedBattle: StoredBattle, now: Long): StoredBattle = {
    val safeNow = math.max(now, storedBattle.lastUpdatedAt)
    if (storedBattle.state.phase == "finished") {
      return rememberFinishedAt(storedBattle.copy(
        state = storedBattle.state.copy(serverTime = safeNow),
        lastUpdatedAt = safeNow,
        pendingStepMs = 0L
      ))
    }

    val elapsedMs = safeNow - storedBattle.lastUpdatedAt
    val totalAccumulatedMs = storedBattle.pendingStepMs + elapsedMs
    val steps = totalAccumulatedMs / tickStepMs
    val remainderMs = totalAccumulatedMs % tickStepMs

    if (steps <= 0) {
      val nextState = runtime.step(storedBattle.state, 0L, safeNow)
      rememberFinishedAt(storedBattle.copy(
        state = nextState,
        lastUpdatedAt = safeNow,
        pendingStepMs = remainderMs
      ))
    } else {
      val steppedThroughAt = safeNow - remainderMs
      val nextState = (0L until steps).foldLeft(storedBattle.state) { case (state, stepIndex) =>
        val stepNow = steppedThroughAt - ((steps - stepIndex - 1L) * tickStepMs)
        runtime.step(state, tickStepMs, stepNow)
      }

      val clockedState = runtime.step(nextState, 0L, safeNow)

      rememberFinishedAt(StoredBattle(
        roomId = storedBattle.roomId,
        state = clockedState,
        lastUpdatedAt = safeNow,
        pendingStepMs = remainderMs,
        commandOwnershipByPlayerId = storedBattle.commandOwnershipByPlayerId,
        finishedAt = storedBattle.finishedAt
      ))
    }
  }

  private def lastClientCommandSeq(state: BattleAggregateState, request: BattleCommandRequest): Long =
    state.players.find(_.playerId == request.playerId).map(_.lastClientCommandSeq).getOrElse(0L)

  private def validateCommandOwnership(
    storedBattle: StoredBattle,
    request: BattleCommandRequest
  ): Either[String, Unit] = {
    storedBattle.state.players.find(_.playerId == request.playerId) match {
      case None =>
        Left("player_not_found")
      case Some(player) if player.isBot =>
        Left("bot_commands_not_supported")
      case Some(_) =>
        val normalizedTicket = request.ticketId.map(_.trim).filter(_.nonEmpty)
        val expectedTicket = storedBattle.commandOwnershipByPlayerId.get(request.playerId.value)
        if (expectedTicket.nonEmpty && normalizedTicket == expectedTicket) {
          Right(())
        } else {
          Left("command_not_authorized")
        }
    }
  }

  private def normalizeCommandOwnership(commandOwnership: Seq[BattleCommandOwnership]): Map[String, String] =
    commandOwnership.flatMap { ownership =>
      val playerId = ownership.playerId.value.trim
      val ticketId = ownership.ticketId.trim
      if (playerId.nonEmpty && ticketId.nonEmpty) {
        Some(playerId -> ticketId)
      } else {
        None
      }
    }.toMap

  private def rememberFinishedAt(storedBattle: StoredBattle): StoredBattle =
    storedBattle.finishedAt match {
      case Some(_) => storedBattle
      case None    => storedBattle.copy(finishedAt = resolveFinishedAt(storedBattle.state))
    }

  private def resolveFinishedAt(state: BattleAggregateState): Option[Long] =
    if (state.phase != "finished") {
      None
    } else if (state.elapsedMs >= state.durationMs) {
      Some(state.endsAt)
    } else {
      Some(state.serverTime)
    }

  private def projectFinishedBattle(state: BattleAggregateState): Unit =
    finishProjector.foreach(_.projectFinishedBattle(state))
}
