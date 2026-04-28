package slaydemo.backend.governance.routes

import java.io.InputStream
import java.nio.charset.StandardCharsets
import com.sun.net.httpserver.HttpExchange

import slaydemo.backend.governance.objects.{ContributionAdjustmentRecord, GovernanceReviewNotificationRecord}
import slaydemo.backend.governance.services.{
  ContributionAdjustmentService,
  ContributionAdjustmentSubmissionResult,
  GovernanceNotificationService,
  GovernanceReviewNotificationSubmissionResult
}
import slaydemo.backend.mails.objects.MailRecord

final class GovernanceRoutes(
  contributionAdjustmentService: ContributionAdjustmentService,
  notificationService: GovernanceNotificationService
) {
  def contributionAdjustments(exchange: HttpExchange): Unit = {
    addCors(exchange)
    try {
      exchange.getRequestMethod.toUpperCase match {
        case "OPTIONS" =>
          exchange.sendResponseHeaders(204, -1)
        case "GET" =>
          val query = parseQuery(exchange.getRequestURI.getRawQuery)
          val limit = query.get("limit").flatMap(_.toIntOption).getOrElse(500)
          sendJson(exchange, 200, renderAdjustments(contributionAdjustmentService.list(limit)))
        case "POST" =>
          parseContributionAdjustmentBody(exchange.getRequestBody) match {
            case Right(request) =>
              contributionAdjustmentService.create(
                request.actorHandle,
                request.targetHandle,
                request.delta,
                request.reason,
                request.sourceLabel,
                request.sourcePath
              ) match {
                case Right(result) =>
                  sendJson(exchange, 200, renderResult(result))
                case Left("invalid_actor") =>
                  sendJson(exchange, 403, """{"error":"invalid_actor","code":"invalid_actor"}""")
                case Left("invalid_target") =>
                  sendJson(exchange, 400, """{"error":"invalid_target","code":"invalid_target"}""")
                case Left("invalid_delta") =>
                  sendJson(exchange, 400, """{"error":"invalid_delta","code":"invalid_delta"}""")
                case Left(other) =>
                  sendJson(exchange, 400, s"""{"error":"${escape(other)}","code":"bad_request"}""")
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

  def adminNotifications(exchange: HttpExchange): Unit = {
    addCors(exchange)
    try {
      exchange.getRequestMethod.toUpperCase match {
        case "OPTIONS" =>
          exchange.sendResponseHeaders(204, -1)
        case "GET" =>
          val query = parseQuery(exchange.getRequestURI.getRawQuery)
          val limit = query.get("limit").flatMap(_.toIntOption).getOrElse(100)
          val kind = query.get("kind").map(_.trim).filter(_.nonEmpty)
          val targetType = query.get("targetType").map(_.trim).filter(_.nonEmpty)
          sendJson(
            exchange,
            200,
            renderNotifications(notificationService.listReviewNotifications(kind, targetType, limit))
          )
        case "POST" =>
          parseReviewNotificationBody(exchange.getRequestBody) match {
            case Right(request) =>
              notificationService.createReviewNotification(
                request.actorHandle,
                request.kind,
                request.targetType,
                request.targetId,
                request.targetTitle,
                request.targetPath,
                request.body
              ) match {
                case Right(result) =>
                  sendJson(exchange, 200, renderNotificationResult(result))
                case Left("invalid_kind") =>
                  sendJson(exchange, 400, """{"error":"invalid_kind","code":"invalid_kind"}""")
                case Left("invalid_target") =>
                  sendJson(exchange, 400, """{"error":"invalid_target","code":"invalid_target"}""")
                case Left("invalid_body") =>
                  sendJson(exchange, 400, """{"error":"invalid_body","code":"invalid_body"}""")
                case Left(other) =>
                  sendJson(exchange, 400, s"""{"error":"${escape(other)}","code":"bad_request"}""")
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

  private final case class ContributionAdjustmentRequest(
    actorHandle: String,
    targetHandle: String,
    delta: Int,
    reason: String,
    sourceLabel: String,
    sourcePath: String
  )

  private final case class GovernanceReviewNotificationRequest(
    actorHandle: String,
    kind: String,
    targetType: String,
    targetId: String,
    targetTitle: String,
    targetPath: String,
    body: String
  )

  private def renderResult(result: ContributionAdjustmentSubmissionResult): String = {
    s"""{
       |  "ok": true,
       |  "adjustment": ${renderAdjustment(result.record)},
       |  "mail": ${renderMail(result.mail)}
       |}""".stripMargin
  }

  private def renderNotificationResult(result: GovernanceReviewNotificationSubmissionResult): String = {
    s"""{
       |  "ok": true,
       |  "notification": ${renderNotification(result.record)},
       |  "mail": ${renderMail(result.mail, Some(result.record))}
       |}""".stripMargin
  }

  private def renderAdjustments(records: Seq[ContributionAdjustmentRecord]): String = {
    val body = records.map(record => s"  ${renderAdjustment(record)}").mkString(",\n")
    s"""{"adjustments":[
       |$body
       |]}""".stripMargin
  }

  private def renderNotifications(records: Seq[GovernanceReviewNotificationRecord]): String = {
    val body = records.map(record => s"  ${renderNotification(record)}").mkString(",\n")
    s"""{"notifications":[
       |$body
       |]}""".stripMargin
  }

  private def renderAdjustment(record: ContributionAdjustmentRecord): String = {
    s"""{
       |    "id": "${escape(record.id)}",
       |    "actorHandle": "${escape(record.actorHandle)}",
       |    "targetHandle": "${escape(record.targetHandle)}",
       |    "delta": ${record.delta},
       |    "reason": "${escape(record.reason)}",
       |    "createdAt": ${record.createdAt},
       |    "sourceLabel": "${escape(record.sourceLabel)}",
       |    "sourcePath": "${escape(record.sourcePath)}"
       |  }""".stripMargin
  }

  private def renderNotification(record: GovernanceReviewNotificationRecord): String = {
    s"""{
       |    "id": "${escape(record.id)}",
       |    "actorHandle": "${escape(record.actorHandle)}",
       |    "kind": "${escape(record.kind)}",
       |    "targetType": "${escape(record.targetType)}",
       |    "targetId": "${escape(record.targetId)}",
       |    "targetTitle": "${escape(record.targetTitle)}",
       |    "targetPath": "${escape(record.targetPath)}",
       |    "body": "${escape(record.body)}",
       |    "createdAt": ${record.createdAt},
       |    "mailId": "${escape(record.mailId)}"
       |  }""".stripMargin
  }

  private def renderMail(mail: MailRecord, notification: Option[GovernanceReviewNotificationRecord] = None): String = {
    val governanceFields = renderGovernanceMailFields(notification)
    s"""{
       |    "id": "${escape(mail.id)}",
       |    "ownerHandle": "${escape(mail.ownerHandle)}",
       |    "kind": "${escape(mail.kind)}",
       |    "subject": "${escape(mail.subject)}",
       |    "excerpt": "${escape(mail.excerpt)}",
       |    "senderLabel": "${escape(mail.senderLabel)}",
       |    "unread": ${mail.unread},
       |    "important": ${mail.important},
       |    "createdAt": ${mail.createdAt}$governanceFields
       |  }""".stripMargin
  }

  private def renderGovernanceMailFields(notification: Option[GovernanceReviewNotificationRecord]): String = {
    notification
      .map { record =>
        val targetLabel = if (record.targetTitle.trim.nonEmpty) record.targetTitle else record.targetId
        s""",
           |    "governanceActorHandle": "${escape(record.actorHandle)}",
           |    "governanceTargetPath": "${escape(record.targetPath)}",
           |    "governanceTargetLabel": "${escape(targetLabel)}",
           |    "governanceTargetType": "${escape(record.targetType)}",
           |    "governanceNotificationId": "${escape(record.id)}"""".stripMargin
      }
      .getOrElse("")
  }

  private def parseContributionAdjustmentBody(input: InputStream): Either[String, ContributionAdjustmentRequest] = {
    val body = new String(input.readAllBytes(), StandardCharsets.UTF_8).trim
    if (body.isEmpty) {
      Left("Request body must be a JSON object.")
    } else {
      val fields = extractStringFields(body)
      val delta = extractIntField(body, "delta").orElse(fields.get("delta").flatMap(_.toIntOption))
      val actorHandle = fields.getOrElse("actorHandle", "").trim
      val targetHandle = fields.getOrElse("targetHandle", "").trim
      val reason = fields.getOrElse("reason", "").trim
      val sourceLabel = fields.getOrElse("sourceLabel", "").trim
      val sourcePath = fields.getOrElse("sourcePath", "").trim

      if (actorHandle.isEmpty || targetHandle.isEmpty || delta.isEmpty) {
        Left("Request body must include actorHandle, targetHandle, and delta.")
      } else {
        Right(ContributionAdjustmentRequest(actorHandle, targetHandle, delta.get, reason, sourceLabel, sourcePath))
      }
    }
  }

  private def parseReviewNotificationBody(input: InputStream): Either[String, GovernanceReviewNotificationRequest] = {
    val body = new String(input.readAllBytes(), StandardCharsets.UTF_8).trim
    if (body.isEmpty) {
      Left("Request body must be a JSON object.")
    } else {
      val fields = extractStringFields(body)
      val actorHandle = fields.getOrElse("actorHandle", "").trim
      val kind = fields.getOrElse("kind", "").trim
      val targetType = fields.getOrElse("targetType", "").trim
      val targetId = fields.getOrElse("targetId", "").trim
      val targetTitle = fields.getOrElse("targetTitle", "").trim
      val targetPath = fields.getOrElse("targetPath", "").trim
      val bodyText = fields.getOrElse("body", "").trim

      if (kind.isEmpty || targetType.isEmpty || targetId.isEmpty || bodyText.isEmpty) {
        Left("Request body must include kind, targetType, targetId, and body.")
      } else {
        Right(GovernanceReviewNotificationRequest(actorHandle, kind, targetType, targetId, targetTitle, targetPath, bodyText))
      }
    }
  }

  private def extractStringFields(body: String): Map[String, String] = {
    val pattern = "\"([^\"]+)\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"".r
    pattern.findAllMatchIn(body).map(matchResult => matchResult.group(1) -> unescape(matchResult.group(2))).toMap
  }

  private def extractIntField(body: String, field: String): Option[Int] = {
    val pattern = s""""$field"\\s*:\\s*(-?\\d+)""".r
    pattern.findFirstMatchIn(body).flatMap(matchResult => matchResult.group(1).toIntOption)
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
