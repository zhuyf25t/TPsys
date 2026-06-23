package route.forum

import cats.effect.IO
import cats.syntax.all.*
import org.http4s.{HttpRoutes, MessageFailure, Method, Request, Response, Status}

import services.forum.api.{
  ForumAPIMessageSupport,
  ForumAddReplyAPIMessage,
  ForumApiErrorCode,
  ForumApiTargetParsers,
  ForumCreateTopicAPIMessage,
  ForumAPIParser,
  ForumSetReplyVoteAPIMessage,
  ForumSetTopicVoteAPIMessage,
  ForumTopicListResponse,
  ForumTopicListAPIMessage,
  ForumTopicLoadAPIMessage,
  ForumTopicWrapperResponse
}
import services.forum.services.ForumService
import route.HttpApiError
import route.HttpApiErrors.typedApiError
import route.Http4sCors.{corsNoContent, corsOk, withCors}
import route.Http4sRequestPaths.requestPath
import route.Http4sResponses.errorResponse
import system.api.{APIMessageError, APIMessageRouter, RegisteredAPIMessage}
import system.api.APIMessageRouter.{APIMessageAlias, APIMessageRequestAlias}
import system.api.RegisteredAPIMessage.apiWithContext

private[route] object ForumHttp4sRoutes {
  def routes(service: ForumService): HttpRoutes[IO] =
    postAliasRoutes(service) <+> getAliasRoutes(service) <+> compatibilityRoutes

  private def postAliasRoutes(service: ForumService): HttpRoutes[IO] =
    APIMessageRouter.dynamicAliasRoutes(
      apiMessages = forumPostApiMessages(service),
      aliasForRequest = forumPostAlias(service),
      errorHandler = forumAPIMessageErrorResponse
    )

  private def forumPostApiMessages(service: ForumService): List[RegisteredAPIMessage] =
    List(
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

  private def getAliasRoutes(service: ForumService): HttpRoutes[IO] =
    APIMessageRouter.requestAliasRoutes(
      apiMessages = forumGetApiMessages(service),
      aliasForRequest = forumGetAlias(service),
      errorHandler = forumAPIMessageErrorResponse
    )

  private def forumGetApiMessages(service: ForumService): List[RegisteredAPIMessage] =
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
      ](service, ForumAPIMessageSupport.invalidJsonObject)
    )

  private def forumPostAlias(service: ForumService)(request: Request[IO]): Option[APIMessageAlias] = {
    val path = requestPath(request)
    if ForumApiTargetParsers.isTopicsCollection(path) then
      Some(
        APIMessageAlias.fromContextMessage[ForumService, ForumCreateTopicAPIMessage, ForumTopicWrapperResponse](
          context = service,
          responseTransform = createdResponse
        )
      )
    else if ForumApiTargetParsers.isReplyVotesPath(path) then
      Some(
        APIMessageAlias.fromContextMessage[ForumService, ForumSetReplyVoteAPIMessage, ForumTopicWrapperResponse](
          context = service,
          transformMessage = _.withPathIds(
            pathTopicId = ForumApiTargetParsers.topicIdFrom(path),
            pathReplyId = ForumApiTargetParsers.replyIdFrom(path)
          ),
          responseTransform = withCors
        )
      )
    else if ForumApiTargetParsers.isRepliesPath(path) then
      Some(
        APIMessageAlias.fromContextMessage[ForumService, ForumAddReplyAPIMessage, ForumTopicWrapperResponse](
          context = service,
          transformMessage = _.withTopicId(ForumApiTargetParsers.topicIdFrom(path)),
          responseTransform = withCors
        )
      )
    else if ForumApiTargetParsers.isTopicVotesPath(path) then
      Some(
        APIMessageAlias.fromContextMessage[ForumService, ForumSetTopicVoteAPIMessage, ForumTopicWrapperResponse](
          context = service,
          transformMessage = _.withTopicId(ForumApiTargetParsers.topicIdFrom(path)),
          responseTransform = withCors
        )
      )
    else None
  }

  private def forumGetAlias(service: ForumService)(request: Request[IO]): Option[APIMessageRequestAlias] =
    if request.method != Method.GET || !isForumPath(request) then None
    else {
      val path = requestPath(request)
      if ForumApiTargetParsers.isTopicsCollection(path) then
        Some(
          APIMessageRequestAlias.fromContextMessage[ForumService, ForumTopicListAPIMessage, ForumTopicListResponse](
            context = service,
            message = ForumAPIParser.listMessageFromQuery(request.params),
            responseTransform = withCors
          )
        )
      else
        Some(
          APIMessageRequestAlias.fromContextMessage[ForumService, ForumTopicLoadAPIMessage, ForumTopicWrapperResponse](
            context = service,
            message = ForumAPIParser.loadMessageFromPathAndQuery(path, request.params),
            responseTransform = withCors
          )
        )
    }

  private def compatibilityRoutes: HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case request if isForumPath(request) =>
        request.method match {
          case Method.OPTIONS =>
            corsNoContent
          case Method.HEAD =>
            corsOk
          case _ =>
            errorResponse(routeError(ForumApiErrorCode.MethodNotAllowed))
        }
    }

  private def forumAPIMessageErrorResponse: PartialFunction[Throwable, IO[Response[IO]]] = {
    case _: MessageFailure =>
      errorResponse(routeError(ForumApiErrorCode.InvalidJsonObject))
    case error: APIMessageError =>
      errorResponse(routeError(forumApiErrorCode(error)))
  }

  private def createdResponse(response: Response[IO]): Response[IO] =
    withCors(response.withStatus(Status.Created))

  private def isForumPath(request: Request[IO]): Boolean =
    val path = requestPath(request)
    path.startsWith("/forum/") || path.startsWith("/api/forum/")

  private def routeError(code: ForumApiErrorCode): HttpApiError = {
    val wireCode = ForumApiErrorCode.wireValue(code)
    typedApiError(
      statusCode = ForumApiErrorCode.statusCode(code),
      code = wireCode,
      message = ForumApiErrorCode.message(code)
    )
  }

  private def forumApiErrorCode(error: APIMessageError): ForumApiErrorCode =
    ForumApiErrorCode.values
      .find(code =>
        error.getMessage == ForumApiErrorCode.message(code) ||
          error.getMessage == ForumApiErrorCode.wireValue(code)
      )
      .getOrElse(ForumApiErrorCode.InvalidJsonObject)
}
