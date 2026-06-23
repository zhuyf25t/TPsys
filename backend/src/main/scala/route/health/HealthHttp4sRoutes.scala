package route.health

import cats.effect.IO
import cats.syntax.all.*
import org.http4s.{HttpRoutes, Method, Request}

import route.HttpApiError
import route.HttpApiErrors.typedApiError
import route.Http4sCors.{corsNoContent, corsOk, withCors}
import route.Http4sRequestPaths.requestPath
import route.Http4sResponses.errorResponse
import system.api.{APIMessageRouter, HealthAPIMessage}
import system.api.APIMessageRouter.APIMessageRequestAlias
import system.objects.{HealthApiErrorCode, HealthRequestTarget}
import system.services.HealthService

private[route] object HealthHttp4sRoutes {
  def routes(service: HealthService): HttpRoutes[IO] =
    getAliasRoutes(service) <+> compatibilityRoutes

  private def getAliasRoutes(service: HealthService): HttpRoutes[IO] =
    APIMessageRouter.requestAliasRoutes(
      apiMessages = List(HealthAPIMessage.registered(service)),
      aliasForRequest = healthGetAlias(service)
    )

  private def healthGetAlias(service: HealthService)(request: Request[IO]): Option[APIMessageRequestAlias] =
    Option.when(request.method == Method.GET && isHealthPath(request))(
      APIMessageRequestAlias.fromContextMessage[HealthService, HealthAPIMessage, system.objects.HealthResponse](
        context = service,
        message = HealthAPIMessage(),
        responseTransform = withCors
      )
    )

  private def compatibilityRoutes: HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case request if isHealthPath(request) =>
        request.method match {
          case Method.OPTIONS =>
            corsNoContent
          case Method.HEAD =>
            corsOk
          case _ =>
            errorResponse(healthApiError(HealthApiErrorCode.MethodNotAllowed))
        }
    }

  private def isHealthPath(request: Request[IO]): Boolean =
    HealthRequestTarget.isHealthPath(requestPath(request))

  private def healthApiError(code: HealthApiErrorCode): HttpApiError =
    typedApiError(
      statusCode = HealthApiErrorCode.statusCode(code),
      code = HealthApiErrorCode.wireValue(code),
      message = HealthApiErrorCode.message(code)
    )
}
