package services.battle.microservices.queue.api.room

import cats.effect.IO

import services.battle.microservices.queue.api.shared.BattleQueueAPIMessageErrors
import services.battle.microservices.queue.objects.queue.{
  BattleRoomStartGateAction,
  RealtimeRoomHeartbeatCommand,
  RealtimeRoomSnapshot
}
import services.battle.microservices.queue.services.BattleQueueService

object BattleRoomHeartbeatAPIPlanner {
  def plan(queueService: BattleQueueService, message: BattleRoomHeartbeatAPIMessage): IO[RealtimeRoomSnapshot] =
    for
      result <- queueService.heartbeat(toCommand(message))
      snapshot <- BattleQueueAPIMessageErrors.room(result)
    yield snapshot

  private def toCommand(message: BattleRoomHeartbeatAPIMessage): RealtimeRoomHeartbeatCommand =
    RealtimeRoomHeartbeatCommand(
      roomId = message.roomId,
      ticketId = message.ticketId,
      handle = message.handle,
      startGateAction = BattleRoomStartGateAction.fromWire(message.startPaused),
      chatMessage = message.chatMessage
    )
}
