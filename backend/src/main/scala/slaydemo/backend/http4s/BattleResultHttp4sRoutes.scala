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
  BattleResultRecordResponse,
  BattleResultRequestTarget
}
import slaydemo.backend.battle.services.{BattleResultRecordError, BattleResultService}
import slaydemo.backend.http4s.Http4sRouteSupport.{blocking, codeMessageError, corsNoContent, corsOk, errorResponse, methodNotAllowedError, renderError, requestPath, typedApiError, withCors}

private[http4s] object BattleResultHttp4sRoutes {
  private val MethodNotAllowedError =
    methodNotAllowedError("Only GET, POST, HEAD, and OPTIONS are supported.")
  private val BadJsonError =
    typedApiError(statusCode = 400, code = "bad_request", message = "Request body must be a JSON object.")
  private val InvalidBattleIdError =
    codeMessageError(statusCode = 400, code = "invalid_battle_id")
  private val InvalidHandleError =
    codeMessageError(statusCode = 400, code = "invalid_handle")
  private val VisitorNotAllowedError =
    codeMessageError(statusCode = 403, code = "visitor_not_allowed")

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
                errorResponse(resultRecordDecodeApiError(error))
              case Right(command) =>
                blocking(service.record(command)).map {
                  case Right(record) =>
                    withCors(Response[IO](Status.Created).withEntity(BattleResultRecordResponse.fromRecord(record).asJson))
                  case Left(error) =>
                    resultRecordError(error)
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

  private def resultRecordError(error: BattleResultRecordError): Response[IO] =
    renderError(resultRecordApiError(error))

  private def resultRecordApiError(error: BattleResultRecordError): HttpApiError =
    error match {
      case BattleResultRecordError.InvalidHandle     => InvalidHandleError
      case BattleResultRecordError.VisitorNotAllowed => VisitorNotAllowedError
    }
}
