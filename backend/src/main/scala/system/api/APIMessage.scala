package system.api

import cats.effect.IO
import io.circe.{Decoder, Encoder, Error, Json}
import io.circe.syntax.*
import system.objects.UserId

import java.sql.Connection
import scala.reflect.ClassTag

trait APIMessage[Response]:
  def plan(connection: Connection): IO[Response]

trait APIWithTokenMessage[Response] extends APIMessage[Response]

trait APIMessageWithContext[Context, Response]:
  def plan(context: Context, connection: Connection): IO[Response]

trait APIWithTokenContextMessage[Context, Response] extends APIMessageWithContext[Context, Response]

final case class APIName(value: String) extends AnyVal

final case class RegisteredAPIMessage(
  apiName: APIName,
  requiresUserToken: Boolean,
  planJson: (Json, Connection) => IO[Json]
)

sealed abstract class APIMessageError(message: String) extends RuntimeException(message)

object APIMessageError:
  final case class BadRequest(message: String) extends APIMessageError(message)
  final case class Unauthorized(message: String) extends APIMessageError(message)
  final case class Forbidden(message: String) extends APIMessageError(message)
  final case class Conflict(message: String) extends APIMessageError(message)
  final case class NotFound(message: String) extends APIMessageError(message)

object APIMessage:
  def apiNameFromClassName(className: String): APIName =
    val objectName = className.stripSuffix("$")
    val baseName =
      if objectName.endsWith("APIMessagePlanner") then objectName.stripSuffix("APIMessagePlanner")
      else objectName.stripSuffix("APIMessage")
    APIName(baseName.toLowerCase)

  def apiNameFromClass[Message](using classTag: ClassTag[Message]): APIName =
    apiNameFromClassName(classTag.runtimeClass.getSimpleName)

  def injectedUserId(payload: Json): IO[UserId] =
    injectedUserIdValue(payload) match {
      case Right(userId) =>
        IO.pure(userId)
      case Left(message) =>
        IO.raiseError(APIMessageError.Unauthorized(message))
    }

  def injectedUserIdJson(userId: UserId): Json =
    Json.fromString(userId.value)

  def injectedUserIdValue(payload: Json): Either[String, UserId] =
    payload.hcursor.get[String]("userId") match {
      case Right(value) if value.trim.nonEmpty =>
        Right(UserId(value.trim))
      case _ =>
        Left("Login is required.")
    }

object RegisteredAPIMessage:
  def api[Message <: APIMessage[Response], Response](using
    Decoder[Message],
    Encoder[Response],
    ClassTag[Message]
  ): RegisteredAPIMessage =
    build[Message, Response](requiresUserToken = false)

  def apiWithToken[Message <: APIWithTokenMessage[Response], Response](using
    Decoder[Message],
    Encoder[Response],
    ClassTag[Message]
  ): RegisteredAPIMessage =
    build[Message, Response](requiresUserToken = true)

  def apiWithTokenAndContext[
    Context,
    Message <: APIWithTokenContextMessage[Context, Response],
    Response
  ](
    context: Context,
    decodeFailure: Error => APIMessageError = defaultDecodeFailure
  )(using
    Decoder[Message],
    Encoder[Response],
    ClassTag[Message]
  ): RegisteredAPIMessage =
    apiWithTokenAndContextFromClass[Context, Message, Response](
      context = context,
      decodeFailure = decodeFailure
    )

  def apiWithTokenAndContextFromClass[
    Context,
    Message <: APIWithTokenContextMessage[Context, Response],
    Response
  ](
    context: Context,
    decodeFailure: Error => APIMessageError = defaultDecodeFailure
  )(using
    Decoder[Message],
    Encoder[Response],
    ClassTag[Message]
  ): RegisteredAPIMessage =
    buildWithContext[Context, Message, Response](
      context = context,
      requiresUserToken = true,
      decodeFailure = decodeFailure
    )

  def apiWithContext[
    Context,
    Message <: APIMessageWithContext[Context, Response],
    Response
  ](
    context: Context,
    decodeFailure: Error => APIMessageError = defaultDecodeFailure
  )(using
    Decoder[Message],
    Encoder[Response],
    ClassTag[Message]
  ): RegisteredAPIMessage =
    buildWithContext[Context, Message, Response](
      context = context,
      requiresUserToken = false,
      decodeFailure = decodeFailure
    )

  private def build[Message <: APIMessage[Response], Response](requiresUserToken: Boolean)(using
    Decoder[Message],
    Encoder[Response],
    ClassTag[Message]
  ): RegisteredAPIMessage =
    RegisteredAPIMessage(
      apiName = nameOf[Message],
      requiresUserToken = requiresUserToken,
      planJson = (payload, connection) =>
        for
          message <- IO.fromEither(
            payload.as[Message].left.map(error => APIMessageError.BadRequest(s"Invalid request body: ${error.getMessage}"))
          )
          response <- message.plan(connection)
        yield response.asJson
    )

  private def buildWithContext[
    Context,
    Message <: APIMessageWithContext[Context, Response],
    Response
  ](
    context: Context,
    requiresUserToken: Boolean,
    decodeFailure: Error => APIMessageError
  )(using
    Decoder[Message],
    Encoder[Response],
    ClassTag[Message]
  ): RegisteredAPIMessage =
    RegisteredAPIMessage(
      apiName = nameOf[Message],
      requiresUserToken = requiresUserToken,
      planJson = (payload, connection) =>
        for
          message <- IO.fromEither(payload.as[Message].left.map(decodeFailure))
          response <- message.plan(context, connection)
        yield response.asJson
    )

  private def defaultDecodeFailure(error: Error): APIMessageError =
    APIMessageError.BadRequest(s"Invalid request body: ${error.getMessage}")

  private def nameOf[Message](using ClassTag[Message]): APIName =
    APIMessage.apiNameFromClass[Message]
