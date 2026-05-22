package services.battle.api

import cats.effect.IO
import io.circe.Json

import services.battle.objects.BattleId
import services.battle.objects.apiTypes.BattleStateResponse
import services.battle.services.BattleStateReadError
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
    payload.hcursor.get[Option[String]]("battleId") match {
      case Right(Some(value)) if value.trim.nonEmpty =>
        IO.pure(BattleId(value.trim))
      case _ =>
        BattleAPIMessageSupport.badRequest("battleId is required.")
    }
}
