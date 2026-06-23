package services.replay.api

import cats.effect.IO

import services.replay.services.ReplayService

object ReplayReadAPIPlanner {
  def planCatalog(service: ReplayService, message: ReplayCatalogAPIMessage): IO[ReplayCatalogResponse] =
    for
      records <- service.list(ReplayReadAPIParser.listLimit(message.limit).value)
    yield ReplayCatalogResponse.fromRecords(records, ReplayReadAPIParser.selectedHandle(message))

  def planDetail(service: ReplayService, message: ReplayDetailAPIMessage): IO[ReplayDetailResponse] =
    for
      replayId <- IO.fromEither(
        message.replayId.toRight(ReplayRecordDecodeError.InvalidReplayId).left.map(ReplayAPIMessageErrors.recordDecode)
      )
      record <- service.load(replayId).flatMap(ReplayAPIMessageErrors.replayLoad)
    yield ReplayDetailResponse(
      ReplayDetailRecordResponse.fromRecord(record, ReplayReadAPIParser.selectedHandle(message))
    )

  def planComments(service: ReplayService, message: ReplayCommentsAPIMessage): IO[ReplayCommentsResponse] =
    for
      replayId <- IO.fromEither(
        message.replayId.toRight(ReplayRecordDecodeError.InvalidReplayId).left.map(ReplayAPIMessageErrors.recordDecode)
      )
      _ <- service.load(replayId).flatMap(ReplayAPIMessageErrors.replayLoad)
      records <- service.listComments(replayId, ReplayReadAPIParser.listLimit(message.limit).value)
    yield ReplayCommentsResponse(records.map(ReplayCommentResponse.fromRecord))
}
