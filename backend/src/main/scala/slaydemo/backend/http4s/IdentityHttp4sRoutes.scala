package slaydemo.backend.http4s

import cats.effect.IO
import io.circe.syntax.*
import org.http4s.circe.CirceEntityDecoder
import org.http4s.{Headers, HttpRoutes, Method, Request, Response, Status}
import org.typelevel.ci.CIString

import slaydemo.backend.http4s.Http4sRouteSupport.{apiError, blocking, corsNoContent, decodeEntityBody, errorResponse, jsonOk, requestPath}
import slaydemo.backend.identity.api.{
  IdentityApiErrorCode,
  IdentityApiRequestDecodeError,
  IdentityAccountsResponse,
  IdentityAuthResponse,
  IdentityRegistrationApiRequest,
  IdentityRequestTarget,
  IdentitySessionApiRequest,
  IdentitySessionTokenParser
}
import slaydemo.backend.identity.objects.SessionToken
import slaydemo.backend.identity.services.IdentityService

private[http4s] object IdentityHttp4sRoutes {
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
        registrationRequest.toCommand match {
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
        sessionRequest.toCommand match {
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
    apiError(
      status = identityApiStatus(code),
      code = IdentityApiErrorCode.wireValue(code),
      message = IdentityApiErrorCode.message(code)
    )

  private def identityApiStatus(code: IdentityApiErrorCode): Status =
    code match {
      case IdentityApiErrorCode.PostMethodNotAllowed => Status.MethodNotAllowed
      case IdentityApiErrorCode.GetMethodNotAllowed  => Status.MethodNotAllowed
      case IdentityApiErrorCode.HandleTaken          => Status.Conflict
      case IdentityApiErrorCode.InvalidCredentials   => Status.Unauthorized
      case IdentityApiErrorCode.MissingSession       => Status.Unauthorized
      case IdentityApiErrorCode.InvalidSession       => Status.Unauthorized
      case IdentityApiErrorCode.InvalidJsonObject    => Status.BadRequest
      case IdentityApiErrorCode.InvalidHandle        => Status.BadRequest
      case IdentityApiErrorCode.InvalidPassword      => Status.BadRequest
      case IdentityApiErrorCode.InvalidSkin          => Status.BadRequest
    }

}
