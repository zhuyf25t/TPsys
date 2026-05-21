package slaydemo.backend.replay.objects.apiTypes

import io.circe.Json
import io.circe.parser.parse

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

object ReplayApiCodec {
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
}
