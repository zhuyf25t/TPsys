package services.battle.microservices.queue.services

import cats.effect.IO
import cats.syntax.all.*

import services.battle.microservices.queue.objects.queue.*

import services.battle.objects.BattleMode
import services.identity.objects.PlayerHandle

private[battle] object BattleQueueRoomSelectionRules {
  /** 中文名：openwaitingrooms（openWaitingRooms）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗。 */
  def openWaitingRooms(rooms: Iterable[QueueRoom]): IO[Vector[QueueRoom]] =
    rooms.toVector
      .filterA(room => room.isWaiting.map(_ && room.participants.length < room.capacity.value))
      .map(_.sortBy(_.createdAt.value))

  /** 中文名：reusable房间（reusableRoom）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗。 */
  def reusableRoom(
    openRooms: Vector[QueueRoom],
    handle: PlayerHandle,
    battleMode: BattleMode,
    queueRequestId: Option[QueueRequestId]
  ): IO[Option[QueueRoom]] =
    val modeRooms = openRooms.filter(_.battleMode == battleMode)
    shouldStartFreshRoom(modeRooms, handle, queueRequestId).map { startFresh =>
      if startFresh then None else modeRooms.headOption
    }

  private def shouldStartFreshRoom(
    openRooms: Vector[QueueRoom],
    handle: PlayerHandle,
    queueRequestId: Option[QueueRequestId]
  ): IO[Boolean] =
    queueRequestId match {
      case None =>
        IO.pure(false)
      case Some(_) =>
        openRooms.existsM { room =>
          room.participants.existsM(entry => BattleQueueParticipantRules.sameHandleKey(entry.participant.handle, handle.key))
        }
    }
}
