package services.battle.microservices.queue.services

import java.util.concurrent.atomic.AtomicReference

import cats.effect.IO

import BattleQueueSnapshots.{toQueueSnapshot, toRoomSnapshot}
import services.battle.microservices.queue.objects.queue.*
import services.battle.microservices.session.services.{BattleSessionSeed, RandomBattleIdGenerator}
import services.battle.objects.BattleMode
import services.battle.objects.core.{
  BattleId,
  DurationMillis,
  EpochMillis,
  PlayerId,
  RoomId
}
import services.identity.objects.PlayerHandle

final class InMemoryBattleQueueService(
  capacity: BattleCapacity,
  matchmakingDuration: DurationMillis,
  currentTimeMillis: () => Long,
  newBattleId: () => IO[BattleId]
) extends BattleQueueService {
  private val runtimeState: AtomicReference[QueueRuntimeState] =
    AtomicReference(QueueRuntimeState.initial)

  /** 中文名：加入队列（join）。游戏职责：把玩家加入等待房间，并在房间满足条件时生成战斗会话。 */
  override def join(command: BattleQueueJoinCommand): IO[BattleQueueSnapshot] =
    for
      now <- currentTime
      snapshot <- updateAdvancedState(now) { advancedState =>
        val normalizedCommand = BattleQueueJoinRules.normalizeCommand(command)
        val (reusedSnapshot, stateAfterReuse) =
          reuseWaitingQueueRequestOrForgetStale(advancedState, normalizedCommand.queueRequestId, normalizedCommand.battleMode, now)

        reusedSnapshot match {
          case Some(snapshot) =>
            IO.pure(stateAfterReuse -> snapshot)
          case None =>
            val (room, stateWithRoom) =
              selectJoinRoom(stateAfterReuse, normalizedCommand.handle, normalizedCommand.battleMode, normalizedCommand.queueRequestId, now)
            val (ticketId, stateAfterTicket) = nextTicketId(stateWithRoom)
            val (playerId, stateAfterPlayer) = nextPlayerId(stateAfterTicket)
            val draft = BattleQueueJoinRules.draft(normalizedCommand, room, ticketId, playerId, now)
            advanceRoom(draft.room, now).map { updatedRoom =>
              val nextState =
                stateAfterPlayer
                  .withRoom(updatedRoom)
                  .withTickets(stateAfterPlayer.tickets.updated(ticketId, draft.ticket))
                  .withQueueRequests(BattleQueueJoinRules.queueRequestsAfterJoin(stateAfterPlayer.queueRequests, normalizedCommand, ticketId))

              nextState -> toQueueSnapshot(updatedRoom, draft.entry, now)
            }
        }
      }
    yield snapshot

  /** 中文名：查询排队状态（status）。游戏职责：按 ticketId 返回等待房间和即将进入战斗的快照。 */
  override def status(ticketId: TicketId): IO[Either[BattleQueueStatusError, BattleQueueSnapshot]] =
    for
      now <- currentTime
      snapshot <- updateAdvancedState(now) { advancedState =>
        BattleQueueTicketSnapshots
          .snapshotForTicket(advancedState.tickets, advancedState.rooms, ticketId, now)
          .map(snapshot => advancedState -> snapshot)
      }
    yield snapshot.toRight(BattleQueueStatusError.TicketNotFound)

  /** 中文名：离开队列（leave）。游戏职责：从等待房间移除 ticket，并返回离队结果 ADT。 */
  override def leave(ticketId: TicketId): IO[BattleQueueLeaveOutcome] =
    updateState { currentState =>
        val transition = BattleQueueLeaveRules.leave(currentState.rooms, currentState.tickets, currentState.queueRequests, ticketId)
        IO.pure(
          currentState
            .withRooms(transition.rooms)
            .withTickets(transition.tickets)
            .withQueueRequests(transition.queueRequests) -> transition.outcome
        )
    }

  /** 中文名：房间快照（roomSnapshot）。游戏职责：按 roomId 返回等待区房间当前状态。 */
  override def roomSnapshot(roomId: RoomId): IO[Either[BattleRoomError, RealtimeRoomSnapshot]] =
    for
      now <- currentTime
      result <- updateAdvancedState(now) { advancedState =>
        IO.pure(advancedState -> advancedState.rooms.get(roomId).map(toRoomSnapshot(_, now)).toRight(BattleRoomError.RoomNotFound))
      }
    yield result

  /** 中文名：房间心跳（heartbeat）。游戏职责：刷新等待区玩家在线状态，并返回最新房间快照。 */
  override def heartbeat(request: RealtimeRoomHeartbeatCommand): IO[Either[BattleRoomError, RealtimeRoomSnapshot]] =
    for
      now <- currentTime
      result <- updateAdvancedState(now) { advancedState =>
        val (nextState, result) =
          BattleQueueHeartbeatRules.roomIdForHeartbeat(advancedState.tickets, request) match {
            case None =>
              (advancedState, Left(BattleRoomError.MissingRoomId))
            case Some(id) =>
              advancedState.rooms.get(id) match {
                case None =>
                  (advancedState, Left(BattleRoomError.RoomNotFound))
                case Some(room) =>
                  val updatedRoom = BattleQueueHeartbeatRules.updateHeartbeat(room, request, now)
                  (advancedState.withRoom(updatedRoom), Right(toRoomSnapshot(updatedRoom, now)))
              }
          }

        IO.pure(nextState -> result)
      }
    yield result

  /** 中文名：标记战斗结束（markBattleFinished）。游戏职责：把已启动战斗对应的等待房间推进到 Finished ADT 状态。 */
  override def markBattleFinished(roomId: RoomId, finishedAt: EpochMillis): IO[Unit] =
    updateState { currentState =>
      IO.pure(currentState.withRooms(BattleQueueRoomLifecycleRules.markFinished(currentState.rooms, roomId, finishedAt)) -> ())
    }

  /** 中文名：读取活动战斗会话（activeBattleSession）。游戏职责：让战斗运行时按 battleId 找到已启动的 session seed。 */
  override def activeBattleSession(battleId: BattleId): IO[Option[BattleSessionSeed]] =
    for
      now <- currentTime
      session <- updateAdvancedState(now) { advancedState =>
        IO.pure(advancedState -> BattleQueueSessionLookupRules.activeBattleSession(advancedState.rooms.values, battleId, now))
      }
    yield session

  private def selectJoinRoom(
    state: QueueRuntimeState,
    handle: PlayerHandle,
    battleMode: BattleMode,
    queueRequestId: Option[QueueRequestId],
    now: EpochMillis
  ): (QueueRoom, QueueRuntimeState) = {
    val openRooms = BattleQueueRoomSelectionRules.openWaitingRooms(state.rooms.values)
    BattleQueueRoomSelectionRules
      .reusableRoom(openRooms, handle, battleMode, queueRequestId)
      .map(room => (room, state))
      .getOrElse(createRoom(state, now, battleMode))
  }

  private def createRoom(state: QueueRuntimeState, now: EpochMillis, battleMode: BattleMode): (QueueRoom, QueueRuntimeState) = {
    val (roomId, stateAfterRoomId) = nextRoomId(state)
    val room = BattleQueueRoomLifecycleRules.newWaitingRoom(roomId, battleMode, now, matchmakingDuration, capacity)
    (room, stateAfterRoomId.withRoom(room))
  }

  private def currentTime: IO[EpochMillis] =
    IO.blocking(EpochMillis(currentTimeMillis()))

  private def updateState[A](transition: QueueRuntimeState => IO[(QueueRuntimeState, A)]): IO[A] =
    IO.defer {
      val currentState = runtimeState.get()
      transition(currentState).flatMap { case (nextState, result) =>
        if runtimeState.compareAndSet(currentState, nextState) then IO.pure(result)
        else updateState(transition)
      }
    }

  private def updateAdvancedState[A](
    now: EpochMillis
  )(transition: QueueRuntimeState => IO[(QueueRuntimeState, A)]): IO[A] =
    updateState { currentState =>
      advanceRooms(currentState, now).flatMap(transition)
    }

  private def advanceRooms(state: QueueRuntimeState, now: EpochMillis): IO[QueueRuntimeState] =
    state.rooms.foldLeft(IO.pure(Map.empty[RoomId, QueueRoom])) { case (updatedRoomsIO, (roomId, room)) =>
      for
        updatedRooms <- updatedRoomsIO
        updatedRoom <- advanceRoom(room, now)
      yield updatedRooms.updated(roomId, updatedRoom)
    }.map(state.withRooms)

  private def advanceRoom(room: QueueRoom, now: EpochMillis): IO[QueueRoom] =
    BattleQueueRoomLifecycleRules.startDecision(room, now) match {
      case QueueRoomStartDecision.Start =>
        newBattleId().map(battleId => BattleQueueRoomLifecycleRules.startRoom(room, battleId, now))
      case QueueRoomStartDecision.Keep =>
        IO.pure(room)
    }

  private def reuseWaitingQueueRequestOrForgetStale(
    state: QueueRuntimeState,
    queueRequestId: Option[QueueRequestId],
    battleMode: BattleMode,
    now: EpochMillis
  ): (Option[BattleQueueSnapshot], QueueRuntimeState) =
    queueRequestId match {
      case None =>
        (None, state)
      case Some(id) =>
        val result = BattleQueueRequestReuseRules.reuseWaitingRequest(
          queueRequests = state.queueRequests,
          tickets = state.tickets,
          rooms = state.rooms,
          queueRequestId = id,
          battleMode = battleMode,
          now = now
        )
        (result.snapshot, state.withQueueRequests(result.queueRequests))
    }

  private def nextTicketId(state: QueueRuntimeState): (TicketId, QueueRuntimeState) = {
    val (id, nextAllocator) = state.idAllocator.allocateTicketId
    (id, state.copy(idAllocator = nextAllocator))
  }

  private def nextRoomId(state: QueueRuntimeState): (RoomId, QueueRuntimeState) = {
    val (id, nextAllocator) = state.idAllocator.allocateRoomId
    (id, state.copy(idAllocator = nextAllocator))
  }

  private def nextPlayerId(state: QueueRuntimeState): (PlayerId, QueueRuntimeState) = {
    val (id, nextAllocator) = state.idAllocator.allocatePlayerId
    (id, state.copy(idAllocator = nextAllocator))
  }
}

object InMemoryBattleQueueService {
  val DefaultCapacity: BattleCapacity = BattleCapacity(6)
  val DefaultMatchmakingDuration: DurationMillis = DurationMillis(5_000L)

  /** 中文名：创建内存队列服务（apply）。游戏职责：为本地运行时创建默认排队服务实例。 */
  def apply(): InMemoryBattleQueueService =
    new InMemoryBattleQueueService(
      capacity = DefaultCapacity,
      matchmakingDuration = DefaultMatchmakingDuration,
      currentTimeMillis = () => System.currentTimeMillis(),
      newBattleId = () => RandomBattleIdGenerator.nextBattleId()
    )
}
