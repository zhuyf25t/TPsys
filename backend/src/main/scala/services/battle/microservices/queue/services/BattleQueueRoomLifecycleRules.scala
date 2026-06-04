package services.battle.microservices.queue.services

import cats.effect.IO

import services.battle.microservices.queue.objects.queue.*

import services.battle.objects.BattleMode
import services.battle.objects.core.{BattleId, DurationMillis, EpochMillis, RoomId}

private[battle] object BattleQueueRoomLifecycleRules {
  /** 中文名：newwaiting房间（newWaitingRoom）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗。 */
  def newWaitingRoom(
    roomId: RoomId,
    battleMode: BattleMode,
    now: EpochMillis,
    matchmakingDuration: DurationMillis,
    capacity: BattleCapacity
  ): IO[QueueRoom] =
    IO.pure(QueueRoom(
      roomId = roomId,
      battleMode = battleMode,
      createdAt = now,
      startsAt = EpochMillis(now.value + matchmakingDuration.value),
      deadline = EpochMillis(now.value + matchmakingDuration.value),
      participants = Vector.empty,
      capacity = capacity,
      durationMs = matchmakingDuration,
      lifecycle = QueueRoomLifecycle.Waiting
    ))

  /** 中文名：开始决策（startDecision）。游戏职责：用 ADT 表达等待房间是否应该进入战斗，避免服务层用 Boolean/if-else 模拟房间状态机。 */
  def startDecision(room: QueueRoom, now: EpochMillis): IO[QueueRoomStartDecision] =
    IO.pure(room.lifecycle match {
      case QueueRoomLifecycle.Waiting if room.participants.nonEmpty && now.value >= room.deadline.value =>
        QueueRoomStartDecision.Start
      case _ =>
        QueueRoomStartDecision.Keep
    })

  /** 中文名：start房间（startRoom）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗。 */
  def startRoom(room: QueueRoom, battleId: BattleId, now: EpochMillis): IO[QueueRoom] =
    for
      participants <- IO.pure(room.participants.map(entry => BattleRoomBootstrapParticipant(entry.playerId, entry.participant)))
      session <- BattleRoomBootstrapper.createSession(
        battleId = battleId,
        battleMode = room.battleMode,
        startsAt = room.startsAt,
        now = now,
        capacity = room.capacity,
        participants = participants
      )
    yield room.copy(lifecycle = QueueRoomLifecycle.Active(session))

  /** 中文名：标记已结束（markFinished）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗。 */
  def markFinished(
    rooms: Map[RoomId, QueueRoom],
    roomId: RoomId,
    finishedAt: EpochMillis
  ): IO[Map[RoomId, QueueRoom]] =
    rooms.get(roomId) match {
      case None =>
        IO.pure(rooms)
      case Some(room) =>
        room.markFinished(finishedAt).map(updatedRoom => rooms.updated(roomId, updatedRoom))
    }
}
