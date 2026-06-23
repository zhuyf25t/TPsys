package route.mail

import cats.effect.IO
import cats.syntax.all.*
import org.http4s.{HttpRoutes, MessageFailure, Method, Request, Response}

import route.HttpApiError
import route.HttpApiErrors.typedApiError
import route.Http4sCors.{corsNoContent, withCors}
import route.Http4sRequestPaths.requestPath
import route.Http4sResponses.errorResponse
import services.mail.api.{
  MailAPIParser,
  MailAPIMessageSupport,
  MailApiErrorCode,
  MailListAPIMessage,
  MailListResponse,
  MailReadAPIMessage,
  MailReadResponse,
  MailRequestTarget
}
import services.mail.services.MailService
import system.api.{APIMessage, APIMessageError, APIMessageRouter}
import system.api.APIMessageRouter.APIMessageRequestAlias
import system.api.RegisteredAPIMessage.apiWithContext

private[route] object MailHttp4sRoutes {
  def routes(service: MailService): HttpRoutes[IO] =
    postAliasRoutes(service) <+> getAliasRoutes(service) <+> compatibilityRoutes

  private def postAliasRoutes(service: MailService): HttpRoutes[IO] =
    APIMessageRouter.aliasRoutes(
      apiMessages = List(
        apiWithContext[
          MailService,
          MailReadAPIMessage,
          MailReadResponse
        ](service, MailAPIMessageSupport.invalidJsonObject)
      ),
      pathAliases = Map(
        "/mails/read" -> APIMessage.apiNameFromClass[MailReadAPIMessage],
        "/api/mails/read" -> APIMessage.apiNameFromClass[MailReadAPIMessage]
      ),
      responseTransform = withCors,
      errorHandler = mailAPIMessageErrorResponse
    )

  private def getAliasRoutes(service: MailService): HttpRoutes[IO] =
    APIMessageRouter.requestAliasRoutes(
      apiMessages = List(
        apiWithContext[
          MailService,
          MailListAPIMessage,
          MailListResponse
        ](service, MailAPIMessageSupport.invalidJsonObject)
      ),
      aliasForRequest = mailGetAlias(service),
      errorHandler = mailAPIMessageErrorResponse
    )

  private def mailGetAlias(service: MailService)(request: Request[IO]): Option[APIMessageRequestAlias] =
    Option.when(request.method == Method.GET && MailRequestTarget.isListPath(requestPath(request)))(
      APIMessageRequestAlias.fromContextMessage[MailService, MailListAPIMessage, MailListResponse](
        context = service,
        message = MailAPIParser.listMessageFromQuery(request.params),
        responseTransform = withCors
      )
    )

  private def compatibilityRoutes: HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case request if MailRequestTarget.isListPath(requestPath(request)) =>
        request.method match {
          case Method.OPTIONS =>
            corsNoContent
          case _ =>
            errorResponse(mailApiError(MailApiErrorCode.MethodNotAllowed))
        }
      case request if MailRequestTarget.isReadPath(requestPath(request)) =>
        request.method match {
          case Method.OPTIONS =>
            corsNoContent
          case _ =>
            errorResponse(mailApiError(MailApiErrorCode.MethodNotAllowed))
        }
    }

  private def mailAPIMessageErrorResponse: PartialFunction[Throwable, IO[Response[IO]]] = {
    case _: MessageFailure =>
      errorResponse(mailApiError(MailApiErrorCode.InvalidJsonObject))
    case error: APIMessageError =>
      errorResponse(mailApiError(mailApiErrorCode(error)))
  }

  private def mailApiError(code: MailApiErrorCode): HttpApiError =
    typedApiError(
      statusCode = MailApiErrorCode.statusCode(code),
      code = MailApiErrorCode.wireValue(code),
      message = MailApiErrorCode.message(code)
    )

  private def mailApiErrorCode(error: APIMessageError): MailApiErrorCode =
    MailApiErrorCode.values
      .find(code =>
        error.getMessage == MailApiErrorCode.message(code) ||
          error.getMessage == MailApiErrorCode.wireValue(code)
      )
      .getOrElse(MailApiErrorCode.InvalidJsonObject)

}
