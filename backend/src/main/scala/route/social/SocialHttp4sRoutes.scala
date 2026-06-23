package route.social

import cats.effect.IO
import cats.syntax.all.*
import org.http4s.{HttpRoutes, MessageFailure, Method, Request, Response}

import route.HttpApiError
import route.HttpApiErrors.typedApiError
import route.Http4sCors.{corsNoContent, withCors}
import route.Http4sRequestPaths.requestPath
import route.Http4sResponses.errorResponse
import services.social.api.{
  FriendRequestCreateAPIMessage,
  FriendRequestCreateResponse,
  FriendRequestListAPIMessage,
  FriendRequestListResponse,
  FriendRequestRespondResponse,
  FriendRequestRespondAPIMessage,
  SocialAPIParser,
  SocialApiErrorCode,
  SocialAPIMessageSupport,
  SocialRequestTarget
}
import services.social.services.FriendRequestService
import system.api.{APIMessage, APIMessageError, APIMessageRouter}
import system.api.APIMessageRouter.APIMessageRequestAlias
import system.api.RegisteredAPIMessage.apiWithContext

private[route] object SocialHttp4sRoutes {
  def routes(service: FriendRequestService): HttpRoutes[IO] =
    postAliasRoutes(service) <+> getAliasRoutes(service) <+> compatibilityRoutes

  private def postAliasRoutes(service: FriendRequestService): HttpRoutes[IO] =
    APIMessageRouter.aliasRoutes(
      socialPostApiMessages(service),
      pathAliases = Map(
        "/social/friend-requests" -> APIMessage.apiNameFromClass[FriendRequestCreateAPIMessage],
        "/api/social/friend-requests" -> APIMessage.apiNameFromClass[FriendRequestCreateAPIMessage],
        "/social/friend-requests/respond" -> APIMessage.apiNameFromClass[FriendRequestRespondAPIMessage],
        "/api/social/friend-requests/respond" -> APIMessage.apiNameFromClass[FriendRequestRespondAPIMessage]
      ),
      responseTransform = withCors,
      errorHandler = socialAPIMessageErrorResponse
    )

  private def socialPostApiMessages(service: FriendRequestService): List[system.api.RegisteredAPIMessage] =
    List(
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

  private def getAliasRoutes(service: FriendRequestService): HttpRoutes[IO] =
    APIMessageRouter.requestAliasRoutes(
      apiMessages = List(
        apiWithContext[
          FriendRequestService,
          FriendRequestListAPIMessage,
          FriendRequestListResponse
        ](service, SocialAPIMessageSupport.invalidJsonObject)
      ),
      aliasForRequest = socialGetAlias(service),
      errorHandler = socialAPIMessageErrorResponse
    )

  private def socialGetAlias(service: FriendRequestService)(request: Request[IO]): Option[APIMessageRequestAlias] =
    Option.when(request.method == Method.GET && SocialRequestTarget.isFriendRequestPath(requestPath(request)))(
      APIMessageRequestAlias.fromContextMessage[FriendRequestService, FriendRequestListAPIMessage, FriendRequestListResponse](
        context = service,
        message = SocialAPIParser.listMessageFromQuery(request.params),
        responseTransform = withCors
      )
    )

  private def compatibilityRoutes: HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case request if SocialRequestTarget.isFriendRequestRespondPath(requestPath(request)) =>
        request.method match {
          case Method.OPTIONS =>
            corsNoContent
          case _ =>
            errorResponse(socialApiError(SocialApiErrorCode.MethodNotAllowed))
        }
      case request if SocialRequestTarget.isFriendRequestPath(requestPath(request)) =>
        request.method match {
          case Method.OPTIONS =>
            corsNoContent
          case _ =>
            errorResponse(socialApiError(SocialApiErrorCode.MethodNotAllowed))
        }
    }

  private def socialAPIMessageErrorResponse: PartialFunction[Throwable, IO[Response[IO]]] = {
    case _: MessageFailure =>
      errorResponse(socialApiError(SocialApiErrorCode.InvalidJsonObject))
    case error: APIMessageError =>
      errorResponse(socialApiError(socialApiErrorCode(error)))
  }

  private def socialApiError(code: SocialApiErrorCode): HttpApiError =
    typedApiError(
      statusCode = SocialApiErrorCode.statusCode(code),
      code = SocialApiErrorCode.wireValue(code),
      message = SocialApiErrorCode.message(code)
    )

  private def socialApiErrorCode(error: APIMessageError): SocialApiErrorCode =
    SocialApiErrorCode.values
      .find(code =>
        error.getMessage == SocialApiErrorCode.message(code) ||
          error.getMessage == SocialApiErrorCode.wireValue(code)
      )
      .getOrElse(SocialApiErrorCode.InvalidJsonObject)

}
