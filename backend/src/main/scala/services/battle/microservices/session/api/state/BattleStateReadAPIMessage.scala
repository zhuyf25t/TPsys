package services.battle.microservices.session.api.state

import cats.effect.IO
import io.circe.{Decoder, DecodingFailure, Error, Json}

import java.sql.Connection

import services.battle.microservices.session.services.{BattleStateReadError, BattleStateService}
import services.battle.microservices.session.api.state.BattleStateReadAPIRequest.given
import services.battle.objects.core.{
  BattleAggregateState
}
import system.api.{APIMessage, APIMessageError, APIWithTokenContextMessage}
import system.objects.UserId

final case class BattleStateReadAPIMessage(
  userId: UserId,
  query: BattleStateReadQuery
) extends APIWithTokenContextMessage[BattleStateService, BattleAggregateState] {
  override def plan(stateService: BattleStateService, connection: Connection): IO[BattleAggregateState] =
    for
      state <- readState(stateService, query)
    yield state

  private def readState(stateService: BattleStateService, query: BattleStateReadQuery): IO[BattleAggregateState] =
    stateService.currentState(query.battleId).flatMap {
      case Right(state) =>
        IO.pure(state)
      case Left(BattleStateReadError.BattleNotFound) =>
        IO.raiseError(APIMessageError.NotFound("battle_not_found"))
    }
}

object BattleStateReadAPIMessage {
  given Decoder[BattleStateReadAPIMessage] =
    Decoder.instance { cursor =>
      for
        request <- decodeRequest(cursor.value)
          .left
          .map(error => DecodingFailure(BattleStateReadRequestDecodeError.message(error), cursor.history))
        userId <- APIMessage.injectedUserIdValue(cursor.value)
          .left
          .map(message => DecodingFailure(message, cursor.history))
      yield BattleStateReadAPIMessage(userId, request)
    }

  private def decodeRequest(payload: Json): Either[BattleStateReadRequestDecodeError, BattleStateReadQuery] =
    payload.as[BattleStateReadQuery].left.map(_ => BattleStateReadRequestDecodeError.InvalidJsonObject)

  private[battle] def requestDecodeFailure(error: Error): APIMessageError =
    error match {
      case failure: DecodingFailure if failure.message == "Login is required." =>
        APIMessageError.Unauthorized("Login is required.")
      case _ =>
        APIMessageError.BadRequest("battleId is required.")
    }
}
