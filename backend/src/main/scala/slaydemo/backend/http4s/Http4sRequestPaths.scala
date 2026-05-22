package slaydemo.backend.http4s

import cats.effect.IO
import org.http4s.Request

private[http4s] object Http4sRequestPaths {
  def requestPath(request: Request[IO]): String =
    request.uri.path.renderString
}
