package services.battle.routes

import cats.effect.IO

import services.battle.api.{
  BattleResultApiCodec,
  BattleResultListResponse,
  BattleResultRecordDecodeError,
  BattleResultRecordResponse
}
import services.battle.application.BattleResultRecordError
import system.api.RegisteredAPIMessage

object BattleResultListAPIMessage {
  def registered(services: BattleAPIMessageServices): RegisteredAPIMessage =
    BattleAPIMessageSupport.registered(getClass.getSimpleName) { payload =>
      val query = BattleResultApiCodec.decodeListQuery(payload)

      IO.blocking(services.resultService.list(query.handle, query.battleId, query.limit)).flatMap(records =>
        BattleAPIMessageSupport.encode(BattleResultListResponse.fromRecords(records))
      )
    }
}

object BattleResultRecordAPIMessage {
  def registered(services: BattleAPIMessageServices): RegisteredAPIMessage =
    BattleAPIMessageSupport.registered(getClass.getSimpleName) { payload =>
      BattleResultApiCodec.decodeRecordCommand(payload) match {
        case Left(error) =>
          decodeError(error)
        case Right(command) =>
          IO.blocking(services.resultService.record(command)).flatMap {
            case Right(record) =>
              BattleAPIMessageSupport.encode(BattleResultRecordResponse.fromRecord(record))
            case Left(error) =>
              recordError(error)
          }
      }
    }

  private def decodeError(error: BattleResultRecordDecodeError): IO[Nothing] =
    error match {
      case BattleResultRecordDecodeError.VisitorNotAllowed =>
        BattleAPIMessageSupport.forbidden("visitor_not_allowed")
      case BattleResultRecordDecodeError.InvalidBattleId =>
        BattleAPIMessageSupport.badRequest("invalid_battle_id")
      case BattleResultRecordDecodeError.InvalidHandle =>
        BattleAPIMessageSupport.badRequest("invalid_handle")
      case BattleResultRecordDecodeError.BadJson =>
        BattleAPIMessageSupport.badRequest("Request body must be a JSON object.")
    }

  private def recordError(error: BattleResultRecordError): IO[Nothing] =
    error match {
      case BattleResultRecordError.VisitorNotAllowed =>
        BattleAPIMessageSupport.forbidden("visitor_not_allowed")
      case BattleResultRecordError.InvalidHandle =>
        BattleAPIMessageSupport.badRequest("invalid_handle")
    }
}
