package slaydemo.backend.identity.routes

import java.io.InputStream
import java.nio.charset.StandardCharsets
import com.sun.net.httpserver.HttpExchange

import slaydemo.backend.identity.api.{IdentityAuthResponse, IdentityRegisterRequest, IdentitySessionRequest}
import slaydemo.backend.identity.services.IdentityService

final class IdentityRoutes(service: IdentityService) {
  private val allowedSkins = Set("blue", "survivor", "soldier", "old")

  def register(exchange: HttpExchange): Unit = {
    handle(exchange) {
      parseBody(exchange.getRequestBody) match {
        case Right(fields) =>
          val request = IdentityRegisterRequest(
            handle = fields.getOrElse("handle", ""),
            password = fields.getOrElse("password", ""),
            skinId = fields.getOrElse("skinId", "blue")
          )

          service.register(request.handle, request.password, request.skinId) match {
            case Right(account) =>
              jsonOk(exchange, 200, IdentityAuthResponse(account.handle, account.skinId, account.sessionToken))
            case Left("handle_taken") =>
              jsonError(exchange, 409, "handle_taken", "Handle already exists.")
            case Left("invalid_handle") =>
              jsonError(exchange, 400, "invalid_handle", "Handle must be 3-16 characters and use letters, numbers, -, _.")
            case Left("invalid_password") =>
              jsonError(exchange, 400, "invalid_password", "Password must be at least 4 characters.")
            case Left("invalid_skin") =>
              jsonError(exchange, 400, "invalid_skin", s"Skin must be one of: ${allowedSkins.toSeq.sorted.mkString(", ")}.")
            case Left(other) =>
              jsonError(exchange, 400, other, "Registration rejected.")
          }
        case Left(error) =>
          jsonError(exchange, 400, "bad_request", error)
      }
    }
  }

  def issueSession(exchange: HttpExchange): Unit = {
    handle(exchange) {
      parseBody(exchange.getRequestBody) match {
        case Right(fields) =>
          val request = IdentitySessionRequest(
            handle = fields.getOrElse("handle", ""),
            password = fields.getOrElse("password", "")
          )

          service.issueSession(request.handle, request.password) match {
            case Right(account) =>
              jsonOk(exchange, 200, IdentityAuthResponse(account.handle, account.skinId, account.sessionToken))
            case Left("invalid_credentials") =>
              jsonError(exchange, 401, "invalid_credentials", "Handle or password is incorrect.")
            case Left(other) =>
              jsonError(exchange, 400, other, "Session request rejected.")
          }
        case Left(error) =>
          jsonError(exchange, 400, "bad_request", error)
      }
    }
  }

  def current(exchange: HttpExchange): Unit = {
    addCors(exchange)
    try {
      exchange.getRequestMethod.toUpperCase match {
        case "OPTIONS" =>
          exchange.sendResponseHeaders(204, -1)
        case "GET" =>
          parseSessionToken(exchange) match {
            case Some(sessionToken) =>
              service.loadAccountBySessionToken(sessionToken) match {
                case Some(account) =>
                  jsonOk(exchange, 200, IdentityAuthResponse(account.handle, account.skinId, account.sessionToken))
                case None =>
                  jsonError(exchange, 401, "invalid_session", "Current session is not valid.")
              }
            case None =>
              jsonError(exchange, 401, "missing_session", "Session token is required.")
          }
        case _ =>
          jsonError(exchange, 405, "method_not_allowed", "Only GET and OPTIONS are supported.")
      }
    } finally {
      exchange.close()
    }
  }

  def accounts(exchange: HttpExchange): Unit = {
    addCors(exchange)
    try {
      exchange.getRequestMethod.toUpperCase match {
        case "OPTIONS" =>
          exchange.sendResponseHeaders(204, -1)
        case "GET" =>
          val accounts = service.listActiveAccounts()
          sendJson(
            exchange,
            200,
            s"""{"accounts":[${accounts.map(renderAccountSummary).mkString(",")}]}"""
          )
        case _ =>
          jsonError(exchange, 405, "method_not_allowed", "Only GET and OPTIONS are supported.")
      }
    } finally {
      exchange.close()
    }
  }

  private def handle(exchange: HttpExchange)(action: => Unit): Unit = {
    addCors(exchange)
    try {
      exchange.getRequestMethod.toUpperCase match {
        case "OPTIONS" =>
          exchange.sendResponseHeaders(204, -1)
        case "POST" =>
          action
        case _ =>
          jsonError(exchange, 405, "method_not_allowed", "Only POST and OPTIONS are supported.")
      }
    } finally {
      exchange.close()
    }
  }

  private def addCors(exchange: HttpExchange): Unit = {
    val headers = exchange.getResponseHeaders
    headers.set("Access-Control-Allow-Origin", "*")
    headers.set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Session-Token")
    headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
    headers.set("Content-Type", "application/json; charset=utf-8")
  }

  private def parseSessionToken(exchange: HttpExchange): Option[String] = {
    val authorization = Option(exchange.getRequestHeaders.getFirst("Authorization")).map(_.trim).filter(_.nonEmpty)
    val tokenFromAuthorization = authorization.flatMap { header =>
      header.split("\\s+", 2).toList match {
        case "Bearer" :: token :: Nil if token.trim.nonEmpty => Some(token.trim)
        case token :: Nil if token.nonEmpty => Some(token)
        case _ => None
      }
    }

    tokenFromAuthorization
      .orElse(Option(exchange.getRequestHeaders.getFirst("X-Session-Token")).map(_.trim).filter(_.nonEmpty))
  }

  private def parseBody(input: InputStream): Either[String, Map[String, String]] = {
    val body = new String(input.readAllBytes(), StandardCharsets.UTF_8).trim
    if (body.isEmpty) {
      Right(Map.empty)
    } else {
      val pattern = "\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"".r
      val pairs = pattern.findAllMatchIn(body).map(matchResult => matchResult.group(1) -> unescape(matchResult.group(2))).toMap

      if (pairs.nonEmpty) Right(pairs)
      else Left("Request body must be a JSON object with string fields.")
    }
  }

  private def jsonOk(exchange: HttpExchange, status: Int, response: IdentityAuthResponse): Unit = {
    sendJson(exchange, status, s"""{"handle":"${escape(response.handle)}","skinId":"${escape(response.skinId)}","session":"${escape(response.session)}"}""")
  }

  private def renderAccountSummary(account: slaydemo.backend.identity.objects.IdentityAccount): String = {
    s"""{"handle":"${escape(account.handle)}","displayName":"${escape(account.displayName)}","skinId":"${escape(account.skinId)}"}"""
  }

  private def jsonError(exchange: HttpExchange, status: Int, code: String, message: String): Unit = {
    sendJson(
      exchange,
      status,
      s"""{"error":"${escape(message)}","code":"${escape(code)}"}"""
    )
  }

  private def sendJson(exchange: HttpExchange, status: Int, json: String): Unit = {
    val bytes = json.getBytes(StandardCharsets.UTF_8)
    exchange.sendResponseHeaders(status, bytes.length.toLong)
    val output = exchange.getResponseBody
    try output.write(bytes)
    finally output.close()
  }

  private def escape(value: String): String =
    value
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\r", "\\r")

  private def unescape(value: String): String =
    value
      .replace("\\n", "\n")
      .replace("\\r", "\r")
      .replace("\\\"", "\"")
      .replace("\\\\", "\\")
}
