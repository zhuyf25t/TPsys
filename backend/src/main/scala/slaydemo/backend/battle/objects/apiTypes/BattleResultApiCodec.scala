package slaydemo.backend.battle.objects.apiTypes

import io.circe.JsonObject
import io.circe.parser.parse

import slaydemo.backend.battle.objects.BattleId
import slaydemo.backend.battle.services.{BattleResultRecordCommand, BattleResultRecordError}
import slaydemo.backend.identity.objects.PlayerHandle

final case class BattleResultListQuery(
  handle: Option[PlayerHandle],
  battleId: Option[BattleId],
  limit: Int
)

enum BattleResultListQueryDecodeResult {
  case Query(request: BattleResultListQuery)
  case EmptyResults
}

enum BattleResultRecordDecodeError {
  case BadJson
  case InvalidBattleId
  case InvalidHandle
  case VisitorNotAllowed
}

enum BattleResultApiErrorCode {
  case MethodNotAllowed
  case BadJson
  case InvalidBattleId
  case InvalidHandle
  case VisitorNotAllowed
}

object BattleResultApiErrorCode {
  def fromRecordDecodeError(error: BattleResultRecordDecodeError): BattleResultApiErrorCode =
    error match {
      case BattleResultRecordDecodeError.BadJson =>
        BattleResultApiErrorCode.BadJson
      case BattleResultRecordDecodeError.InvalidBattleId =>
        BattleResultApiErrorCode.InvalidBattleId
      case BattleResultRecordDecodeError.InvalidHandle =>
        BattleResultApiErrorCode.InvalidHandle
      case BattleResultRecordDecodeError.VisitorNotAllowed =>
        BattleResultApiErrorCode.VisitorNotAllowed
    }

  def fromRecordError(error: BattleResultRecordError): BattleResultApiErrorCode =
    error match {
      case BattleResultRecordError.InvalidHandle =>
        BattleResultApiErrorCode.InvalidHandle
      case BattleResultRecordError.VisitorNotAllowed =>
        BattleResultApiErrorCode.VisitorNotAllowed
    }

  def wireValue(code: BattleResultApiErrorCode): String =
    code match {
      case BattleResultApiErrorCode.MethodNotAllowed =>
        "method_not_allowed"
      case BattleResultApiErrorCode.BadJson =>
        "bad_request"
      case BattleResultApiErrorCode.InvalidBattleId =>
        "invalid_battle_id"
      case BattleResultApiErrorCode.InvalidHandle =>
        "invalid_handle"
      case BattleResultApiErrorCode.VisitorNotAllowed =>
        "visitor_not_allowed"
    }

  def message(code: BattleResultApiErrorCode): String =
    code match {
      case BattleResultApiErrorCode.MethodNotAllowed =>
        "Only GET, POST, HEAD, and OPTIONS are supported."
      case BattleResultApiErrorCode.BadJson =>
        "Request body must be a JSON object."
      case _ =>
        wireValue(code)
    }

  def statusCode(code: BattleResultApiErrorCode): Int =
    code match {
      case BattleResultApiErrorCode.MethodNotAllowed =>
        405
      case BattleResultApiErrorCode.VisitorNotAllowed =>
        403
      case _ =>
        400
    }
}

object BattleResultRequestTarget {
  private val AllowedResultPaths: Set[String] =
    Set("/battle/results", "/api/battle/results")

  def isResultPath(path: String): Boolean =
    AllowedResultPaths.contains(path)
}

object BattleResultApiCodec {
  private val EmptyRecordRequestJson: JsonObject =
    JsonObject.empty

  def parseListRequest(query: Map[String, String]): BattleResultListQueryDecodeResult =
    BattleResultCommandParsers.parseListRequest(query) match {
      case BattleResultListRequestParseResult.EmptyResults =>
        BattleResultListQueryDecodeResult.EmptyResults
      case BattleResultListRequestParseResult.Query(request) =>
        BattleResultListQueryDecodeResult.Query(
          BattleResultListQuery(
            handle = request.handle,
            battleId = request.battleId,
            limit = request.limit
          )
        )
    }

  def parseRecordCommand(rawBody: String): Either[BattleResultRecordDecodeError, BattleResultRecordCommand] =
    parseRecordJson(rawBody)
      .flatMap(jsonObject => BattleResultCommandParsers.parseRecordCommand(jsonObject).left.map(recordDecodeError))

  private def recordDecodeError(error: BattleResultRecordCommandParseError): BattleResultRecordDecodeError =
    error match {
      case BattleResultRecordCommandParseError.InvalidBattleId =>
        BattleResultRecordDecodeError.InvalidBattleId
      case BattleResultRecordCommandParseError.InvalidHandle =>
        BattleResultRecordDecodeError.InvalidHandle
      case BattleResultRecordCommandParseError.VisitorNotAllowed =>
        BattleResultRecordDecodeError.VisitorNotAllowed
    }

  private def parseRecordJson(rawBody: String): Either[BattleResultRecordDecodeError, JsonObject] = {
    val trimmed = Option(rawBody).getOrElse("").trim
    if trimmed.isEmpty then Right(EmptyRecordRequestJson)
    else
      parse(trimmed) match {
        case Left(_) =>
          Left(BattleResultRecordDecodeError.BadJson)
        case Right(json) =>
          json.asObject.toRight(BattleResultRecordDecodeError.BadJson)
      }
  }
}
