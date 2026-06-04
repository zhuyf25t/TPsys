package services.battle.microservices.results.api

import cats.effect.IO
import io.circe.{Decoder, DecodingFailure, Json}

import java.sql.Connection

import services.battle.microservices.results.objects.result.BattleResultListQuery
import services.battle.microservices.results.services.BattleResultService
import services.battle.microservices.results.api.results.BattleResultListResponse
import services.battle.microservices.results.api.results.BattleResultListRequest.given
import services.battle.microservices.results.api.results.BattleResultResponseMapping
import services.battle.microservices.results.objects.result.BattleResultListLimit
import system.api.{APIMessage, APIWithTokenMessage}
import system.objects.UserId

final case class BattleResultListAPIMessage(
  userId: UserId,
  query: BattleResultListQuery
) extends APIWithTokenMessage[BattleResultListResponse] {
  override def plan(connection: Connection): IO[BattleResultListResponse] =
    for
      records <- BattleResultService.list(connection, query)
      response <- BattleResultResponseMapping.fromRecords(records)
    yield response
}

object BattleResultListAPIMessage {
  given Decoder[BattleResultListAPIMessage] =
    Decoder.instance { cursor =>
      APIMessage
        .injectedUserIdValue(cursor.value)
        .left
        .map(message => DecodingFailure(message, cursor.history))
        .map(userId => BattleResultListAPIMessage(userId, decodeRequest(cursor.value)))
    }

  private def decodeRequest(payload: Json): BattleResultListQuery =
    payload.as[BattleResultListQuery].getOrElse(BattleResultListQuery(None, None, BattleResultListLimit(25)))

}
