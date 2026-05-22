package slaydemo.backend.http4s

import cats.effect.IO
import io.circe.Json
import org.http4s.{Response, Status}
import org.http4s.circe.CirceEntityEncoder.*

import slaydemo.backend.http4s.Http4sCors.withCors

private[http4s] object Http4sResponses {
  def jsonOk(json: Json): IO[Response[IO]] =
    IO.pure(withCors(Response[IO](Status.Ok).withEntity(json)))

  def jsonCreated(json: Json): IO[Response[IO]] =
    IO.pure(withCors(Response[IO](Status.Created).withEntity(json)))

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
}
