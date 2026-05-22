package services.replay.database

import io.circe.{Decoder, Encoder, HCursor, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.parser.parse as parseJson

import services.battle.objects.{BattleId, BattlePlacement, BattleSurvivalOutcome, DurationMillis, EpochMillis, Rating, RatingDelta, Score}
import services.identity.objects.{DisplayName, PlayerHandle}
import services.replay.objects.{
  ReplayCommentId,
  ReplayCommentRecord,
  ReplayFrameCount,
  ReplayFramesJson,
  ReplayId,
  ReplayPlaybackAvailability,
  ReplayRecord,
  ReplaySettlementRecord,
  ReplayTitle
}
import services.replay.support.ReplayFramesJsonCodec

private[database] final case class ReplayFilePayload(
  records: Vector[ReplayRecord],
  comments: Vector[ReplayCommentRecord],
  settlementsByReplay: Map[ReplayId, Vector[ReplaySettlementRecord]]
)

private[database] object ReplayFilePayload {
  val empty: ReplayFilePayload =
    ReplayFilePayload(records = Vector.empty, comments = Vector.empty, settlementsByReplay = Map.empty)
}

private[database] object ReplayFileJsonParser {
  def parse(raw: String): ReplayFilePayload =
    parseJson(raw)
      .toOption
      .flatMap(_.as[ReplayFileJsonPayload].toOption)
      .map(_.toDomain)
      .getOrElse(ReplayFilePayload.empty)
}

private[database] final case class ReplayFileJsonPayload(
  schema: String,
  records: Vector[ReplayFileReplayJson],
  comments: Vector[ReplayFileCommentJson],
  settlements: Vector[ReplayFileSettlementJson]
) {
  def toDomain: ReplayFilePayload = {
    val settlementsByReplay =
      settlements.map(settlement => settlement.replayId -> settlement.toDomain).groupMap(_._1)(_._2)

    ReplayFilePayload(
      records = records.map(_.toDomain),
      comments = comments.map(_.toDomain),
      settlementsByReplay = settlementsByReplay
    )
  }
}

private[database] object ReplayFileJsonPayload {
  private val Schema = "slay-demo.replay-catalog.v2"

  given Encoder[ReplayFileJsonPayload] = deriveEncoder

  given Decoder[ReplayFileJsonPayload] = Decoder.instance { cursor =>
    for
      schema <- cursor.get[Option[String]]("schema").orElse(Right(Some(Schema)))
      records <- decodeVector[ReplayFileReplayJson](cursor, "records")
      comments <- decodeVector[ReplayFileCommentJson](cursor, "comments")
      settlements <- decodeVector[ReplayFileSettlementJson](cursor, "settlements")
    yield ReplayFileJsonPayload(
      schema = schema.getOrElse(Schema),
      records = records,
      comments = comments,
      settlements = settlements
    )
  }

  def fromDomain(
    records: Vector[ReplayRecord],
    comments: Vector[ReplayCommentRecord],
    settlements: Vector[(ReplayId, ReplaySettlementRecord)]
  ): ReplayFileJsonPayload =
    ReplayFileJsonPayload(
      schema = Schema,
      records = records.map(ReplayFileReplayJson.fromDomain),
      comments = comments.map(ReplayFileCommentJson.fromDomain),
      settlements = settlements.map(ReplayFileSettlementJson.fromDomain)
    )

  private def decodeVector[A: Decoder](cursor: HCursor, field: String): Decoder.Result[Vector[A]] =
    cursor
      .get[Option[Vector[Json]]](field)
      .map(_.getOrElse(Vector.empty).flatMap(_.as[A].toOption))
      .orElse(Right(Vector.empty))
}

private[database] final case class ReplayFileReplayJson(
  replayId: String,
  battleId: String,
  handle: String,
  displayName: String,
  finishedAt: Long,
  finishedAtLabel: String,
  title: String,
  modeLabel: String,
  resultLabel: String,
  mapLabel: String,
  highlightLine: String,
  coverLabel: String,
  playersLine: String,
  timelineHint: String,
  score: Int,
  placement: Option[Int],
  ratingBefore: Option[Int],
  ratingDelta: Option[Int],
  ratingAfter: Option[Int],
  durationMs: Long,
  aliveAtEnd: Boolean,
  thumbnailDataUrl: Option[String],
  currentLoadout: Option[String],
  frameCount: Int,
  playbackAvailable: Boolean,
  framesJsonB64: String
) {
  def toDomain: ReplayRecord =
    ReplayRecord(
      replayId = ReplayId(replayId),
      battleId = BattleId(battleId),
      handle = PlayerHandle(handle),
      displayName = DisplayName(displayName),
      finishedAt = EpochMillis(finishedAt),
      finishedAtLabel = finishedAtLabel,
      title = ReplayTitle.fromWire(title),
      modeLabel = modeLabel,
      resultLabel = resultLabel,
      mapLabel = mapLabel,
      highlightLine = highlightLine,
      coverLabel = coverLabel,
      playersLine = playersLine,
      timelineHint = timelineHint,
      score = Score(score),
      placement = placement.flatMap(BattlePlacement.fromWire),
      ratingBefore = ratingBefore.map(Rating.apply),
      ratingDelta = ratingDelta.map(RatingDelta.apply),
      ratingAfter = ratingAfter.map(Rating.apply),
      durationMs = DurationMillis(durationMs),
      survivalOutcome = BattleSurvivalOutcome.fromAliveAtEnd(aliveAtEnd),
      thumbnailDataUrl = thumbnailDataUrl.flatMap(nonEmptyText),
      currentLoadout = currentLoadout.flatMap(nonEmptyText),
      frameCount = ReplayFrameCount.fromWire(frameCount),
      playbackAvailability = ReplayPlaybackAvailability.fromAvailableFlag(playbackAvailable),
      framesJson = ReplayFramesJson.fromNormalized(ReplayFramesJsonCodec.decode(framesJsonB64))
    )

  private def nonEmptyText(value: String): Option[String] =
    Option(value).map(_.trim).filter(_.nonEmpty)
}

private[database] object ReplayFileReplayJson {
  given Encoder[ReplayFileReplayJson] = deriveEncoder
  given Decoder[ReplayFileReplayJson] = deriveDecoder

  def fromDomain(record: ReplayRecord): ReplayFileReplayJson =
    ReplayFileReplayJson(
      replayId = record.replayId.value,
      battleId = record.battleId.value,
      handle = record.handle.value,
      displayName = record.displayName.value,
      finishedAt = record.finishedAt.value,
      finishedAtLabel = record.finishedAtLabel,
      title = record.title.value,
      modeLabel = record.modeLabel,
      resultLabel = record.resultLabel,
      mapLabel = record.mapLabel,
      highlightLine = record.highlightLine,
      coverLabel = record.coverLabel,
      playersLine = record.playersLine,
      timelineHint = record.timelineHint,
      score = record.score.value,
      placement = record.placement.map(_.value),
      ratingBefore = record.ratingBefore.map(_.value),
      ratingDelta = record.ratingDelta.map(_.value),
      ratingAfter = record.ratingAfter.map(_.value),
      durationMs = record.durationMs.value,
      aliveAtEnd = record.aliveAtEnd,
      thumbnailDataUrl = record.thumbnailDataUrl,
      currentLoadout = record.currentLoadout,
      frameCount = record.frameCount.value,
      playbackAvailable = record.playbackAvailable,
      framesJsonB64 = ReplayFramesJsonCodec.encode(record.framesJson.value)
    )
}

private[database] final case class ReplayFileCommentJson(
  id: String,
  replayId: String,
  authorHandle: String,
  body: String,
  createdAt: Long
) {
  def toDomain: ReplayCommentRecord =
    ReplayCommentRecord(
      id = ReplayCommentId(id),
      replayId = ReplayId(replayId),
      authorHandle = PlayerHandle(authorHandle),
      body = body,
      createdAt = EpochMillis(createdAt)
    )
}

private[database] object ReplayFileCommentJson {
  given Encoder[ReplayFileCommentJson] = deriveEncoder
  given Decoder[ReplayFileCommentJson] = deriveDecoder

  def fromDomain(record: ReplayCommentRecord): ReplayFileCommentJson =
    ReplayFileCommentJson(
      id = record.id.value,
      replayId = record.replayId.value,
      authorHandle = record.authorHandle.value,
      body = record.body,
      createdAt = record.createdAt.value
    )
}

private[database] final case class ReplayFileSettlementJson(
  replayId: ReplayId,
  handle: String,
  displayName: String,
  resultLabel: String,
  highlightLine: String,
  score: Int,
  placement: Option[Int],
  ratingBefore: Option[Int],
  ratingDelta: Option[Int],
  ratingAfter: Option[Int],
  aliveAtEnd: Boolean,
  currentLoadout: Option[String]
) {
  def toDomain: ReplaySettlementRecord =
    ReplaySettlementRecord(
      handle = PlayerHandle(handle),
      displayName = DisplayName(displayName),
      resultLabel = resultLabel,
      highlightLine = highlightLine,
      score = Score(score),
      placement = placement.flatMap(BattlePlacement.fromWire),
      ratingBefore = ratingBefore.map(Rating.apply),
      ratingDelta = ratingDelta.map(RatingDelta.apply),
      ratingAfter = ratingAfter.map(Rating.apply),
      survivalOutcome = BattleSurvivalOutcome.fromAliveAtEnd(aliveAtEnd),
      currentLoadout = currentLoadout.flatMap(nonEmptyText)
    )

  private def nonEmptyText(value: String): Option[String] =
    Option(value).map(_.trim).filter(_.nonEmpty)
}

private[database] object ReplayFileSettlementJson {
  given Encoder[ReplayFileSettlementJson] =
    Encoder
      .forProduct12(
        "replayId",
        "handle",
        "displayName",
        "resultLabel",
        "highlightLine",
        "score",
        "placement",
        "ratingBefore",
        "ratingDelta",
        "ratingAfter",
        "aliveAtEnd",
        "currentLoadout"
      )((value: ReplayFileSettlementJson) =>
        (
          value.replayId.value,
          value.handle,
          value.displayName,
          value.resultLabel,
          value.highlightLine,
          value.score,
          value.placement,
          value.ratingBefore,
          value.ratingDelta,
          value.ratingAfter,
          value.aliveAtEnd,
          value.currentLoadout
        )
      )

  given Decoder[ReplayFileSettlementJson] =
    Decoder.instance { cursor =>
      for
        replayId <- cursor.get[String]("replayId")
        handle <- cursor.get[String]("handle")
        displayName <- cursor.get[String]("displayName")
        resultLabel <- cursor.get[String]("resultLabel")
        highlightLine <- cursor.get[String]("highlightLine")
        score <- cursor.get[Int]("score")
        placement <- cursor.get[Option[Int]]("placement")
        ratingBefore <- cursor.get[Option[Int]]("ratingBefore")
        ratingDelta <- cursor.get[Option[Int]]("ratingDelta")
        ratingAfter <- cursor.get[Option[Int]]("ratingAfter")
        aliveAtEnd <- cursor.get[Boolean]("aliveAtEnd")
        currentLoadout <- cursor.get[Option[String]]("currentLoadout")
      yield ReplayFileSettlementJson(
        replayId = ReplayId(replayId),
        handle = handle,
        displayName = displayName,
        resultLabel = resultLabel,
        highlightLine = highlightLine,
        score = score,
        placement = placement,
        ratingBefore = ratingBefore,
        ratingDelta = ratingDelta,
        ratingAfter = ratingAfter,
        aliveAtEnd = aliveAtEnd,
        currentLoadout = currentLoadout
      )
    }

  def fromDomain(item: (ReplayId, ReplaySettlementRecord)): ReplayFileSettlementJson = {
    val (replayId, settlement) = item
    ReplayFileSettlementJson(
      replayId = replayId,
      handle = settlement.handle.value,
      displayName = settlement.displayName.value,
      resultLabel = settlement.resultLabel,
      highlightLine = settlement.highlightLine,
      score = settlement.score.value,
      placement = settlement.placement.map(_.value),
      ratingBefore = settlement.ratingBefore.map(_.value),
      ratingDelta = settlement.ratingDelta.map(_.value),
      ratingAfter = settlement.ratingAfter.map(_.value),
      aliveAtEnd = settlement.aliveAtEnd,
      currentLoadout = settlement.currentLoadout
    )
  }
}
