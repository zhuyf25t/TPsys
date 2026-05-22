package slaydemo.backend.http4s

import cats.effect.IO
import org.http4s.Request

private[http4s] object Http4sRouteSupport {
  def blocking[A](thunk: => A): IO[A] =
    IO.blocking(thunk)

  def requestPath(request: Request[IO]): String =
    request.uri.path.renderString
}
