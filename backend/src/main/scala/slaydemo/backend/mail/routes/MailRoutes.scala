package slaydemo.backend.mail.routes

import java.util.Locale

import com.sun.net.httpserver.HttpExchange

import slaydemo.backend.mail.objects.apiTypes.{
  MailCommandParsers,
  MailListResponse,
  MailReadResponse,
  MailRouteOwnerError,
  MailRouteReadError
}
import slaydemo.backend.mail.services.{MailReadError, MailService}
import slaydemo.backend.shared.json.JsonObjectParser
import slaydemo.backend.shared.routes.HttpRouteSupport

final class MailRoutes(service: MailService) {
  def mails(exchange: HttpExchange): Unit = {
    HttpRouteSupport.addCors(exchange)

    try {
      exchange.getRequestMethod.toUpperCase(Locale.ROOT) match {
        case "OPTIONS" =>
          HttpRouteSupport.sendEmpty(exchange, 204)
        case "GET" =>
          MailCommandParsers.parseOwner(exchange.getRequestURI.getRawQuery) match {
            case Left(MailRouteOwnerError.MissingOwner) =>
              HttpRouteSupport.sendJsonError(exchange, 400, "missing_owner", "missing_owner")
            case Left(MailRouteOwnerError.VisitorNotAllowed) =>
              HttpRouteSupport.sendJsonError(exchange, 403, "visitor_not_allowed", "visitor_not_allowed")
            case Left(MailRouteOwnerError.InvalidOwner) =>
              HttpRouteSupport.sendJsonError(exchange, 400, "invalid_owner", "invalid_owner")
            case Right(ownerHandle) =>
              HttpRouteSupport.sendJson(exchange, 200, MailListResponse.renderRecords(service.list(ownerHandle)))
          }
        case _ =>
          HttpRouteSupport.sendJsonError(exchange, 405, "method_not_allowed", "Method is not allowed.")
      }
    } finally {
      exchange.close()
    }
  }

  def read(exchange: HttpExchange): Unit = {
    HttpRouteSupport.addCors(exchange)

    try {
      exchange.getRequestMethod.toUpperCase(Locale.ROOT) match {
        case "OPTIONS" =>
          HttpRouteSupport.sendEmpty(exchange, 204)
        case "POST" =>
          JsonObjectParser.parseStringFields(HttpRouteSupport.readRequestBody(exchange)) match {
            case Left(_) =>
              HttpRouteSupport.sendJsonError(exchange, 400, "bad_request", "Request body must be a JSON object with string fields.")
            case Right(fields) =>
              MailCommandParsers.parseReadCommand(fields) match {
                case Left(MailRouteReadError.MissingOwner) =>
                  HttpRouteSupport.sendJsonError(exchange, 400, "missing_owner", "missing_owner")
                case Left(MailRouteReadError.VisitorNotAllowed) =>
                  HttpRouteSupport.sendJsonError(exchange, 403, "visitor_not_allowed", "visitor_not_allowed")
                case Left(MailRouteReadError.InvalidOwner) =>
                  HttpRouteSupport.sendJsonError(exchange, 400, "invalid_owner", "invalid_owner")
                case Left(MailRouteReadError.MissingMailId) =>
                  HttpRouteSupport.sendJsonError(exchange, 400, "missing_mail_id", "missing_mail_id")
                case Right(command) =>
                  service.markRead(command.ownerHandle, command.mailId) match {
                    case Right(_) =>
                      HttpRouteSupport.sendJson(exchange, 200, MailReadResponse.renderOk)
                    case Left(MailReadError.MailNotFound) =>
                      HttpRouteSupport.sendJsonError(exchange, 404, "mail_not_found", "mail_not_found")
                  }
              }
          }
        case _ =>
          HttpRouteSupport.sendJsonError(exchange, 405, "method_not_allowed", "Method is not allowed.")
      }
    } finally {
      exchange.close()
    }
  }
}

object MailRoutes {
  def apply(service: MailService): MailRoutes =
    new MailRoutes(service)
}
