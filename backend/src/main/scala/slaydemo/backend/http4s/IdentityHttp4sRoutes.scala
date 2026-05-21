package slaydemo.backend.http4s

import cats.effect.IO
import io.circe.syntax.*
import org.http4s.circe.{CirceEntityDecoder, CirceEntityEncoder}
import org.http4s.dsl.io.*
import org.http4s.{Headers, HttpRoutes, Method, Request, Response, Status}
import org.typelevel.ci.CIString

import slaydemo.backend.http4s.Http4sRouteSupport.{apiError, blocking, withCors}
import slaydemo.backend.identity.api.{
  IdentityAccountsResponse,
  IdentityAuthResponse,
  IdentityCommandParsers,
  IdentityRegistrationApiRequest,
  IdentityRegistrationCommandParseError,
  IdentitySessionApiRequest,
  IdentitySessionCommandParseError,
  IdentitySessionTokenParser
}
import slaydemo.backend.identity.objects.SessionToken
import slaydemo.backend.identity.services.{
  IdentityCurrentSessionError,
  IdentityRegistrationError,
  IdentityService,
  IdentitySessionError
}

private[http4s] object IdentityHttp4sRoutes {
  private val RegisterPaths: Set[String] =
    Set("/identity/register", "/api/identity/register")
  private val SessionPaths: Set[String] =
    Set("/identity/session", "/api/identity/session")
  private val CurrentPaths: Set[String] =
    Set("/identity/me", "/api/identity/me")
  private val AccountsPaths: Set[String] =
    Set("/identity/accounts", "/api/identity/accounts")

  private val PostMethodNotAllowedError =
    HttpApiError(status = Status.MethodNotAllowed, code = "method_not_allowed", message = "Only POST and OPTIONS are supported.")
  private val GetMethodNotAllowedError =
    HttpApiError(status = Status.MethodNotAllowed, code = "method_not_allowed", message = "Only GET and OPTIONS are supported.")
  private val InvalidHandleError =
    HttpApiError(status = Status.BadRequest, code = "invalid_handle", message = "Handle must be 3-16 characters and use letters, numbers, -, _.")
  private val InvalidPasswordError =
    HttpApiError(status = Status.BadRequest, code = "invalid_password", message = "Password must be at least 4 characters.")
  private val InvalidSkinError =
    HttpApiError(status = Status.BadRequest, code = "invalid_skin", message = "Skin must be one of: blue, old, soldier, survivor.")
  private val HandleTakenError =
    HttpApiError(status = Status.Conflict, code = "handle_taken", message = "Handle already exists.")
  private val InvalidCredentialsError =
    HttpApiError(status = Status.Unauthorized, code = "invalid_credentials", message = "Handle or password is incorrect.")
  private val MissingSessionError =
    HttpApiError(status = Status.Unauthorized, code = "missing_session", message = "Session token is required.")
  private val InvalidSessionError =
    HttpApiError(status = Status.Unauthorized, code = "invalid_session", message = "Current session is not valid.")

  import CirceEntityDecoder.*
  import CirceEntityEncoder.*

  def routes(service: IdentityService): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case request if RegisterPaths.contains(path(request)) =>
        request.method match {
          case Method.OPTIONS =>
            IO.pure(withCors(Response[IO](Status.NoContent)))
          case Method.POST =>
            register(request, service)
          case _ =>
            IO.pure(apiError(PostMethodNotAllowedError))
        }
      case request if SessionPaths.contains(path(request)) =>
        request.method match {
          case Method.OPTIONS =>
            IO.pure(withCors(Response[IO](Status.NoContent)))
          case Method.POST =>
            issueSession(request, service)
          case _ =>
            IO.pure(apiError(PostMethodNotAllowedError))
        }
      case request if CurrentPaths.contains(path(request)) =>
        request.method match {
          case Method.OPTIONS =>
            IO.pure(withCors(Response[IO](Status.NoContent)))
          case Method.GET =>
            current(request, service)
          case _ =>
            IO.pure(apiError(GetMethodNotAllowedError))
        }
      case request if AccountsPaths.contains(path(request)) =>
        request.method match {
          case Method.OPTIONS =>
            IO.pure(withCors(Response[IO](Status.NoContent)))
          case Method.GET =>
            blocking(service.listActiveAccounts()).flatMap(accounts =>
              Ok(IdentityAccountsResponse(accounts).asJson).map(withCors)
            )
          case _ =>
            IO.pure(apiError(GetMethodNotAllowedError))
        }
    }

  private def register(request: Request[IO], service: IdentityService): IO[Response[IO]] =
    readRegistrationRequest(request).flatMap {
      case Left(message) =>
        IO.pure(apiError(badRequest(message)))
      case Right(registrationRequest) =>
        IdentityCommandParsers.parseRegistrationCommand(registrationRequest) match {
          case Left(IdentityRegistrationCommandParseError.InvalidHandle) =>
            IO.pure(apiError(InvalidHandleError))
          case Left(IdentityRegistrationCommandParseError.InvalidPassword) =>
            IO.pure(apiError(InvalidPasswordError))
          case Left(IdentityRegistrationCommandParseError.InvalidSkin) =>
            IO.pure(apiError(InvalidSkinError))
          case Right(command) =>
            blocking(service.register(command)).flatMap {
              case Right(account) =>
                Ok(IdentityAuthResponse.fromAccount(account).asJson).map(withCors)
              case Left(IdentityRegistrationError.HandleTaken) =>
                IO.pure(apiError(HandleTakenError))
            }
        }
    }

  private def issueSession(request: Request[IO], service: IdentityService): IO[Response[IO]] =
    readSessionRequest(request).flatMap {
      case Left(message) =>
        IO.pure(apiError(badRequest(message)))
      case Right(sessionRequest) =>
        IdentityCommandParsers.parseSessionCommand(sessionRequest) match {
          case Left(IdentitySessionCommandParseError.InvalidCredentials) =>
            IO.pure(apiError(InvalidCredentialsError))
          case Right(command) =>
            blocking(service.issueSession(command)).flatMap {
              case Right(account) =>
                Ok(IdentityAuthResponse.fromAccount(account).asJson).map(withCors)
              case Left(IdentitySessionError.InvalidCredentials) =>
                IO.pure(apiError(InvalidCredentialsError))
            }
        }
    }

  private def current(request: Request[IO], service: IdentityService): IO[Response[IO]] =
    blocking(service.current(parseSessionToken(request))).flatMap {
      case Right(account) =>
        Ok(IdentityAuthResponse.fromAccount(account).asJson).map(withCors)
      case Left(IdentityCurrentSessionError.MissingSession) =>
        IO.pure(apiError(MissingSessionError))
      case Left(IdentityCurrentSessionError.InvalidSession) =>
        IO.pure(apiError(InvalidSessionError))
    }

  private def readRegistrationRequest(request: Request[IO]): IO[Either[String, IdentityRegistrationApiRequest]] =
    request
      .as[IdentityRegistrationApiRequest]
      .attempt
      .map(_.left.map(_ => "Request body must be a JSON object with string fields."))

  private def readSessionRequest(request: Request[IO]): IO[Either[String, IdentitySessionApiRequest]] =
    request
      .as[IdentitySessionApiRequest]
      .attempt
      .map(_.left.map(_ => "Request body must be a JSON object with string fields."))

  private def parseSessionToken(request: Request[IO]): Option[SessionToken] =
    IdentitySessionTokenParser.parse(
      authorization = headerValue(request.headers, "Authorization"),
      xSessionToken = headerValue(request.headers, "X-Session-Token")
    )

  private def headerValue(headers: Headers, name: String): Option[String] =
    headers.get(CIString(name)).map(_.head.value)

  private def path(request: Request[IO]): String =
    request.uri.path.renderString

  private def badRequest(message: String): HttpApiError =
    HttpApiError(status = Status.BadRequest, code = "bad_request", message = message)
}
