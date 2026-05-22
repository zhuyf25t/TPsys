package services.battle.api

import cats.effect.IO
import io.circe.{Encoder, Json}
import io.circe.syntax.*
import system.api.{APIMessage, APIMessageError, RegisteredAPIMessage}

private[api] object BattleAPIMessageSupport {
  def registered(className: String)(plan: Json => IO[Json]): RegisteredAPIMessage =
    RegisteredAPIMessage(
      apiName = APIMessage.apiNameFromClassName(className),
      requiresUserToken = false,
      planJson = plan
    )

  def encode[A](value: A)(using Encoder[A]): IO[Json] =
    IO.pure(value.asJson)

  def badRequest[A](message: String): IO[A] =
    IO.raiseError(APIMessageError.BadRequest(message))

  def unauthorized[A](message: String): IO[A] =
    IO.raiseError(APIMessageError.Unauthorized(message))

  def forbidden[A](message: String): IO[A] =
    IO.raiseError(APIMessageError.Forbidden(message))

  def notFound[A](message: String): IO[A] =
    IO.raiseError(APIMessageError.NotFound(message))
}
