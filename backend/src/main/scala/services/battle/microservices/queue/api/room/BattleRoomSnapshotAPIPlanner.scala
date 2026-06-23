package services.battle.microservices.queue.api.room

import cats.effect.IO

import services.battle.microservices.queue.api.shared.BattleQueueAPIMessageErrors
import services.battle.microservices.queue.objects.queue.RealtimeRoomSnapshot
import services.battle.microservices.queue.services.BattleQueueService
import services.battle.objects.core.RoomId
import system.api.APIMessageError

object BattleRoomSnapshotAPIPlanner {
  def plan(queueService: BattleQueueService, message: BattleRoomSnapshotAPIMessage): IO[RealtimeRoomSnapshot] =
    for
      roomId <- requiredRoomId(message.roomId)
      result <- queueService.roomSnapshot(roomId)
      snapshot <- BattleQueueAPIMessageErrors.room(result)
    yield snapshot

  private def requiredRoomId(roomId: Option[RoomId]): IO[RoomId] =
    IO.fromOption(roomId)(APIMessageError.BadRequest("roomId is required."))
}
