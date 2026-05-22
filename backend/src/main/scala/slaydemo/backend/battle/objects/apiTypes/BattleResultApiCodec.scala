package slaydemo.backend.battle.objects.apiTypes

import io.circe.JsonObject
import io.circe.parser.parse

import slaydemo.backend.battle.objects.BattleId
import slaydemo.backend.battle.services.BattleResultRecordCommand
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
