package services.battle.microservices.queue.services

import java.util.concurrent.atomic.AtomicReference

import services.battle.objects.queue.*
import services.battle.objects.queue.BattleQueueSnapshots.{toQueueSnapshot, toRoomSnapshot}
import services.battle.objects.queue.{BattleQueueSnapshot, RealtimeRoomSnapshot}
import services.battle.microservices.session.services.{BattleSessionSeed, RandomBattleIdGenerator}
import services.battle.objects.{BattleMode, BattleQueueJoinCommand, BattleQueueLeaveOutcome, RealtimeRoomHeartbeatCommand}
import services.battle.objects.core.{
  BattleCapacity,
  BattleId,
  DurationMillis,
  EpochMillis,
  PlayerId,
  QueueRequestId,
  RoomId,
  TicketId
}
import services.identity.objects.PlayerHandle

final class InMemoryBattleQueueService(
  capacity: BattleCapacity,
  matchmakingDuration: DurationMillis,
  currentTimeMillis: () => Long,
  newBattleId: () => BattleId
) extends BattleQueueService {
  private val lock = Object()
  private val runtimeState: AtomicReference[QueueRuntimeState] =
    AtomicReference(QueueRuntimeState.initial)

  /** 中文名：加入队列（join）。游戏职责：把玩家加入等待房间，并在房间满足条件时生成战斗会话。 */
  override def join(command: BattleQueueJoinCommand): BattleQueueSnapshot = {
    val now = EpochMillis(currentTimeMillis())
    lock.synchronized {
      val advancedState = advanceRooms(runtimeState.get(), now)
      val normalizedCommand = BattleQueueJoinRules.normalizeCommand(command)
      val (reusedSnapshot, stateAfterReuse) =
        reuseWaitingQueueRequestOrForgetStale(advancedState, normalizedCommand.queueRequestId, normalizedCommand.battleMode, now)

      reusedSnapshot match {
        case Some(snapshot) =>
          runtimeState.set(stateAfterReuse)
          snapshot
        case None =>
          val (room, stateWithRoom) =
            selectJoinRoom(stateAfterReuse, normalizedCommand.handle, normalizedCommand.battleMode, normalizedCommand.queueRequestId, now)
          val (ticketId, stateAfterTicket) = nextTicketId(stateWithRoom)
          val (playerId, stateAfterPlayer) = nextPlayerId(stateAfterTicket)
          val draft = BattleQueueJoinRules.draft(normalizedCommand, room, ticketId, playerId, now)
          val updatedRoom = advanceRoom(draft.room, now)
          val nextState =
            stateAfterPlayer
              .withRoom(updatedRoom)
              .withTickets(stateAfterPlayer.tickets.updated(ticketId, draft.ticket))
              .withQueueRequests(BattleQueueJoinRules.queueRequestsAfterJoin(stateAfterPlayer.queueRequests, normalizedCommand, ticketId))

          runtimeState.set(nextState)
          toQueueSnapshot(updatedRoom, draft.entry, now)
      }
    }
  }

  /** 中文名：查询排队状态（status）。游戏职责：按 ticketId 返回等待房间和即将进入战斗的快照。 */
  override def status(ticketId: TicketId): Either[BattleQueueStatusError, BattleQueueSnapshot] = {
    val now = EpochMillis(currentTimeMillis())
    lock.synchronized {
      val advancedState = advanceRooms(runtimeState.get(), now)
      runtimeState.set(advancedState)
      BattleQueueTicketSnapshots
        .snapshotForTicket(advancedState.tickets, advancedState.rooms, ticketId, now)
        .toRight(BattleQueueStatusError.TicketNotFound)
    }
  }

  /** 中文名：离开队列（leave）。游戏职责：从等待房间移除 ticket，并返回离队结果 ADT。 */
  override def leave(ticketId: TicketId): BattleQueueLeaveOutcome =
    lock.synchronized {
      val currentState = runtimeState.get()
      val transition = BattleQueueLeaveRules.leave(currentState.rooms, currentState.tickets, currentState.queueRequests, ticketId)
      runtimeState.set(
        currentState
          .withRooms(transition.rooms)
          .withTickets(transition.tickets)
          .withQueueRequests(transition.queueRequests)
      )
      transition.outcome
    }

  /** 中文名：房间快照（roomSnapshot）。游戏职责：按 roomId 返回等待区房间当前状态。 */
  override def roomSnapshot(roomId: RoomId): Either[BattleRoomError, RealtimeRoomSnapshot] = {
    val now = EpochMillis(currentTimeMillis())
    lock.synchronized {
      val advancedState = advanceRooms(runtimeState.get(), now)
      runtimeState.set(advancedState)
      advancedState.rooms.get(roomId).map(toRoomSnapshot(_, now)).toRight(BattleRoomError.RoomNotFound)
    }
  }

  /** 中文名：房间心跳（heartbeat）。游戏职责：刷新等待区玩家在线状态，并返回最新房间快照。 */
  override def heartbeat(request: RealtimeRoomHeartbeatCommand): Either[BattleRoomError, RealtimeRoomSnapshot] = {
    val now = EpochMillis(currentTimeMillis())
    lock.synchronized {
      val advancedState = advanceRooms(runtimeState.get(), now)
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

      runtimeState.set(nextState)
      result
    }
  }

  /** 中文名：标记战斗结束（markBattleFinished）。游戏职责：把已启动战斗对应的等待房间推进到 Finished ADT 状态。 */
  override def markBattleFinished(roomId: RoomId, finishedAt: EpochMillis): Unit =
    lock.synchronized {
      val currentState = runtimeState.get()
      runtimeState.set(currentState.withRooms(BattleQueueRoomLifecycleRules.markFinished(currentState.rooms, roomId, finishedAt)))
    }

  /** 中文名：读取活动战斗会话（activeBattleSession）。游戏职责：让战斗运行时按 battleId 找到已启动的 session seed。 */
  override def activeBattleSession(battleId: BattleId): Option[BattleSessionSeed] = {
    val now = EpochMillis(currentTimeMillis())
    lock.synchronized {
      val advancedState = advanceRooms(runtimeState.get(), now)
      runtimeState.set(advancedState)
      BattleQueueSessionLookupRules.activeBattleSession(advancedState.rooms.values, battleId, now)
    }
  }

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

  private def advanceRooms(state: QueueRuntimeState, now: EpochMillis): QueueRuntimeState =
    state.withRooms(state.rooms.view.mapValues(room => advanceRoom(room, now)).toMap)

  private def advanceRoom(room: QueueRoom, now: EpochMillis): QueueRoom =
    BattleQueueRoomLifecycleRules.startDecision(room, now) match {
      case QueueRoomStartDecision.Start =>
        BattleQueueRoomLifecycleRules.startRoom(room, newBattleId(), now)
      case QueueRoomStartDecision.Keep =>
        room
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
