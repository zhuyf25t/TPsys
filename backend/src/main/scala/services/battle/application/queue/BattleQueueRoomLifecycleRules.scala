package services.battle.application

import services.battle.application.*

import services.battle.objects.*

private[services] object BattleQueueRoomLifecycleRules {
  /** 中文名：newwaiting房间（newWaitingRoom）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗。 */
  def newWaitingRoom(
    roomId: RoomId,
    battleMode: BattleMode,
    now: EpochMillis,
    matchmakingDuration: DurationMillis,
    capacity: BattleCapacity
  ): QueueRoom =
    QueueRoom(
      roomId = roomId,
      battleMode = battleMode,
      createdAt = now,
      startsAt = EpochMillis(now.value + matchmakingDuration.value),
      deadline = EpochMillis(now.value + matchmakingDuration.value),
      participants = Vector.empty,
      capacity = capacity,
      durationMs = matchmakingDuration,
      phase = MatchmakingRoomPhase.Waiting,
      finishedAt = None,
      battleSession = None
    )

  /** 中文名：shouldstart（shouldStart）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗。 */
  def shouldStart(room: QueueRoom, now: EpochMillis): Boolean =
    room.phase == MatchmakingRoomPhase.Waiting &&
      room.participants.nonEmpty &&
      now.value >= room.deadline.value

  /** 中文名：start房间（startRoom）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗。 */
  def startRoom(room: QueueRoom, battleId: BattleId, now: EpochMillis): QueueRoom = {
    val session = BattleRoomBootstrapper.createSession(
      battleId = battleId,
      battleMode = room.battleMode,
      startsAt = room.startsAt,
      now = now,
      capacity = room.capacity,
      participants = room.participants.map(entry => BattleRoomBootstrapParticipant(entry.playerId, entry.participant))
    )

    room.copy(phase = MatchmakingRoomPhase.Active, battleSession = Some(session))
  }

  /** 中文名：标记已结束（markFinished）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗。 */
  def markFinished(
    rooms: Map[RoomId, QueueRoom],
    roomId: RoomId,
    finishedAt: EpochMillis
  ): Map[RoomId, QueueRoom] =
    rooms.get(roomId) match {
      case None =>
        rooms
      case Some(room) =>
        rooms.updated(
          roomId,
          room.copy(
            phase = MatchmakingRoomPhase.Finished,
            finishedAt = room.finishedAt.orElse(Some(finishedAt))
          )
        )
    }
}
