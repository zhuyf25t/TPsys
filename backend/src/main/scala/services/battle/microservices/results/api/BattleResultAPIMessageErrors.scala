package services.battle.microservices.results.api

import services.battle.microservices.results.objects.result.BattleResultRecordValidationError
import system.api.APIMessageError

private[api] object BattleResultAPIMessageErrors {
  def recordValidation(error: BattleResultRecordValidationError): APIMessageError =
    error match {
      case BattleResultRecordValidationError.InvalidHandle =>
        APIMessageError.BadRequest("invalid_handle")
      case BattleResultRecordValidationError.VisitorNotAllowed =>
        APIMessageError.Forbidden("visitor_not_allowed")
    }
}
