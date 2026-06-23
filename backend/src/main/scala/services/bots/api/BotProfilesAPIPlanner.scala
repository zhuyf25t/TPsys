package services.bots.api

import cats.effect.IO

import services.bots.services.BotProfileService

object BotProfilesAPIPlanner {
  def plan(service: BotProfileService): IO[BotProfilesResponse] =
    for
      records <- service.list()
      response <- IO.pure(BotProfilesResponse.fromRecords(records))
    yield response
}
