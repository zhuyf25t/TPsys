package slaydemo.backend.http4s

import cats.effect.IO
import fs2.Stream
import io.circe.syntax.*
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.io.*
import org.http4s.{Header, HttpRoutes, Method, Request, Response, Status}
import org.typelevel.ci.CIString

import java.nio.charset.StandardCharsets
import scala.concurrent.duration.*

import slaydemo.backend.battle.objects.apiTypes.{BattleStateRequestTarget, BattleStateResponse}
import slaydemo.backend.battle.objects.{BattleAggregateState, BattleId, BattlePhase}
import slaydemo.backend.battle.services.{BattleStateReadError, BattleStateService}
import slaydemo.backend.http4s.Http4sRouteSupport.{apiError, blocking, codeMessageError, methodNotAllowedError, typedApiError, withCors}

private[http4s] object BattleStateHttp4sRoutes {
  private val InvalidBattleIdError =
    typedApiError(statusCode = 400, code = "invalid_battle_id", message = "battleId is required.")
  private val BattleNotFoundError =
    codeMessageError(statusCode = 404, code = "battle_not_found")
  private val MethodNotAllowedError =
    methodNotAllowedError("Only GET, HEAD, and OPTIONS are supported.")

  def readRoutes(battleStateService: BattleStateService): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case request if isBattleStateReadPath(request) =>
        request.method match {
          case Method.OPTIONS =>
            IO.pure(withCors(Response[IO](Status.NoContent)))
          case Method.HEAD =>
            IO.pure(withCors(Response[IO](Status.Ok)))
          case Method.GET =>
            battleIdFromStateRequest(request) match {
              case None =>
                IO.pure(apiError(InvalidBattleIdError))
              case Some(battleId) =>
                blocking(battleStateService.currentState(battleId)).flatMap {
                  case Right(state) =>
                    Ok(BattleStateResponse.fromState(state).asJson).map(withCors)
                  case Left(BattleStateReadError.BattleNotFound) =>
                    IO.pure(apiError(BattleNotFoundError))
                }
            }
          case _ =>
            IO.pure(apiError(MethodNotAllowedError))
        }
    }

  def streamRoutes(battleStateService: BattleStateService): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case request if isBattleStateStreamPath(request) =>
        request.method match {
          case Method.OPTIONS =>
            IO.pure(withCors(Response[IO](Status.NoContent)))
          case Method.GET =>
            battleIdFromStateStreamRequest(request) match {
              case None =>
                IO.pure(apiError(InvalidBattleIdError))
              case Some(battleId) =>
                blocking(battleStateService.currentState(battleId)).map {
                  case Left(BattleStateReadError.BattleNotFound) =>
                    apiError(BattleNotFoundError)
                  case Right(state) =>
                    stateStreamResponse(battleId, state, battleStateService)
                }
            }
          case _ =>
            IO.pure(apiError(MethodNotAllowedError))
        }
    }

  private def isBattleStateReadPath(request: Request[IO]): Boolean =
    BattleStateRequestTarget.isReadPath(request.uri.path.renderString)

  private def isBattleStateStreamPath(request: Request[IO]): Boolean =
    BattleStateRequestTarget.isStreamPath(request.uri.path.renderString)

  private def battleIdFromStateRequest(request: Request[IO]): Option[BattleId] =
    BattleStateRequestTarget.battleIdFromRead(request.uri.path.renderString, request.params)

  private def battleIdFromStateStreamRequest(request: Request[IO]): Option[BattleId] =
    BattleStateRequestTarget.battleIdFromStream(request.params)

  private def stateStreamResponse(
    battleId: BattleId,
    initialState: BattleAggregateState,
    battleStateService: BattleStateService
  ): Response[IO] =
    withCors(
      Response[IO](Status.Ok)
        .withEntity(stateStreamBytes(battleId, initialState, battleStateService))
        .putHeaders(
          Header.Raw(CIString("Content-Type"), "text/event-stream; charset=utf-8"),
          Header.Raw(CIString("Cache-Control"), "no-cache"),
          Header.Raw(CIString("Connection"), "keep-alive")
        )
    )

  private def stateStreamBytes(
    battleId: BattleId,
    initialState: BattleAggregateState,
    battleStateService: BattleStateService
  ): Stream[IO, Byte] =
    stateStreamFrames(battleId, initialState, battleStateService)
      .flatMap(frame => Stream.emits(frame.getBytes(StandardCharsets.UTF_8)).covary[IO])

  private def stateStreamFrames(
    battleId: BattleId,
    initialState: BattleAggregateState,
    battleStateService: BattleStateService
  ): Stream[IO, String] = {
    def loop(state: BattleAggregateState): Stream[IO, String] = {
      val frame = s"event: state\ndata: ${BattleStateResponse.jsonString(state)}\n\n"
      if state.phase == BattlePhase.Finished then Stream.emit(frame)
      else
        Stream.emit(frame) ++
          Stream.sleep_[IO](33.millis) ++
          Stream
            .eval(blocking(battleStateService.currentState(battleId).toOption))
            .flatMap {
              case None       => Stream.empty
              case Some(next) => loop(next)
            }
    }

    loop(initialState)
  }
}
