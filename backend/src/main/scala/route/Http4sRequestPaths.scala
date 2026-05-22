package route

import cats.effect.IO
import org.http4s.Request

private[route] object Http4sRequestPaths {
  def requestPath(request: Request[IO]): String =
    request.uri.path.renderString
}
