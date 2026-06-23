package route.battle

import scala.concurrent.duration.*

import cats.effect.{IO, Resource}
import cats.syntax.all.*
import fs2.{Stream, text}
import io.circe.syntax.*
import org.http4s.{Header, Headers, HttpRoutes, Response, Status}
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.io.*
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.websocket.WebSocketFrame
import org.typelevel.ci.CIString
import services.identity.objects.SessionToken
import services.identity.services.{IdentityCurrentSessionError, IdentityService}
import services.battle.routes.{BattleAPIRuntimeContext, BattleRoutes}
import services.battle.microservices.runtime.api.{
  BattleChannelAPIEncoding,
  BattleCommandAPIMessage,
  BattleRuntimeChannelAPIMessagePlanner,
  BattleRuntimeChannelStateReadError
}
import services.battle.microservices.runtime.api.BattleCommandAPIMessage.given
import services.battle.microservices.runtime.objects.command.{BattleCommandAccepted, BattleCommandRequest}
import services.battle.microservices.session.services.BattleStateService
import services.battle.objects.core.{BattleAggregateState, BattleId}
import system.api.{APIMessage, APIMessageError, APIMessageRouter, APIName}
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
    val commandCompatibilityApiMessages = BattleRoutes.commandCompatibilityApiMessages(context)
    APIMessageRouter.routes(
      apiMessages = BattleRoutes.queueApiMessages(context),
      resolveUserToken = resolveUserToken(identityService)
    ) <+> APIMessageRouter.routes(
      apiMessages = BattleRoutes.runtimeApiMessages(context),
      resolveUserToken = resolveUserToken(identityService)
    ) <+> APIMessageRouter.routes(
      apiMessages = BattleRoutes.connectionBackedResultApiMessages,
      resolveUserToken = resolveUserToken(identityService),
      connectionResource = connectionResource
    ) <+> APIMessageRouter.aliasRoutes(
      apiMessages = commandCompatibilityApiMessages,
      pathAliases = commandCompatibilityPathAliases(commandCompatibilityApiMessages.head.apiName),
      responseTransform = withCors
    ) <+> publicBattleRoutes(context.stateService, webSocketBuilder)

  private def commandCompatibilityPathAliases(apiName: APIName): Map[String, APIName] =
    Map(
      "/battle/command" -> apiName,
      "/api/battle/command" -> apiName
    )

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
          case Left(BattleRuntimeChannelStateReadError.BattleNotFound) =>
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
    BattleCommandAPIMessage.decodeCommandText(text) match {
      case Left(error) =>
        IO.pure(BattleChannelAPIEncoding.battleCommandErrorMessage(BattleCommandAPIMessage.commandDecodeErrorCode(error)))
      case Right(command) =>
        commandPlan(stateService, command).attempt.map {
          case Right(accepted) =>
              BattleChannelAPIEncoding.battleCommandAcceptedMessage(accepted)
            case Left(error) =>
              BattleChannelAPIEncoding.battleCommandErrorMessage(commandPlanErrorCode(error))
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
    BattleCommandAPIMessage.decodeCommandText(text) match {
      case Left(error) =>
        IO.pure(BattleChannelAPIEncoding.commandErrorJson(BattleCommandAPIMessage.commandDecodeErrorCode(error)))
      case Right(command) =>
        commandPlan(stateService, command).attempt.map {
          case Right(accepted) =>
              BattleChannelAPIEncoding.commandAcceptedJson(accepted)
            case Left(error) =>
              BattleChannelAPIEncoding.commandErrorJson(commandPlanErrorCode(error))
          }
    }

  private def commandPlanErrorCode(error: Throwable): String =
    error match {
      case APIMessageError.NotFound(message)       => message
      case APIMessageError.Forbidden(message)      => message
      case APIMessageError.BadRequest(message)     => message
      case APIMessageError.Unauthorized(message)   => message
      case _                                      => "battle_command_failed"
    }

  private def commandPlan(
    stateService: BattleStateService,
    command: BattleCommandRequest
  ): IO[BattleCommandAccepted] =
    BattleRuntimeChannelAPIMessagePlanner.submitCompatibilityCommand(stateService, command)

  private def jsonStateResponse(stateService: BattleStateService, battleId: BattleId): IO[Response[IO]] =
    readState(stateService, battleId).flatMap {
      case Right(state) =>
        Ok(BattleChannelAPIEncoding.stateJson(state)).map(withCors)
      case Left(BattleRuntimeChannelStateReadError.BattleNotFound) =>
        NotFound(ErrorResponse("battle_not_found").asJson).map(withCors)
    }

  private def stateStreamResponse(stateService: BattleStateService, battleId: BattleId): IO[Response[IO]] =
    readState(stateService, battleId).map {
      case Left(BattleRuntimeChannelStateReadError.BattleNotFound) =>
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
  ): IO[Either[BattleRuntimeChannelStateReadError, BattleAggregateState]] =
    BattleRuntimeChannelAPIMessagePlanner.readPublicState(stateService, battleId)

  private def renderStateEvent(state: BattleAggregateState): String =
    BattleChannelAPIEncoding.stateEvent(state)

  private def renderBattleChannelStateMessage(state: BattleAggregateState): String =
    BattleChannelAPIEncoding.battleStateMessage(state)

  private def stateStreamHeaders: Headers =
    Headers(
      Header.Raw(CIString("Content-Type"), "text/event-stream; charset=utf-8"),
      Header.Raw(CIString("Cache-Control"), "no-cache, no-transform"),
      Header.Raw(CIString("Connection"), "keep-alive")
    )

  private def resolveUserToken(identityService: IdentityService)(
    userToken: String,
    connection: Connection
  ) =
    identityService.current(SessionToken.fromString(userToken)).flatMap {
      case Right(account) =>
        IO.pure(APIMessage.injectedUserIdJson(account.userId))
      case Left(IdentityCurrentSessionError.MissingSession) =>
        IO.raiseError(APIMessageError.Unauthorized("Login is required."))
      case Left(IdentityCurrentSessionError.InvalidSession) =>
        IO.raiseError(APIMessageError.Unauthorized("Session token is not valid."))
    }
}
