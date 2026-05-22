package slaydemo.backend.http4s

import cats.effect.IO
import io.circe.Json
import org.http4s.{EntityDecoder, Request}
import org.http4s.circe.CirceEntityDecoder.*

private[http4s] object Http4sRequestDecoders {
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
}
