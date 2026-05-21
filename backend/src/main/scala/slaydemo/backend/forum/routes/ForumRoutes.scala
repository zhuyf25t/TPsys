package slaydemo.backend.forum.routes

import java.util.Locale

import com.sun.net.httpserver.HttpExchange

import slaydemo.backend.forum.objects.apiTypes.{ForumTopicListResponse, ForumTopicWrapperResponse}
import slaydemo.backend.forum.services.ForumService
import slaydemo.backend.shared.routes.HttpRouteSupport

final class ForumRoutes(service: ForumService) {
  private val mutationHandler = ForumMutationRouteHandler(service)

  def handle(exchange: HttpExchange): Unit = {
    HttpRouteSupport.addCors(exchange)

    try {
      exchange.getRequestMethod.toUpperCase(Locale.ROOT) match {
        case "OPTIONS" =>
          HttpRouteSupport.sendEmpty(exchange, 204)
        case "HEAD" =>
          HttpRouteSupport.sendEmpty(exchange, 200)
        case "GET" if ForumRouteTargetParsers.isTopicsCollection(exchange.getRequestURI.getPath) =>
          val topics = service.listTopics(resolveViewerHandle(exchange))
          HttpRouteSupport.sendJson(exchange, 200, ForumTopicListResponse.renderViews(topics))
        case "GET" =>
          ForumRouteTargetParsers.topicIdFrom(exchange.getRequestURI.getPath) match {
            case None =>
              ForumRouteHttpSupport.jsonError(exchange, 404, "topic_not_found", "topic_not_found")
            case Some(topicId) =>
              ForumCommandParsers.parseTopicId(topicId) match {
                case None =>
                  ForumRouteHttpSupport.jsonError(exchange, 404, "topic_not_found", "topic_not_found")
                case Some(parsedTopicId) =>
                  service.loadTopic(parsedTopicId, resolveViewerHandle(exchange)) match {
                    case Some(topic) =>
                      HttpRouteSupport.sendJson(exchange, 200, ForumTopicWrapperResponse.renderView(topic))
                    case None =>
                      ForumRouteHttpSupport.jsonError(exchange, 404, "topic_not_found", "topic_not_found")
                  }
              }
          }
        case "POST" if ForumRouteTargetParsers.isTopicsCollection(exchange.getRequestURI.getPath) =>
          mutationHandler.createTopic(exchange)
        case "POST" if ForumRouteTargetParsers.isReplyVotesPath(exchange.getRequestURI.getPath) =>
          mutationHandler.setReplyVote(exchange)
        case "POST" if ForumRouteTargetParsers.isRepliesPath(exchange.getRequestURI.getPath) =>
          mutationHandler.addReply(exchange)
        case "POST" if ForumRouteTargetParsers.isTopicVotesPath(exchange.getRequestURI.getPath) =>
          mutationHandler.setTopicVote(exchange)
        case _ =>
          ForumRouteHttpSupport.jsonError(exchange, 405, "method_not_allowed", "Method is not allowed.")
      }
    } finally {
      exchange.close()
    }
  }

  private def resolveViewerHandle(exchange: HttpExchange) =
    ForumRouteTargetParsers.resolveViewerHandle(exchange.getRequestURI.getRawQuery)

}

object ForumRoutes {
  def apply(service: ForumService): ForumRoutes =
    new ForumRoutes(service)
}
