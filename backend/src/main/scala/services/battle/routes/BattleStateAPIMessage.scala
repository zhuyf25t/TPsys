package services.battle.routes

import cats.effect.IO
import io.circe.Json

import services.battle.objects.BattleId
import services.battle.api.{BattleStateReadAPIRequest, BattleStateReadAPIRequestError, BattleStateResponse}
import services.battle.application.BattleStateReadError
import system.api.RegisteredAPIMessage

object BattleStateReadAPIMessage {
  def registered(services: BattleAPIMessageServices): RegisteredAPIMessage =
    BattleAPIMessageSupport.registered(getClass.getSimpleName) { payload =>
      battleId(payload).flatMap { id =>
        IO.blocking(services.stateService.currentState(id)).flatMap {
          case Right(state) =>
            BattleAPIMessageSupport.encode(BattleStateResponse.fromState(state))
          case Left(BattleStateReadError.BattleNotFound) =>
            BattleAPIMessageSupport.notFound("battle_not_found")
        }
      }
    }

  private def battleId(payload: Json): IO[BattleId] =
    BattleStateReadAPIRequest.decodeBattleId(payload) match {
      case Right(battleId) =>
        IO.pure(battleId)
      case Left(BattleStateReadAPIRequestError.InvalidJsonObject | BattleStateReadAPIRequestError.MissingBattleId) =>
        BattleAPIMessageSupport.badRequest("battleId is required.")
    }
}
