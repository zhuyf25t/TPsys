package slaydemo.backend.replay.routes

import java.util.Locale

import slaydemo.backend.battle.objects.{BattleId, BattlePlacement, BattleSurvivalOutcome, DurationMillis, EpochMillis, Score}
import slaydemo.backend.identity.objects.{DisplayName, PlayerHandle}
import slaydemo.backend.replay.objects.{ReplayFrameCount, ReplayId, ReplayPlaybackAvailability}
import slaydemo.backend.replay.services.{ReplayCommentCommand, ReplayIdentifierPolicy, ReplayRecordCommand}
import slaydemo.backend.shared.policies.HandlePolicy

private[backend] enum ReplayRecordCommandParseError {
  case InvalidReplayId
  case InvalidBattleId
  case InvalidHandle
  case VisitorNotAllowed
}

private[backend] enum ReplayCommentCommandParseError {
  case InvalidReplayId
  case InvalidAuthorHandle
  case VisitorNotAllowed
}

private[backend] object ReplayCommandParsers {
  def parseReplayRecordCommand(
    fields: Map[String, ReplayJsonValue],
    framesJson: String
  ): Either[ReplayRecordCommandParseError, ReplayRecordCommand] =
    for {
      replayId <- parseReplayId(readString(fields, "replayId").getOrElse(""))
        .toRight(ReplayRecordCommandParseError.InvalidReplayId)
      battleId <- parseBattleId(readString(fields, "battleId").getOrElse(""))
        .toRight(ReplayRecordCommandParseError.InvalidBattleId)
      handle <- parseRecordHandle(readString(fields, "handle").getOrElse(""))
    } yield {
      val frameCount = ReplayFrameCount.fromWire(readInt(fields, "frameCount").getOrElse(0))
      ReplayRecordCommand(
        replayId = replayId,
        battleId = battleId,
        handle = handle,
        displayName = DisplayName(nonEmpty(readString(fields, "displayName").getOrElse("")).getOrElse(handle.value)),
        finishedAt = EpochMillis(math.max(0L, readLong(fields, "finishedAt").getOrElse(0L))),
        finishedAtLabel = readString(fields, "finishedAtLabel").getOrElse(""),
        title = readString(fields, "title").getOrElse(""),
        modeLabel = readString(fields, "modeLabel").getOrElse(""),
        resultLabel = readString(fields, "resultLabel").getOrElse(""),
        mapLabel = readString(fields, "mapLabel").getOrElse(""),
        highlightLine = readString(fields, "highlightLine").getOrElse(""),
        coverLabel = readString(fields, "coverLabel").getOrElse(""),
        playersLine = readString(fields, "playersLine").getOrElse(""),
        timelineHint = readString(fields, "timelineHint").getOrElse(""),
        score = Score(math.max(0, readInt(fields, "score").getOrElse(0))),
        placement = readOptionalInt(fields, "placement").flatMap(BattlePlacement.fromWire),
        durationMs = DurationMillis(math.max(0L, readLong(fields, "durationMs").getOrElse(0L))),
        survivalOutcome = BattleSurvivalOutcome.fromAliveAtEnd(readBoolean(fields, "aliveAtEnd").getOrElse(false)),
        thumbnailDataUrl = readNullableString(fields, "thumbnailDataUrl"),
        currentLoadout = readNullableString(fields, "currentLoadout"),
        frameCount = frameCount,
        requestedPlaybackAvailability = ReplayPlaybackAvailability.fromAvailableFlag(readBoolean(fields, "playbackAvailable").getOrElse(false)),
        framesJson = framesJson
      )
    }

  def parseReplayCommentCommand(
    replayId: ReplayId,
    fields: Map[String, ReplayJsonValue]
  ): Either[ReplayCommentCommandParseError, ReplayCommentCommand] =
    for {
      parsedReplayId <- parseReplayId(replayId.value).toRight(ReplayCommentCommandParseError.InvalidReplayId)
      author <- parseCommentHandle(readString(fields, "authorHandle").getOrElse(""))
    } yield ReplayCommentCommand(
      replayId = parsedReplayId,
      authorHandle = author,
      body = readString(fields, "body").getOrElse("")
    )

  def readString(fields: Map[String, ReplayJsonValue], key: String): Option[String] =
    fields.get(key) match {
      case Some(ReplayJsonValue.StringValue(value)) => Some(value)
      case Some(ReplayJsonValue.NumberValue(value)) if value.isWhole => Some(value.toLong.toString)
      case Some(ReplayJsonValue.NumberValue(value)) => Some(value.toString)
      case Some(ReplayJsonValue.BooleanValue(value)) => Some(value.toString)
      case _ => None
    }

  def readRawJson(fields: Map[String, ReplayJsonValue], key: String): Option[String] =
    fields.get(key) match {
      case Some(ReplayJsonValue.RawJsonValue(value)) => Some(value)
      case _ => None
    }

  def parseReplayId(value: String): Option[ReplayId] =
    nonEmpty(value).filter(ReplayIdentifierPolicy.isSafeIdentifier).map(ReplayId.apply)

  private def parseBattleId(value: String): Option[BattleId] =
    nonEmpty(value).filter(_.length <= 200).map(BattleId.apply)

  private def parseRecordHandle(value: String): Either[ReplayRecordCommandParseError, PlayerHandle] = {
    val trimmed = HandlePolicy.trim(value)
    if trimmed.isEmpty then Left(ReplayRecordCommandParseError.InvalidHandle)
    else if !HandlePolicy.isPlayableIdentityHandle(trimmed) then Left(ReplayRecordCommandParseError.VisitorNotAllowed)
    else PlayerHandle.forLookup(trimmed).toRight(ReplayRecordCommandParseError.InvalidHandle)
  }

  private def parseCommentHandle(value: String): Either[ReplayCommentCommandParseError, PlayerHandle] = {
    val trimmed = HandlePolicy.trim(value)
    if trimmed.isEmpty then Left(ReplayCommentCommandParseError.InvalidAuthorHandle)
    else if !HandlePolicy.isPlayableIdentityHandle(trimmed) then Left(ReplayCommentCommandParseError.VisitorNotAllowed)
    else PlayerHandle.forLookup(trimmed).toRight(ReplayCommentCommandParseError.InvalidAuthorHandle)
  }

  private def readNullableString(fields: Map[String, ReplayJsonValue], key: String): Option[String] =
    fields.get(key) match {
      case Some(ReplayJsonValue.NullValue) | None => None
      case _ => readString(fields, key).map(_.trim).filter(value => value.nonEmpty && value != "null")
    }

  private def readLong(fields: Map[String, ReplayJsonValue], key: String): Option[Long] =
    fields.get(key) match {
      case Some(ReplayJsonValue.StringValue(value)) => value.trim.toLongOption
      case Some(ReplayJsonValue.NumberValue(value)) if isWholeLong(value) => Some(value.toLong)
      case _ => None
    }

  private def readInt(fields: Map[String, ReplayJsonValue], key: String): Option[Int] =
    fields.get(key) match {
      case Some(ReplayJsonValue.StringValue(value)) => value.trim.toIntOption
      case Some(ReplayJsonValue.NumberValue(value)) if isWholeInt(value) => Some(value.toInt)
      case _ => None
    }

  private def readOptionalInt(fields: Map[String, ReplayJsonValue], key: String): Option[Int] =
    fields.get(key) match {
      case Some(ReplayJsonValue.NullValue) | None => None
      case _ => readInt(fields, key)
    }

  private def readBoolean(fields: Map[String, ReplayJsonValue], key: String): Option[Boolean] =
    fields.get(key) match {
      case Some(ReplayJsonValue.BooleanValue(value)) => Some(value)
      case Some(ReplayJsonValue.StringValue(value)) =>
        value.trim.toLowerCase(Locale.ROOT) match {
          case "true"  => Some(true)
          case "false" => Some(false)
          case _       => None
        }
      case _ => None
    }

  private def isWholeInt(value: Double): Boolean =
    value.isWhole && value >= Int.MinValue.toDouble && value <= Int.MaxValue.toDouble

  private def isWholeLong(value: Double): Boolean =
    value.isWhole && value >= Long.MinValue.toDouble && value <= Long.MaxValue.toDouble

  private def nonEmpty(value: String): Option[String] =
    Option(value).map(_.trim).filter(_.nonEmpty)
}
