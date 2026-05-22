package services.battle.application

import services.battle.application.*

import services.battle.objects.{EpochMillis, RealtimeRoomHeartbeatCommand}
import services.identity.objects.PlayerHandle
import system.policies.HandlePolicy

private[services] object BattleQueueParticipantRules {
  /** 中文名：规范化optionaltext（normalizeOptionalText）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗�?*/
  def normalizeOptionalText(value: String): Option[String] =
    Option(value).map(_.trim).filter(_.nonEmpty)

  /** 中文名：规范化玩家名（normalizeHandle）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗�?*/
  def normalizeHandle(handle: PlayerHandle): PlayerHandle =
    PlayerHandle.forLookup(handle.value).getOrElse(handle)

  /** 中文名：same玩家名key（sameHandleKey）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗�?*/
  def sameHandleKey(left: PlayerHandle, rightKey: String): Boolean =
    normalizeHandle(left).key == HandlePolicy.normalizeKey(rightKey)

  /** 中文名：心跳matches（heartbeatMatches）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗�?*/
  def heartbeatMatches(entry: QueueParticipantEntry, request: RealtimeRoomHeartbeatCommand): Boolean = {
    val ticketMatches = request.ticketId.contains(entry.ticketId)
    val handleMatches = request.handle.exists(handle => sameHandleKey(entry.participant.handle, normalizeHandle(handle).key))
    ticketMatches || handleMatches
  }

  /** 中文名：touch心跳participant（touchHeartbeatParticipant）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗�?*/
  def touchHeartbeatParticipant(entry: QueueParticipantEntry, now: EpochMillis): QueueParticipantEntry =
    entry.copy(participant = entry.participant.copy(lastSeen = now))
}
