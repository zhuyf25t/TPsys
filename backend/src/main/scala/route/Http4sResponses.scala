package route

import cats.effect.IO
import io.circe.Json
import org.http4s.{Response, Status}
import org.http4s.circe.CirceEntityEncoder.*

import route.Http4sCors.withCors

private[route] object Http4sResponses {
  def jsonOk(json: Json): IO[Response[IO]] =
    IO.pure(withCors(Response[IO](Status.Ok).withEntity(json)))

  def jsonCreated(json: Json): IO[Response[IO]] =
    IO.pure(withCors(Response[IO](Status.Created).withEntity(json)))

  def renderError(error: HttpApiError): Response[IO] =
    withCors(
      Response[IO](error.status).withEntity(
        HttpApiErrorResponse.fromError(error)
      )
    )

  def errorResponse(error: HttpApiError): IO[Response[IO]] =
    IO.pure(renderError(error))
}
