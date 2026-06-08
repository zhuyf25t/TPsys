package route.battle

import scala.concurrent.duration.*

import cats.effect.{IO, Resource}
import cats.syntax.all.*
import fs2.{Stream, text}
import io.circe.Json
import io.circe.parser.parse
import io.circe.syntax.*
import org.http4s.{Header, Headers, HttpRoutes, Request, Response, Status}
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.io.*
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.websocket.WebSocketFrame
import org.typelevel.ci.CIString
import services.identity.objects.SessionToken
import services.identity.services.{IdentityCurrentSessionError, IdentityService}
import services.battle.routes.{BattleAPIRuntimeContext, BattleRoutes}
import services.battle.microservices.session.api.command.{
  BattleCommandAcceptedResponse,
  BattleCommandRequestDecodeError,
  BattleCommandRequestPayload
}
import services.battle.microservices.session.api.command.BattleCommandAcceptedResponse.given
import services.battle.microservices.session.api.state.BattleStateRootResponse.given
import services.battle.microservices.session.services.{BattleCommandSubmitError, BattleStateReadError, BattleStateService}
import services.battle.objects.core.{BattleAggregateState, BattleId}
import system.api.{APIMessageError, APIMessageRouter}
import system.objects.ErrorResponse

import route.Http4sCors.{corsNoContent, withCors}

import java.sql.Connection

object BattleHttp4sRoutes {
  private val StateStreamInterval = 33.millis

  def routes(
    context: BattleAPIRuntimeContext,
    identityService: IdentityService,
    connectionResource: Resource[IO, Connection],
    webSocketBuilder: Option[WebSocketBuilder2[IO]] = None
  ): HttpRoutes[IO] =
    APIMessageRouter.routes(
      apiMessages = BattleRoutes.runtimeApiMessages(context),
      resolveUserToken = resolveUserToken(identityService)
    ) <+> APIMessageRouter.routes(
      apiMessages = BattleRoutes.connectionBackedResultApiMessages,
      resolveUserToken = resolveUserToken(identityService),
      connectionResource = connectionResource
    ) <+> publicBattleRoutes(context.stateService, webSocketBuilder)

  private def publicBattleRoutes(
    stateService: BattleStateService,
    webSocketBuilder: Option[WebSocketBuilder2[IO]]
  ): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case GET -> Root / "battle" / "command" / "channel" =>
        commandChannelResponse(stateService, webSocketBuilder)

      case GET -> Root / "api" / "battle" / "command" / "channel" =>
        commandChannelResponse(stateService, webSocketBuilder)

      case request @ GET -> Root / "battle" / "channel" =>
        battleChannelResponse(
          stateService,
          webSocketBuilder,
          BattleId(request.params.getOrElse("battleId", ""))
        )

      case request @ GET -> Root / "api" / "battle" / "channel" =>
        battleChannelResponse(
          stateService,
          webSocketBuilder,
          BattleId(request.params.getOrElse("battleId", ""))
        )

      case OPTIONS -> Root / "battle" / "command" =>
        corsNoContent

      case OPTIONS -> Root / "api" / "battle" / "command" =>
        corsNoContent

      case request @ POST -> Root / "battle" / "command" =>
        commandSubmitResponse(stateService, request)

      case request @ POST -> Root / "api" / "battle" / "command" =>
        commandSubmitResponse(stateService, request)

      case request @ GET -> Root / "battle" / "state" / "stream" =>
        stateStreamResponse(stateService, BattleId(request.params.getOrElse("battleId", "")))

      case request @ GET -> Root / "api" / "battle" / "state" / "stream" =>
        stateStreamResponse(stateService, BattleId(request.params.getOrElse("battleId", "")))

      case GET -> Root / "battle" / "state" / battleId =>
        jsonStateResponse(stateService, BattleId(battleId))

