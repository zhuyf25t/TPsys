package services.replay.api

import cats.effect.IO

import services.replay.services.ReplayService

object ReplayCommentCreateAPIPlanner {
  def plan(service: ReplayService, message: ReplayCommentCreateAPIMessage): IO[ReplayCommentWrapperResponse] =
    for
      command <- IO.fromEither(
        ReplayCommandParsers.parseCommentCommand(message).left.map(ReplayAPIMessageErrors.commentDecode)
      )
      _ <- service.load(command.replayId).flatMap(ReplayAPIMessageErrors.replayLoad)
      comment <- service.addComment(command).flatMap(ReplayAPIMessageErrors.commentService)
    yield ReplayCommentWrapperResponse(ReplayCommentResponse.fromRecord(comment))
}
