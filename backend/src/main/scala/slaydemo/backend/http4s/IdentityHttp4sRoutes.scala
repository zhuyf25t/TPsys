package slaydemo.backend.http4s

import cats.effect.IO
import io.circe.syntax.*
import org.http4s.circe.{CirceEntityDecoder, CirceEntityEncoder}
import org.http4s.dsl.io.*
import org.http4s.{Headers, HttpRoutes, Method, Request, Response, Status}
import org.typelevel.ci.CIString

import slaydemo.backend.http4s.Http4sRouteSupport.{apiError, blocking, typedApiError, withCors}
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
  import CirceEntityEncoder.*

  def routes(service: IdentityService): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case request if IdentityRequestTarget.isRegisterPath(path(request)) =>
        request.method match {
          case Method.OPTIONS =>
            IO.pure(withCors(Response[IO](Status.NoContent)))
          case Method.POST =>
            register(request, service)
          case _ =>
            IO.pure(apiError(identityApiError(IdentityApiErrorCode.PostMethodNotAllowed)))
        }
      case request if IdentityRequestTarget.isSessionPath(path(request)) =>
        request.method match {
          case Method.OPTIONS =>
            IO.pure(withCors(Response[IO](Status.NoContent)))
          case Method.POST =>
            issueSession(request, service)
          case _ =>
            IO.pure(apiError(identityApiError(IdentityApiErrorCode.PostMethodNotAllowed)))
        }
      case request if IdentityRequestTarget.isCurrentPath(path(request)) =>
        request.method match {
          case Method.OPTIONS =>
            IO.pure(withCors(Response[IO](Status.NoContent)))
          case Method.GET =>
            current(request, service)
          case _ =>
            IO.pure(apiError(identityApiError(IdentityApiErrorCode.GetMethodNotAllowed)))
        }
      case request if IdentityRequestTarget.isAccountsPath(path(request)) =>
        request.method match {
          case Method.OPTIONS =>
            IO.pure(withCors(Response[IO](Status.NoContent)))
          case Method.GET =>
            blocking(service.listActiveAccounts()).flatMap(accounts =>
              Ok(IdentityAccountsResponse(accounts).asJson).map(withCors)
            )
          case _ =>
            IO.pure(apiError(identityApiError(IdentityApiErrorCode.GetMethodNotAllowed)))
        }
    }

  private def register(request: Request[IO], service: IdentityService): IO[Response[IO]] =
    readRegistrationRequest(request).flatMap {
      case Left(IdentityApiRequestDecodeError.InvalidJsonObject) =>
        IO.pure(apiError(identityApiError(IdentityApiErrorCode.InvalidJsonObject)))
      case Right(registrationRequest) =>
        registrationRequest.toCommand match {
          case Left(error) =>
            IO.pure(apiError(identityApiError(IdentityApiErrorCode.fromRegistrationParseError(error))))
          case Right(command) =>
            blocking(service.register(command)).flatMap {
              case Right(account) =>
                Ok(IdentityAuthResponse.fromAccount(account).asJson).map(withCors)
              case Left(error) =>
                IO.pure(apiError(identityApiError(IdentityApiErrorCode.fromRegistrationServiceError(error))))
            }
        }
    }

  private def issueSession(request: Request[IO], service: IdentityService): IO[Response[IO]] =
    readSessionRequest(request).flatMap {
      case Left(IdentityApiRequestDecodeError.InvalidJsonObject) =>
        IO.pure(apiError(identityApiError(IdentityApiErrorCode.InvalidJsonObject)))
      case Right(sessionRequest) =>
        sessionRequest.toCommand match {
          case Left(error) =>
            IO.pure(apiError(identityApiError(IdentityApiErrorCode.fromSessionParseError(error))))
          case Right(command) =>
            blocking(service.issueSession(command)).flatMap {
              case Right(account) =>
                Ok(IdentityAuthResponse.fromAccount(account).asJson).map(withCors)
              case Left(error) =>
                IO.pure(apiError(identityApiError(IdentityApiErrorCode.fromSessionServiceError(error))))
            }
        }
    }

  private def current(request: Request[IO], service: IdentityService): IO[Response[IO]] =
    blocking(service.current(parseSessionToken(request))).flatMap {
      case Right(account) =>
        Ok(IdentityAuthResponse.fromAccount(account).asJson).map(withCors)
      case Left(error) =>
        IO.pure(apiError(identityApiError(IdentityApiErrorCode.fromCurrentSessionError(error))))
    }

  private def readRegistrationRequest(request: Request[IO]): IO[Either[IdentityApiRequestDecodeError, IdentityRegistrationApiRequest]] =
    request
      .as[IdentityRegistrationApiRequest]
      .attempt
      .map(_.left.map(_ => IdentityApiRequestDecodeError.InvalidJsonObject))

  private def readSessionRequest(request: Request[IO]): IO[Either[IdentityApiRequestDecodeError, IdentitySessionApiRequest]] =
    request
      .as[IdentitySessionApiRequest]
      .attempt
      .map(_.left.map(_ => IdentityApiRequestDecodeError.InvalidJsonObject))

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

  private def path(request: Request[IO]): String =
    request.uri.path.renderString

}
