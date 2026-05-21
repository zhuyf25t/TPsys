package slaydemo.backend.http4s

import cats.effect.IO
import io.circe.syntax.*
import org.http4s.circe.{CirceEntityDecoder, CirceEntityEncoder}
import org.http4s.dsl.io.*
import org.http4s.{HttpRoutes, Method, Request, Response, Status}

import slaydemo.backend.forum.objects.apiTypes.{
  ForumApiRequestFields,
  ForumCreateTopicParseError,
  ForumRouteErrorMapper,
  ForumRouteTargetParsers,
  ForumTopicMutationParseError,
  ForumTopicListResponse,
  ForumTopicWrapperResponse,
  ForumVoteCommandParseError
}
import slaydemo.backend.forum.services.ForumService
import slaydemo.backend.http4s.Http4sRouteSupport.{apiError, blocking, withCors}

private[http4s] object ForumHttp4sRoutes {
  private val MethodNotAllowedError =
    HttpApiError(status = Status.MethodNotAllowed, code = "method_not_allowed", message = "Method is not allowed.")
  private val TopicNotFoundError =
    HttpApiError(status = Status.NotFound, code = "topic_not_found", message = "topic_not_found")
  private val ReplyNotFoundError =
    HttpApiError(status = Status.NotFound, code = "reply_not_found", message = "reply_not_found")

  import CirceEntityDecoder.*
  import CirceEntityEncoder.*

  def routes(service: ForumService): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case request if isForumPath(request) =>
        request.method match {
          case Method.OPTIONS =>
            IO.pure(withCors(Response[IO](Status.NoContent)))
          case Method.HEAD =>
            IO.pure(withCors(Response[IO](Status.Ok)))
          case Method.GET if ForumRouteTargetParsers.isTopicsCollection(path(request)) =>
            blocking(service.listTopics(viewerHandle(request))).flatMap(topics =>
              Ok(ForumTopicListResponse.fromViews(topics).asJson).map(withCors)
            )
          case Method.GET =>
            loadTopic(request, service)
          case Method.POST if ForumRouteTargetParsers.isTopicsCollection(path(request)) =>
            createTopic(request, service)
          case Method.POST if ForumRouteTargetParsers.isReplyVotesPath(path(request)) =>
            setReplyVote(request, service)
          case Method.POST if ForumRouteTargetParsers.isRepliesPath(path(request)) =>
            addReply(request, service)
          case Method.POST if ForumRouteTargetParsers.isTopicVotesPath(path(request)) =>
            setTopicVote(request, service)
          case _ =>
            IO.pure(apiError(MethodNotAllowedError))
        }
    }

  private def loadTopic(request: Request[IO], service: ForumService): IO[Response[IO]] =
    ForumRouteTargetParsers.topicIdFrom(path(request)) match {
      case None =>
        IO.pure(apiError(TopicNotFoundError))
      case Some(topicId) =>
        blocking(service.loadTopic(topicId, viewerHandle(request))).flatMap {
          case Some(topic) =>
            Ok(ForumTopicWrapperResponse.fromView(topic).asJson).map(withCors)
          case None =>
            IO.pure(apiError(TopicNotFoundError))
        }
    }

  private def createTopic(request: Request[IO], service: ForumService): IO[Response[IO]] =
    parseBody(request).flatMap {
      case Left(message) =>
        IO.pure(apiError(badRequest(message)))
      case Right(fields) =>
        fields.toCreateTopicCommand match {
          case Right(command) =>
            blocking(service.createTopic(command)).map {
              case Right(topic) =>
                withCors(Response[IO](Status.Created).withEntity(ForumTopicWrapperResponse.fromView(topic).asJson))
              case Left(error) =>
                apiError(createApiError(error))
            }
          case Left(error) =>
            IO.pure(apiError(createApiError(error)))
        }
    }

  private def addReply(request: Request[IO], service: ForumService): IO[Response[IO]] =
    ForumRouteTargetParsers.topicIdFrom(path(request)) match {
      case None =>
        IO.pure(apiError(TopicNotFoundError))
      case Some(topicId) =>
        parseBody(request).flatMap {
          case Left(message) =>
            IO.pure(apiError(badRequest(message)))
          case Right(fields) =>
            fields.toAddReplyCommand(topicId) match {
              case Left(error) =>
                IO.pure(apiError(mutationApiError(error)))
              case Right(command) =>
                blocking(service.addReply(command)).map {
                  case Right(topic) =>
                    withCors(Response[IO](Status.Ok).withEntity(ForumTopicWrapperResponse.fromView(topic).asJson))
                  case Left(error) =>
                    apiError(mutationApiError(error))
                }
            }
        }
    }

  private def setTopicVote(request: Request[IO], service: ForumService): IO[Response[IO]] =
    ForumRouteTargetParsers.topicIdFrom(path(request)) match {
      case None =>
        IO.pure(apiError(TopicNotFoundError))
      case Some(topicId) =>
        parseBody(request).flatMap {
          case Left(message) =>
            IO.pure(apiError(badRequest(message)))
          case Right(fields) =>
            fields.toSetTopicVoteCommand(topicId) match {
              case Left(error) =>
                IO.pure(apiError(voteCommandApiError(error)))
              case Right(command) =>
                blocking(service.setTopicVote(command)).map {
                  case Right(topic) =>
                    withCors(Response[IO](Status.Ok).withEntity(ForumTopicWrapperResponse.fromView(topic).asJson))
                  case Left(error) =>
                    apiError(mutationApiError(error))
                }
            }
        }
    }

  private def setReplyVote(request: Request[IO], service: ForumService): IO[Response[IO]] =
    (
      ForumRouteTargetParsers.topicIdFrom(path(request)),
      ForumRouteTargetParsers.replyIdFrom(path(request))
    ) match {
      case (Some(topicId), Some(replyId)) =>
        parseBody(request).flatMap {
          case Left(message) =>
            IO.pure(apiError(badRequest(message)))
          case Right(fields) =>
            fields.toSetReplyVoteCommand(topicId, replyId) match {
              case Left(error) =>
                IO.pure(apiError(voteCommandApiError(error)))
              case Right(command) =>
                blocking(service.setReplyVote(command)).map {
                  case Right(topic) =>
                    withCors(Response[IO](Status.Ok).withEntity(ForumTopicWrapperResponse.fromView(topic).asJson))
                  case Left(error) =>
                    apiError(mutationApiError(error))
                }
            }
        }
      case _ =>
        IO.pure(apiError(ReplyNotFoundError))
    }

  private def parseBody(request: Request[IO]) =
    request
      .as[ForumApiRequestFields]
      .attempt
      .map(_.map(_.toCommandFields).left.map(_ => "Request body must be a JSON object with string fields."))

  private def viewerHandle(request: Request[IO]) =
    ForumRouteTargetParsers.resolveViewerHandle(request.params)

  private def createApiError(error: slaydemo.backend.forum.services.ForumCreateTopicError): HttpApiError = {
    val code = ForumRouteErrorMapper.createErrorCode(error)
    routeError(createStatus(error), code)
  }

  private def createApiError(error: ForumCreateTopicParseError): HttpApiError = {
    val code = ForumRouteErrorMapper.createErrorCode(error)
    routeError(createStatus(error), code)
  }

  private def mutationApiError(error: slaydemo.backend.forum.services.ForumTopicMutationError): HttpApiError = {
    val code = ForumRouteErrorMapper.mutationErrorCode(error)
    routeError(mutationStatus(error), code)
  }

  private def mutationApiError(error: ForumTopicMutationParseError): HttpApiError = {
    val code = ForumRouteErrorMapper.mutationErrorCode(error)
    routeError(mutationStatus(error), code)
  }

  private def createStatus(error: slaydemo.backend.forum.services.ForumCreateTopicError): Status =
    statusFrom(ForumRouteErrorMapper.createStatusFor(error))

  private def createStatus(error: ForumCreateTopicParseError): Status =
    statusFrom(ForumRouteErrorMapper.createStatusFor(error))

  private def mutationStatus(error: slaydemo.backend.forum.services.ForumTopicMutationError): Status =
    statusFrom(ForumRouteErrorMapper.mutationStatusFor(error))

  private def mutationStatus(error: ForumTopicMutationParseError): Status =
    statusFrom(ForumRouteErrorMapper.mutationStatusFor(error))

  private def statusFrom(value: Int): Status =
    value match {
      case 400 => Status.BadRequest
      case 403 => Status.Forbidden
      case 404 => Status.NotFound
      case _   => Status.InternalServerError
    }

  private def isForumPath(request: Request[IO]): Boolean =
    path(request).startsWith("/forum/") || path(request).startsWith("/api/forum/")

  private def path(request: Request[IO]): String =
    request.uri.path.renderString

  private def badRequest(message: String): HttpApiError =
    HttpApiError(status = Status.BadRequest, code = "bad_request", message = message)

  private def voteCommandApiError(error: ForumVoteCommandParseError): HttpApiError =
    error match {
      case ForumVoteCommandParseError.InvalidVote =>
        routeError(Status.BadRequest, "invalid_vote")
      case ForumVoteCommandParseError.Mutation(error) =>
        mutationApiError(error)
    }

  private def routeError(status: Status, code: String): HttpApiError =
    HttpApiError(status = status, code = code, message = code)
}
