package services.battle.microservices.results.api

import cats.effect.IO
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder

import java.sql.Connection

import services.battle.objects.core.BattleId
import services.battle.microservices.results.objects.result.BattleResultListLimit
import services.battle.microservices.results.api.results.BattleResultListResponse
import services.identity.objects.PlayerHandle
import system.api.APIWithTokenMessage
import system.objects.UserId

final case class BattleResultListAPIMessage(
  userId: UserId,
  handle: Option[PlayerHandle],
  battleId: Option[BattleId],
  limit: BattleResultListLimit
) extends APIWithTokenMessage[BattleResultListResponse] {
  override def plan(connection: Connection): IO[BattleResultListResponse] =
    BattleResultListAPIPlanner.plan(connection, this)
}

object BattleResultListAPIMessage {
  import BattleResultAPIMessageDecoding.given

  given Decoder[BattleResultListAPIMessage] =
    deriveDecoder[BattleResultListAPIMessage]
}
