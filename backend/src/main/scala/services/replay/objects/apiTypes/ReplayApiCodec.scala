package services.replay.objects.apiTypes

import io.circe.{Decoder, Json}
import io.circe.generic.semiauto.deriveDecoder
import io.circe.syntax.*
import cats.effect.IO

import services.battle.objects.{BattleId, BattlePlacement, BattleSurvivalOutcome, DurationMillis, EpochMillis, Score}
import services.identity.objects.PlayerHandle
import services.identity.objects.DisplayName
import services.replay.objects.{ReplayFrameCount, ReplayId, ReplayPlaybackAvailability}
import services.replay.services.{ReplayCommentCommand, ReplayIdentifierPolicy, ReplayRecordCommand, ReplayService}
import system.api.{APIMessage, APIMessageError, RegisteredAPIMessage}
import system.policies.HandlePolicy

enum ReplayRecordDecodeError {
  case BadJsonObject
  case InvalidReplayId
  case InvalidBattleId
  case InvalidHandle
  case VisitorNotAllowed
}

enum ReplayCommentDecodeError {
  case BadJsonObject
  case InvalidReplayId
  case InvalidAuthorHandle
  case VisitorNotAllowed
}

enum ReplayCatalogTarget {
  case Collection
  case Detail(replayId: ReplayId)
  case Comments(replayId: ReplayId)
  case InvalidReplayId
}

final case class ReplayCatalogQuery(
  limit: Int,
  selectedHandle: Option[PlayerHandle]
)

object ReplayApiCodec {
  private val CatalogBasePaths: Vector[String] =
    Vector("/replay/catalog", "/api/replay/catalog")

  def catalogTarget(path: String): Option[ReplayCatalogTarget] = {
    CatalogBasePaths.collectFirst(Function.unlift(basePathTarget(path)))
  }

  def selectedHandle(query: Map[String, String]): Option[PlayerHandle] =
    query.get("handle").flatMap(PlayerHandle.forLookup)

  def limit(query: Map[String, String]): Int =
    query.get("limit").flatMap(_.toIntOption).getOrElse(25)

  def catalogQuery(query: Map[String, String]): ReplayCatalogQuery =
    ReplayCatalogQuery(
      limit = limit(query),
      selectedHandle = selectedHandle(query)
    )

  def decodeRecordCommand(payload: Json): Either[ReplayRecordDecodeError, ReplayRecordCommand] =
    payload.as[ReplayRecordAPIRequest].left.map(_ => ReplayRecordDecodeError.BadJsonObject).flatMap(_.toCommand)

  def decodeCommentCommand(
    replayId: ReplayId,
    payload: Json
  ): Either[ReplayCommentDecodeError, ReplayCommentCommand] =
    payload.as[ReplayCommentAPIRequest].left.map(_ => ReplayCommentDecodeError.BadJsonObject).flatMap(_.toCommand(replayId))

  private def basePathTarget(path: String)(basePath: String): Option[ReplayCatalogTarget] =
    if path == basePath then Some(ReplayCatalogTarget.Collection)
    else if path.startsWith(s"$basePath/") then {
      val suffix = path.stripPrefix(s"$basePath/")
      if suffix.endsWith("/comments") then
        parseReplayIdValue(suffix.stripSuffix("/comments"))
          .map(ReplayCatalogTarget.Comments.apply)
          .orElse(Some(ReplayCatalogTarget.InvalidReplayId))
      else
        parseReplayIdValue(suffix)
          .map(ReplayCatalogTarget.Detail.apply)
          .orElse(Some(ReplayCatalogTarget.InvalidReplayId))
    } else None

  private[apiTypes] def parseReplayId(value: String): Either[ReplayRecordDecodeError, ReplayId] =
    parseReplayIdValue(value).toRight(ReplayRecordDecodeError.InvalidReplayId)

  private[apiTypes] def parseCommentReplayId(value: String): Either[ReplayCommentDecodeError, ReplayId] =
    parseReplayIdValue(value).toRight(ReplayCommentDecodeError.InvalidReplayId)

  private[apiTypes] def parseBattleId(value: String): Either[ReplayRecordDecodeError, BattleId] =
    nonEmpty(value).filter(_.length <= 200).map(BattleId.apply).toRight(ReplayRecordDecodeError.InvalidBattleId)

  private[apiTypes] def parseRecordHandle(value: String): Either[ReplayRecordDecodeError, PlayerHandle] = {
    val trimmed = HandlePolicy.trim(value)
    if trimmed.isEmpty then Left(ReplayRecordDecodeError.InvalidHandle)
    else if !HandlePolicy.isPlayableIdentityHandle(trimmed) then Left(ReplayRecordDecodeError.VisitorNotAllowed)
    else PlayerHandle.forLookup(trimmed).toRight(ReplayRecordDecodeError.InvalidHandle)
  }

  private[apiTypes] def parseCommentHandle(value: String): Either[ReplayCommentDecodeError, PlayerHandle] = {
    val trimmed = HandlePolicy.trim(value)
    if trimmed.isEmpty then Left(ReplayCommentDecodeError.InvalidAuthorHandle)
    else if !HandlePolicy.isPlayableIdentityHandle(trimmed) then Left(ReplayCommentDecodeError.VisitorNotAllowed)
    else PlayerHandle.forLookup(trimmed).toRight(ReplayCommentDecodeError.InvalidAuthorHandle)
  }

  private[apiTypes] def nonEmpty(value: String): Option[String] =
    Option(value).map(_.trim).filter(_.nonEmpty)

  private def parseReplayIdValue(value: String): Option[ReplayId] =
    nonEmpty(value).filter(ReplayIdentifierPolicy.isSafeIdentifier).map(ReplayId.apply)
}

