package slaydemo.backend.http4s

import cats.effect.IO
import io.circe.Json
import org.http4s.{EntityDecoder, Request, Response, Status}
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.circe.CirceEntityEncoder.*

import slaydemo.backend.http4s.Http4sCors.withCors

private[http4s] object Http4sRouteSupport {
  def blocking[A](thunk: => A): IO[A] =
    IO.blocking(thunk)

  def requestPath(request: Request[IO]): String =
    request.uri.path.renderString

  def jsonOk(json: Json): IO[Response[IO]] =
    IO.pure(withCors(Response[IO](Status.Ok).withEntity(json)))

  def jsonCreated(json: Json): IO[Response[IO]] =
    IO.pure(withCors(Response[IO](Status.Created).withEntity(json)))

  def decodeJsonObjectBody[E, A](
    request: Request[IO],
    invalidJson: E
  )(decode: Json => Either[E, A]): IO[Either[E, A]] =
    request.as[Json].attempt.map {
      case Left(_) =>
        Left(invalidJson)
      case Right(json) if json.asObject.isEmpty =>
        Left(invalidJson)
      case Right(json) =>
        decode(json)
    }

  def decodeEntityBody[E, A](
    request: Request[IO],
    invalidBody: E
  )(using EntityDecoder[IO, A]): IO[Either[E, A]] =
    request.as[A].attempt.map(_.left.map(_ => invalidBody))

  def decodeTextBody[E, A](
    request: Request[IO],
    invalidBody: E
  )(decode: String => Either[E, A]): IO[Either[E, A]] =
    request.bodyText.compile.string.attempt.map {
      case Left(_) =>
        Left(invalidBody)
      case Right(body) =>
        decode(body)
    }

  def renderError(error: HttpApiError): Response[IO] =
    withCors(
      Response[IO](error.status).withEntity(
        Json.obj(
          "error" -> Json.fromString(error.message),
          "code" -> Json.fromString(error.code)
        )
      )
    )

  def errorResponse(error: HttpApiError): IO[Response[IO]] =
    IO.pure(renderError(error))

  def apiError(status: Status, code: String, message: String): HttpApiError =
    HttpApiError(status = status, code = code, message = message)

  def typedApiError(statusCode: Int, code: String, message: String): HttpApiError =
    apiError(status = statusFrom(statusCode), code = code, message = message)

  def methodNotAllowedError(message: String): HttpApiError =
    typedApiError(statusCode = 405, code = "method_not_allowed", message = message)

  def codeMessageError(statusCode: Int, code: String): HttpApiError =
    typedApiError(statusCode = statusCode, code = code, message = code)

  private def statusFrom(statusCode: Int): Status =
    statusCode match {
      case 400 => Status.BadRequest
      case 401 => Status.Unauthorized
      case 403 => Status.Forbidden
      case 404 => Status.NotFound
      case 405 => Status.MethodNotAllowed
      case 409 => Status.Conflict
      case _   => Status.InternalServerError
    }
}
