package route.replay

import cats.effect.IO
import cats.syntax.all.*
import org.http4s.HttpRoutes

import services.replay.api.{
  ReplayAPIMessageSupport,
  ReplayCatalogAPIMessage,
  ReplayCommentCreateAPIMessage,
  ReplayCommentsAPIMessage,
  ReplayDetailAPIMessage,
  ReplayRecordAPIMessage,
  ReplayCatalogResponse,
  ReplayCommentWrapperResponse,
  ReplayCommentsResponse,
  ReplayDetailResponse
}
import services.replay.services.ReplayService
import system.api.APIMessageRouter
import system.api.RegisteredAPIMessage.apiWithContext

private[route] object ReplayHttpModule {
  def routes(service: ReplayService): HttpRoutes[IO] =
    APIMessageRouter.routes(
      List(
        apiWithContext[
          ReplayService,
          ReplayCatalogAPIMessage,
          ReplayCatalogResponse
        ](service, ReplayAPIMessageSupport.invalidJsonObject),
        apiWithContext[
          ReplayService,
          ReplayDetailAPIMessage,
          ReplayDetailResponse
        ](service, ReplayAPIMessageSupport.invalidJsonObject),
        apiWithContext[
          ReplayService,
          ReplayCommentsAPIMessage,
          ReplayCommentsResponse
        ](service, ReplayAPIMessageSupport.invalidJsonObject),
        apiWithContext[
          ReplayService,
          ReplayRecordAPIMessage,
          ReplayDetailResponse
        ](service, ReplayAPIMessageSupport.invalidJsonObject),
        apiWithContext[
          ReplayService,
          ReplayCommentCreateAPIMessage,
          ReplayCommentWrapperResponse
        ](service, ReplayAPIMessageSupport.invalidJsonObject)
      )
    ) <+>
      ReplayHttp4sRoutes.catalogRoutes(service)
}
