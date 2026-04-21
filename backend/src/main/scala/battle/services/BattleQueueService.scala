package slaydemo.backend.battle.services

import java.util.UUID

import slaydemo.backend.battle.api.BattleQueueJoinRequest
import slaydemo.backend.battle.objects.{BattleQueuePlayer, BattleQueueSnapshot}

trait BattleQueueService {
  def join(request: BattleQueueJoinRequest): Either[String, BattleQueueSnapshot]
  def status(ticketId: String): Option[BattleQueueSnapshot]
  def leave(ticketId: String): Boolean
}

final class InMemoryBattleQueueService(
  capacity: Int = 6,
  durationMs: Long = 10_000L,
  retentionMs: Long = 60_000L
) extends BattleQueueService {
  private final case class QueuedPlayer(ticketId: String, player: BattleQueuePlayer)
  private final case class QueueMatch(matchId: String, startsAt: Long, players: Vector[QueuedPlayer])

  private val lock = new AnyRef
  private var matches: Vector[QueueMatch] = Vector.empty
  private var tickets: Map[String, String] = Map.empty

  override def join(request: BattleQueueJoinRequest): Either[String, BattleQueueSnapshot] = {
    val handle = request.handle.trim
    if (handle.isEmpty) {
      Left("invalid_handle")
    } else {
      lock.synchronized {
        val now = System.currentTimeMillis()
        cleanup(now)

        val queueMatch = findOpenMatch(now).getOrElse(createMatch(now))
        val ticketId = s"ticket-${UUID.randomUUID().toString}"
        val player = QueuedPlayer(ticketId, BattleQueuePlayer(handle, now))
        val nextMatch = queueMatch.copy(players = queueMatch.players :+ player)
        replaceMatch(nextMatch)
        tickets = tickets + (ticketId -> nextMatch.matchId)
        Right(snapshot(ticketId, nextMatch))
      }
    }
  }

  override def status(ticketId: String): Option[BattleQueueSnapshot] = {
    val normalizedTicket = ticketId.trim
    if (normalizedTicket.isEmpty) {
      None
    } else {
      lock.synchronized {
        cleanup(System.currentTimeMillis())
        tickets
          .get(normalizedTicket)
          .flatMap(matchId => matches.find(_.matchId == matchId))
          .map(queueMatch => snapshot(normalizedTicket, queueMatch))
      }
    }
  }

  override def leave(ticketId: String): Boolean = {
    val normalizedTicket = ticketId.trim
    if (normalizedTicket.isEmpty) {
      false
    } else {
      lock.synchronized {
        tickets.get(normalizedTicket) match {
          case Some(matchId) =>
            tickets = tickets - normalizedTicket
            matches = matches.flatMap { queueMatch =>
              if (queueMatch.matchId != matchId) {
                Some(queueMatch)
              } else {
                val nextPlayers = queueMatch.players.filterNot(_.ticketId == normalizedTicket)
                if (nextPlayers.isEmpty) None else Some(queueMatch.copy(players = nextPlayers))
              }
            }
            true
          case None =>
            false
        }
      }
    }
  }

  private def findOpenMatch(now: Long): Option[QueueMatch] =
    matches.find(queueMatch => queueMatch.startsAt > now && queueMatch.players.size < capacity)

  private def createMatch(now: Long): QueueMatch = {
    val queueMatch = QueueMatch(
      matchId = s"match-${UUID.randomUUID().toString}",
      startsAt = now + durationMs,
      players = Vector.empty
    )
    matches = matches :+ queueMatch
    queueMatch
  }

  private def replaceMatch(nextMatch: QueueMatch): Unit = {
    matches = matches.map(queueMatch => if (queueMatch.matchId == nextMatch.matchId) nextMatch else queueMatch)
  }

  private def cleanup(now: Long): Unit = {
    val activeMatches = matches.filter(queueMatch => now <= queueMatch.startsAt + retentionMs)
    val activeMatchIds = activeMatches.map(_.matchId).toSet
    matches = activeMatches
    tickets = tickets.filter { case (_, matchId) => activeMatchIds.contains(matchId) }
  }

  private def snapshot(ticketId: String, queueMatch: QueueMatch): BattleQueueSnapshot =
    BattleQueueSnapshot(
      ticketId = ticketId,
      matchId = queueMatch.matchId,
      startsAt = queueMatch.startsAt,
      players = queueMatch.players.map(_.player),
      capacity = capacity,
      durationMs = durationMs
    )
}
