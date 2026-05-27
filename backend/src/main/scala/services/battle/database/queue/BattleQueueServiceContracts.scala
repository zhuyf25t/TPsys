package services.battle.database.queue

import services.battle.database.session.{BattleRoomLifecycleSink, BattleSessionLookup}
import services.battle.objects.{BattleQueueJoinCommand, BattleQueueLeaveOutcome, RealtimeRoomHeartbeatCommand}
import services.battle.objects.core.{RoomId, TicketId}
import services.battle.objects.queue.{BattleQueueSnapshot, RealtimeRoomSnapshot}

enum BattleQueueStatusError {
  case TicketNotFound
}

enum BattleRoomError {
  case MissingRoomId
  case RoomNotFound
}

trait BattleQueueService extends BattleSessionLookup with BattleRoomLifecycleSink {
  /** 中文名：加入排队（join）。游戏职责：接收玩家进入匹配队列，并返回等待房间/战斗会话快照。 */
  def join(command: BattleQueueJoinCommand): BattleQueueSnapshot

  /** 中文名：查询排队状态（status）。游戏职责：根据 ticketId 读取玩家当前匹配、房间和即将开始的战斗信息。 */
  def status(ticketId: TicketId): Either[BattleQueueStatusError, BattleQueueSnapshot]

  /** 中文名：离开排队（leave）。游戏职责：玩家主动退出匹配队列或等待房间。 */
  def leave(ticketId: TicketId): BattleQueueLeaveOutcome

  /** 中文名：房间快照（roomSnapshot）。游戏职责：读取等待房间内参与者、容量、模式和会话启动信息。 */
  def roomSnapshot(roomId: RoomId): Either[BattleRoomError, RealtimeRoomSnapshot]

  /** 中文名：房间心跳（heartbeat）。游戏职责：刷新等待房间参与者在线时间，并返回最新房间快照。 */
  def heartbeat(request: RealtimeRoomHeartbeatCommand): Either[BattleRoomError, RealtimeRoomSnapshot]
}
