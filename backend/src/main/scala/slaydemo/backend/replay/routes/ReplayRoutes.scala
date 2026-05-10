package slaydemo.backend.replay.routes

import java.util.Locale

import com.sun.net.httpserver.HttpExchange

import slaydemo.backend.replay.objects.ReplayId
import slaydemo.backend.replay.services.ReplayService
import slaydemo.backend.shared.routes.HttpRouteSupport

final class ReplayRoutes(service: ReplayService) {
  def handle(exchange: HttpExchange): Unit = {
    HttpRouteSupport.addCors(exchange)

    try {
      val target = ReplayRouteTargetParsers.parseTarget(exchange.getRequestURI.getPath)
      exchange.getRequestMethod.toUpperCase(Locale.ROOT) match {
        case "OPTIONS" =>
          HttpRouteSupport.sendEmpty(exchange, 204)
        case "HEAD" =>
          if target == ReplayTarget.Invalid then HttpRouteSupport.sendEmpty(exchange, 404)
          else HttpRouteSupport.sendEmpty(exchange, 200)
        case "GET" =>
          handleGet(exchange, target)
        case "POST" =>
          handlePost(exchange, target)
        case _ =>
          jsonError(exchange, ReplayRouteErrorMapper.methodNotAllowed)
      }
    } finally {
      exchange.close()
    }
  }

  private def handleGet(exchange: HttpExchange, target: ReplayTarget): Unit =
    target match {
      case ReplayTarget.Collection =>
        val rawQuery = exchange.getRequestURI.getRawQuery
        val limit = ReplayRouteTargetParsers.limit(rawQuery, default = 25)
        HttpRouteSupport.sendJson(
          exchange,
          200,
          ReplayRouteJsonRenderer.renderCatalog(service.list(limit), ReplayRouteTargetParsers.replayHandleFromQuery(rawQuery))
        )
      case ReplayTarget.Detail(replayId) =>
        service.load(replayId) match {
          case Some(record) =>
            HttpRouteSupport.sendJson(
              exchange,
              200,
              ReplayRouteJsonRenderer.renderDetail(record, ReplayRouteTargetParsers.replayHandleFromQuery(exchange.getRequestURI.getRawQuery))
            )
          case None         => jsonError(exchange, ReplayRouteErrorMapper.replayNotFound)
        }
      case ReplayTarget.Comments(replayId) =>
        service.load(replayId) match {
          case None =>
            jsonError(exchange, ReplayRouteErrorMapper.replayNotFound)
          case Some(_) =>
            val limit = ReplayRouteTargetParsers.limit(exchange.getRequestURI.getRawQuery, default = 50)
            HttpRouteSupport.sendJson(exchange, 200, ReplayRouteJsonRenderer.renderComments(service.listComments(replayId, limit)))
        }
      case ReplayTarget.Invalid =>
        ReplayRouteErrorMapper.target(ReplayTarget.Invalid).foreach(error => jsonError(exchange, error))
      case ReplayTarget.InvalidReplayId =>
        ReplayRouteErrorMapper.target(ReplayTarget.InvalidReplayId).foreach(error => jsonError(exchange, error))
    }

  private def handlePost(exchange: HttpExchange, target: ReplayTarget): Unit =
    target match {
      case ReplayTarget.Collection =>
        handleCatalogPost(exchange)
      case ReplayTarget.Comments(replayId) =>
        handleCommentPost(exchange, replayId)
      case ReplayTarget.Detail(_) =>
        jsonError(exchange, ReplayRouteErrorMapper.methodNotAllowed)
      case ReplayTarget.Invalid =>
        ReplayRouteErrorMapper.target(ReplayTarget.Invalid).foreach(error => jsonError(exchange, error))
      case ReplayTarget.InvalidReplayId =>
        ReplayRouteErrorMapper.target(ReplayTarget.InvalidReplayId).foreach(error => jsonError(exchange, error))
    }

  private def handleCatalogPost(exchange: HttpExchange): Unit =
    ReplayJsonObjectParser.parse(HttpRouteSupport.readRequestBody(exchange)) match {
      case Left(_) =>
        jsonError(exchange, ReplayRouteErrorMapper.badRequest("Request body must be a JSON object."))
      case Right(fields) =>
        val framesJson = ReplayCommandParsers.readString(fields, "framesJson")
          .orElse(ReplayCommandParsers.readRawJson(fields, "frames"))
          .getOrElse("[]")
        ReplayCommandParsers.parseReplayRecordCommand(fields, framesJson) match {
          case Right(command) =>
            service.record(command) match {
              case Right(record) =>
                HttpRouteSupport.sendJson(exchange, 201, ReplayRouteJsonRenderer.renderDetail(record, None))
              case Left(error) =>
                jsonError(exchange, ReplayRouteErrorMapper.record(error))
            }
          case Left(error) =>
            jsonError(exchange, ReplayRouteErrorMapper.recordParse(error))
        }
    }

  private def handleCommentPost(exchange: HttpExchange, replayId: ReplayId): Unit =
    ReplayJsonObjectParser.parse(HttpRouteSupport.readRequestBody(exchange)) match {
      case Left(_) =>
        jsonError(exchange, ReplayRouteErrorMapper.badRequest("Request body must be a JSON object."))
      case Right(fields) =>
        ReplayCommandParsers.parseReplayCommentCommand(replayId, fields) match {
          case Left(error) =>
            jsonError(exchange, ReplayRouteErrorMapper.commentParse(error))
          case Right(command) =>
            service.addComment(command) match {
              case Right(comment) =>
                HttpRouteSupport.sendJson(exchange, 201, ReplayRouteJsonRenderer.renderComment(comment))
              case Left(error) =>
                jsonError(exchange, ReplayRouteErrorMapper.comment(error))
            }
        }
    }

  private def jsonString(value: String): String =
    s""""${HttpRouteSupport.escapeJson(value)}""""

  private def jsonError(exchange: HttpExchange, error: ReplayRouteError): Unit =
    jsonError(exchange, error.status, error.code, error.message)

  private def jsonError(exchange: HttpExchange, status: Int, code: String, message: String): Unit =
    HttpRouteSupport.sendJson(exchange, status, s"""{"error":${jsonString(message)},"code":${jsonString(code)}}""")

}

object ReplayRoutes {
  def apply(service: ReplayService): ReplayRoutes =
    new ReplayRoutes(service)
}
