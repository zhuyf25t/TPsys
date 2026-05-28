package route.bots

import cats.effect.IO
import cats.syntax.all.*
import org.http4s.HttpRoutes

import services.bots.api.BotProfilesAPIMessage
import services.bots.objects.apiTypes.BotProfilesResponse
import services.bots.services.BotProfileService
import system.api.APIMessageRouter
import system.api.RegisteredAPIMessage.apiWithContext

private[route] object BotProfileHttpModule {
  def routes(service: BotProfileService): HttpRoutes[IO] =
    APIMessageRouter.routes(
      List(
        apiWithContext[
          BotProfileService,
          BotProfilesAPIMessage,
          BotProfilesResponse
        ](service)
      )
    ) <+>
      BotProfileHttp4sRoutes.routes(service)
}
