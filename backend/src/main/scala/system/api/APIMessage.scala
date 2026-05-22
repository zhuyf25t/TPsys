package system.api

import cats.effect.IO
import io.circe.{Decoder, Encoder, Json}
import io.circe.syntax.*

import scala.reflect.ClassTag

trait APIMessage[Response]:
  def plan: IO[Response]

trait APIWithTokenMessage[Response] extends APIMessage[Response]

trait NoRequestMessage[Response] extends APIMessage[Response]

final case class RegisteredAPIMessage(
  apiName: String,
  requiresUserToken: Boolean,
  planJson: Json => IO[Json]
)

sealed abstract class APIMessageError(message: String) extends RuntimeException(message)

object APIMessageError:
  final case class BadRequest(message: String) extends APIMessageError(message)
  final case class Unauthorized(message: String) extends APIMessageError(message)
  final case class Forbidden(message: String) extends APIMessageError(message)
  final case class Conflict(message: String) extends APIMessageError(message)
  final case class NotFound(message: String) extends APIMessageError(message)

object APIMessage:
  def apiNameFromClassName(className: String): String =
    val objectName = className.stripSuffix("$")
    val baseName = objectName.stripSuffix("APIMessage")
    baseName.toLowerCase

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

  def noRequest[Message <: NoRequestMessage[Response], Response](message: => Message)(using
    Encoder[Response],
    ClassTag[Message]
  ): RegisteredAPIMessage =
    RegisteredAPIMessage(
      apiName = nameOf[Message],
      requiresUserToken = false,
      planJson = _ => message.plan.map(_.asJson)
    )

  private def build[Message <: APIMessage[Response], Response](requiresUserToken: Boolean)(using
    Decoder[Message],
    Encoder[Response],
    ClassTag[Message]
  ): RegisteredAPIMessage =
    RegisteredAPIMessage(
      apiName = nameOf[Message],
      requiresUserToken = requiresUserToken,
      planJson = payload =>
        for
          message <- IO.fromEither(
            payload.as[Message].left.map(error => APIMessageError.BadRequest(s"Invalid request body: ${error.getMessage}"))
          )
          response <- message.plan
        yield response.asJson
    )

  private def nameOf[Message](using classTag: ClassTag[Message]): String =
    APIMessage.apiNameFromClassName(classTag.runtimeClass.getSimpleName)
