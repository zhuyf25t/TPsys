package slaydemo.backend.replay.objects.apiTypes

import io.circe.Json
import io.circe.parser.parse

import slaydemo.backend.identity.objects.PlayerHandle
import slaydemo.backend.replay.objects.ReplayId
import slaydemo.backend.replay.services.{ReplayCommentCommand, ReplayRecordCommand}

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

object ReplayApiCodec {
  def catalogTarget(path: String): Option[ReplayCatalogTarget] = {
    val normalized = normalizedPath(path)
    if normalized == "/replay/catalog" then Some(ReplayCatalogTarget.Collection)
    else if normalized.startsWith("/replay/catalog/") then {
      val suffix = normalized.stripPrefix("/replay/catalog/")
      if suffix.endsWith("/comments") then
        ReplayCommandParsers.parseReplayId(suffix.stripSuffix("/comments"))
          .map(ReplayCatalogTarget.Comments.apply)
          .orElse(Some(ReplayCatalogTarget.InvalidReplayId))
      else
        ReplayCommandParsers.parseReplayId(suffix)
          .map(ReplayCatalogTarget.Detail.apply)
          .orElse(Some(ReplayCatalogTarget.InvalidReplayId))
    } else None
  }

  def selectedHandle(query: Map[String, String]): Option[PlayerHandle] =
    query.get("handle").flatMap(PlayerHandle.forLookup)

  def limit(query: Map[String, String]): Int =
    query.get("limit").flatMap(_.toIntOption).getOrElse(25)

  def parseRecordCommand(rawBody: String): Either[ReplayRecordDecodeError, ReplayRecordCommand] =
    parseJsonObject(rawBody).left.map(_ => ReplayRecordDecodeError.BadJsonObject).flatMap { fields =>
      val framesJson = ReplayCommandParsers.readString(fields, "framesJson")
        .orElse(ReplayCommandParsers.readRawJson(fields, "frames"))
        .getOrElse("[]")
      ReplayCommandParsers.parseReplayRecordCommand(fields, framesJson).left.map(recordDecodeError)
    }

  def parseCommentCommand(
    replayId: ReplayId,
    rawBody: String
  ): Either[ReplayCommentDecodeError, ReplayCommentCommand] =
    parseJsonObject(rawBody)
      .left.map(_ => ReplayCommentDecodeError.BadJsonObject)
      .flatMap(fields => ReplayCommandParsers.parseReplayCommentCommand(replayId, fields).left.map(commentDecodeError))

  private def parseJsonObject(rawBody: String): Either[Unit, io.circe.JsonObject] = {
    val trimmed = Option(rawBody).getOrElse("").trim
    val parsed = if trimmed.isEmpty then Right(Json.obj()) else parse(trimmed)

    parsed match {
      case Left(_) =>
        Left(())
      case Right(json) =>
        json.asObject.toRight(())
    }
  }

  private def recordDecodeError(error: ReplayRecordCommandParseError): ReplayRecordDecodeError =
    error match {
      case ReplayRecordCommandParseError.InvalidReplayId =>
        ReplayRecordDecodeError.InvalidReplayId
      case ReplayRecordCommandParseError.InvalidBattleId =>
        ReplayRecordDecodeError.InvalidBattleId
      case ReplayRecordCommandParseError.InvalidHandle =>
        ReplayRecordDecodeError.InvalidHandle
      case ReplayRecordCommandParseError.VisitorNotAllowed =>
        ReplayRecordDecodeError.VisitorNotAllowed
    }

  private def commentDecodeError(error: ReplayCommentCommandParseError): ReplayCommentDecodeError =
    error match {
      case ReplayCommentCommandParseError.InvalidReplayId =>
        ReplayCommentDecodeError.InvalidReplayId
      case ReplayCommentCommandParseError.InvalidAuthorHandle =>
        ReplayCommentDecodeError.InvalidAuthorHandle
      case ReplayCommentCommandParseError.VisitorNotAllowed =>
        ReplayCommentDecodeError.VisitorNotAllowed
    }

  private def normalizedPath(path: String): String =
    if path == "/api/replaycatalogapi" then "/replay/catalog"
    else if path.startsWith("/api/replay/catalog") then path.stripPrefix("/api")
    else path
}
