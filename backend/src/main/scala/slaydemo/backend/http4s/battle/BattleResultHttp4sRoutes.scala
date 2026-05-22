package slaydemo.backend.http4s.battle

import cats.effect.IO
import io.circe.syntax.*
import org.http4s.{HttpRoutes, Method, Request}

import slaydemo.backend.battle.objects.apiTypes.{
  BattleResultApiCodec,
  BattleResultApiErrorCode,
  BattleResultListResponse,
  BattleResultListQueryDecodeResult,
  BattleResultRecordDecodeError,
  BattleResultRecordResponse,
  BattleResultRequestTarget
}
import slaydemo.backend.battle.services.{BattleResultRecordError, BattleResultService}
import slaydemo.backend.http4s.HttpApiError
import slaydemo.backend.http4s.HttpApiErrors.typedApiError
import slaydemo.backend.http4s.Http4sCors.{corsNoContent, corsOk}
import slaydemo.backend.http4s.Http4sEffects.blocking
import slaydemo.backend.http4s.Http4sRequestPaths.requestPath
import slaydemo.backend.http4s.Http4sResponses.{errorResponse, jsonCreated, jsonOk}

private[http4s] object BattleResultHttp4sRoutes {
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
            errorResponse(battleResultApiError(BattleResultApiErrorCode.MethodNotAllowed))
        }
    }

  private def isBattleResultPath(request: Request[IO]): Boolean =
    BattleResultRequestTarget.isResultPath(requestPath(request))

  private def resultRecordDecodeApiError(error: BattleResultRecordDecodeError): HttpApiError =
    battleResultApiError(BattleResultApiErrorCode.fromRecordDecodeError(error))

  private def resultRecordApiError(error: BattleResultRecordError): HttpApiError =
    battleResultApiError(BattleResultApiErrorCode.fromRecordError(error))

  private def battleResultApiError(code: BattleResultApiErrorCode): HttpApiError =
    typedApiError(
      statusCode = BattleResultApiErrorCode.statusCode(code),
      code = BattleResultApiErrorCode.wireValue(code),
      message = BattleResultApiErrorCode.message(code)
    )
}
