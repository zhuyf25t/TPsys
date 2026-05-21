package slaydemo.backend.battle.objects.apiTypes

import slaydemo.backend.battle.objects.{BattleId, BattleResultRecord}
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

object BattleResultApiCodec {
  def parseListRequest(rawQuery: String): BattleResultListQueryDecodeResult =
    BattleResultCommandParsers.parseListRequest(rawQuery) match {
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
    ResultJsonObjectParser.parse(rawBody) match {
      case Left(_) =>
        Left(BattleResultRecordDecodeError.BadJson)
      case Right(fields) =>
        BattleResultCommandParsers.parseRecordCommand(fields).left.map(recordDecodeError)
    }

  def renderRecords(records: Vector[BattleResultRecord]): String =
    BattleResultListResponse.renderRecords(records)

  def renderRecord(record: BattleResultRecord): String =
    BattleResultRecordResponse.renderRecord(record)

  private def recordDecodeError(error: BattleResultRecordCommandParseError): BattleResultRecordDecodeError =
    error match {
      case BattleResultRecordCommandParseError.InvalidBattleId =>
        BattleResultRecordDecodeError.InvalidBattleId
      case BattleResultRecordCommandParseError.InvalidHandle =>
        BattleResultRecordDecodeError.InvalidHandle
      case BattleResultRecordCommandParseError.VisitorNotAllowed =>
        BattleResultRecordDecodeError.VisitorNotAllowed
    }
}
