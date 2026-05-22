package slaydemo.backend.http4s.forum

import cats.effect.IO
import io.circe.syntax.*
import org.http4s.circe.CirceEntityDecoder
import org.http4s.{HttpRoutes, Method, Request, Response}

import slaydemo.backend.forum.objects.apiTypes.{
  ForumApiRequestDecodeError,
  ForumApiErrorCode,
  ForumApiRequestFields,
  ForumApiErrorMapper,
  ForumApiTargetParsers,
  ForumCreateTopicParseError,
  ForumRequestFields,
  ForumTopicMutationParseError,
  ForumTopicListResponse,
  ForumTopicWrapperResponse,
  ForumVoteCommandParseError
}
import slaydemo.backend.forum.services.ForumService
import slaydemo.backend.http4s.HttpApiError
import slaydemo.backend.http4s.Http4sRouteSupport.{blocking, codeMessageError, corsNoContent, corsOk, decodeEntityBody, errorResponse, jsonCreated, jsonOk, methodNotAllowedError, requestPath, typedApiError}

private[http4s] object ForumHttp4sRoutes {
  private val MethodNotAllowedError =
    methodNotAllowedError("Method is not allowed.")
  private val TopicNotFoundError =
    codeMessageError(statusCode = 404, code = "topic_not_found")
  private val ReplyNotFoundError =
    codeMessageError(statusCode = 404, code = "reply_not_found")
  private val InvalidJsonObjectError =
    typedApiError(statusCode = 400, code = "bad_request", message = "Request body must be a JSON object with string fields.")

  import CirceEntityDecoder.*

  def routes(service: ForumService): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case request if isForumPath(request) =>
        request.method match {
          case Method.OPTIONS =>
            corsNoContent
          case Method.HEAD =>
            corsOk
          case Method.GET if ForumApiTargetParsers.isTopicsCollection(requestPath(request)) =>
            blocking(service.listTopics(viewerHandle(request))).flatMap(topics =>
              jsonOk(ForumTopicListResponse.fromViews(topics).asJson)
            )
          case Method.GET =>
            loadTopic(request, service)
          case Method.POST if ForumApiTargetParsers.isTopicsCollection(requestPath(request)) =>
            createTopic(request, service)
          case Method.POST if ForumApiTargetParsers.isReplyVotesPath(requestPath(request)) =>
            setReplyVote(request, service)
          case Method.POST if ForumApiTargetParsers.isRepliesPath(requestPath(request)) =>
            addReply(request, service)
          case Method.POST if ForumApiTargetParsers.isTopicVotesPath(requestPath(request)) =>
            setTopicVote(request, service)
          case _ =>
            errorResponse(MethodNotAllowedError)
        }
    }

  private def loadTopic(request: Request[IO], service: ForumService): IO[Response[IO]] =
    ForumApiTargetParsers.topicIdFrom(requestPath(request)) match {
      case None =>
        errorResponse(TopicNotFoundError)
      case Some(topicId) =>
        blocking(service.loadTopic(topicId, viewerHandle(request))).flatMap {
          case Some(topic) =>
            jsonOk(ForumTopicWrapperResponse.fromView(topic).asJson)
          case None =>
            errorResponse(TopicNotFoundError)
        }
    }

  private def createTopic(request: Request[IO], service: ForumService): IO[Response[IO]] =
    parseBody(request).flatMap {
      case Left(ForumApiRequestDecodeError.InvalidJsonObject) =>
        errorResponse(InvalidJsonObjectError)
      case Right(fields) =>
        fields.toCreateTopicCommand match {
          case Right(command) =>
            blocking(service.createTopic(command)).flatMap {
              case Right(topic) =>
                jsonCreated(ForumTopicWrapperResponse.fromView(topic).asJson)
              case Left(error) =>
                errorResponse(createApiError(error))
            }
          case Left(error) =>
            errorResponse(createApiError(error))
        }
    }

  private def addReply(request: Request[IO], service: ForumService): IO[Response[IO]] =
    ForumApiTargetParsers.topicIdFrom(requestPath(request)) match {
      case None =>
        errorResponse(TopicNotFoundError)
      case Some(topicId) =>
        parseBody(request).flatMap {
          case Left(ForumApiRequestDecodeError.InvalidJsonObject) =>
            errorResponse(InvalidJsonObjectError)
          case Right(fields) =>
            fields.toAddReplyCommand(topicId) match {
              case Left(error) =>
                errorResponse(mutationApiError(error))
              case Right(command) =>
                blocking(service.addReply(command)).flatMap {
                  case Right(topic) =>
                    jsonOk(ForumTopicWrapperResponse.fromView(topic).asJson)
                  case Left(error) =>
                    errorResponse(mutationApiError(error))
                }
            }
        }
    }

  private def setTopicVote(request: Request[IO], service: ForumService): IO[Response[IO]] =
    ForumApiTargetParsers.topicIdFrom(requestPath(request)) match {
      case None =>
        errorResponse(TopicNotFoundError)
      case Some(topicId) =>
        parseBody(request).flatMap {
          case Left(ForumApiRequestDecodeError.InvalidJsonObject) =>
            errorResponse(InvalidJsonObjectError)
          case Right(fields) =>
            fields.toSetTopicVoteCommand(topicId) match {
              case Left(error) =>
                errorResponse(voteCommandApiError(error))
              case Right(command) =>
                blocking(service.setTopicVote(command)).flatMap {
                  case Right(topic) =>
                    jsonOk(ForumTopicWrapperResponse.fromView(topic).asJson)
                  case Left(error) =>
                    errorResponse(mutationApiError(error))
                }
            }
        }
    }

  private def setReplyVote(request: Request[IO], service: ForumService): IO[Response[IO]] =
    (
      ForumApiTargetParsers.topicIdFrom(requestPath(request)),
      ForumApiTargetParsers.replyIdFrom(requestPath(request))
    ) match {
      case (Some(topicId), Some(replyId)) =>
        parseBody(request).flatMap {
          case Left(ForumApiRequestDecodeError.InvalidJsonObject) =>
            errorResponse(InvalidJsonObjectError)
          case Right(fields) =>
            fields.toSetReplyVoteCommand(topicId, replyId) match {
              case Left(error) =>
                errorResponse(voteCommandApiError(error))
              case Right(command) =>
                blocking(service.setReplyVote(command)).flatMap {
                  case Right(topic) =>
                    jsonOk(ForumTopicWrapperResponse.fromView(topic).asJson)
                  case Left(error) =>
                    errorResponse(mutationApiError(error))
                }
            }
        }
      case _ =>
        errorResponse(ReplyNotFoundError)
    }

  private def parseBody(request: Request[IO]): IO[Either[ForumApiRequestDecodeError, ForumRequestFields]] =
    decodeEntityBody[ForumApiRequestDecodeError, ForumApiRequestFields](
      request,
      ForumApiRequestDecodeError.InvalidJsonObject
    ).map(_.map(_.toCommandFields))

  private def viewerHandle(request: Request[IO]) =
    ForumApiTargetParsers.resolveViewerHandle(request.params)

  private def createApiError(error: slaydemo.backend.forum.services.ForumCreateTopicError): HttpApiError = {
    val code = ForumApiErrorMapper.createErrorCode(error)
    routeError(code)
  }

  private def createApiError(error: ForumCreateTopicParseError): HttpApiError = {
    val code = ForumApiErrorMapper.createErrorCode(error)
    routeError(code)
  }

  private def mutationApiError(error: slaydemo.backend.forum.services.ForumTopicMutationError): HttpApiError = {
    val code = ForumApiErrorMapper.mutationErrorCode(error)
    routeError(code)
  }

  private def mutationApiError(error: ForumTopicMutationParseError): HttpApiError = {
    val code = ForumApiErrorMapper.mutationErrorCode(error)
    routeError(code)
  }

  private def isForumPath(request: Request[IO]): Boolean =
    val path = requestPath(request)
    path.startsWith("/forum/") || path.startsWith("/api/forum/")

  private def voteCommandApiError(error: ForumVoteCommandParseError): HttpApiError =
    error match {
      case ForumVoteCommandParseError.InvalidVote =>
        routeError(ForumApiErrorCode.InvalidVote)
      case ForumVoteCommandParseError.Mutation(error) =>
        mutationApiError(error)
    }

  private def routeError(code: ForumApiErrorCode): HttpApiError = {
    val wireCode = ForumApiErrorCode.wireValue(code)
    codeMessageError(statusCode = ForumApiErrorCode.statusCode(code), code = wireCode)
  }
}
