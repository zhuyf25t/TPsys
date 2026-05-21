package slaydemo.backend.http4s

import cats.effect.IO
import io.circe.Json
import org.http4s.{Header, Response, Status}
import org.http4s.circe.CirceEntityEncoder.*
import org.typelevel.ci.CIString

private[http4s] final case class HttpApiError(
  status: Status,
  code: String,
  message: String
)

private[http4s] object Http4sRouteSupport {
  def blocking[A](thunk: => A): IO[A] =
    IO.blocking(thunk)

  def apiError(status: Status, code: String, message: String): Response[IO] =
    apiError(HttpApiError(status = status, code = code, message = message))

  def apiError(error: HttpApiError): Response[IO] =
    withCors(
      Response[IO](error.status).withEntity(
        Json.obj(
          "error" -> Json.fromString(error.message),
          "code" -> Json.fromString(error.code)
        )
      )
    )

  def withCors(response: Response[IO]): Response[IO] =
    response.putHeaders(
      Header.Raw(CIString("Access-Control-Allow-Origin"), "*"),
      Header.Raw(CIString("Access-Control-Allow-Headers"), "Content-Type, Authorization, X-Session-Token"),
      Header.Raw(CIString("Access-Control-Allow-Methods"), "GET, POST, OPTIONS, HEAD")
    )
}
