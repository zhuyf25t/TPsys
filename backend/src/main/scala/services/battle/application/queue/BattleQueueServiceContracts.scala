package services.battle.application

import services.battle.application.*

import services.battle.objects.*
import services.battle.application.BattleSessionLookup
import services.identity.objects.{PlayerHandle, SessionToken}

enum BattleQueueStatusError {
  case TicketNotFound
}

enum BattleRoomError {
  case MissingRoomId
  case RoomNotFound
}

enum BattleQueueLeaveOutcome {
  case LeftQueue
  case NotWaiting
  case TicketNotFound
}

trait BattleRoomLifecycleSink {
  /** 中文名：标记战斗已结束（markBattleFinished）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗。 */
  def markBattleFinished(roomId: RoomId, finishedAt: EpochMillis): Unit
}

object NoopBattleRoomLifecycleSink extends BattleRoomLifecycleSink {
  /** 中文名：标记战斗已结束（markBattleFinished）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗。 */
  override def markBattleFinished(roomId: RoomId, finishedAt: EpochMillis): Unit = ()
}

trait BattleQueueService extends BattleSessionLookup with BattleRoomLifecycleSink {
  /** 中文名：加入（join）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗。 */
  def join(command: BattleQueueJoinCommand): BattleQueueSnapshot
  /** 中文名：状态（status）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗。 */
  def status(ticketId: TicketId): Either[BattleQueueStatusError, BattleQueueSnapshot]
  /** 中文名：离开（leave）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗。 */
  def leave(ticketId: TicketId): BattleQueueLeaveOutcome
  /** 中文名：房间快照（roomSnapshot）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗。 */
  def roomSnapshot(roomId: RoomId): Either[BattleRoomError, RealtimeRoomSnapshot]
  /** 中文名：心跳（heartbeat）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗。 */
  def heartbeat(request: RealtimeRoomHeartbeatCommand): Either[BattleRoomError, RealtimeRoomSnapshot]
}

final case class BattleQueueJoinCommand(
  handle: PlayerHandle,
  sessionToken: SessionToken,
  queueRequestId: Option[QueueRequestId],
  rating: Option[Rating],
  avatar: Option[String],
  skin: Option[String]
)

final case class RealtimeRoomHeartbeatCommand(
  roomId: Option[RoomId],
  ticketId: Option[TicketId],
  handle: Option[PlayerHandle]
)
