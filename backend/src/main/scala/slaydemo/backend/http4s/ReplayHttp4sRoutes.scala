package slaydemo.backend.http4s

import cats.effect.IO
import io.circe.syntax.*
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.io.*
import org.http4s.{HttpRoutes, Method, Request, Response, Status}

import slaydemo.backend.http4s.Http4sRouteSupport.{apiError, blocking, withCors}
import slaydemo.backend.replay.objects.ReplayId
import slaydemo.backend.replay.objects.apiTypes.{
  ReplayApiCodec,
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
  private val MethodNotAllowedError =
    HttpApiError(status = Status.MethodNotAllowed, code = "method_not_allowed", message = "Method is not allowed.")
  private val BadJsonObjectError =
    HttpApiError(status = Status.BadRequest, code = "bad_request", message = "Request body must be a JSON object.")
  private val ReplayNotFoundError =
    HttpApiError(status = Status.NotFound, code = "replay_not_found", message = "replay_not_found")
  private val InvalidReplayIdError =
    HttpApiError(status = Status.BadRequest, code = "invalid_replay_id", message = "invalid_replay_id")

  def catalogRoutes(service: ReplayService): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case request if catalogTarget(request).nonEmpty =>
        catalogTarget(request).get match {
          case ReplayCatalogTarget.Collection =>
            handleCollection(service, request)
          case ReplayCatalogTarget.Detail(replayId) =>
            handleDetail(service, request, replayId)
          case ReplayCatalogTarget.Comments(replayId) =>
            handleComments(service, request, replayId)
          case ReplayCatalogTarget.InvalidReplayId =>
            IO.pure(apiError(InvalidReplayIdError))
        }
    }

  private def handleCollection(service: ReplayService, request: Request[IO]): IO[Response[IO]] =
    request.method match {
      case Method.OPTIONS =>
        IO.pure(withCors(Response[IO](Status.NoContent)))
      case Method.HEAD =>
        IO.pure(withCors(Response[IO](Status.Ok)))
      case Method.GET =>
        val query = ReplayApiCodec.catalogQuery(request.params)
        blocking(service.list(query.limit)).flatMap { records =>
          val response = ReplayCatalogResponse.fromRecords(
            records = records,
            selectedHandle = query.selectedHandle
          )
          Ok(response.asJson).map(withCors)
        }
      case Method.POST =>
        decodeRecordRequest(request).flatMap {
          case Left(error) =>
            IO.pure(apiError(error))
          case Right(command) =>
            blocking(service.record(command)).flatMap {
              case Right(record) =>
                Created(ReplayDetailResponse(ReplayDetailRecordResponse.fromRecord(record, None)).asJson).map(withCors)
              case Left(error) =>
                IO.pure(apiError(recordServiceError(error)))
            }
        }
      case _ =>
        IO.pure(apiError(MethodNotAllowedError))
    }

  private def handleDetail(service: ReplayService, request: Request[IO], replayId: ReplayId): IO[Response[IO]] =
    request.method match {
      case Method.OPTIONS =>
        IO.pure(withCors(Response[IO](Status.NoContent)))
      case Method.HEAD =>
        IO.pure(withCors(Response[IO](Status.Ok)))
      case Method.GET =>
        val query = ReplayApiCodec.catalogQuery(request.params)
        blocking(service.load(replayId)).flatMap {
          case Some(record) =>
            Ok(ReplayDetailResponse(ReplayDetailRecordResponse.fromRecord(record, query.selectedHandle)).asJson).map(withCors)
          case None =>
            IO.pure(apiError(ReplayNotFoundError))
        }
      case _ =>
        IO.pure(apiError(MethodNotAllowedError))
    }

  private def handleComments(service: ReplayService, request: Request[IO], replayId: ReplayId): IO[Response[IO]] =
    request.method match {
      case Method.OPTIONS =>
        IO.pure(withCors(Response[IO](Status.NoContent)))
      case Method.HEAD =>
        IO.pure(withCors(Response[IO](Status.Ok)))
      case Method.GET =>
        val query = ReplayApiCodec.catalogQuery(request.params)
        blocking(service.load(replayId)).flatMap {
          case None =>
            IO.pure(apiError(ReplayNotFoundError))
          case Some(_) =>
            blocking(service.listComments(replayId, query.limit)).flatMap { records =>
              Ok(ReplayCommentsResponse(records.map(ReplayCommentResponse.fromRecord)).asJson).map(withCors)
            }
        }
      case Method.POST =>
        blocking(service.load(replayId)).flatMap {
          case None =>
            IO.pure(apiError(ReplayNotFoundError))
          case Some(_) =>
            decodeCommentRequest(request, replayId).flatMap {
              case Left(error) =>
                IO.pure(apiError(error))
              case Right(command) =>
                blocking(service.addComment(command)).flatMap {
                  case Right(comment) =>
                    Created(ReplayCommentWrapperResponse(ReplayCommentResponse.fromRecord(comment)).asJson).map(withCors)
                  case Left(error) =>
                    IO.pure(apiError(commentServiceError(error)))
                }
            }
        }
      case _ =>
        IO.pure(apiError(MethodNotAllowedError))
    }

  private def decodeRecordRequest(request: Request[IO]): IO[Either[HttpApiError, ReplayRecordCommand]] =
    request.as[String].attempt.map {
      case Left(_) =>
        Left(BadJsonObjectError)
      case Right(body) =>
        ReplayApiCodec.parseRecordCommand(body).left.map(recordDecodeError)
    }

  private def decodeCommentRequest(
    request: Request[IO],
    replayId: ReplayId
  ): IO[Either[HttpApiError, ReplayCommentCommand]] =
    request.as[String].attempt.map {
      case Left(_) =>
        Left(BadJsonObjectError)
      case Right(body) =>
        ReplayApiCodec.parseCommentCommand(replayId, body).left.map(commentDecodeError)
    }

  private def recordDecodeError(error: ReplayRecordDecodeError): HttpApiError =
    error match {
      case ReplayRecordDecodeError.BadJsonObject =>
        BadJsonObjectError
      case ReplayRecordDecodeError.InvalidReplayId =>
        InvalidReplayIdError
      case ReplayRecordDecodeError.InvalidBattleId =>
        HttpApiError(Status.BadRequest, "invalid_battle_id", "invalid_battle_id")
      case ReplayRecordDecodeError.InvalidHandle =>
        HttpApiError(Status.BadRequest, "invalid_handle", "invalid_handle")
      case ReplayRecordDecodeError.VisitorNotAllowed =>
        HttpApiError(Status.Forbidden, "visitor_not_allowed", "visitor_not_allowed")
    }

  private def recordServiceError(error: ReplayRecordError): HttpApiError =
    error match {
      case ReplayRecordError.InvalidReplayId =>
        InvalidReplayIdError
      case ReplayRecordError.InvalidFramesJson =>
        HttpApiError(Status.BadRequest, "invalid_frames_json", "invalid_frames_json")
    }

  private def commentDecodeError(error: ReplayCommentDecodeError): HttpApiError =
    error match {
      case ReplayCommentDecodeError.BadJsonObject =>
        BadJsonObjectError
      case ReplayCommentDecodeError.InvalidReplayId =>
        InvalidReplayIdError
      case ReplayCommentDecodeError.InvalidAuthorHandle =>
        HttpApiError(Status.BadRequest, "invalid_author_handle", "invalid_author_handle")
      case ReplayCommentDecodeError.VisitorNotAllowed =>
        HttpApiError(Status.Forbidden, "visitor_not_allowed", "visitor_not_allowed")
    }

  private def commentServiceError(error: ReplayCommentError): HttpApiError =
    error match {
      case ReplayCommentError.InvalidReplayId =>
        InvalidReplayIdError
      case ReplayCommentError.ReplayNotFound =>
        ReplayNotFoundError
      case ReplayCommentError.InvalidAuthor =>
        HttpApiError(Status.Forbidden, "visitor_not_allowed", "visitor_not_allowed")
      case ReplayCommentError.InvalidBody =>
        HttpApiError(Status.BadRequest, "invalid_body", "invalid_body")
    }

  private def catalogTarget(request: Request[IO]): Option[ReplayCatalogTarget] =
    ReplayApiCodec.catalogTarget(request.uri.path.renderString)
}
