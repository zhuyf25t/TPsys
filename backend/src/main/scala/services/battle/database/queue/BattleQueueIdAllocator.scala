package services.battle.database.queue

import services.battle.objects.core.{PlayerId, RoomId, TicketId}

private[services] final case class BattleQueueIdAllocator(
  nextTicketNumber: Long,
  nextRoomNumber: Long,
  nextPlayerNumber: Long
) {
  /** 中文名：allocateticket标识（allocateTicketId）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗。 */
  def allocateTicketId: (TicketId, BattleQueueIdAllocator) =
    (
      TicketId(f"ticket-$nextTicketNumber%06d"),
      copy(nextTicketNumber = nextTicketNumber + 1L)
    )

  /** 中文名：allocate房间标识（allocateRoomId）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗。 */
  def allocateRoomId: (RoomId, BattleQueueIdAllocator) =
    (
      RoomId(f"room-$nextRoomNumber%06d"),
      copy(nextRoomNumber = nextRoomNumber + 1L)
    )

  /** 中文名：allocate玩家标识（allocatePlayerId）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗。 */
  def allocatePlayerId: (PlayerId, BattleQueueIdAllocator) =
    (
      PlayerId(f"player-$nextPlayerNumber%06d"),
      copy(nextPlayerNumber = nextPlayerNumber + 1L)
    )
}

private[services] object BattleQueueIdAllocator {
  val initial: BattleQueueIdAllocator =
    BattleQueueIdAllocator(
      nextTicketNumber = 1L,
      nextRoomNumber = 1L,
      nextPlayerNumber = 1L
    )
}
