package route.forum

import cats.effect.IO
import io.circe.syntax.*
import org.http4s.circe.CirceEntityDecoder
import org.http4s.{HttpRoutes, Method, Request, Response}

import services.forum.api.{
  ForumApiErrorCode,
  ForumApiErrorMapper,
  ForumApiTargetParsers,
  ForumCreateTopicParseError,
  ForumRequestFields,
  ForumTopicMutationParseError,
  ForumVoteCommandParseError
}
import services.forum.objects.apiTypes.{ForumApiRequestDecodeError, ForumApiRequestFields, ForumTopicListResponse, ForumTopicWrapperResponse}
import services.forum.services.ForumService
import route.HttpApiError
import route.HttpApiErrors.typedApiError
import route.Http4sCors.{corsNoContent, corsOk}
import route.Http4sRequestDecoders.decodeEntityBody
import route.Http4sRequestPaths.requestPath
import route.Http4sResponses.{errorResponse, jsonCreated, jsonOk}

private[route] object ForumHttp4sRoutes {
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
            service.listTopics(viewerHandle(request)).flatMap(topics =>
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
            errorResponse(routeError(ForumApiErrorCode.MethodNotAllowed))
        }
    }

  private def loadTopic(request: Request[IO], service: ForumService): IO[Response[IO]] =
    ForumApiTargetParsers.topicIdFrom(requestPath(request)) match {
      case None =>
        errorResponse(routeError(ForumApiErrorCode.TopicNotFound))
      case Some(topicId) =>
        service.loadTopic(topicId, viewerHandle(request)).flatMap {
          case Some(topic) =>
            jsonOk(ForumTopicWrapperResponse.fromView(topic).asJson)
          case None =>
            errorResponse(routeError(ForumApiErrorCode.TopicNotFound))
        }
    }

  private def createTopic(request: Request[IO], service: ForumService): IO[Response[IO]] =
    parseBody(request).flatMap {
      case Left(ForumApiRequestDecodeError.InvalidJsonObject) =>
        errorResponse(routeError(ForumApiErrorCode.InvalidJsonObject))
      case Right(fields) =>
        fields.toCreateTopicCommand match {
          case Right(command) =>
            service.createTopic(command).flatMap {
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
        errorResponse(routeError(ForumApiErrorCode.TopicNotFound))
      case Some(topicId) =>
        parseBody(request).flatMap {
          case Left(ForumApiRequestDecodeError.InvalidJsonObject) =>
            errorResponse(routeError(ForumApiErrorCode.InvalidJsonObject))
          case Right(fields) =>
            fields.toAddReplyCommand(topicId) match {
              case Left(error) =>
                errorResponse(mutationApiError(error))
              case Right(command) =>
                service.addReply(command).flatMap {
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
        errorResponse(routeError(ForumApiErrorCode.TopicNotFound))
      case Some(topicId) =>
        parseBody(request).flatMap {
          case Left(ForumApiRequestDecodeError.InvalidJsonObject) =>
            errorResponse(routeError(ForumApiErrorCode.InvalidJsonObject))
          case Right(fields) =>
            fields.toSetTopicVoteCommand(topicId) match {
              case Left(error) =>
                errorResponse(voteCommandApiError(error))
              case Right(command) =>
                service.setTopicVote(command).flatMap {
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
            errorResponse(routeError(ForumApiErrorCode.InvalidJsonObject))
          case Right(fields) =>
            fields.toSetReplyVoteCommand(topicId, replyId) match {
              case Left(error) =>
                errorResponse(voteCommandApiError(error))
              case Right(command) =>
                service.setReplyVote(command).flatMap {
                  case Right(topic) =>
                    jsonOk(ForumTopicWrapperResponse.fromView(topic).asJson)
                  case Left(error) =>
                    errorResponse(mutationApiError(error))
                }
            }
        }
      case _ =>
        errorResponse(routeError(ForumApiErrorCode.ReplyNotFound))
    }

  private def parseBody(request: Request[IO]): IO[Either[ForumApiRequestDecodeError, ForumRequestFields]] =
    decodeEntityBody[ForumApiRequestDecodeError, ForumApiRequestFields](
      request,
      ForumApiRequestDecodeError.InvalidJsonObject
    ).map(_.map(ForumRequestFields.fromApi))

  private def viewerHandle(request: Request[IO]) =
    ForumApiTargetParsers.resolveViewerHandle(request.params)

  private def createApiError(error: services.forum.services.ForumCreateTopicError): HttpApiError = {
    val code = ForumApiErrorMapper.createErrorCode(error)
    routeError(code)
  }

  private def createApiError(error: ForumCreateTopicParseError): HttpApiError = {
    val code = ForumApiErrorMapper.createErrorCode(error)
    routeError(code)
  }

  private def mutationApiError(error: services.forum.services.ForumTopicMutationError): HttpApiError = {
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
    typedApiError(
      statusCode = ForumApiErrorCode.statusCode(code),
      code = wireCode,
      message = ForumApiErrorCode.message(code)
    )
  }
}
