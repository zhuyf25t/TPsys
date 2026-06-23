package services.battle.microservices.queue.api.room

import cats.effect.IO
import io.circe.{Decoder, Error}
import io.circe.generic.semiauto.deriveDecoder

import java.sql.Connection

import services.battle.microservices.queue.services.BattleQueueService
import services.battle.microservices.queue.api.shared.BattleQueueAPIMessageErrors
import services.battle.microservices.queue.objects.queue.{
  RealtimeRoomSnapshot
}
import services.battle.objects.core.RoomId
import system.api.{APIMessageError, APIWithTokenContextMessage}
import system.objects.UserId

final case class BattleRoomSnapshotAPIMessage(
  userId: UserId,
  roomId: Option[RoomId]
) extends APIWithTokenContextMessage[BattleQueueService, RealtimeRoomSnapshot] {
  override def plan(queueService: BattleQueueService, connection: Connection): IO[RealtimeRoomSnapshot] =
    BattleRoomSnapshotAPIPlanner.plan(queueService, this)
}

object BattleRoomSnapshotAPIMessage {
  import services.battle.microservices.queue.api.shared.BattleQueueAPIMessageDecoding.given

  given Decoder[BattleRoomSnapshotAPIMessage] =
    deriveDecoder[BattleRoomSnapshotAPIMessage]

  private[battle] def requestDecodeFailure(error: Error): APIMessageError =
    BattleQueueAPIMessageErrors.roomSnapshotDecodeFailure(error)
}
