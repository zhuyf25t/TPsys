package slaydemo.backend.battle.services

import slaydemo.backend.battle.objects.*

private[services] object BattleQueueSessionLookupRules {
  def activeBattleSession(
    rooms: Iterable[QueueRoom],
    battleId: BattleId,
    now: EpochMillis
  ): Option[BattleSessionSeed] =
    rooms.iterator.flatMap { room =>
      room.battleSession.filter(_.battleId == battleId).map { session =>
        BattleSessionSeed(
          roomId = room.roomId,
          descriptor = session.copy(serverTime = now),
          commandOwnership = room.participants.map(entry => BattleCommandOwnership(entry.playerId, entry.ticketId))
        )
      }
    }.toVector.headOption
}
