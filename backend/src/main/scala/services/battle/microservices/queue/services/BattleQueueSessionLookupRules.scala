package services.battle.microservices.queue.services

import services.battle.objects.queue.*

import services.battle.microservices.session.services.{BattleCommandOwnership, BattleSessionSeed}
import services.battle.objects.core.{BattleId, EpochMillis}

private[battle] object BattleQueueSessionLookupRules {
  /** 中文名：active战斗会话（activeBattleSession）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗。 */
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
