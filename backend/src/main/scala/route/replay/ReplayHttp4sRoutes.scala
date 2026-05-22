package route.replay

import cats.effect.IO
import io.circe.Json
import io.circe.syntax.*
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.{HttpRoutes, Method, Request, Response}

import route.HttpApiError
import route.HttpApiErrors.typedApiError
import route.Http4sCors.{corsNoContent, corsOk}
import route.Http4sEffects.blocking
import route.Http4sRequestPaths.requestPath
import route.Http4sResponses.{errorResponse, jsonCreated, jsonOk}
import services.replay.objects.ReplayId
import services.replay.objects.apiTypes.{
  ReplayApiCodec,
  ReplayApiErrorCode,
  ReplayApiErrorMapper,
  ReplayCatalogResponse,
  ReplayCatalogTarget,
  ReplayCommentDecodeError,
  ReplayCommentResponse,
  ReplayCommentWrapperResponse,
  ReplayCommentsResponse,
  ReplayDetailRecordResponse,
  ReplayDetailResponse,
  ReplayRecordDecodeError
}
import services.replay.services.{
  ReplayCommentCommand,
  ReplayCommentError,
  ReplayRecordCommand,
  ReplayRecordError,
  ReplayService
}

private[route] object ReplayHttp4sRoutes {
  def catalogRoutes(service: ReplayService): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case request @ CatalogRequest(target) =>
        target match {
          case ReplayCatalogTarget.Collection =>
            handleCollection(service, request)
          case ReplayCatalogTarget.Detail(replayId) =>
            handleDetail(service, request, replayId)
          case ReplayCatalogTarget.Comments(replayId) =>
            handleComments(service, request, replayId)
          case ReplayCatalogTarget.InvalidReplayId =>
            errorResponse(replayApiError(ReplayApiErrorCode.InvalidReplayId))
        }
    }

  private def handleCollection(service: ReplayService, request: Request[IO]): IO[Response[IO]] =
    request.method match {
      case Method.OPTIONS =>
        corsNoContent
      case Method.HEAD =>
        corsOk
      case Method.GET =>
        val query = ReplayApiCodec.catalogQuery(request.params)
        blocking(service.list(query.limit)).flatMap { records =>
          val response = ReplayCatalogResponse.fromRecords(
            records = records,
            selectedHandle = query.selectedHandle
          )
          jsonOk(response.asJson)
        }
      case Method.POST =>
        decodeRecordRequest(request).flatMap {
          case Left(error) =>
            errorResponse(recordDecodeError(error))
          case Right(command) =>
            blocking(service.record(command)).flatMap {
              case Right(record) =>
                jsonCreated(ReplayDetailResponse(ReplayDetailRecordResponse.fromRecord(record, None)).asJson)
              case Left(error) =>
                errorResponse(recordServiceError(error))
            }
        }
      case _ =>
        errorResponse(replayApiError(ReplayApiErrorCode.MethodNotAllowed))
    }

  private def handleDetail(service: ReplayService, request: Request[IO], replayId: ReplayId): IO[Response[IO]] =
    request.method match {
      case Method.OPTIONS =>
        corsNoContent
      case Method.HEAD =>
        corsOk
      case Method.GET =>
        val query = ReplayApiCodec.catalogQuery(request.params)
        blocking(service.load(replayId)).flatMap {
          case Some(record) =>
            jsonOk(ReplayDetailResponse(ReplayDetailRecordResponse.fromRecord(record, query.selectedHandle)).asJson)
          case None =>
            errorResponse(replayApiError(ReplayApiErrorCode.ReplayNotFound))
        }
      case _ =>
        errorResponse(replayApiError(ReplayApiErrorCode.MethodNotAllowed))
    }

  private def handleComments(service: ReplayService, request: Request[IO], replayId: ReplayId): IO[Response[IO]] =
    request.method match {
      case Method.OPTIONS =>
        corsNoContent
      case Method.HEAD =>
        corsOk
      case Method.GET =>
        val query = ReplayApiCodec.catalogQuery(request.params)
        blocking(service.load(replayId)).flatMap {
          case None =>
            errorResponse(replayApiError(ReplayApiErrorCode.ReplayNotFound))
          case Some(_) =>
            blocking(service.listComments(replayId, query.limit)).flatMap { records =>
              jsonOk(ReplayCommentsResponse(records.map(ReplayCommentResponse.fromRecord)).asJson)
            }
        }
      case Method.POST =>
        blocking(service.load(replayId)).flatMap {
          case None =>
            errorResponse(replayApiError(ReplayApiErrorCode.ReplayNotFound))
          case Some(_) =>
            decodeCommentRequest(request, replayId).flatMap {
              case Left(error) =>
                errorResponse(commentDecodeError(error))
              case Right(command) =>
                blocking(service.addComment(command)).flatMap {
                  case Right(comment) =>
                    jsonCreated(ReplayCommentWrapperResponse(ReplayCommentResponse.fromRecord(comment)).asJson)
                  case Left(error) =>
                    errorResponse(commentServiceError(error))
                }
            }
        }
      case _ =>
        errorResponse(replayApiError(ReplayApiErrorCode.MethodNotAllowed))
    }

  private def decodeRecordRequest(request: Request[IO]): IO[Either[ReplayRecordDecodeError, ReplayRecordCommand]] =
    request
      .as[Json]
      .map(ReplayApiCodec.decodeRecordCommand)
      .handleError(_ => Left(ReplayRecordDecodeError.BadJsonObject))

  private def decodeCommentRequest(
    request: Request[IO],
    replayId: ReplayId
  ): IO[Either[ReplayCommentDecodeError, ReplayCommentCommand]] =
    request
      .as[Json]
      .map(ReplayApiCodec.decodeCommentCommand(replayId, _))
      .handleError(_ => Left(ReplayCommentDecodeError.BadJsonObject))

  private def recordDecodeError(error: ReplayRecordDecodeError): HttpApiError =
    replayApiError(ReplayApiErrorMapper.recordDecodeErrorCode(error))

  private def recordServiceError(error: ReplayRecordError): HttpApiError =
    replayApiError(ReplayApiErrorMapper.recordServiceErrorCode(error))

  private def commentDecodeError(error: ReplayCommentDecodeError): HttpApiError =
    replayApiError(ReplayApiErrorMapper.commentDecodeErrorCode(error))

  private def commentServiceError(error: ReplayCommentError): HttpApiError =
    replayApiError(ReplayApiErrorMapper.commentServiceErrorCode(error))

  private def catalogTarget(request: Request[IO]): Option[ReplayCatalogTarget] =
    ReplayApiCodec.catalogTarget(requestPath(request))

  private def replayApiError(code: ReplayApiErrorCode): HttpApiError =
    typedApiError(
      statusCode = ReplayApiErrorCode.statusCode(code),
      code = ReplayApiErrorCode.wireValue(code),
      message = ReplayApiErrorCode.message(code)
    )

  private object CatalogRequest {
    def unapply(request: Request[IO]): Option[ReplayCatalogTarget] =
      catalogTarget(request)
  }
}
