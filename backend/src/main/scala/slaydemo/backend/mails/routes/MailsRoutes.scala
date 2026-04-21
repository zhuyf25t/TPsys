package slaydemo.backend.mails.routes

import java.io.InputStream
import java.nio.charset.StandardCharsets
import com.sun.net.httpserver.HttpExchange

import slaydemo.backend.mails.objects.MailRecord
import slaydemo.backend.mails.services.MailService

final class MailsRoutes(service: MailService) {
  def mails(exchange: HttpExchange): Unit = {
    addCors(exchange)
    try {
      exchange.getRequestMethod.toUpperCase match {
        case "OPTIONS" =>
          exchange.sendResponseHeaders(204, -1)
        case "GET" =>
          val query = parseQuery(exchange.getRequestURI.getRawQuery)
          query.get("owner").orElse(query.get("ownerHandle")) match {
            case Some(ownerHandle) if ownerHandle.trim.nonEmpty =>
              sendJson(exchange, 200, renderMails(service.list(ownerHandle)))
            case _ =>
              sendJson(exchange, 400, """{"error":"missing_owner","code":"missing_owner"}""")
          }
        case _ =>
          sendJson(exchange, 405, """{"error":"method_not_allowed","code":"method_not_allowed"}""")
      }
    } finally {
      exchange.close()
    }
  }

  def read(exchange: HttpExchange): Unit = {
    addCors(exchange)
    try {
      exchange.getRequestMethod.toUpperCase match {
        case "OPTIONS" =>
          exchange.sendResponseHeaders(204, -1)
        case "POST" =>
          parseBody(exchange.getRequestBody) match {
            case Right(fields) =>
              val ownerHandle = fields.getOrElse("ownerHandle", "").trim
              val mailId = fields.getOrElse("mailId", "").trim
              if (ownerHandle.isEmpty || mailId.isEmpty) {
                sendJson(exchange, 400, """{"error":"missing_fields","code":"missing_fields"}""")
              } else if (service.markRead(ownerHandle, mailId)) {
                sendJson(exchange, 200, """{"ok":true}""")
              } else {
                sendJson(exchange, 404, """{"error":"mail_not_found","code":"mail_not_found"}""")
              }
            case Left(error) =>
              sendJson(exchange, 400, s"""{"error":"${escape(error)}","code":"bad_request"}""")
          }
        case _ =>
          sendJson(exchange, 405, """{"error":"method_not_allowed","code":"method_not_allowed"}""")
      }
    } finally {
      exchange.close()
    }
  }

  private def renderMails(records: Seq[MailRecord]): String = {
    val body = records.map(renderMail).mkString(",\n")
    s"""{"mails":[
       |$body
       |]}""".stripMargin
  }

  private def renderMail(record: MailRecord): String = {
    s"""  {
       |    "id": "${escape(record.id)}",
       |    "ownerHandle": "${escape(record.ownerHandle)}",
       |    "kind": "${escape(record.kind)}",
       |    "subject": "${escape(record.subject)}",
       |    "excerpt": "${escape(record.excerpt)}",
       |    "senderLabel": "${escape(record.senderLabel)}",
       |    "unread": ${record.unread},
       |    "important": ${record.important},
       |    "createdAt": ${record.createdAt}
       |  }""".stripMargin
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
