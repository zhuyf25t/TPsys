package route

import cats.effect.IO
import org.http4s.{EntityDecoder, Request}

private[route] object Http4sRequestDecoders {
  def decodeEntityBody[E, A](
    request: Request[IO],
    invalidBody: E
  )(using EntityDecoder[IO, A]): IO[Either[E, A]] =
    request.as[A].attempt.map(_.left.map(_ => invalidBody))
}
