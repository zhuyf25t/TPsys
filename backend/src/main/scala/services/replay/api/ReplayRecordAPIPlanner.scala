package services.replay.api

import cats.effect.IO

import services.replay.services.ReplayService

object ReplayRecordAPIPlanner {
  def plan(service: ReplayService, message: ReplayRecordAPIMessage): IO[ReplayDetailResponse] =
    for {
      command <- IO.fromEither(
        ReplayCommandParsers.parseRecordCommand(message).left.map(ReplayAPIMessageErrors.recordDecode)
      )
      record <- service.record(command).flatMap(ReplayAPIMessageErrors.recordService)
    } yield ReplayDetailResponse(ReplayDetailRecordResponse.fromRecord(record, None))
}
