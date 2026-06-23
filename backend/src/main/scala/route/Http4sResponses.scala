package route

import cats.effect.IO
import org.http4s.Response
import org.http4s.circe.CirceEntityEncoder.*

import route.Http4sCors.withCors

private[route] object Http4sResponses {
  def renderError(error: HttpApiError): Response[IO] =
    withCors(
      Response[IO](error.status).withEntity(
        HttpApiErrorResponse.fromError(error)
      )
    )

  def errorResponse(error: HttpApiError): IO[Response[IO]] =
    IO.pure(renderError(error))
}
