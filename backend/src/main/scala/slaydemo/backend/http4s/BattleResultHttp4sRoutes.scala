package slaydemo.backend.http4s

import cats.effect.IO
import io.circe.syntax.*
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.io.*
import org.http4s.{HttpRoutes, Method, Request, Response, Status}

import slaydemo.backend.battle.objects.apiTypes.{
  BattleResultApiCodec,
  BattleResultListResponse,
  BattleResultListQueryDecodeResult,
  BattleResultRecordDecodeError,
  BattleResultRecordResponse
}
import slaydemo.backend.battle.services.{BattleResultRecordError, BattleResultService}
import slaydemo.backend.http4s.Http4sRouteSupport.{apiError, blocking, withCors}

private[http4s] object BattleResultHttp4sRoutes {
  private val AllowedPaths: Set[String] =
    Set("/battle/results", "/api/battle/results", "/battleresultsapi", "/api/battleresultsapi")

  private val MethodNotAllowedError =
    HttpApiError(status = Status.MethodNotAllowed, code = "method_not_allowed", message = "Only GET, POST, HEAD, and OPTIONS are supported.")
  private val BadJsonError =
    HttpApiError(status = Status.BadRequest, code = "bad_request", message = "Request body must be a JSON object.")
  private val InvalidBattleIdError =
    HttpApiError(status = Status.BadRequest, code = "invalid_battle_id", message = "invalid_battle_id")
  private val InvalidHandleError =
    HttpApiError(status = Status.BadRequest, code = "invalid_handle", message = "invalid_handle")
  private val VisitorNotAllowedError =
    HttpApiError(status = Status.Forbidden, code = "visitor_not_allowed", message = "visitor_not_allowed")

  def routes(service: BattleResultService): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case request if isBattleResultPath(request) =>
        request.method match {
          case Method.OPTIONS =>
            IO.pure(withCors(Response[IO](Status.NoContent)))
          case Method.HEAD =>
            IO.pure(withCors(Response[IO](Status.Ok)))
          case Method.GET =>
            BattleResultApiCodec.parseListRequest(request.uri.query.renderString) match {
              case BattleResultListQueryDecodeResult.EmptyResults =>
                Ok(BattleResultListResponse.Empty.asJson).map(withCors)
              case BattleResultListQueryDecodeResult.Query(listRequest) =>
                blocking(
                  service.list(
                    handle = listRequest.handle,
                    battleId = listRequest.battleId,
                    limit = listRequest.limit
                  )
                ).flatMap(records => Ok(BattleResultListResponse.fromRecords(records).asJson).map(withCors))
            }
          case Method.POST =>
            request.bodyText.compile.string.map(BattleResultApiCodec.parseRecordCommand).flatMap {
              case Left(error) =>
                IO.pure(resultRecordDecodeError(error))
              case Right(command) =>
                blocking(service.record(command)).map {
                  case Right(record) =>
                    withCors(Response[IO](Status.Created).withEntity(BattleResultRecordResponse.fromRecord(record).asJson))
                  case Left(error) =>
                    resultRecordError(error)
                }
            }
          case _ =>
            IO.pure(apiError(MethodNotAllowedError))
        }
    }

  private def isBattleResultPath(request: Request[IO]): Boolean =
    AllowedPaths.contains(request.uri.path.renderString)

  private def resultRecordDecodeError(error: BattleResultRecordDecodeError): Response[IO] =
    apiError(resultRecordDecodeApiError(error))

  private def resultRecordDecodeApiError(error: BattleResultRecordDecodeError): HttpApiError =
    error match {
      case BattleResultRecordDecodeError.BadJson           => BadJsonError
      case BattleResultRecordDecodeError.InvalidBattleId   => InvalidBattleIdError
      case BattleResultRecordDecodeError.InvalidHandle     => InvalidHandleError
      case BattleResultRecordDecodeError.VisitorNotAllowed => VisitorNotAllowedError
    }

  private def resultRecordError(error: BattleResultRecordError): Response[IO] =
    apiError(resultRecordApiError(error))

  private def resultRecordApiError(error: BattleResultRecordError): HttpApiError =
    error match {
      case BattleResultRecordError.InvalidHandle     => InvalidHandleError
      case BattleResultRecordError.VisitorNotAllowed => VisitorNotAllowedError
    }
}
