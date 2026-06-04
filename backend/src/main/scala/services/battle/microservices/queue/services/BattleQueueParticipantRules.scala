package services.battle.microservices.queue.services

import cats.effect.IO
import cats.syntax.all.*

import services.battle.microservices.queue.objects.queue.*

import services.battle.objects.core.EpochMillis
import services.identity.objects.PlayerHandle
import system.policies.HandlePolicy

private[battle] object BattleQueueParticipantRules {
  /** 中文名：规范化optionaltext（normalizeOptionalText）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗�?*/
  def normalizeOptionalText(value: String): IO[Option[String]] =
    IO.pure(Option(value).map(_.trim).filter(_.nonEmpty))

  /** 中文名：规范化玩家名（normalizeHandle）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗�?*/
  def normalizeHandle(handle: PlayerHandle): IO[PlayerHandle] =
    IO.pure(PlayerHandle.forLookup(handle.value).getOrElse(handle))

  /** 中文名：same玩家名key（sameHandleKey）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗�?*/
  def sameHandleKey(left: PlayerHandle, rightKey: String): IO[Boolean] =
    normalizeHandle(left).map(_.key == HandlePolicy.normalizeKey(rightKey))

  /** 中文名：心跳matches（heartbeatMatches）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗�?*/
  def heartbeatMatches(entry: QueueParticipantEntry, request: RealtimeRoomHeartbeatCommand): IO[Boolean] =
    for
      ticketMatches <- IO.pure(request.ticketId.contains(entry.ticketId))
      normalizedRequestHandle <- request.handle.traverse(normalizeHandle)
      handleMatches <- normalizedRequestHandle match {
        case Some(handle) => sameHandleKey(entry.participant.handle, handle.key)
        case None         => IO.pure(false)
      }
    yield ticketMatches || handleMatches

  /** 中文名：touch心跳participant（touchHeartbeatParticipant）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗�?*/
  def touchHeartbeatParticipant(entry: QueueParticipantEntry, now: EpochMillis): IO[QueueParticipantEntry] =
    IO.pure(entry.copy(participant = entry.participant.copy(lastSeen = now)))
}
