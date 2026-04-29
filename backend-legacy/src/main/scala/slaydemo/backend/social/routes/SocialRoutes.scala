package slaydemo.backend.social.routes

import java.io.InputStream
import java.nio.charset.StandardCharsets
import com.sun.net.httpserver.HttpExchange

import slaydemo.backend.social.services.FriendRequestService
import slaydemo.backend.social.services.{FriendRequestResponseResult, FriendRequestSubmissionResult}

final class SocialRoutes(service: FriendRequestService) {
  def friendRequests(exchange: HttpExchange): Unit = {
    addCors(exchange)
    try {
      exchange.getRequestMethod.toUpperCase match {
        case "OPTIONS" =>
          exchange.sendResponseHeaders(204, -1)
        case "GET" if !exchange.getRequestURI.getPath.endsWith("/respond") =>
          list(exchange)
        case "POST" if exchange.getRequestURI.getPath.endsWith("/respond") =>
          respond(exchange)
        case "POST" =>
          create(exchange)
        case _ =>
          sendJson(exchange, 405, """{"error":"method_not_allowed","code":"method_not_allowed"}""")
      }
    } finally {
      exchange.close()
    }
  }

  private def list(exchange: HttpExchange): Unit = {
    val query = parseQuery(exchange.getRequestURI.getRawQuery)
    query.get("ownerHandle") match {
      case Some(ownerHandle) if ownerHandle.trim.nonEmpty =>
        sendJson(exchange, 200, renderRequests(service.list(ownerHandle)))
      case _ =>
        sendJson(exchange, 400, """{"error":"missing_owner","code":"missing_owner"}""")
    }
  }

  private def create(exchange: HttpExchange): Unit = {
    exchange.getRequestMethod.toUpperCase match {
      case "OPTIONS" =>
        exchange.sendResponseHeaders(204, -1)
      case "POST" =>
        parseBody(exchange.getRequestBody) match {
          case Right(fields) =>
            service.create(fields.getOrElse("sourceHandle", ""), fields.getOrElse("targetHandle", "")) match {
              case Right(result) =>
                sendJson(exchange, 200, renderCreateResult(result))
              case Left("invalid_handles") =>
                sendJson(exchange, 400, """{"error":"invalid_handles","code":"invalid_handles"}""")
              case Left(other) =>
                sendJson(exchange, 400, s"""{"error":"${escape(other)}","code":"bad_request"}""")
            }
          case Left(error) =>
            sendJson(exchange, 400, s"""{"error":"${escape(error)}","code":"bad_request"}""")
        }
      case _ =>
        sendJson(exchange, 405, """{"error":"method_not_allowed","code":"method_not_allowed"}""")
    }
  }

  private def respond(exchange: HttpExchange): Unit = {
    exchange.getRequestMethod.toUpperCase match {
      case "OPTIONS" =>
        exchange.sendResponseHeaders(204, -1)
      case "POST" =>
        parseBody(exchange.getRequestBody) match {
          case Right(fields) =>
            service.respond(
              fields.getOrElse("requestId", ""),
              fields.getOrElse("actorHandle", ""),
              fields.getOrElse("decision", "")
            ) match {
              case Right(result) =>
                sendJson(exchange, 200, renderResponseResult(result))
              case Left("missing_fields") =>
                sendJson(exchange, 400, """{"error":"missing_fields","code":"missing_fields"}""")
              case Left("invalid_decision") =>
                sendJson(exchange, 400, """{"error":"invalid_decision","code":"invalid_decision"}""")
              case Left("forbidden") =>
                sendJson(exchange, 403, """{"error":"forbidden","code":"forbidden"}""")
              case Left("request_not_found") =>
                sendJson(exchange, 404, """{"error":"request_not_found","code":"request_not_found"}""")
              case Left(other) =>
                sendJson(exchange, 400, s"""{"error":"${escape(other)}","code":"bad_request"}""")
            }
          case Left(error) =>
            sendJson(exchange, 400, s"""{"error":"${escape(error)}","code":"bad_request"}""")
        }
      case _ =>
        sendJson(exchange, 405, """{"error":"method_not_allowed","code":"method_not_allowed"}""")
    }
  }

