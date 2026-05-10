package slaydemo.backend.battle.services

import slaydemo.backend.battle.objects.*

private[services] object BattleQueueRoomLifecycleRules {
  def newWaitingRoom(
    roomId: RoomId,
    now: EpochMillis,
    matchmakingDuration: DurationMillis,
    capacity: BattleCapacity
  ): QueueRoom =
    QueueRoom(
      roomId = roomId,
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

  def shouldStart(room: QueueRoom, now: EpochMillis): Boolean =
    room.phase == MatchmakingRoomPhase.Waiting &&
      room.participants.nonEmpty &&
      now.value >= room.deadline.value

  def startRoom(room: QueueRoom, battleId: BattleId, now: EpochMillis): QueueRoom = {
    val session = BattleRoomBootstrapper.createSession(
      battleId = battleId,
      startsAt = room.startsAt,
      now = now,
      capacity = room.capacity,
      participants = room.participants.map(entry => BattleRoomBootstrapParticipant(entry.playerId, entry.participant))
    )

    room.copy(phase = MatchmakingRoomPhase.Active, battleSession = Some(session))
  }

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
