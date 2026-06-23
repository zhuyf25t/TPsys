package services.battle.microservices.queue.api.queue

import cats.effect.IO

import services.battle.microservices.queue.api.shared.BattleQueueAPIMessageErrors
import services.battle.microservices.queue.objects.queue.{BattleQueueJoinCommand, BattleQueueSnapshot}
import services.battle.objects.BattleMode
import services.identity.objects.{PlayerHandle, SessionToken}
import system.api.APIMessageError

object BattleQueueJoinAPIPlanner {
  def plan(
    context: BattleQueueJoinAPIContext,
    message: BattleQueueJoinAPIMessage
  ): IO[BattleQueueSnapshot] =
    for
      command <- toCommand(message)
      _ <- context.authorizationService
        .authorize(command)
        .flatMap(BattleQueueAPIMessageErrors.joinAuthorization)
      snapshot <- context.queueService.join(command)
    yield snapshot

  private def toCommand(message: BattleQueueJoinAPIMessage): IO[BattleQueueJoinCommand] =
    for
      handle <- requiredHandle(message.handle)
      sessionToken <- requiredSessionToken(message.sessionToken)
    yield BattleQueueJoinCommand(
      handle = handle,
      sessionToken = sessionToken,
      battleMode = message.modeId.getOrElse(BattleMode.default),
      queueRequestId = message.queueRequestId,
      rating = message.rating,
      avatar = message.avatar,
      skin = message.skin
    )

  private def requiredHandle(handle: Option[PlayerHandle]): IO[PlayerHandle] =
    IO.fromOption(handle)(APIMessageError.BadRequest("Handle must be a playable non-visitor handle."))

  private def requiredSessionToken(sessionToken: Option[SessionToken]): IO[SessionToken] =
    IO.fromOption(sessionToken)(APIMessageError.Unauthorized("Session token is required."))
}
