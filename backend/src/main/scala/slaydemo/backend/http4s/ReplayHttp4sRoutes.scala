package slaydemo.backend.http4s

import cats.effect.IO
import io.circe.syntax.*
import org.http4s.{HttpRoutes, Method, Request, Response, Status}

import slaydemo.backend.http4s.Http4sRouteSupport.{apiError, blocking, corsNoContent, corsOk, decodeTextBody, errorResponse, jsonCreated, jsonOk, requestPath}
import slaydemo.backend.replay.objects.ReplayId
import slaydemo.backend.replay.objects.apiTypes.{
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
import slaydemo.backend.replay.services.{
  ReplayCommentCommand,
  ReplayCommentError,
  ReplayRecordCommand,
  ReplayRecordError,
  ReplayService
}

private[http4s] object ReplayHttp4sRoutes {
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
    decodeTextBody(request, ReplayRecordDecodeError.BadJsonObject)(ReplayApiCodec.parseRecordCommand)

  private def decodeCommentRequest(
    request: Request[IO],
    replayId: ReplayId
  ): IO[Either[ReplayCommentDecodeError, ReplayCommentCommand]] =
    decodeTextBody(request, ReplayCommentDecodeError.BadJsonObject)(body =>
      ReplayApiCodec.parseCommentCommand(replayId, body)
    )

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
    apiError(
      status = replayApiStatus(code),
      code = ReplayApiErrorCode.wireValue(code),
      message = ReplayApiErrorCode.message(code)
    )

  private def replayApiStatus(code: ReplayApiErrorCode): Status =
    code match {
      case ReplayApiErrorCode.MethodNotAllowed    => Status.MethodNotAllowed
      case ReplayApiErrorCode.VisitorNotAllowed   => Status.Forbidden
      case ReplayApiErrorCode.ReplayNotFound      => Status.NotFound
      case ReplayApiErrorCode.BadJsonObject       => Status.BadRequest
      case ReplayApiErrorCode.InvalidReplayId     => Status.BadRequest
      case ReplayApiErrorCode.InvalidBattleId     => Status.BadRequest
      case ReplayApiErrorCode.InvalidHandle       => Status.BadRequest
      case ReplayApiErrorCode.InvalidFramesJson   => Status.BadRequest
      case ReplayApiErrorCode.InvalidAuthorHandle => Status.BadRequest
      case ReplayApiErrorCode.InvalidBody         => Status.BadRequest
    }

  private object CatalogRequest {
    def unapply(request: Request[IO]): Option[ReplayCatalogTarget] =
      catalogTarget(request)
  }
}
