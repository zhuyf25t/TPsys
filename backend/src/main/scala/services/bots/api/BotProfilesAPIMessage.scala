package services.bots.api

import cats.effect.IO
import io.circe.Decoder

import java.sql.Connection

import services.bots.objects.apiTypes.BotProfilesResponse
import services.bots.services.BotProfileService
import system.api.APIMessageWithContext

final case class BotProfilesAPIMessage() extends APIMessageWithContext[BotProfileService, BotProfilesResponse] {
  override def plan(service: BotProfileService, connection: Connection): IO[BotProfilesResponse] =
    IO.blocking(service.list()).map(BotProfilesResponse.fromRecords)
}

object BotProfilesAPIMessage {
  given Decoder[BotProfilesAPIMessage] =
    Decoder.const(BotProfilesAPIMessage())
}
