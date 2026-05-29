package services.bots.api

import cats.effect.IO
import io.circe.Decoder

import java.sql.Connection

import services.bots.objects.apiTypes.BotProfilesResponse
import services.bots.services.BotProfileService
import system.api.APIMessageWithContext

final case class BotProfilesAPIMessage() extends APIMessageWithContext[BotProfileService, BotProfilesResponse] {
  override def plan(service: BotProfileService, connection: Connection): IO[BotProfilesResponse] =
    for
      records <- service.list()
      response <- IO.pure(BotProfilesResponse.fromRecords(records))
    yield response
}

object BotProfilesAPIMessage {
  given Decoder[BotProfilesAPIMessage] =
    Decoder.const(BotProfilesAPIMessage())
}
