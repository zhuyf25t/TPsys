package services.battle.api

import io.circe.{Decoder, Json}
import io.circe.generic.semiauto.deriveDecoder

import services.battle.objects.{
  BattleId,
  BattlePlacement,
  BattleSurvivalOutcome,
  DurationMillis,
  EpochMillis,
  Rating,
  RatingDelta,
  Score,
  BattleResultRecordCommand
}
import services.identity.objects.{DisplayName, PlayerHandle}
import system.policies.HandlePolicy

final case class BattleResultListQuery(
  handle: Option[PlayerHandle],
  battleId: Option[BattleId],
  limit: Int
)

enum BattleResultRecordDecodeError {
  case BadJson
  case InvalidBattleId
  case InvalidHandle
  case VisitorNotAllowed
}

object BattleResultApiCodec {
  def decodeListQuery(payload: Json): BattleResultListQuery =
    payload.as[BattleResultListAPIRequest].getOrElse(BattleResultListAPIRequest()).toQuery

  def decodeRecordCommand(payload: Json): Either[BattleResultRecordDecodeError, BattleResultRecordCommand] =
    payload.as[BattleResultRecordAPIRequest].left.map(_ => BattleResultRecordDecodeError.BadJson).flatMap(_.toCommand)

  private[api] def parseSubmissionHandle(value: String): Either[BattleResultRecordDecodeError, PlayerHandle] = {
    val trimmed = HandlePolicy.trim(value)
    if trimmed.isEmpty then Left(BattleResultRecordDecodeError.InvalidHandle)
    else if !HandlePolicy.isPlayableIdentityHandle(trimmed) then Left(BattleResultRecordDecodeError.VisitorNotAllowed)
    else PlayerHandle.forLookup(trimmed).toRight(BattleResultRecordDecodeError.InvalidHandle)
  }

  private[api] def nonEmptyText(value: String): Option[String] =
    Option(value).map(_.trim).filter(_.nonEmpty)
}

private[api] final case class BattleResultListAPIRequest(
  handle: Option[String] = None,
  battleId: Option[String] = None,
  limit: Option[Int] = None
) {
  def toQuery: BattleResultListQuery =
    BattleResultListQuery(
      handle = handle.flatMap(BattleResultApiCodec.nonEmptyText).flatMap(PlayerHandle.forLookup),
      battleId = battleId.flatMap(BattleResultApiCodec.nonEmptyText).map(BattleId.apply),
      limit = limit.getOrElse(25)
    )
}

private[api] object BattleResultListAPIRequest {
  given Decoder[BattleResultListAPIRequest] = deriveDecoder
}

private[api] final case class BattleResultRecordAPIRequest(
  battleId: Option[String] = None,
  handle: Option[String] = None,
  displayName: Option[String] = None,
  finishedAt: Option[Long] = None,
  finishedAtLabel: Option[String] = None,
  durationMs: Option[Long] = None,
  score: Option[Int] = None,
  placement: Option[Int] = None,
  aliveAtEnd: Option[Boolean] = None,
  ratingBefore: Option[Int] = None,
  ratingDelta: Option[Int] = None,
  ratingAfter: Option[Int] = None,
  resultLabel: Option[String] = None,
  modeLabel: Option[String] = None,
  mapLabel: Option[String] = None,
  highlightLine: Option[String] = None,
  playersLine: Option[String] = None,
  timelineHint: Option[String] = None,
  currentLoadout: Option[String] = None
) {
  def toCommand: Either[BattleResultRecordDecodeError, BattleResultRecordCommand] =
    for {
      parsedBattleId <- battleId
        .flatMap(BattleResultApiCodec.nonEmptyText)
        .map(BattleId.apply)
        .toRight(BattleResultRecordDecodeError.InvalidBattleId)
      parsedHandle <- BattleResultApiCodec.parseSubmissionHandle(handle.getOrElse(""))
    } yield BattleResultRecordCommand(
      battleId = parsedBattleId,
      handle = parsedHandle,
      displayName = DisplayName(displayName.flatMap(BattleResultApiCodec.nonEmptyText).getOrElse(parsedHandle.value)),
      finishedAt = EpochMillis(math.max(0L, finishedAt.getOrElse(0L))),
      finishedAtLabel = finishedAtLabel.getOrElse(""),
      durationMs = DurationMillis(math.max(0L, durationMs.getOrElse(0L))),
      score = Score(math.max(0, score.getOrElse(0))),
      placement = placement.flatMap(BattlePlacement.fromWire),
      survivalOutcome = BattleSurvivalOutcome.fromAliveAtEnd(aliveAtEnd.getOrElse(false)),
      ratingBefore = Rating(ratingBefore.getOrElse(0)),
      ratingDelta = RatingDelta(ratingDelta.getOrElse(0)),
      ratingAfter = Rating(ratingAfter.getOrElse(0)),
      resultLabel = resultLabel.getOrElse(""),
      modeLabel = modeLabel.getOrElse(""),
      mapLabel = mapLabel.getOrElse(""),
      highlightLine = highlightLine.getOrElse(""),
      playersLine = playersLine.getOrElse(""),
      timelineHint = timelineHint.getOrElse(""),
      currentLoadout = currentLoadout.flatMap(BattleResultApiCodec.nonEmptyText).filter(_ != "null")
    )
}

private[api] object BattleResultRecordAPIRequest {
  given Decoder[BattleResultRecordAPIRequest] = deriveDecoder
}
