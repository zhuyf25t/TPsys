package slaydemo.backend.http4s

import cats.effect.IO
import io.circe.syntax.*
import org.http4s.{HttpRoutes, Method, Request, Status}

import slaydemo.backend.battle.objects.apiTypes.{
  BattleResultApiCodec,
  BattleResultListResponse,
  BattleResultListQueryDecodeResult,
  BattleResultRecordDecodeError,
  BattleResultRecordResponse,
  BattleResultRequestTarget
}
import slaydemo.backend.battle.services.{BattleResultRecordError, BattleResultService}
import slaydemo.backend.http4s.Http4sRouteSupport.{apiError, blocking, corsNoContent, corsOk, errorResponse, jsonCreated, jsonOk, requestPath}

private[http4s] object BattleResultHttp4sRoutes {
  private val MethodNotAllowedError =
    apiError(
      Status.MethodNotAllowed,
      "method_not_allowed",
      "Only GET, POST, HEAD, and OPTIONS are supported."
    )
  private val BadJsonError =
    apiError(
      Status.BadRequest,
      "bad_request",
      "Request body must be a JSON object."
    )
  private val InvalidBattleIdError =
    apiError(
      Status.BadRequest,
      "invalid_battle_id",
      "invalid_battle_id"
    )
  private val InvalidHandleError =
    apiError(
      Status.BadRequest,
      "invalid_handle",
      "invalid_handle"
    )
  private val VisitorNotAllowedError =
    apiError(
      Status.Forbidden,
      "visitor_not_allowed",
      "visitor_not_allowed"
    )

  def routes(service: BattleResultService): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case request if isBattleResultPath(request) =>
        request.method match {
          case Method.OPTIONS =>
            corsNoContent
          case Method.HEAD =>
            corsOk
          case Method.GET =>
            BattleResultApiCodec.parseListRequest(request.params) match {
              case BattleResultListQueryDecodeResult.EmptyResults =>
                jsonOk(BattleResultListResponse.Empty.asJson)
              case BattleResultListQueryDecodeResult.Query(listRequest) =>
                blocking(
                  service.list(
                    handle = listRequest.handle,
                    battleId = listRequest.battleId,
                    limit = listRequest.limit
                  )
                ).flatMap(records => jsonOk(BattleResultListResponse.fromRecords(records).asJson))
            }
          case Method.POST =>
            request.bodyText.compile.string.map(BattleResultApiCodec.parseRecordCommand).flatMap {
              case Left(error) =>
                errorResponse(resultRecordDecodeApiError(error))
              case Right(command) =>
                blocking(service.record(command)).flatMap {
                  case Right(record) =>
                    jsonCreated(BattleResultRecordResponse.fromRecord(record).asJson)
                  case Left(error) =>
                    errorResponse(resultRecordApiError(error))
                }
            }
          case _ =>
            errorResponse(MethodNotAllowedError)
        }
    }

  private def isBattleResultPath(request: Request[IO]): Boolean =
    BattleResultRequestTarget.isResultPath(requestPath(request))

  private def resultRecordDecodeApiError(error: BattleResultRecordDecodeError): HttpApiError =
    error match {
      case BattleResultRecordDecodeError.BadJson           => BadJsonError
      case BattleResultRecordDecodeError.InvalidBattleId   => InvalidBattleIdError
      case BattleResultRecordDecodeError.InvalidHandle     => InvalidHandleError
      case BattleResultRecordDecodeError.VisitorNotAllowed => VisitorNotAllowedError
    }

  private def resultRecordApiError(error: BattleResultRecordError): HttpApiError =
    error match {
      case BattleResultRecordError.InvalidHandle     => InvalidHandleError
      case BattleResultRecordError.VisitorNotAllowed => VisitorNotAllowedError
    }
}
