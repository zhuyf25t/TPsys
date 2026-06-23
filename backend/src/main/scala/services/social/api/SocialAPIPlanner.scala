package services.social.api

import cats.effect.IO

import services.social.services.FriendRequestService

object SocialAPIPlanner {
  def planCreate(service: FriendRequestService, message: FriendRequestCreateAPIMessage): IO[FriendRequestCreateResponse] =
    for
      command <- IO.fromEither(
        SocialCommandParsers.parseCreateHandles(message).left.map(SocialAPIMessageErrors.createRoute)
      )
      result <- service.create(command.sourceHandle, command.targetHandle).flatMap(SocialAPIMessageErrors.createService)
    yield FriendRequestCreateResponse.fromResult(result)

  def planList(service: FriendRequestService, message: FriendRequestListAPIMessage): IO[FriendRequestListResponse] =
    for
      owner <- IO.fromEither(
        FriendRequestOwnerQuery.parse(message.ownerHandle).left.map(SocialAPIMessageErrors.owner)
      )
      records <- service.list(owner)
    yield FriendRequestListResponse.fromRecords(records)

  def planRespond(service: FriendRequestService, message: FriendRequestRespondAPIMessage): IO[FriendRequestRespondResponse] =
    for
      command <- IO.fromEither(
        SocialCommandParsers.parseRespondCommand(message).left.map(SocialAPIMessageErrors.respondRoute)
      )
      result <- service.respond(command.requestId, command.actorHandle, command.decision).flatMap(SocialAPIMessageErrors.respondService)
    yield FriendRequestRespondResponse.fromResult(result)
}
