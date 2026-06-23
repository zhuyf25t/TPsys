package system.api

import cats.data.OptionT
import cats.effect.{IO, Resource}
import io.circe.{Decoder, Encoder, Json}
import io.circe.syntax.*
import org.http4s.{HttpRoutes, MessageFailure, Method, Request, Response, Status}
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.dsl.io.*
import system.objects.ErrorResponse

import java.sql.Connection
import scala.reflect.ClassTag

object APIMessageRouter:
  final case class APIMessageAlias(
    apiName: APIName,
    responseTransform: Response[IO] => Response[IO] = APIMessageAlias.keepResponse,
    directPlanJson: Option[(Request[IO], Connection) => IO[Json]] = None
  )

  final case class APIMessageRequestAlias(
    apiName: APIName,
    payload: Request[IO] => IO[Json] = APIMessageRequestAlias.emptyPayload,
    responseTransform: Response[IO] => Response[IO] = APIMessageAlias.keepResponse,
    directPlanJson: Option[Connection => IO[Json]] = None
  )

  object APIMessageRequestAlias:
    val emptyPayload: Request[IO] => IO[Json] =
      _ => IO.pure(Json.fromFields(Vector.empty))

    def fromMessage[Message <: APIMessage[ApiResponse], ApiResponse](
      message: Message,
      responseTransform: Response[IO] => Response[IO] = APIMessageAlias.keepResponse
    )(using Encoder[ApiResponse], ClassTag[Message]): APIMessageRequestAlias =
      APIMessageRequestAlias(
        apiName = APIMessage.apiNameFromClass[Message],
        responseTransform = responseTransform,
        directPlanJson = Some(connection => message.plan(connection).map(_.asJson))
      )

    def fromContextMessage[
      Context,
      Message <: APIMessageWithContext[Context, ApiResponse],
      ApiResponse
    ](
      context: Context,
      message: Message,
      responseTransform: Response[IO] => Response[IO] = APIMessageAlias.keepResponse
    )(using Encoder[ApiResponse], ClassTag[Message]): APIMessageRequestAlias =
      APIMessageRequestAlias(
        apiName = APIMessage.apiNameFromClass[Message],
        responseTransform = responseTransform,
        directPlanJson = Some(connection => message.plan(context, connection).map(_.asJson))
      )

  object APIMessageAlias:
    val keepResponse: Response[IO] => Response[IO] =
      response => response

    def fromContextMessage[
      Context,
      Message <: APIMessageWithContext[Context, ApiResponse],
      ApiResponse
    ](
      context: Context,
      transformMessage: Message => Message = identity[Message],
      responseTransform: Response[IO] => Response[IO] = keepResponse
    )(using Decoder[Message], Encoder[ApiResponse], ClassTag[Message]): APIMessageAlias =
      APIMessageAlias(
        apiName = APIMessage.apiNameFromClass[Message],
        responseTransform = responseTransform,
        directPlanJson = Some { (request, connection) =>
          for
            message <- request.as[Message].map(transformMessage)
            response <- message.plan(context, connection)
          yield response.asJson
        }
      )

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
        }()
    }

  def aliasRoutes(
    apiMessages: List[RegisteredAPIMessage],
    pathAliases: Map[String, APIName],
    resolveUserToken: (String, Connection) => IO[Json] = unsupportedTokenResolver,
    connectionResource: Resource[IO, Connection] = UnsupportedConnectionResource.resource,
    responseTransform: Response[IO] => Response[IO] = identity,
    errorHandler: PartialFunction[Throwable, IO[Response[IO]]] = PartialFunction.empty
  ): HttpRoutes[IO] =
    dynamicAliasRoutes(
      apiMessages = apiMessages,
      aliasForRequest = request =>
        pathAliases
          .get(normalizePath(request.uri.path.renderString))
          .map(apiName => APIMessageAlias(apiName = apiName, responseTransform = responseTransform)),
      resolveUserToken = resolveUserToken,
      connectionResource = connectionResource,
      errorHandler = errorHandler
    )

  def dynamicAliasRoutes(
    apiMessages: List[RegisteredAPIMessage],
    aliasForRequest: Request[IO] => Option[APIMessageAlias],
    resolveUserToken: (String, Connection) => IO[Json] = unsupportedTokenResolver,
    connectionResource: Resource[IO, Connection] = UnsupportedConnectionResource.resource,
    errorHandler: PartialFunction[Throwable, IO[Response[IO]]] = PartialFunction.empty
  ): HttpRoutes[IO] =
    val apiMessagesByName = apiMessages.map(apiMessage => apiMessage.apiName.value -> apiMessage).toMap

    HttpRoutes.of[IO] {
      case req if req.method == Method.POST && aliasForRequest(req).nonEmpty =>
        aliasForRequest(req) match
          case Some(alias) =>
            apiMessagesByName.get(alias.apiName.value) match
              case Some(apiMessage) =>
                alias.directPlanJson match
                  case Some(planJson) =>
                    handleErrors {
                      connectionResource.use { connection =>
                        for
                          responseJson <- planJson(req, connection)
                          response <- Ok(responseJson)
                        yield alias.responseTransform(response)
                      }
                    }(errorHandler)
                  case None =>
                    handleErrors {
                      runAPIMessage(
                        req = req,
                        apiMessage = apiMessage,
                        resolveUserToken = resolveUserToken,
                        connectionResource = connectionResource
                      ).map(alias.responseTransform)
                    }(errorHandler)
              case None =>
                InternalServerError(ErrorResponse("API alias target is not registered.").asJson).map(alias.responseTransform)
          case None =>
            NotFound()
    }

  def requestAliasRoutes(
    apiMessages: List[RegisteredAPIMessage],
    aliasForRequest: Request[IO] => Option[APIMessageRequestAlias],
    resolveUserToken: (String, Connection) => IO[Json] = unsupportedTokenResolver,
    connectionResource: Resource[IO, Connection] = UnsupportedConnectionResource.resource,
    errorHandler: PartialFunction[Throwable, IO[Response[IO]]] = PartialFunction.empty
  ): HttpRoutes[IO] =
    val apiMessagesByName = apiMessages.map(apiMessage => apiMessage.apiName.value -> apiMessage).toMap

    HttpRoutes[IO] { req =>
      aliasForRequest(req) match
        case Some(alias) =>
          val response =
            alias.directPlanJson match
              case Some(planJson) =>
                handleErrors {
                  connectionResource.use { connection =>
                    for
                      responseJson <- planJson(connection)
                      response <- Ok(responseJson)
                    yield alias.responseTransform(response)
                  }
                }(errorHandler)
              case None =>
                apiMessagesByName.get(alias.apiName.value) match
                  case Some(apiMessage) =>
                    handleErrors {
                      runAPIMessageFromPayload(
                        req = req,
                        apiMessage = apiMessage,
                        payload = alias.payload(req),
                        resolveUserToken = resolveUserToken,
                        connectionResource = connectionResource
                      ).map(alias.responseTransform)
                    }(errorHandler)
                  case None =>
                    InternalServerError(ErrorResponse("API alias target is not registered.").asJson)
                      .map(alias.responseTransform)
          OptionT.liftF(response)
        case None =>
          OptionT.none[IO, Response[IO]]
    }

  private def normalizePath(path: String): String =
    if path.endsWith("/") && path.length > 1 then path.dropRight(1) else path

  private def runAPIMessage(
    req: org.http4s.Request[IO],
    apiMessage: RegisteredAPIMessage,
    resolveUserToken: (String, Connection) => IO[Json],
    connectionResource: Resource[IO, Connection]
  ): IO[Response[IO]] =
    runAPIMessageFromPayload(
      req = req,
      apiMessage = apiMessage,
      payload = req.as[Json],
      resolveUserToken = resolveUserToken,
      connectionResource = connectionResource
    )

  private def runAPIMessageFromPayload(
    req: org.http4s.Request[IO],
    apiMessage: RegisteredAPIMessage,
    payload: IO[Json],
    resolveUserToken: (String, Connection) => IO[Json],
    connectionResource: Resource[IO, Connection]
  ): IO[Response[IO]] =
    connectionResource.use { connection =>
      for
        requestPayload <- payload
        backendPayload <- preparePayload(apiMessage, requestPayload, connection, resolveUserToken)
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

  private def handleErrors(
    action: IO[Response[IO]]
  )(errorHandler: PartialFunction[Throwable, IO[Response[IO]]] = PartialFunction.empty): IO[Response[IO]] =
    action.handleErrorWith {
      case error if errorHandler.isDefinedAt(error) =>
        errorHandler(error)
      case error: MessageFailure =>
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
    Resource.pure(UnsupportedAPIConnection.create)
