package services.battle.microservices.queue.services

import cats.effect.IO

import services.battle.microservices.queue.objects.queue.TicketId
import services.battle.objects.core.{PlayerId, RoomId}

private[battle] final case class BattleQueueIdAllocator(
  nextTicketNumber: Long,
  nextRoomNumber: Long,
  nextPlayerNumber: Long
) {
  /** 中文名：allocateticket标识（allocateTicketId）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗。 */
  def allocateTicketId: IO[(TicketId, BattleQueueIdAllocator)] =
    IO.pure((
      TicketId(f"ticket-$nextTicketNumber%06d"),
      copy(nextTicketNumber = nextTicketNumber + 1L)
    ))

  /** 中文名：allocate房间标识（allocateRoomId）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗。 */
  def allocateRoomId: IO[(RoomId, BattleQueueIdAllocator)] =
    IO.pure((
      RoomId(f"room-$nextRoomNumber%06d"),
      copy(nextRoomNumber = nextRoomNumber + 1L)
    ))

  /** 中文名：allocate玩家标识（allocatePlayerId）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗。 */
  def allocatePlayerId: IO[(PlayerId, BattleQueueIdAllocator)] =
    IO.pure((
      PlayerId(f"player-$nextPlayerNumber%06d"),
      copy(nextPlayerNumber = nextPlayerNumber + 1L)
    ))
}

private[battle] object BattleQueueIdAllocator {
  val initial: BattleQueueIdAllocator =
    BattleQueueIdAllocator(
      nextTicketNumber = 1L,
      nextRoomNumber = 1L,
      nextPlayerNumber = 1L
    )
}