      case GET -> Root / "api" / "battle" / "state" / battleId =>
        jsonStateResponse(stateService, BattleId(battleId))
    }

  private def battleChannelResponse(
    stateService: BattleStateService,
    webSocketBuilder: Option[WebSocketBuilder2[IO]],
    battleId: BattleId
  ): IO[Response[IO]] =
    webSocketBuilder match {
      case Some(builder) =>
        readState(stateService, battleId).flatMap {
          case Left(BattleStateReadError.BattleNotFound) =>
            NotFound(ErrorResponse("battle_not_found").asJson).map(withCors)
          case Right(firstState) =>
            builder.build { inbound =>
              val commandFrames =
                inbound
                  .collect { case frame: WebSocketFrame.Text => frame.str }
                  .evalMap(text => battleChannelCommandTextResponse(stateService, text))
                  .map(WebSocketFrame.Text(_))
              val stateFrames =
                battleChannelStateStream(stateService, battleId, firstState)
                  .map(message => WebSocketFrame.Text(message))

              stateFrames.merge(commandFrames)
            }
        }
      case None =>
        ServiceUnavailable(ErrorResponse("battle_channel_unavailable").asJson).map(withCors)
    }

  private def battleChannelCommandTextResponse(stateService: BattleStateService, text: String): IO[String] =
    parse(text).left
      .map(_ => BattleCommandRequestDecodeError.InvalidJsonObject)
      .flatMap(BattleCommandRequestPayload.decode) match {
        case Left(error) =>
          IO.pure(renderBattleChannelCommandMessage(commandErrorPayload(commandDecodeErrorCode(error))))
        case Right(command) =>
          stateService.acceptCommand(command).map {
            case Right(accepted) =>
              renderBattleChannelCommandMessage(accepted.asJson)
            case Left(error) =>
              renderBattleChannelCommandMessage(commandErrorPayload(commandSubmitErrorCode(error)))
          }
    }

  private def battleChannelStateStream(
    stateService: BattleStateService,
    battleId: BattleId,
    firstState: BattleAggregateState
  ): Stream[IO, String] =
    (Stream.emit(firstState) ++
      Stream.awakeEvery[IO](StateStreamInterval).evalMap(_ => readState(stateService, battleId)).collect {
        case Right(state) => state
      }).map(renderBattleChannelStateMessage)

  private def commandChannelResponse(
    stateService: BattleStateService,
    webSocketBuilder: Option[WebSocketBuilder2[IO]]
  ): IO[Response[IO]] =
    webSocketBuilder match {
      case Some(builder) =>
        builder.build { inbound =>
          inbound
            .collect { case frame: WebSocketFrame.Text => frame.str }
            .evalMap(text => commandChannelTextResponse(stateService, text))
            .map(WebSocketFrame.Text(_))
        }
      case None =>
        ServiceUnavailable(ErrorResponse("battle_command_channel_unavailable").asJson).map(withCors)
    }

  private def commandChannelTextResponse(stateService: BattleStateService, text: String): IO[String] =
    parse(text).left
      .map(_ => BattleCommandRequestDecodeError.InvalidJsonObject)
      .flatMap(BattleCommandRequestPayload.decode) match {
        case Left(error) =>
          IO.pure(commandErrorJson(commandDecodeErrorCode(error)))
        case Right(command) =>
          stateService.acceptCommand(command).map {
            case Right(accepted) =>
              accepted.asJson.noSpaces
            case Left(error) =>
              commandErrorJson(commandSubmitErrorCode(error))
          }
    }

  private def commandSubmitResponse(stateService: BattleStateService, request: Request[IO]): IO[Response[IO]] =
    request.as[Json].attempt.flatMap {
      case Left(_) =>
        BadRequest(ErrorResponse("invalid_battle_command_request").asJson).map(withCors)
      case Right(payload) =>
        BattleCommandRequestPayload.decode(payload) match {
          case Left(error) =>
            commandDecodeErrorResponse(error)
          case Right(command) =>
            stateService.acceptCommand(command).flatMap {
              case Right(accepted) =>
                Ok(accepted.asJson).map(withCors)
              case Left(error) =>
                commandSubmitErrorResponse(error)
            }
        }
    }

  private def commandDecodeErrorResponse(error: BattleCommandRequestDecodeError): IO[Response[IO]] =
    commandDecodeErrorCode(error) match {
      case "command_not_authorized" =>
        Forbidden(ErrorResponse("command_not_authorized").asJson).map(withCors)
      case code =>
        BadRequest(ErrorResponse(code).asJson).map(withCors)
    }

  private def commandSubmitErrorResponse(error: BattleCommandSubmitError): IO[Response[IO]] =
    commandSubmitErrorCode(error) match {
      case "battle_not_found" =>
        NotFound(ErrorResponse("battle_not_found").asJson).map(withCors)
      case "command_not_authorized" =>
        Forbidden(ErrorResponse("command_not_authorized").asJson).map(withCors)
      case code =>
        BadRequest(ErrorResponse(code).asJson).map(withCors)
    }

  private def commandDecodeErrorCode(error: BattleCommandRequestDecodeError): String =
    error match {
      case BattleCommandRequestDecodeError.MissingTicket =>
        "command_not_authorized"
      case BattleCommandRequestDecodeError.InvalidJsonObject =>
        "invalid_battle_command_request"
      case BattleCommandRequestDecodeError.InvalidField(field) =>
        s"invalid_battle_command_field_${field.toString}"
    }

  private def commandSubmitErrorCode(error: BattleCommandSubmitError): String =
    error match {
      case BattleCommandSubmitError.BattleNotFound             => "battle_not_found"
      case BattleCommandSubmitError.PlayerNotFound             => "player_not_found"
      case BattleCommandSubmitError.BotCommandsNotSupported    => "bot_commands_not_supported"
      case BattleCommandSubmitError.CommandNotAuthorized       => "command_not_authorized"
    }

  private def commandErrorJson(code: String): String =
    commandErrorPayload(code).noSpaces

  private def commandErrorPayload(code: String): Json =
    Json.obj("message" -> code.asJson)

  private def renderBattleChannelCommandMessage(payload: Json): String =
    Json.obj(
      "kind" -> "command".asJson,
      "payload" -> payload
    ).noSpaces

  private def renderBattleChannelStateMessage(state: BattleAggregateState): String =
    Json.obj(
      "kind" -> "state".asJson,
      "state" -> state.asJson
    ).noSpaces

  private def jsonStateResponse(stateService: BattleStateService, battleId: BattleId): IO[Response[IO]] =
    readState(stateService, battleId).flatMap {
      case Right(state) =>
        Ok(state.asJson).map(withCors)
      case Left(BattleStateReadError.BattleNotFound) =>
        NotFound(ErrorResponse("battle_not_found").asJson).map(withCors)
    }

  private def stateStreamResponse(stateService: BattleStateService, battleId: BattleId): IO[Response[IO]] =
    readState(stateService, battleId).map {
      case Left(BattleStateReadError.BattleNotFound) =>
        withCors(Response[IO](Status.NotFound).withEntity(ErrorResponse("battle_not_found").asJson))
      case Right(firstState) =>
        withCors(
          Response[IO](
            status = Status.Ok,
            headers = stateStreamHeaders,
            body = stateEventStream(stateService, battleId, firstState).through(text.utf8.encode)
          )
        )
    }

  private def stateEventStream(
    stateService: BattleStateService,
    battleId: BattleId,
    firstState: BattleAggregateState
  ): Stream[IO, String] =
    (Stream.emit(firstState) ++
      Stream.awakeEvery[IO](StateStreamInterval).evalMap(_ => readState(stateService, battleId)).collect {
        case Right(state) => state
      }).map(renderStateEvent)

  private def readState(
    stateService: BattleStateService,
    battleId: BattleId
  ): IO[Either[BattleStateReadError, BattleAggregateState]] =
    if battleId.value.trim.isEmpty then IO.pure(Left(BattleStateReadError.BattleNotFound))
    else stateService.currentState(battleId)

  private def renderStateEvent(state: BattleAggregateState): String =
    s"event: state\ndata: ${state.asJson.noSpaces}\n\n"

  private def stateStreamHeaders: Headers =
    Headers(
      Header.Raw(CIString("Content-Type"), "text/event-stream; charset=utf-8"),
      Header.Raw(CIString("Cache-Control"), "no-cache, no-transform"),
      Header.Raw(CIString("Connection"), "keep-alive")
    )

  private def resolveUserToken(identityService: IdentityService)(
    userToken: String,
    connection: Connection
  ): IO[Json] =
    identityService.current(SessionToken.fromString(userToken)).flatMap {
      case Right(account) =>
        IO.pure(Json.fromString(account.userId.value))
      case Left(IdentityCurrentSessionError.MissingSession) =>
        IO.raiseError(APIMessageError.Unauthorized("Login is required."))
      case Left(IdentityCurrentSessionError.InvalidSession) =>
        IO.raiseError(APIMessageError.Unauthorized("Session token is not valid."))
    }
}
