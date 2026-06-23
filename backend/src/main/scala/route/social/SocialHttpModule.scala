package route.social

import cats.effect.IO
import cats.syntax.all.*
import org.http4s.HttpRoutes

import services.social.api.{
  FriendRequestCreateAPIMessage,
  FriendRequestCreateResponse,
  FriendRequestListAPIMessage,
  FriendRequestListResponse,
  FriendRequestRespondAPIMessage,
  FriendRequestRespondResponse,
  SocialAPIMessageSupport
}
import services.social.services.FriendRequestService
import system.api.APIMessageRouter
import system.api.RegisteredAPIMessage.apiWithContext

private[route] object SocialHttpModule {
  def routes(service: FriendRequestService): HttpRoutes[IO] =
    APIMessageRouter.routes(
      List(
        apiWithContext[
          FriendRequestService,
          FriendRequestListAPIMessage,
          FriendRequestListResponse
        ](service, SocialAPIMessageSupport.invalidJsonObject),
        apiWithContext[
          FriendRequestService,
          FriendRequestCreateAPIMessage,
          FriendRequestCreateResponse
        ](service, SocialAPIMessageSupport.invalidJsonObject),
        apiWithContext[
          FriendRequestService,
          FriendRequestRespondAPIMessage,
          FriendRequestRespondResponse
        ](service, SocialAPIMessageSupport.invalidJsonObject)
      )
    ) <+> SocialHttp4sRoutes.routes(service)
}
