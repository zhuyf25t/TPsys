package slaydemo.backend.http4s

import cats.effect.IO
import org.http4s.{Header, Response, Status}
import org.typelevel.ci.CIString

private[http4s] object Http4sCors {
  def corsNoContent: IO[Response[IO]] =
    IO.pure(withCors(Response[IO](Status.NoContent)))

  def corsOk: IO[Response[IO]] =
    IO.pure(withCors(Response[IO](Status.Ok)))

  def withCors(response: Response[IO]): Response[IO] =
    response.putHeaders(
      Header.Raw(CIString("Access-Control-Allow-Origin"), "*"),
      Header.Raw(CIString("Access-Control-Allow-Headers"), "Content-Type, Authorization, X-Session-Token"),
      Header.Raw(CIString("Access-Control-Allow-Methods"), "GET, POST, OPTIONS, HEAD")
    )
}
