package route.replay

import cats.effect.IO
import cats.syntax.all.*
import org.http4s.HttpRoutes

import services.replay.objects.apiTypes.ReplayCatalogAPIMessage
import services.replay.services.ReplayService
import system.api.APIMessageRouter

private[route] object ReplayHttpModule {
  def routes(service: ReplayService): HttpRoutes[IO] =
    APIMessageRouter.routes(List(ReplayCatalogAPIMessage.registered(service))) <+>
      ReplayHttp4sRoutes.catalogRoutes(service)
}
