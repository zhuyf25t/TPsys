package slaydemo.backend.identity.routes

import java.util.Locale

import com.sun.net.httpserver.HttpExchange

import slaydemo.backend.identity.api.IdentityAuthResponse
import slaydemo.backend.identity.objects.{IdentityAccount, PlainTextPassword, PlayerHandle, SessionToken, SkinId}
import slaydemo.backend.identity.services.{
  IdentityCurrentSessionError,
  IdentityRegistrationCommand,
  IdentityRegistrationError,
  IdentityService,
  IdentitySessionCommand,
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
          parseRegistrationCommand(fields) match {
            case Left(IdentityRegistrationCommandParseError.InvalidHandle) =>
              jsonError(exchange, 400, "invalid_handle", "Handle must be 3-16 characters and use letters, numbers, -, _.")
            case Left(IdentityRegistrationCommandParseError.InvalidPassword) =>
              jsonError(exchange, 400, "invalid_password", "Password must be at least 4 characters.")
            case Left(IdentityRegistrationCommandParseError.InvalidSkin) =>
              jsonError(exchange, 400, "invalid_skin", "Skin must be one of: blue, old, soldier, survivor.")
            case Right(command) =>
              service.register(command) match {
                case Right(account) =>
                  jsonOk(exchange, 200, toAuthResponse(account))
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
          parseSessionCommand(fields) match {
            case Left(IdentitySessionCommandParseError.InvalidCredentials) =>
              jsonError(exchange, 401, "invalid_credentials", "Handle or password is incorrect.")
            case Right(command) =>
              service.issueSession(command) match {
                case Right(account) =>
                  jsonOk(exchange, 200, toAuthResponse(account))
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
              jsonOk(exchange, 200, toAuthResponse(account))
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
          val accounts = service.listActiveAccounts()
          val renderedAccounts = accounts
            .map(account =>
              s"""{"handle":"${HttpRouteSupport.escapeJson(account.handle)}","displayName":"${HttpRouteSupport.escapeJson(account.displayName)}","skinId":"${HttpRouteSupport.escapeJson(account.skinId)}"}"""
            )
            .mkString(",")
          HttpRouteSupport.sendJson(exchange, 200, s"""{"accounts":[$renderedAccounts]}""")
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

  private def parseRegistrationCommand(
    fields: Map[String, String]
  ): Either[IdentityRegistrationCommandParseError, IdentityRegistrationCommand] =
    for {
      handle <- PlayerHandle.forRegistration(fields.getOrElse("handle", ""))
        .toRight(IdentityRegistrationCommandParseError.InvalidHandle)
      password <- PlainTextPassword.fromString(fields.getOrElse("password", ""))
        .toRight(IdentityRegistrationCommandParseError.InvalidPassword)
      skinId <- SkinId.fromString(fields.getOrElse("skinId", "blue"))
        .toRight(IdentityRegistrationCommandParseError.InvalidSkin)
    } yield IdentityRegistrationCommand(
      handle = handle,
      password = password,
      skinId = skinId
    )

  private def parseSessionCommand(
    fields: Map[String, String]
  ): Either[IdentitySessionCommandParseError, IdentitySessionCommand] =
    for {
      handle <- PlayerHandle.forLookup(fields.getOrElse("handle", ""))
        .toRight(IdentitySessionCommandParseError.InvalidCredentials)
      password <- PlainTextPassword.fromString(fields.getOrElse("password", ""))
        .toRight(IdentitySessionCommandParseError.InvalidCredentials)
    } yield IdentitySessionCommand(
      handle = handle,
      password = password
    )

  private def parseSessionToken(exchange: HttpExchange): Option[SessionToken] = {
    val authorization = Option(exchange.getRequestHeaders.getFirst("Authorization")).map(_.trim).filter(_.nonEmpty)
    val fromAuthorization = authorization.flatMap { header =>
      header.split("\\s+", 2).toList match {
        case method :: token :: Nil if method.equalsIgnoreCase("Bearer") => SessionToken.fromString(token)
        case token :: Nil                                                => SessionToken.fromString(token)
        case _                                                           => None
      }
    }

    fromAuthorization.orElse(
      Option(exchange.getRequestHeaders.getFirst("X-Session-Token")).flatMap(SessionToken.fromString)
    )
  }

  private def toAuthResponse(account: IdentityAccount): IdentityAuthResponse =
    IdentityAuthResponse(
      handle = account.handle.value,
      skinId = SkinId.wireValue(account.skinId),
      session = account.sessionToken.map(_.value).getOrElse("")
    )

  private def jsonOk(exchange: HttpExchange, status: Int, response: IdentityAuthResponse): Unit =
    HttpRouteSupport.sendJson(
      exchange,
      status,
      s"""{"handle":"${HttpRouteSupport.escapeJson(response.handle)}","skinId":"${HttpRouteSupport.escapeJson(response.skinId)}","session":"${HttpRouteSupport.escapeJson(response.session)}"}"""
    )

  private def jsonError(exchange: HttpExchange, status: Int, code: String, message: String): Unit =
    HttpRouteSupport.sendJson(
      exchange,
      status,
      s"""{"error":"${HttpRouteSupport.escapeJson(message)}","code":"${HttpRouteSupport.escapeJson(code)}"}"""
    )
}

object IdentityRoutes {
  def apply(service: IdentityService): IdentityRoutes =
    new IdentityRoutes(service)
}

private enum IdentityRegistrationCommandParseError {
  case InvalidHandle
  case InvalidPassword
  case InvalidSkin
}

private enum IdentitySessionCommandParseError {
  case InvalidCredentials
}
