package services.battle.application

import services.battle.application.*

import services.battle.objects.{BattleMode, MatchmakingRoomPhase, QueueRequestId}
import services.identity.objects.PlayerHandle

private[services] object BattleQueueRoomSelectionRules {
  /** 中文名：openwaitingrooms（openWaitingRooms）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗。 */
  def openWaitingRooms(rooms: Iterable[QueueRoom]): Vector[QueueRoom] =
    rooms
      .filter(room => room.phase == MatchmakingRoomPhase.Waiting && room.participants.length < room.capacity.value)
      .toVector
      .sortBy(_.createdAt.value)

  /** 中文名：reusable房间（reusableRoom）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗。 */
  def reusableRoom(
    openRooms: Vector[QueueRoom],
    handle: PlayerHandle,
    battleMode: BattleMode,
    queueRequestId: Option[QueueRequestId]
  ): Option[QueueRoom] =
    val modeRooms = openRooms.filter(_.battleMode == battleMode)
    if shouldStartFreshRoom(modeRooms, handle, queueRequestId) then None
    else modeRooms.headOption

  private def shouldStartFreshRoom(
    openRooms: Vector[QueueRoom],
    handle: PlayerHandle,
    queueRequestId: Option[QueueRequestId]
  ): Boolean =
    queueRequestId.exists(_ =>
      openRooms.exists(room =>
        room.participants.exists(entry => BattleQueueParticipantRules.sameHandleKey(entry.participant.handle, handle.key))
      )
    )
}
