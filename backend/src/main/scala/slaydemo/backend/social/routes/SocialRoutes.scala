package slaydemo.backend.social.routes

import java.util.Locale

import com.sun.net.httpserver.HttpExchange

import slaydemo.backend.shared.json.JsonObjectParser
import slaydemo.backend.shared.routes.HttpRouteSupport
import slaydemo.backend.social.services.{
  FriendRequestCreateError,
  FriendRequestRespondError,
  FriendRequestService
}

final class SocialRoutes(service: FriendRequestService) {
  def friendRequests(exchange: HttpExchange): Unit = {
    HttpRouteSupport.addCors(exchange)

    try {
      exchange.getRequestMethod.toUpperCase(Locale.ROOT) match {
        case "OPTIONS" =>
          HttpRouteSupport.sendEmpty(exchange, 204)
        case "GET" if !exchange.getRequestURI.getPath.endsWith("/respond") =>
          list(exchange)
        case "POST" if exchange.getRequestURI.getPath.endsWith("/respond") =>
          respond(exchange)
        case "POST" =>
          create(exchange)
        case _ =>
          HttpRouteSupport.sendJsonError(exchange, 405, "method_not_allowed", "Method is not allowed.")
      }
    } finally {
      exchange.close()
    }
  }

  private def list(exchange: HttpExchange): Unit =
    SocialCommandParsers.parseOwner(exchange.getRequestURI.getRawQuery) match {
      case Left(SocialRouteHandleError.Missing) =>
        HttpRouteSupport.sendJsonError(exchange, 400, "missing_owner", "missing_owner")
      case Left(SocialRouteHandleError.VisitorNotAllowed) =>
        HttpRouteSupport.sendJsonError(exchange, 403, "visitor_not_allowed", "visitor_not_allowed")
      case Left(SocialRouteHandleError.Invalid) =>
        HttpRouteSupport.sendJsonError(exchange, 400, "invalid_owner", "invalid_owner")
      case Right(ownerHandle) =>
        HttpRouteSupport.sendJson(exchange, 200, SocialRouteJsonRenderer.renderRequests(service.list(ownerHandle)))
    }

  private def create(exchange: HttpExchange): Unit =
    JsonObjectParser.parseStringFields(HttpRouteSupport.readRequestBody(exchange)) match {
      case Left(_) =>
        HttpRouteSupport.sendJsonError(exchange, 400, "bad_request", "Request body must be a JSON object with string fields.")
      case Right(fields) =>
        SocialCommandParsers.parseCreateHandles(fields) match {
          case Left(SocialRouteCreateError.InvalidHandles) =>
            HttpRouteSupport.sendJsonError(exchange, 400, "invalid_handles", "invalid_handles")
          case Left(SocialRouteCreateError.VisitorNotAllowed) =>
            HttpRouteSupport.sendJsonError(exchange, 403, "visitor_not_allowed", "visitor_not_allowed")
          case Right(command) =>
            service.create(command.sourceHandle, command.targetHandle) match {
              case Right(result) =>
                HttpRouteSupport.sendJson(exchange, 200, SocialRouteJsonRenderer.renderCreateResult(result))
              case Left(FriendRequestCreateError.InvalidHandles) =>
                HttpRouteSupport.sendJsonError(exchange, 400, "invalid_handles", "invalid_handles")
            }
        }
    }

  private def respond(exchange: HttpExchange): Unit =
    JsonObjectParser.parseStringFields(HttpRouteSupport.readRequestBody(exchange)) match {
      case Left(_) =>
        HttpRouteSupport.sendJsonError(exchange, 400, "bad_request", "Request body must be a JSON object with string fields.")
      case Right(fields) =>
        SocialCommandParsers.parseRespondCommand(fields) match {
          case Left(SocialRouteRespondError.InvalidDecision) =>
            HttpRouteSupport.sendJsonError(exchange, 400, "invalid_decision", "invalid_decision")
          case Left(SocialRouteRespondError.MissingFields) =>
            HttpRouteSupport.sendJsonError(exchange, 400, "missing_fields", "missing_fields")
          case Left(SocialRouteRespondError.InvalidActorHandle) =>
            HttpRouteSupport.sendJsonError(exchange, 400, "invalid_actor", "invalid_actor")
          case Left(SocialRouteRespondError.VisitorNotAllowed) =>
            HttpRouteSupport.sendJsonError(exchange, 403, "visitor_not_allowed", "visitor_not_allowed")
          case Right(command) =>
            service.respond(command.requestId, command.actorHandle, command.decision) match {
              case Right(result) =>
                HttpRouteSupport.sendJson(exchange, 200, SocialRouteJsonRenderer.renderResponseResult(result))
              case Left(FriendRequestRespondError.RequestNotFound) =>
                HttpRouteSupport.sendJsonError(exchange, 404, "request_not_found", "request_not_found")
              case Left(FriendRequestRespondError.Forbidden) =>
                HttpRouteSupport.sendJsonError(exchange, 403, "forbidden", "forbidden")
            }
        }
    }
}

object SocialRoutes {
  def apply(service: FriendRequestService): SocialRoutes =
    new SocialRoutes(service)
}
