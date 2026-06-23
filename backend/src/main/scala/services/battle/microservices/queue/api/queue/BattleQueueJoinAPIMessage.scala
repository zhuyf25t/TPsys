package services.battle.microservices.queue.api.queue

import cats.effect.IO
import io.circe.{Decoder, Error}
import io.circe.generic.semiauto.deriveDecoder

import java.sql.Connection

import services.battle.objects.BattleMode
import services.battle.microservices.actors.objects.player.{BattleAvatarKey, BattleSkinKey, Rating}
import services.battle.microservices.queue.services.{
  BattleQueueJoinAuthorizationService,
  BattleQueueService
}
import services.battle.microservices.queue.api.shared.BattleQueueAPIMessageErrors
import services.battle.microservices.queue.objects.queue.{
  BattleQueueSnapshot,
  QueueRequestId
}
import services.identity.objects.{PlayerHandle, SessionToken}
import system.api.{APIMessageError, APIWithTokenContextMessage}
import system.objects.UserId

final case class BattleQueueJoinAPIContext(
  queueService: BattleQueueService,
  authorizationService: BattleQueueJoinAuthorizationService
)

final case class BattleQueueJoinAPIMessage(
  userId: UserId,
  handle: Option[PlayerHandle],
  sessionToken: Option[SessionToken],
  modeId: Option[BattleMode],
  queueRequestId: Option[QueueRequestId],
  rating: Option[Rating],
  avatar: Option[BattleAvatarKey],
  skin: Option[BattleSkinKey]
) extends APIWithTokenContextMessage[BattleQueueJoinAPIContext, BattleQueueSnapshot] {
  override def plan(context: BattleQueueJoinAPIContext, connection: Connection): IO[BattleQueueSnapshot] =
    BattleQueueJoinAPIPlanner.plan(context, this)
}

object BattleQueueJoinAPIMessage {
  import services.battle.microservices.queue.api.shared.BattleQueueAPIMessageDecoding.given

  given Decoder[BattleQueueJoinAPIMessage] =
    deriveDecoder[BattleQueueJoinAPIMessage]

  private[battle] def requestDecodeFailure(error: Error): APIMessageError =
    BattleQueueAPIMessageErrors.joinDecodeFailure(error)
}
