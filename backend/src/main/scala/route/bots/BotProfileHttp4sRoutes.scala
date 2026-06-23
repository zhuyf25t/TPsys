package route.bots

import cats.effect.IO
import cats.syntax.all.*
import org.http4s.{HttpRoutes, Method, Request}

import services.bots.api.{BotProfileApiErrorCode, BotProfileRequestTarget, BotProfilesAPIMessage, BotProfilesResponse}
import services.bots.services.BotProfileService
import route.HttpApiError
import route.HttpApiErrors.typedApiError
import route.Http4sCors.{corsNoContent, corsOk, withCors}
import route.Http4sRequestPaths.requestPath
import route.Http4sResponses.errorResponse
import system.api.APIMessageRouter
import system.api.APIMessageRouter.APIMessageRequestAlias
import system.api.RegisteredAPIMessage.apiWithContext

private[route] object BotProfileHttp4sRoutes {
  def routes(service: BotProfileService): HttpRoutes[IO] =
    getAliasRoutes(service) <+> compatibilityRoutes

  private def getAliasRoutes(service: BotProfileService): HttpRoutes[IO] =
    APIMessageRouter.requestAliasRoutes(
      apiMessages = List(
        apiWithContext[
          BotProfileService,
          BotProfilesAPIMessage,
          BotProfilesResponse
        ](service)
      ),
      aliasForRequest = botProfileGetAlias(service)
    )

  private def botProfileGetAlias(service: BotProfileService)(request: Request[IO]): Option[APIMessageRequestAlias] =
    Option.when(request.method == Method.GET && isBotProfilePath(request))(
      APIMessageRequestAlias.fromContextMessage[BotProfileService, BotProfilesAPIMessage, BotProfilesResponse](
        context = service,
        message = BotProfilesAPIMessage(),
        responseTransform = withCors
      )
    )

  private def compatibilityRoutes: HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case request if isBotProfilePath(request) =>
        request.method match {
          case Method.OPTIONS =>
            corsNoContent
          case Method.HEAD =>
            corsOk
          case _ =>
            errorResponse(botProfileApiError(BotProfileApiErrorCode.MethodNotAllowed))
        }
    }

  private def isBotProfilePath(request: Request[IO]): Boolean =
    BotProfileRequestTarget.isProfilePath(requestPath(request))

  private def botProfileApiError(code: BotProfileApiErrorCode): HttpApiError =
    typedApiError(
      statusCode = BotProfileApiErrorCode.statusCode(code),
      code = BotProfileApiErrorCode.wireValue(code),
      message = BotProfileApiErrorCode.message(code)
    )
}
