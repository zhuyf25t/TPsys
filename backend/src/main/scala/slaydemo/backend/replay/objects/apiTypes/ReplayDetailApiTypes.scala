package slaydemo.backend.replay.objects.apiTypes

import io.circe.parser.parse
import io.circe.syntax.*
import io.circe.{Encoder, Json}

import slaydemo.backend.identity.objects.PlayerHandle
import slaydemo.backend.replay.objects.{ReplayCommentRecord, ReplayRecord, ReplaySettlementRecord}

final case class ReplayDetailRecordResponse(
  replayId: String,
  battleId: String,
  title: String,
  modeLabel: String,
  resultLabel: String,
  finishedAt: Long,
  finishedAtLabel: String,
  mapLabel: String,
  highlightLine: String,
  coverLabel: String,
  playersLine: String,
  timelineHint: String,
  score: Int,
  placement: Option[Int],
  ratingBefore: Option[Int],
  ratingAfter: Option[Int],
  ratingDelta: Option[Int],
  durationMs: Long,
  aliveAtEnd: Boolean,
  thumbnailDataUrl: Option[String],
  frameCount: Int,
  playbackAvailable: Boolean,
  handle: String,
  displayName: String,
  currentLoadout: Option[String],
  frames: Json
)

object ReplayDetailRecordResponse {
  given Encoder[ReplayDetailRecordResponse] =
    Encoder.instance { value =>
      Json.obj(
        "replayId" -> Json.fromString(value.replayId),
        "battleId" -> Json.fromString(value.battleId),
        "title" -> Json.fromString(value.title),
        "modeLabel" -> Json.fromString(value.modeLabel),
        "resultLabel" -> Json.fromString(value.resultLabel),
        "finishedAt" -> Json.fromLong(value.finishedAt),
        "finishedAtLabel" -> Json.fromString(value.finishedAtLabel),
        "mapLabel" -> Json.fromString(value.mapLabel),
        "highlightLine" -> Json.fromString(value.highlightLine),
        "coverLabel" -> Json.fromString(value.coverLabel),
        "playersLine" -> Json.fromString(value.playersLine),
        "timelineHint" -> Json.fromString(value.timelineHint),
        "score" -> Json.fromInt(value.score),
        "placement" -> value.placement.asJson,
        "ratingBefore" -> value.ratingBefore.asJson,
        "ratingAfter" -> value.ratingAfter.asJson,
        "ratingDelta" -> value.ratingDelta.asJson,
        "durationMs" -> Json.fromLong(value.durationMs),
        "aliveAtEnd" -> Json.fromBoolean(value.aliveAtEnd),
        "thumbnailDataUrl" -> value.thumbnailDataUrl.asJson,
        "frameCount" -> Json.fromInt(value.frameCount),
        "playbackAvailable" -> Json.fromBoolean(value.playbackAvailable),
        "handle" -> Json.fromString(value.handle),
        "displayName" -> Json.fromString(value.displayName),
        "currentLoadout" -> value.currentLoadout.asJson,
        "frames" -> value.frames
      )
    }

  def fromRecord(record: ReplayRecord, selectedHandle: Option[PlayerHandle]): ReplayDetailRecordResponse = {
    val settlement = selectedSettlement(record, selectedHandle)
    val resultLabel = settlement.map(_.resultLabel).getOrElse(record.resultLabel)

    ReplayDetailRecordResponse(
      replayId = record.replayId.value,
      battleId = record.battleId.value,
      title = settlement.map(item => s"${item.resultLabel} - ${record.finishedAtLabel}").getOrElse(record.title.value),
      modeLabel = record.modeLabel,
      resultLabel = resultLabel,
      finishedAt = record.finishedAt.value,
      finishedAtLabel = record.finishedAtLabel,
      mapLabel = record.mapLabel,
      highlightLine = settlement.map(_.highlightLine).getOrElse(record.highlightLine),
      coverLabel = record.coverLabel,
      playersLine = record.playersLine,
      timelineHint = record.timelineHint,
      score = settlement.map(_.score.value).getOrElse(record.score.value),
      placement = settlement.map(_.placement).getOrElse(record.placement).map(_.value),
      ratingBefore = settlement.map(_.ratingBefore).getOrElse(record.ratingBefore).map(_.value),
      ratingAfter = settlement.map(_.ratingAfter).getOrElse(record.ratingAfter).map(_.value),
      ratingDelta = settlement.map(_.ratingDelta).getOrElse(record.ratingDelta).map(_.value),
      durationMs = record.durationMs.value,
      aliveAtEnd = settlement.map(_.aliveAtEnd).getOrElse(record.aliveAtEnd),
      thumbnailDataUrl = record.thumbnailDataUrl.filter(_.trim.nonEmpty),
      frameCount = record.frameCount.value,
      playbackAvailable = record.playbackAvailable,
      handle = settlement.map(_.handle.value).getOrElse(record.handle.value),
      displayName = settlement.map(_.displayName.value).getOrElse(record.displayName.value),
      currentLoadout = settlement.flatMap(_.currentLoadout).orElse(record.currentLoadout),
      frames = parse(record.framesJson.value).getOrElse(Json.arr())
    )
  }

  private def selectedSettlement(record: ReplayRecord, selectedHandle: Option[PlayerHandle]): Option[ReplaySettlementRecord] =
    selectedHandle.flatMap(record.settlementFor)
}

final case class ReplayDetailResponse(replay: ReplayDetailRecordResponse)

object ReplayDetailResponse {
  given Encoder[ReplayDetailResponse] =
    Encoder.forProduct1("replay")(_.replay)
}

final case class ReplayCommentResponse(
  id: String,
  replayId: String,
  authorHandle: String,
  body: String,
  createdAt: Long
)

object ReplayCommentResponse {
  given Encoder[ReplayCommentResponse] =
    Encoder.forProduct5("id", "replayId", "authorHandle", "body", "createdAt")(value =>
      (value.id, value.replayId, value.authorHandle, value.body, value.createdAt)
    )

  def fromRecord(record: ReplayCommentRecord): ReplayCommentResponse =
    ReplayCommentResponse(
      id = record.id.value,
      replayId = record.replayId.value,
      authorHandle = record.authorHandle.value,
      body = record.body,
      createdAt = record.createdAt.value
    )
}

final case class ReplayCommentsResponse(comments: Vector[ReplayCommentResponse])

object ReplayCommentsResponse {
  given Encoder[ReplayCommentsResponse] =
    Encoder.forProduct1("comments")(_.comments)
}

final case class ReplayCommentWrapperResponse(comment: ReplayCommentResponse)

object ReplayCommentWrapperResponse {
  given Encoder[ReplayCommentWrapperResponse] =
    Encoder.forProduct1("comment")(_.comment)
}
