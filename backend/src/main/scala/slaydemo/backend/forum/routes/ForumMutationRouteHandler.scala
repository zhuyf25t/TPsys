package slaydemo.backend.forum.routes

import com.sun.net.httpserver.HttpExchange

import slaydemo.backend.forum.objects.apiTypes.{ForumCommandParsers, ForumTopicWrapperResponse}
import slaydemo.backend.forum.services.ForumService
import slaydemo.backend.shared.routes.HttpRouteSupport

private[routes] final class ForumMutationRouteHandler(service: ForumService) {
  def createTopic(exchange: HttpExchange): Unit =
    ForumRouteHttpSupport.parseBody(exchange) match {
      case Left(message) =>
        ForumRouteHttpSupport.jsonError(exchange, 400, "bad_request", message)
      case Right(fields) =>
        ForumCommandParsers.parseCreateTopicCommand(fields) match {
          case Right(command) =>
            service.createTopic(command) match {
              case Right(topic) =>
                HttpRouteSupport.sendJson(exchange, 201, ForumTopicWrapperResponse.renderView(topic))
              case Left(error) =>
                val code = ForumRouteErrorMapper.createErrorCode(error)
                ForumRouteHttpSupport.jsonError(exchange, ForumRouteErrorMapper.createStatusFor(error), code, code)
            }
          case Left(error) =>
            val code = ForumRouteErrorMapper.createErrorCode(error)
            ForumRouteHttpSupport.jsonError(exchange, ForumRouteErrorMapper.createStatusFor(error), code, code)
        }
    }

  def addReply(exchange: HttpExchange): Unit =
    ForumRouteTargetParsers.topicIdFrom(exchange.getRequestURI.getPath) match {
      case None =>
        ForumRouteHttpSupport.jsonError(exchange, 404, "topic_not_found", "topic_not_found")
      case Some(topicId) =>
        ForumRouteHttpSupport.parseBody(exchange) match {
          case Left(message) =>
            ForumRouteHttpSupport.jsonError(exchange, 400, "bad_request", message)
          case Right(fields) =>
            ForumCommandParsers.parseAddReplyCommand(topicId, fields) match {
              case Left(error) =>
                val code = ForumRouteErrorMapper.mutationErrorCode(error)
                ForumRouteHttpSupport.jsonError(exchange, ForumRouteErrorMapper.mutationStatusFor(error), code, code)
              case Right(command) =>
                service.addReply(command) match {
                  case Right(topic) =>
                    HttpRouteSupport.sendJson(exchange, 200, ForumTopicWrapperResponse.renderView(topic))
                  case Left(error) =>
                    val code = ForumRouteErrorMapper.mutationErrorCode(error)
                    ForumRouteHttpSupport.jsonError(exchange, ForumRouteErrorMapper.mutationStatusFor(error), code, code)
                }
            }
        }
    }

  def setTopicVote(exchange: HttpExchange): Unit =
    ForumRouteTargetParsers.topicIdFrom(exchange.getRequestURI.getPath) match {
      case None =>
        ForumRouteHttpSupport.jsonError(exchange, 404, "topic_not_found", "topic_not_found")
      case Some(topicId) =>
        ForumRouteHttpSupport.parseBody(exchange) match {
          case Left(message) =>
            ForumRouteHttpSupport.jsonError(exchange, 400, "bad_request", message)
          case Right(fields) =>
            ForumCommandParsers.parseVote(fields) match {
              case Left(code) =>
                ForumRouteHttpSupport.jsonError(exchange, 400, code, code)
              case Right(vote) =>
                ForumCommandParsers.parseSetTopicVoteCommand(topicId, fields, vote) match {
                  case Left(error) =>
                    val code = ForumRouteErrorMapper.mutationErrorCode(error)
                    ForumRouteHttpSupport.jsonError(exchange, ForumRouteErrorMapper.mutationStatusFor(error), code, code)
                  case Right(command) =>
                    service.setTopicVote(command) match {
                      case Right(topic) =>
                        HttpRouteSupport.sendJson(exchange, 200, ForumTopicWrapperResponse.renderView(topic))
                      case Left(error) =>
                        val code = ForumRouteErrorMapper.mutationErrorCode(error)
                        ForumRouteHttpSupport.jsonError(exchange, ForumRouteErrorMapper.mutationStatusFor(error), code, code)
                    }
                }
            }
        }
    }

  def setReplyVote(exchange: HttpExchange): Unit =
    (
      ForumRouteTargetParsers.topicIdFrom(exchange.getRequestURI.getPath),
      ForumRouteTargetParsers.replyIdFrom(exchange.getRequestURI.getPath)
    ) match {
      case (Some(topicId), Some(replyId)) =>
        ForumRouteHttpSupport.parseBody(exchange) match {
          case Left(message) =>
            ForumRouteHttpSupport.jsonError(exchange, 400, "bad_request", message)
          case Right(fields) =>
            ForumCommandParsers.parseVote(fields) match {
              case Left(code) =>
                ForumRouteHttpSupport.jsonError(exchange, 400, code, code)
              case Right(vote) =>
                ForumCommandParsers.parseSetReplyVoteCommand(topicId, replyId, fields, vote) match {
                  case Left(error) =>
                    val code = ForumRouteErrorMapper.mutationErrorCode(error)
                    ForumRouteHttpSupport.jsonError(exchange, ForumRouteErrorMapper.mutationStatusFor(error), code, code)
                  case Right(command) =>
                    service.setReplyVote(command) match {
                      case Right(topic) =>
                        HttpRouteSupport.sendJson(exchange, 200, ForumTopicWrapperResponse.renderView(topic))
                      case Left(error) =>
                        val code = ForumRouteErrorMapper.mutationErrorCode(error)
                        ForumRouteHttpSupport.jsonError(exchange, ForumRouteErrorMapper.mutationStatusFor(error), code, code)
                    }
                }
            }
        }
      case _ =>
        ForumRouteHttpSupport.jsonError(exchange, 404, "reply_not_found", "reply_not_found")
    }
}

private[routes] object ForumMutationRouteHandler {
  def apply(service: ForumService): ForumMutationRouteHandler =
    new ForumMutationRouteHandler(service)
}
