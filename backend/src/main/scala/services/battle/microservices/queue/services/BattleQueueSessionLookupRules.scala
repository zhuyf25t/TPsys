package services.battle.microservices.queue.services

import cats.effect.IO

import services.battle.microservices.queue.objects.queue.*

import services.battle.microservices.session.services.{BattleCommandOwnership, BattleSessionSeed}
import services.battle.objects.core.{BattleId, EpochMillis}

private[battle] object BattleQueueSessionLookupRules {
  /** 中文名：active战斗会话（activeBattleSession）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗。 */
  def activeBattleSession(
    rooms: Iterable[QueueRoom],
    battleId: BattleId,
    now: EpochMillis
  ): IO[Option[BattleSessionSeed]] =
    rooms.foldLeft(IO.pure(Option.empty[BattleSessionSeed])) { (foundIO, room) =>
      foundIO.flatMap {
        case Some(seed) =>
          IO.pure(Some(seed))
        case None =>
          room.battleSession.map { maybeSession =>
            maybeSession.filter(_.battleId == battleId).map { session =>
              BattleSessionSeed(
                roomId = room.roomId,
                descriptor = session.copy(serverTime = now),
                commandOwnership = room.participants.map(entry => BattleCommandOwnership(entry.playerId, entry.ticketId))
              )
            }
          }
      }
    }
}
