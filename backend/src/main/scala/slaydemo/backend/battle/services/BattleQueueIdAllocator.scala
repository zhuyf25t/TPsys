package slaydemo.backend.battle.services

import slaydemo.backend.battle.objects.*

private[services] final case class BattleQueueIdAllocator(
  nextTicketNumber: Long,
  nextRoomNumber: Long,
  nextPlayerNumber: Long
) {
  def allocateTicketId: (TicketId, BattleQueueIdAllocator) =
    (
      TicketId(f"ticket-$nextTicketNumber%06d"),
      copy(nextTicketNumber = nextTicketNumber + 1L)
    )

  def allocateRoomId: (RoomId, BattleQueueIdAllocator) =
    (
      RoomId(f"room-$nextRoomNumber%06d"),
      copy(nextRoomNumber = nextRoomNumber + 1L)
    )

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
