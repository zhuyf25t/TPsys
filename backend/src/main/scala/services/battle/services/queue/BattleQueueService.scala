package services.battle.services.queue

import services.battle.services.*

import services.battle.objects.*
import services.battle.services.queue.BattleQueueSnapshots.{toQueueSnapshot, toRoomSnapshot}
import services.identity.objects.PlayerHandle

final class InMemoryBattleQueueService(
  capacity: BattleCapacity,
  matchmakingDuration: DurationMillis,
  currentTimeMillis: () => Long,
  newBattleId: () => BattleId
) extends BattleQueueService {
  private val lock = Object()
  private var rooms: Map[RoomId, QueueRoom] = Map.empty
  private var tickets: Map[TicketId, TicketRecord] = Map.empty
  private var queueRequests: Map[QueueRequestId, TicketId] = Map.empty
  private var idAllocator: BattleQueueIdAllocator = BattleQueueIdAllocator.initial

  /** 中文名：加入（join）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗。 */
  override def join(command: BattleQueueJoinCommand): BattleQueueSnapshot = {
    val now = EpochMillis(currentTimeMillis())
    lock.synchronized {
      advanceRooms(now)
      val normalizedCommand = BattleQueueJoinRules.normalizeCommand(command)

      normalizedCommand.queueRequestId.flatMap(reuseWaitingQueueRequestOrForgetStale(_, now)) match {
        case Some(snapshot) =>
          snapshot
        case None =>
          val room = selectJoinRoom(normalizedCommand.handle, normalizedCommand.queueRequestId, now)
          val ticketId = nextTicketId()
          val playerId = nextPlayerId()
          val draft = BattleQueueJoinRules.draft(normalizedCommand, room, ticketId, playerId, now)
          val updatedRoom = advanceRoom(draft.room, now)
          rooms = rooms.updated(updatedRoom.roomId, updatedRoom)
          tickets = tickets.updated(ticketId, draft.ticket)
          queueRequests = BattleQueueJoinRules.queueRequestsAfterJoin(queueRequests, normalizedCommand, ticketId)
          toQueueSnapshot(updatedRoom, draft.entry, now)
      }
    }
  }

  /** 中文名：状态（status）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗。 */
  override def status(ticketId: TicketId): Either[BattleQueueStatusError, BattleQueueSnapshot] = {
    val now = EpochMillis(currentTimeMillis())
    lock.synchronized {
      advanceRooms(now)
      BattleQueueTicketSnapshots
        .snapshotForTicket(tickets, rooms, ticketId, now)
        .toRight(BattleQueueStatusError.TicketNotFound)
    }
  }

  /** 中文名：离开（leave）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗。 */
  override def leave(ticketId: TicketId): BattleQueueLeaveOutcome =
    lock.synchronized {
      val transition = BattleQueueLeaveRules.leave(rooms, tickets, queueRequests, ticketId)
      rooms = transition.rooms
      tickets = transition.tickets
      queueRequests = transition.queueRequests
      transition.outcome
    }

  /** 中文名：房间快照（roomSnapshot）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗。 */
  override def roomSnapshot(roomId: RoomId): Either[BattleRoomError, RealtimeRoomSnapshot] = {
    val now = EpochMillis(currentTimeMillis())
    lock.synchronized {
      advanceRooms(now)
      rooms.get(roomId).map(toRoomSnapshot(_, now)).toRight(BattleRoomError.RoomNotFound)
    }
  }

  /** 中文名：心跳（heartbeat）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗。 */
  override def heartbeat(request: RealtimeRoomHeartbeatCommand): Either[BattleRoomError, RealtimeRoomSnapshot] = {
    val now = EpochMillis(currentTimeMillis())
    lock.synchronized {
      advanceRooms(now)
      BattleQueueHeartbeatRules.roomIdForHeartbeat(tickets, request) match {
        case None =>
          Left(BattleRoomError.MissingRoomId)
        case Some(id) =>
          rooms.get(id) match {
            case None =>
              Left(BattleRoomError.RoomNotFound)
            case Some(room) =>
              val updatedRoom = BattleQueueHeartbeatRules.updateHeartbeat(room, request, now)
              rooms = rooms.updated(updatedRoom.roomId, updatedRoom)
              Right(toRoomSnapshot(updatedRoom, now))
          }
      }
      }
    }

  /** 中文名：标记战斗已结束（markBattleFinished）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗。 */
  override def markBattleFinished(roomId: RoomId, finishedAt: EpochMillis): Unit =
    lock.synchronized {
      rooms = BattleQueueRoomLifecycleRules.markFinished(rooms, roomId, finishedAt)
    }

  /** 中文名：active战斗会话（activeBattleSession）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗。 */
  override def activeBattleSession(battleId: BattleId): Option[BattleSessionSeed] = {
    val now = EpochMillis(currentTimeMillis())
    lock.synchronized {
      advanceRooms(now)
      BattleQueueSessionLookupRules.activeBattleSession(rooms.values, battleId, now)
    }
  }

  private def selectJoinRoom(handle: PlayerHandle, queueRequestId: Option[QueueRequestId], now: EpochMillis): QueueRoom = {
    val openRooms = BattleQueueRoomSelectionRules.openWaitingRooms(rooms.values)
    BattleQueueRoomSelectionRules.reusableRoom(openRooms, handle, queueRequestId).getOrElse(createRoom(now))
  }

  private def createRoom(now: EpochMillis): QueueRoom = {
    val roomId = nextRoomId()
    val room = BattleQueueRoomLifecycleRules.newWaitingRoom(roomId, now, matchmakingDuration, capacity)
    rooms = rooms.updated(roomId, room)
    room
  }

  private def advanceRooms(now: EpochMillis): Unit =
    rooms = rooms.view.mapValues(room => advanceRoom(room, now)).toMap

  private def advanceRoom(room: QueueRoom, now: EpochMillis): QueueRoom =
    if BattleQueueRoomLifecycleRules.shouldStart(room, now)
    then BattleQueueRoomLifecycleRules.startRoom(room, newBattleId(), now)
    else room

  private def reuseWaitingQueueRequestOrForgetStale(
    queueRequestId: QueueRequestId,
    now: EpochMillis
  ): Option[BattleQueueSnapshot] = {
    val result = BattleQueueRequestReuseRules.reuseWaitingRequest(
      queueRequests = queueRequests,
      tickets = tickets,
      rooms = rooms,
      queueRequestId = queueRequestId,
      now = now
    )
    queueRequests = result.queueRequests
    result.snapshot
  }

  private def nextTicketId(): TicketId = {
    val (id, nextAllocator) = idAllocator.allocateTicketId
    idAllocator = nextAllocator
    id
  }

  private def nextRoomId(): RoomId = {
    val (id, nextAllocator) = idAllocator.allocateRoomId
    idAllocator = nextAllocator
    id
  }

  private def nextPlayerId(): PlayerId = {
    val (id, nextAllocator) = idAllocator.allocatePlayerId
    idAllocator = nextAllocator
    id
  }

}

object InMemoryBattleQueueService {
  val DefaultCapacity: BattleCapacity = BattleCapacity(6)
  val DefaultMatchmakingDuration: DurationMillis = DurationMillis(5_000L)

  /** 中文名：应用（apply）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗。 */
  def apply(): InMemoryBattleQueueService =
    new InMemoryBattleQueueService(
      capacity = DefaultCapacity,
      matchmakingDuration = DefaultMatchmakingDuration,
      currentTimeMillis = () => System.currentTimeMillis(),
      newBattleId = () => RandomBattleIdGenerator.nextBattleId()
    )
}
