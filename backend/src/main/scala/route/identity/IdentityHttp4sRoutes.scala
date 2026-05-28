package route.identity

import cats.effect.IO
import io.circe.syntax.*
import org.http4s.circe.CirceEntityDecoder
import org.http4s.{Headers, HttpRoutes, Method, Request, Response}
import org.typelevel.ci.CIString

import route.HttpApiError
import route.HttpApiErrors.typedApiError
import route.Http4sCors.corsNoContent
import route.Http4sEffects.blocking
import route.Http4sRequestDecoders.decodeEntityBody
import route.Http4sRequestPaths.requestPath
import route.Http4sResponses.{errorResponse, jsonOk}
import services.identity.api.{
  IdentityCommandParsers,
  IdentityApiErrorCode,
  IdentityApiRequestDecodeError,
  IdentityRequestTarget,
  IdentitySessionTokenParser
}
import services.identity.objects.SessionToken
import services.identity.objects.apiTypes.{
  IdentityAccountsResponse,
  IdentityAuthResponse,
  IdentityRegistrationApiRequest,
  IdentitySessionApiRequest
}
import services.identity.services.IdentityService

private[route] object IdentityHttp4sRoutes {
  import CirceEntityDecoder.*

  def routes(service: IdentityService): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case request if IdentityRequestTarget.isRegisterPath(requestPath(request)) =>
        request.method match {
          case Method.OPTIONS =>
            corsNoContent
          case Method.POST =>
            register(request, service)
          case _ =>
            errorResponse(identityApiError(IdentityApiErrorCode.PostMethodNotAllowed))
        }
      case request if IdentityRequestTarget.isSessionPath(requestPath(request)) =>
        request.method match {
          case Method.OPTIONS =>
            corsNoContent
          case Method.POST =>
            issueSession(request, service)
          case _ =>
            errorResponse(identityApiError(IdentityApiErrorCode.PostMethodNotAllowed))
        }
      case request if IdentityRequestTarget.isCurrentPath(requestPath(request)) =>
        request.method match {
          case Method.OPTIONS =>
            corsNoContent
          case Method.GET =>
            current(request, service)
          case _ =>
            errorResponse(identityApiError(IdentityApiErrorCode.GetMethodNotAllowed))
        }
      case request if IdentityRequestTarget.isAccountsPath(requestPath(request)) =>
        request.method match {
          case Method.OPTIONS =>
            corsNoContent
          case Method.GET =>
            blocking(service.listActiveAccounts()).flatMap(accounts =>
              jsonOk(IdentityAccountsResponse(accounts).asJson)
            )
          case _ =>
            errorResponse(identityApiError(IdentityApiErrorCode.GetMethodNotAllowed))
        }
    }

  private def register(request: Request[IO], service: IdentityService): IO[Response[IO]] =
    readRegistrationRequest(request).flatMap {
      case Left(IdentityApiRequestDecodeError.InvalidJsonObject) =>
        errorResponse(identityApiError(IdentityApiErrorCode.InvalidJsonObject))
      case Right(registrationRequest) =>
        IdentityCommandParsers.parseRegistrationCommand(registrationRequest) match {
          case Left(error) =>
            errorResponse(identityApiError(IdentityApiErrorCode.fromRegistrationParseError(error)))
          case Right(command) =>
            blocking(service.register(command)).flatMap {
              case Right(account) =>
                jsonOk(IdentityAuthResponse.fromAccount(account).asJson)
              case Left(error) =>
                errorResponse(identityApiError(IdentityApiErrorCode.fromRegistrationServiceError(error)))
            }
        }
    }

  private def issueSession(request: Request[IO], service: IdentityService): IO[Response[IO]] =
    readSessionRequest(request).flatMap {
      case Left(IdentityApiRequestDecodeError.InvalidJsonObject) =>
        errorResponse(identityApiError(IdentityApiErrorCode.InvalidJsonObject))
      case Right(sessionRequest) =>
        IdentityCommandParsers.parseSessionCommand(sessionRequest) match {
          case Left(error) =>
            errorResponse(identityApiError(IdentityApiErrorCode.fromSessionParseError(error)))
          case Right(command) =>
            blocking(service.issueSession(command)).flatMap {
              case Right(account) =>
                jsonOk(IdentityAuthResponse.fromAccount(account).asJson)
              case Left(error) =>
                errorResponse(identityApiError(IdentityApiErrorCode.fromSessionServiceError(error)))
            }
        }
    }

  private def current(request: Request[IO], service: IdentityService): IO[Response[IO]] =
    blocking(service.current(parseSessionToken(request))).flatMap {
      case Right(account) =>
        jsonOk(IdentityAuthResponse.fromAccount(account).asJson)
      case Left(error) =>
        errorResponse(identityApiError(IdentityApiErrorCode.fromCurrentSessionError(error)))
    }

  private def readRegistrationRequest(request: Request[IO]): IO[Either[IdentityApiRequestDecodeError, IdentityRegistrationApiRequest]] =
    decodeEntityBody[IdentityApiRequestDecodeError, IdentityRegistrationApiRequest](
      request,
      IdentityApiRequestDecodeError.InvalidJsonObject
    )

  private def readSessionRequest(request: Request[IO]): IO[Either[IdentityApiRequestDecodeError, IdentitySessionApiRequest]] =
    decodeEntityBody[IdentityApiRequestDecodeError, IdentitySessionApiRequest](
      request,
      IdentityApiRequestDecodeError.InvalidJsonObject
    )

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

}
