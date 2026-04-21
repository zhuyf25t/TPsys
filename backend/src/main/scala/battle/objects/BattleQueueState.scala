package slaydemo.backend.battle.objects

final case class BattleQueuePlayer(handle: String, joinedAt: Long)

final case class BattleQueueSnapshot(
  ticketId: String,
  matchId: String,
  startsAt: Long,
  players: Seq[BattleQueuePlayer],
  capacity: Int,
  durationMs: Long
)

