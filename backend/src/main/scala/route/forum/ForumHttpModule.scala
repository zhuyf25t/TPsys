package route.forum

import cats.effect.IO
import cats.syntax.all.*
import org.http4s.HttpRoutes

import services.forum.api.{
  ForumAPIMessageSupport,
  ForumAddReplyAPIMessage,
  ForumCreateTopicAPIMessage,
  ForumSetReplyVoteAPIMessage,
  ForumSetTopicVoteAPIMessage,
  ForumTopicListAPIMessage,
  ForumTopicLoadAPIMessage
}
import services.forum.objects.apiTypes.{ForumTopicListResponse, ForumTopicWrapperResponse}
import services.forum.services.ForumService
import system.api.APIMessageRouter
import system.api.RegisteredAPIMessage.apiWithContext

private[route] object ForumHttpModule {
  def routes(service: ForumService): HttpRoutes[IO] =
    APIMessageRouter.routes(
      List(
        apiWithContext[
          ForumService,
          ForumTopicListAPIMessage,
          ForumTopicListResponse
        ](service, ForumAPIMessageSupport.invalidJsonObject),
        apiWithContext[
          ForumService,
          ForumTopicLoadAPIMessage,
          ForumTopicWrapperResponse
        ](service, ForumAPIMessageSupport.invalidJsonObject),
        apiWithContext[
          ForumService,
          ForumCreateTopicAPIMessage,
          ForumTopicWrapperResponse
        ](service, ForumAPIMessageSupport.invalidJsonObject),
        apiWithContext[
          ForumService,
          ForumAddReplyAPIMessage,
          ForumTopicWrapperResponse
        ](service, ForumAPIMessageSupport.invalidJsonObject),
        apiWithContext[
          ForumService,
          ForumSetTopicVoteAPIMessage,
          ForumTopicWrapperResponse
        ](service, ForumAPIMessageSupport.invalidJsonObject),
        apiWithContext[
          ForumService,
          ForumSetReplyVoteAPIMessage,
          ForumTopicWrapperResponse
        ](service, ForumAPIMessageSupport.invalidJsonObject)
      )
    ) <+> ForumHttp4sRoutes.routes(service)
}
