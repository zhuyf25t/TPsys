package system.api

import cats.effect.{IO, Resource}
import io.circe.Json
import io.circe.syntax.*
import org.http4s.{HttpRoutes, InvalidMessageBodyFailure, Response, Status}
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.dsl.io.*
import system.objects.ErrorResponse

import java.lang.reflect.{InvocationHandler, Method, Proxy}
import java.sql.Connection

object APIMessageRouter:
  def routes(
    apiMessages: List[RegisteredAPIMessage],
    resolveUserToken: (String, Connection) => IO[Json] = unsupportedTokenResolver,
    connectionResource: Resource[IO, Connection] = UnsupportedConnectionResource.resource
  ): HttpRoutes[IO] =
    val apiMessagesByName = apiMessages.map(apiMessage => apiMessage.apiName.value -> apiMessage).toMap

    HttpRoutes.of[IO] {
      case req @ POST -> Root / "api" / apiName if apiMessagesByName.contains(apiName) =>
        val apiMessage = apiMessagesByName(apiName)
        handleErrors {
          runAPIMessage(req, apiMessage, resolveUserToken, connectionResource)
        }
    }

  private def runAPIMessage(
    req: org.http4s.Request[IO],
    apiMessage: RegisteredAPIMessage,
    resolveUserToken: (String, Connection) => IO[Json],
    connectionResource: Resource[IO, Connection]
  ): IO[Response[IO]] =
    connectionResource.use { connection =>
      for
        payload <- req.as[Json]
        backendPayload <- preparePayload(apiMessage, payload, connection, resolveUserToken)
        response <- apiMessage.planJson(backendPayload, connection)
        httpResponse <- Ok(response)
      yield httpResponse
    }

  private def preparePayload(
    apiMessage: RegisteredAPIMessage,
    payload: Json,
    connection: Connection,
    resolveUserToken: (String, Connection) => IO[Json]
  ): IO[Json] =
    if apiMessage.requiresUserToken then
      for
        userToken <- extractUserToken(payload)
        userIdJson <- resolveUserToken(userToken, connection)
        backendPayload <- replaceUserTokenWithUserId(payload, userIdJson)
      yield backendPayload
    else IO.pure(payload)

  private def extractUserToken(payload: Json): IO[String] =
    payload.hcursor.get[String]("userToken") match
      case Right(value) if value.trim.nonEmpty => IO.pure(value.trim)
      case _ => IO.raiseError(APIMessageError.Unauthorized("Login is required."))

  private def replaceUserTokenWithUserId(payload: Json, userIdJson: Json): IO[Json] =
    payload.asObject match
      case Some(value) =>
        IO.pure(Json.fromJsonObject(value.remove("userToken").add("userId", userIdJson)))
      case None =>
        IO.raiseError(APIMessageError.BadRequest("Request body must be a JSON object."))

  private def unsupportedTokenResolver(userToken: String, connection: Connection): IO[Json] =
    IO.raiseError(APIMessageError.Unauthorized("Login is required."))

  private def handleErrors(action: IO[Response[IO]]): IO[Response[IO]] =
    action.handleErrorWith {
      case error: InvalidMessageBodyFailure =>
        BadRequest(ErrorResponse(error.getMessage).asJson)
      case error: APIMessageError.BadRequest =>
        BadRequest(ErrorResponse(error.getMessage).asJson)
      case error: APIMessageError.Unauthorized =>
        IO.pure(Response[IO](Status.Unauthorized).withEntity(ErrorResponse(error.getMessage).asJson))
      case error: APIMessageError.Forbidden =>
        Forbidden(ErrorResponse(error.getMessage).asJson)
      case error: APIMessageError.Conflict =>
        Conflict(ErrorResponse(error.getMessage).asJson)
      case error: APIMessageError.NotFound =>
        NotFound(ErrorResponse(error.getMessage).asJson)
      case error =>
        InternalServerError(ErrorResponse(error.getMessage).asJson)
    }

private object UnsupportedConnectionResource:
  val resource: Resource[IO, Connection] =
    Resource.pure(UnsupportedConnectionProxy.create)

private object UnsupportedConnectionProxy:
  def create: Connection =
    Proxy
      .newProxyInstance(
        classOf[Connection].getClassLoader,
        Array(classOf[Connection]),
        handler
      )
      .asInstanceOf[Connection]

  private val handler: InvocationHandler =
    (_: AnyRef, method: Method, _: Array[AnyRef]) =>
      method.getName match
        case "close" =>
          ()
        case "isClosed" =>
          java.lang.Boolean.TRUE
        case "toString" =>
          "UnsupportedConnection(APIMessageRouter default)"
        case "isWrapperFor" =>
          java.lang.Boolean.FALSE
        case "unwrap" =>
          throw UnsupportedOperationException("No JDBC connection is configured for this API route.")
        case _ =>
          throw UnsupportedOperationException("No JDBC connection is configured for this API route.")
