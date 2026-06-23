package route.identity

import cats.effect.IO
import cats.syntax.all.*
import org.http4s.{Headers, HttpRoutes, MessageFailure, Method, Request, Response}
import org.typelevel.ci.CIString

import route.HttpApiError
import route.HttpApiErrors.typedApiError
import route.Http4sCors.{corsNoContent, withCors}
import route.Http4sRequestPaths.requestPath
import route.Http4sResponses.errorResponse
import services.identity.api.{
  IdentityAccountsAPIMessage,
  IdentityAccountsResponse,
  IdentityAPIMessageSupport,
  IdentityAuthResponse,
  IdentityApiErrorCode,
  IdentityCurrentAPIMessage,
  IdentityRegisterAPIMessage,
  IdentityRequestTarget,
  IdentitySessionAPIMessage,
  IdentitySessionTokenParser
}
import services.identity.objects.SessionToken
import services.identity.services.IdentityService
import system.api.{APIMessage, APIMessageError, APIMessageRouter}
import system.api.APIMessageRouter.APIMessageRequestAlias
import system.api.RegisteredAPIMessage.apiWithContext

private[route] object IdentityHttp4sRoutes {
  def routes(service: IdentityService): HttpRoutes[IO] =
    postAliasRoutes(service) <+> getAliasRoutes(service) <+> compatibilityRoutes

  private def postAliasRoutes(service: IdentityService): HttpRoutes[IO] =
    APIMessageRouter.aliasRoutes(
      apiMessages = List(
        apiWithContext[
          IdentityService,
          IdentityRegisterAPIMessage,
          IdentityAuthResponse
        ](service, IdentityAPIMessageSupport.invalidJsonObject),
        apiWithContext[
          IdentityService,
          IdentitySessionAPIMessage,
          IdentityAuthResponse
        ](service, IdentityAPIMessageSupport.invalidJsonObject)
      ),
      pathAliases = Map(
        "/identity/register" -> APIMessage.apiNameFromClass[IdentityRegisterAPIMessage],
        "/api/identity/register" -> APIMessage.apiNameFromClass[IdentityRegisterAPIMessage],
        "/identity/session" -> APIMessage.apiNameFromClass[IdentitySessionAPIMessage],
        "/api/identity/session" -> APIMessage.apiNameFromClass[IdentitySessionAPIMessage]
      ),
      responseTransform = withCors,
      errorHandler = identityAPIMessageErrorResponse
    )

  private def getAliasRoutes(service: IdentityService): HttpRoutes[IO] =
    APIMessageRouter.requestAliasRoutes(
      apiMessages = List(
        apiWithContext[
          IdentityService,
          IdentityCurrentAPIMessage,
          IdentityAuthResponse
        ](service, IdentityAPIMessageSupport.invalidJsonObject),
        apiWithContext[
          IdentityService,
          IdentityAccountsAPIMessage,
          IdentityAccountsResponse
        ](service, IdentityAPIMessageSupport.invalidJsonObject)
      ),
      aliasForRequest = identityGetAlias(service),
      errorHandler = identityAPIMessageErrorResponse
    )

  private def identityGetAlias(service: IdentityService)(request: Request[IO]): Option[APIMessageRequestAlias] =
    if request.method != Method.GET then None
    else if IdentityRequestTarget.isCurrentPath(requestPath(request)) then
      Some(
        APIMessageRequestAlias.fromContextMessage[IdentityService, IdentityCurrentAPIMessage, IdentityAuthResponse](
          context = service,
          message = IdentityCurrentAPIMessage(parseSessionToken(request)),
          responseTransform = withCors
        )
      )
    else if IdentityRequestTarget.isAccountsPath(requestPath(request)) then
      Some(
        APIMessageRequestAlias.fromContextMessage[IdentityService, IdentityAccountsAPIMessage, IdentityAccountsResponse](
          context = service,
          message = IdentityAccountsAPIMessage(),
          responseTransform = withCors
        )
      )
    else None

  private def compatibilityRoutes: HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case request if IdentityRequestTarget.isRegisterPath(requestPath(request)) =>
        request.method match {
          case Method.OPTIONS =>
            corsNoContent
          case _ =>
            errorResponse(identityApiError(IdentityApiErrorCode.PostMethodNotAllowed))
        }
      case request if IdentityRequestTarget.isSessionPath(requestPath(request)) =>
        request.method match {
          case Method.OPTIONS =>
            corsNoContent
          case _ =>
            errorResponse(identityApiError(IdentityApiErrorCode.PostMethodNotAllowed))
        }
      case request if IdentityRequestTarget.isCurrentPath(requestPath(request)) =>
        request.method match {
          case Method.OPTIONS =>
            corsNoContent
          case _ =>
            errorResponse(identityApiError(IdentityApiErrorCode.GetMethodNotAllowed))
        }
      case request if IdentityRequestTarget.isAccountsPath(requestPath(request)) =>
        request.method match {
          case Method.OPTIONS =>
            corsNoContent
          case _ =>
            errorResponse(identityApiError(IdentityApiErrorCode.GetMethodNotAllowed))
        }
    }

  private def identityAPIMessageErrorResponse: PartialFunction[Throwable, IO[Response[IO]]] = {
    case _: MessageFailure =>
      errorResponse(identityApiError(IdentityApiErrorCode.InvalidJsonObject))
    case error: APIMessageError =>
      errorResponse(identityApiError(identityApiErrorCode(error)))
  }

  private def parseSessionToken(request: Request[IO]): Option[SessionToken] =
    IdentitySessionTokenParser.parseFromHeaderLookup(name => headerValue(request.headers, name))

  private def headerValue(headers: Headers, name: String): Option[String] =
    headers.get(CIString(name)).flatMap(_.toList.headOption.map(_.value))

  private def identityApiError(code: IdentityApiErrorCode): HttpApiError =
    typedApiError(
      statusCode = IdentityApiErrorCode.statusCode(code),
      code = IdentityApiErrorCode.wireValue(code),
      message = IdentityApiErrorCode.message(code)
    )

  private def identityApiErrorCode(error: APIMessageError): IdentityApiErrorCode =
    IdentityApiErrorCode.values
      .find(code =>
        error.getMessage == IdentityApiErrorCode.message(code) ||
          error.getMessage == IdentityApiErrorCode.wireValue(code)
      )
      .getOrElse(IdentityApiErrorCode.InvalidJsonObject)

}
