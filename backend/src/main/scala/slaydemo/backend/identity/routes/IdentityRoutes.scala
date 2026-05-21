package slaydemo.backend.identity.routes

import java.util.Locale

import com.sun.net.httpserver.HttpExchange

import slaydemo.backend.identity.api.{IdentityAccountsResponse, IdentityAuthResponse, IdentityErrorResponse}
import slaydemo.backend.identity.objects.SessionToken
import slaydemo.backend.identity.services.{
  IdentityCurrentSessionError,
  IdentityRegistrationError,
  IdentityService,
  IdentitySessionError
}
import slaydemo.backend.shared.json.JsonObjectParser
import slaydemo.backend.shared.routes.HttpRouteSupport

final class IdentityRoutes(service: IdentityService) {
  def register(exchange: HttpExchange): Unit =
    handlePost(exchange) {
      readStringFields(exchange) match {
        case Left(message) =>
          jsonError(exchange, 400, "bad_request", message)
        case Right(fields) =>
          IdentityCommandParsers.parseRegistrationCommand(fields) match {
            case Left(IdentityRegistrationCommandParseError.InvalidHandle) =>
              jsonError(exchange, 400, "invalid_handle", "Handle must be 3-16 characters and use letters, numbers, -, _.")
            case Left(IdentityRegistrationCommandParseError.InvalidPassword) =>
              jsonError(exchange, 400, "invalid_password", "Password must be at least 4 characters.")
            case Left(IdentityRegistrationCommandParseError.InvalidSkin) =>
              jsonError(exchange, 400, "invalid_skin", "Skin must be one of: blue, old, soldier, survivor.")
            case Right(command) =>
              service.register(command) match {
                case Right(account) =>
                  jsonOk(exchange, 200, IdentityAuthResponse.renderAccount(account))
                case Left(IdentityRegistrationError.HandleTaken) =>
                  jsonError(exchange, 409, "handle_taken", "Handle already exists.")
              }
          }
      }
    }

  def issueSession(exchange: HttpExchange): Unit =
    handlePost(exchange) {
      readStringFields(exchange) match {
        case Left(message) =>
          jsonError(exchange, 400, "bad_request", message)
        case Right(fields) =>
          IdentityCommandParsers.parseSessionCommand(fields) match {
            case Left(IdentitySessionCommandParseError.InvalidCredentials) =>
              jsonError(exchange, 401, "invalid_credentials", "Handle or password is incorrect.")
            case Right(command) =>
              service.issueSession(command) match {
                case Right(account) =>
                  jsonOk(exchange, 200, IdentityAuthResponse.renderAccount(account))
                case Left(IdentitySessionError.InvalidCredentials) =>
                  jsonError(exchange, 401, "invalid_credentials", "Handle or password is incorrect.")
              }
          }
      }
    }

  def current(exchange: HttpExchange): Unit = {
    HttpRouteSupport.addCors(exchange)

    try {
      exchange.getRequestMethod.toUpperCase(Locale.ROOT) match {
        case "OPTIONS" =>
          HttpRouteSupport.sendEmpty(exchange, 204)
        case "GET" =>
          service.current(parseSessionToken(exchange)) match {
            case Right(account) =>
              jsonOk(exchange, 200, IdentityAuthResponse.renderAccount(account))
            case Left(IdentityCurrentSessionError.MissingSession) =>
              jsonError(exchange, 401, "missing_session", "Session token is required.")
            case Left(IdentityCurrentSessionError.InvalidSession) =>
              jsonError(exchange, 401, "invalid_session", "Current session is not valid.")
          }
        case _ =>
          jsonError(exchange, 405, "method_not_allowed", "Only GET and OPTIONS are supported.")
      }
    } finally {
      exchange.close()
    }
  }

  def accounts(exchange: HttpExchange): Unit = {
    HttpRouteSupport.addCors(exchange)

    try {
      exchange.getRequestMethod.toUpperCase(Locale.ROOT) match {
        case "OPTIONS" =>
          HttpRouteSupport.sendEmpty(exchange, 204)
        case "GET" =>
          HttpRouteSupport.sendJson(exchange, 200, IdentityAccountsResponse.render(service.listActiveAccounts()))
        case _ =>
          jsonError(exchange, 405, "method_not_allowed", "Only GET and OPTIONS are supported.")
      }
    } finally {
      exchange.close()
    }
  }

  private def handlePost(exchange: HttpExchange)(action: => Unit): Unit = {
    HttpRouteSupport.addCors(exchange)

    try {
      exchange.getRequestMethod.toUpperCase(Locale.ROOT) match {
        case "OPTIONS" =>
          HttpRouteSupport.sendEmpty(exchange, 204)
        case "POST" =>
          action
        case _ =>
          jsonError(exchange, 405, "method_not_allowed", "Only POST and OPTIONS are supported.")
      }
    } finally {
      exchange.close()
    }
  }

  private def readStringFields(exchange: HttpExchange): Either[String, Map[String, String]] = {
    val body = HttpRouteSupport.readRequestBody(exchange)
    JsonObjectParser
      .parseStringFields(body)
      .left
      .map(_ => "Request body must be a JSON object with string fields.")
  }

  private def parseSessionToken(exchange: HttpExchange): Option[SessionToken] = {
    IdentitySessionTokenParser.parse(
      authorization = Option(exchange.getRequestHeaders.getFirst("Authorization")),
      xSessionToken = Option(exchange.getRequestHeaders.getFirst("X-Session-Token"))
    )
  }

  private def jsonOk(exchange: HttpExchange, status: Int, responseJson: String): Unit =
    HttpRouteSupport.sendJson(exchange, status, responseJson)

  private def jsonError(exchange: HttpExchange, status: Int, code: String, message: String): Unit =
    HttpRouteSupport.sendJson(exchange, status, IdentityErrorResponse.render(code, message))
}

object IdentityRoutes {
  def apply(service: IdentityService): IdentityRoutes =
    new IdentityRoutes(service)
}