private[apiTypes] final case class ReplayRecordAPIRequest(
  replayId: Option[String] = None,
  battleId: Option[String] = None,
  handle: Option[String] = None,
  displayName: Option[String] = None,
  finishedAt: Option[Long] = None,
  finishedAtLabel: Option[String] = None,
  title: Option[String] = None,
  modeLabel: Option[String] = None,
  resultLabel: Option[String] = None,
  mapLabel: Option[String] = None,
  highlightLine: Option[String] = None,
  coverLabel: Option[String] = None,
  playersLine: Option[String] = None,
  timelineHint: Option[String] = None,
  score: Option[Int] = None,
  placement: Option[Int] = None,
  durationMs: Option[Long] = None,
  aliveAtEnd: Option[Boolean] = None,
  thumbnailDataUrl: Option[String] = None,
  currentLoadout: Option[String] = None,
  frameCount: Option[Int] = None,
  playbackAvailable: Option[Boolean] = None,
  framesJson: Option[String] = None,
  frames: Option[Json] = None
) {
  def toCommand: Either[ReplayRecordDecodeError, ReplayRecordCommand] =
    for {
      parsedReplayId <- ReplayApiCodec.parseReplayId(replayId.getOrElse(""))
      parsedBattleId <- ReplayApiCodec.parseBattleId(battleId.getOrElse(""))
      parsedHandle <- ReplayApiCodec.parseRecordHandle(handle.getOrElse(""))
    } yield ReplayRecordCommand(
      replayId = parsedReplayId,
      battleId = parsedBattleId,
      handle = parsedHandle,
      displayName = DisplayName(displayName.flatMap(ReplayApiCodec.nonEmpty).getOrElse(parsedHandle.value)),
      finishedAt = EpochMillis(math.max(0L, finishedAt.getOrElse(0L))),
      finishedAtLabel = finishedAtLabel.getOrElse(""),
      title = title.getOrElse(""),
      modeLabel = modeLabel.getOrElse(""),
      resultLabel = resultLabel.getOrElse(""),
      mapLabel = mapLabel.getOrElse(""),
      highlightLine = highlightLine.getOrElse(""),
      coverLabel = coverLabel.getOrElse(""),
      playersLine = playersLine.getOrElse(""),
      timelineHint = timelineHint.getOrElse(""),
      score = Score(math.max(0, score.getOrElse(0))),
      placement = placement.flatMap(BattlePlacement.fromWire),
      durationMs = DurationMillis(math.max(0L, durationMs.getOrElse(0L))),
      survivalOutcome = BattleSurvivalOutcome.fromAliveAtEnd(aliveAtEnd.getOrElse(false)),
      thumbnailDataUrl = thumbnailDataUrl.flatMap(ReplayApiCodec.nonEmpty).filter(_ != "null"),
      currentLoadout = currentLoadout.flatMap(ReplayApiCodec.nonEmpty).filter(_ != "null"),
      frameCount = ReplayFrameCount.fromWire(frameCount.getOrElse(0)),
      requestedPlaybackAvailability = ReplayPlaybackAvailability.fromAvailableFlag(playbackAvailable.getOrElse(false)),
      framesJson = framesJson.orElse(frames.map(_.noSpaces)).getOrElse("[]")
    )
}

private[apiTypes] object ReplayRecordAPIRequest {
  given Decoder[ReplayRecordAPIRequest] = deriveDecoder
}

private[apiTypes] final case class ReplayCommentAPIRequest(
  authorHandle: Option[String] = None,
  body: Option[String] = None
) {
  def toCommand(replayId: ReplayId): Either[ReplayCommentDecodeError, ReplayCommentCommand] =
    for {
      parsedReplayId <- ReplayApiCodec.parseCommentReplayId(replayId.value)
      author <- ReplayApiCodec.parseCommentHandle(authorHandle.getOrElse(""))
    } yield ReplayCommentCommand(
      replayId = parsedReplayId,
      authorHandle = author,
      body = body.getOrElse("")
    )
}

private[apiTypes] object ReplayCommentAPIRequest {
  given Decoder[ReplayCommentAPIRequest] = deriveDecoder
}

private[apiTypes] final case class ReplayCatalogAPIRequest(
  limit: Option[Int] = None,
  handle: Option[String] = None
) {
  def toQuery: ReplayCatalogQuery =
    ReplayCatalogQuery(
      limit = limit.getOrElse(25),
      selectedHandle = handle.flatMap(ReplayApiCodec.nonEmpty).flatMap(PlayerHandle.forLookup)
    )
}

private[apiTypes] object ReplayCatalogAPIRequest {
  given Decoder[ReplayCatalogAPIRequest] = deriveDecoder
}

object ReplayCatalogAPIMessage {
  def registered(service: ReplayService): RegisteredAPIMessage =
    RegisteredAPIMessage(
      apiName = APIMessage.apiNameFromClassName(getClass.getSimpleName),
      requiresUserToken = false,
      planJson = payload => plan(service, payload)
    )

  private def plan(service: ReplayService, payload: Json): IO[Json] =
    catalogQueryFromPayload(payload).flatMap { query =>
      IO.blocking(service.list(query.limit))
        .map(records => ReplayCatalogResponse.fromRecords(records, query.selectedHandle).asJson)
    }

  private def catalogQueryFromPayload(payload: Json): IO[ReplayCatalogQuery] =
    IO.fromEither(
      payload
        .as[ReplayCatalogAPIRequest]
        .left
        .map(_ => APIMessageError.BadRequest("Request body must be a JSON object."))
        .map(_.toQuery)
    )
}
