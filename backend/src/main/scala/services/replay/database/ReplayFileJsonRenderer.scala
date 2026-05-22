package services.replay.database

import io.circe.syntax.*

import services.replay.objects.{ReplayCommentRecord, ReplayId, ReplayRecord, ReplaySettlementRecord}

private[database] object ReplayFileJsonRenderer {
  def renderPayload(
    records: Vector[ReplayRecord],
    comments: Vector[ReplayCommentRecord],
    settlements: Vector[(ReplayId, ReplaySettlementRecord)]
  ): String =
    ReplayFileJsonPayload.fromDomain(records, comments, settlements).asJson.spaces2 + System.lineSeparator()
}
