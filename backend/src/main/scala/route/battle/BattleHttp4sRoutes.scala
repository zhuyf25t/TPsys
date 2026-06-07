package route.battle

import scala.concurrent.duration.*

import cats.effect.{IO, Resource}
import cats.syntax.all.*
import fs2.{Stream, text}
import io.circe.Json
import io.circe.syntax.*
import org.http4s.{Header, Headers, HttpRoutes, Response, Status}
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.io.*
import org.typelevel.ci.CIString
import services.identity.objects.SessionToken
import services.identity.services.{IdentityCurrentSessionError, IdentityService}
import services.battle.routes.{BattleAPIRuntimeContext, BattleRoutes}
import services.battle.microservices.session.api.state.BattleStateRootResponse.given
import services.battle.microservices.session.services.{BattleStateReadError, BattleStateService}
import services.battle.objects.core.{BattleAggregateState, BattleId}
import system.api.{APIMessageError, APIMessageRouter}
import system.objects.ErrorResponse

import route.Http4sCors.withCors

import java.sql.Connection

object BattleHttp4sRoutes {
  private val StateStreamInterval = 150.millis

  def routes(
    context: BattleAPIRuntimeContext,
    identityService: IdentityService,
    connectionResource: Resource[IO, Connection]
  ): HttpRoutes[IO] =
    APIMessageRouter.routes(
      apiMessages = BattleRoutes.runtimeApiMessages(context),
      resolveUserToken = resolveUserToken(identityService)
    ) <+> APIMessageRouter.routes(
      apiMessages = BattleRoutes.connectionBackedResultApiMessages,
      resolveUserToken = resolveUserToken(identityService),
      connectionResource = connectionResource
    ) <+> publicStateRoutes(context.stateService)

  private def publicStateRoutes(stateService: BattleStateService): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case request @ GET -> Root / "battle" / "state" / "stream" =>
        stateStreamResponse(stateService, BattleId(request.params.getOrElse("battleId", "")))

      case request @ GET -> Root / "api" / "battle" / "state" / "stream" =>
        stateStreamResponse(stateService, BattleId(request.params.getOrElse("battleId", "")))

      case GET -> Root / "battle" / "state" / battleId =>
        jsonStateResponse(stateService, BattleId(battleId))

      case GET -> Root / "api" / "battle" / "state" / battleId =>
        jsonStateResponse(stateService, BattleId(battleId))
    }

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
