package slaydemo.backend.http4s

import cats.effect.IO
import io.circe.syntax.*
import org.http4s.circe.CirceEntityEncoder.*
import org.http4s.dsl.io.*
import org.http4s.{HttpRoutes, Method, Request, Response, Status}

import slaydemo.backend.http4s.Http4sRouteSupport.{apiError, blocking, withCors}
import slaydemo.backend.identity.objects.PlayerHandle
import slaydemo.backend.replay.objects.ReplayId
import slaydemo.backend.replay.objects.apiTypes.{
  ReplayCommandParsers,
  ReplayCatalogResponse,
  ReplayCommentCommandParseError,
  ReplayCommentResponse,
  ReplayCommentWrapperResponse,
  ReplayCommentsResponse,
  ReplayDetailRecordResponse,
  ReplayDetailResponse,
  ReplayJsonObjectParser,
  ReplayRecordCommandParseError
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
      case request if replayTarget(request).nonEmpty =>
        replayTarget(request).get match {
          case ReplayHttp4sTarget.Collection =>
            handleCollection(service, request)
          case ReplayHttp4sTarget.Detail(replayId) =>
            handleDetail(service, request, replayId)
          case ReplayHttp4sTarget.Comments(replayId) =>
            handleComments(service, request, replayId)
          case ReplayHttp4sTarget.InvalidReplayId =>
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
        blocking(service.list(limitFrom(request))).flatMap { records =>
          val response = ReplayCatalogResponse.fromRecords(
            records = records,
            selectedHandle = selectedHandle(request)
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
        blocking(service.load(replayId)).flatMap {
          case Some(record) =>
            Ok(ReplayDetailResponse(ReplayDetailRecordResponse.fromRecord(record, selectedHandle(request))).asJson).map(withCors)
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
        blocking(service.load(replayId)).flatMap {
          case None =>
            IO.pure(apiError(ReplayNotFoundError))
          case Some(_) =>
            blocking(service.listComments(replayId, limitFrom(request))).flatMap { records =>
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
        ReplayJsonObjectParser.parse(body) match {
          case Left(_) =>
            Left(BadJsonObjectError)
          case Right(fields) =>
            val framesJson = ReplayCommandParsers.readString(fields, "framesJson")
              .orElse(ReplayCommandParsers.readRawJson(fields, "frames"))
              .getOrElse("[]")
            ReplayCommandParsers.parseReplayRecordCommand(fields, framesJson).left.map(recordParseError)
        }
    }

  private def decodeCommentRequest(
    request: Request[IO],
    replayId: ReplayId
  ): IO[Either[HttpApiError, ReplayCommentCommand]] =
    request.as[String].attempt.map {
      case Left(_) =>
        Left(BadJsonObjectError)
      case Right(body) =>
        ReplayJsonObjectParser.parse(body) match {
          case Left(_) =>
            Left(BadJsonObjectError)
          case Right(fields) =>
            ReplayCommandParsers.parseReplayCommentCommand(replayId, fields).left.map(commentParseError)
        }
    }

  private def recordParseError(error: ReplayRecordCommandParseError): HttpApiError =
    error match {
      case ReplayRecordCommandParseError.InvalidReplayId =>
        InvalidReplayIdError
      case ReplayRecordCommandParseError.InvalidBattleId =>
        HttpApiError(Status.BadRequest, "invalid_battle_id", "invalid_battle_id")
      case ReplayRecordCommandParseError.InvalidHandle =>
        HttpApiError(Status.BadRequest, "invalid_handle", "invalid_handle")
      case ReplayRecordCommandParseError.VisitorNotAllowed =>
        HttpApiError(Status.Forbidden, "visitor_not_allowed", "visitor_not_allowed")
    }

  private def recordServiceError(error: ReplayRecordError): HttpApiError =
    error match {
      case ReplayRecordError.InvalidReplayId =>
        InvalidReplayIdError
      case ReplayRecordError.InvalidFramesJson =>
        HttpApiError(Status.BadRequest, "invalid_frames_json", "invalid_frames_json")
    }

  private def commentParseError(error: ReplayCommentCommandParseError): HttpApiError =
    error match {
      case ReplayCommentCommandParseError.InvalidReplayId =>
        InvalidReplayIdError
      case ReplayCommentCommandParseError.InvalidAuthorHandle =>
        HttpApiError(Status.BadRequest, "invalid_author_handle", "invalid_author_handle")
      case ReplayCommentCommandParseError.VisitorNotAllowed =>
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

  private def replayTarget(request: Request[IO]): Option[ReplayHttp4sTarget] = {
    val path = normalizedPath(request.uri.path.renderString)
    if path == "/replay/catalog" then Some(ReplayHttp4sTarget.Collection)
    else if path.startsWith("/replay/catalog/") then {
      val suffix = path.stripPrefix("/replay/catalog/")
      if suffix.endsWith("/comments") then
        replayIdFrom(suffix.stripSuffix("/comments")).map(ReplayHttp4sTarget.Comments.apply).orElse(Some(ReplayHttp4sTarget.InvalidReplayId))
      else replayIdFrom(suffix).map(ReplayHttp4sTarget.Detail.apply).orElse(Some(ReplayHttp4sTarget.InvalidReplayId))
    } else None
  }

  private def normalizedPath(path: String): String =
    if path == "/api/replaycatalogapi" then "/replay/catalog"
    else if path.startsWith("/api/replay/catalog") then path.stripPrefix("/api")
    else path

  private def replayIdFrom(value: String): Option[ReplayId] =
    ReplayCommandParsers.parseReplayId(value)

  private def selectedHandle(request: Request[IO]): Option[PlayerHandle] =
    request.params.get("handle").flatMap(PlayerHandle.forLookup)

  private def limitFrom(request: Request[IO]): Int =
    request.params.get("limit").flatMap(_.toIntOption).getOrElse(25)

  private enum ReplayHttp4sTarget {
    case Collection
    case Detail(replayId: ReplayId)
    case Comments(replayId: ReplayId)
    case InvalidReplayId
  }
}