  private def renderCreateResult(result: FriendRequestSubmissionResult): String = {
    val mail = result.mail.map(renderMail).getOrElse("null")
    s"""{
       |  "created": ${result.created},
       |  "alreadySent": ${result.alreadySent},
       |  "request": ${renderRequest(result.request)},
       |  "mail": $mail
       |}""".stripMargin
  }

  private def renderRequests(records: Seq[slaydemo.backend.social.objects.FriendRequestRecord]): String = {
    val body = records.map(renderRequest).mkString(",\n")
    s"""{
       |  "requests": [
       |$body
       |  ]
       |}""".stripMargin
  }

  private def renderResponseResult(result: FriendRequestResponseResult): String = {
    val mail = result.mail.map(renderMail).getOrElse("null")
    s"""{
       |  "request": ${renderRequest(result.request)},
       |  "mail": $mail
       |}""".stripMargin
  }

  private def renderRequest(request: slaydemo.backend.social.objects.FriendRequestRecord): String = {
    val respondedAt = request.respondedAt.map(_.toString).getOrElse("null")
    s"""{
       |    "id": "${escape(request.id)}",
       |    "sourceHandle": "${escape(request.sourceHandle)}",
       |    "targetHandle": "${escape(request.targetHandle)}",
       |    "createdAt": ${request.createdAt},
       |    "status": "${escape(request.status)}",
       |    "respondedAt": $respondedAt
       |  }""".stripMargin
  }

  private def renderMail(mail: slaydemo.backend.mails.objects.MailRecord): String = {
    s"""{
       |    "id": "${escape(mail.id)}",
       |    "ownerHandle": "${escape(mail.ownerHandle)}",
       |    "kind": "${escape(mail.kind)}",
       |    "subject": "${escape(mail.subject)}",
       |    "excerpt": "${escape(mail.excerpt)}",
       |    "senderLabel": "${escape(mail.senderLabel)}",
       |    "unread": ${mail.unread},
       |    "important": ${mail.important},
       |    "createdAt": ${mail.createdAt}
       |  }""".stripMargin
  }

  private def parseQuery(query: String): Map[String, String] = {
    Option(query).toSeq
      .flatMap(_.split("&").toSeq)
      .flatMap { pair =>
        pair.split("=", 2).toSeq match {
          case Seq(key, value) => Some(urlDecode(key) -> urlDecode(value))
          case Seq(key)        => Some(urlDecode(key) -> "")
          case _               => None
        }
      }
      .toMap
  }

  private def parseBody(input: InputStream): Either[String, Map[String, String]] = {
    val body = new String(input.readAllBytes(), StandardCharsets.UTF_8).trim
    if (body.isEmpty) {
      Right(Map.empty)
    } else {
      val pattern = "\"([^\"]+)\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"".r
      val pairs = pattern.findAllMatchIn(body).map(matchResult => matchResult.group(1) -> unescape(matchResult.group(2))).toMap
      if (pairs.nonEmpty) Right(pairs) else Left("Request body must be a JSON object with string fields.")
    }
  }

  private def addCors(exchange: HttpExchange): Unit = {
    val headers = exchange.getResponseHeaders
    headers.set("Access-Control-Allow-Origin", "*")
    headers.set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Session-Token")
    headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
    headers.set("Content-Type", "application/json; charset=utf-8")
  }

  private def sendJson(exchange: HttpExchange, status: Int, json: String): Unit = {
    val bytes = json.getBytes(StandardCharsets.UTF_8)
    exchange.sendResponseHeaders(status, bytes.length.toLong)
    val output = exchange.getResponseBody
    try output.write(bytes)
    finally output.close()
  }

  private def urlDecode(value: String): String =
    java.net.URLDecoder.decode(value, StandardCharsets.UTF_8)

  private def escape(value: String): String =
    value
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("\n", "\\n")
      .replace("\r", "\\r")
      .replace("\t", "\\t")

  private def unescape(value: String): String =
    value
      .replace("\\n", "\n")
      .replace("\\r", "\r")
      .replace("\\\"", "\"")
      .replace("\\\\", "\\")
}
