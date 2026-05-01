package slaydemo.backend.governance.routes

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

import com.sun.net.httpserver.HttpExchange

import slaydemo.backend.governance.objects.*
import slaydemo.backend.governance.services.*
import slaydemo.backend.mail.objects.MailKind
import slaydemo.backend.shared.policies.HandlePolicy
import slaydemo.backend.shared.routes.HttpRouteSupport

final class GovernanceRoutes(
  contributionAdjustmentService: ContributionAdjustmentService,
  notificationService: GovernanceNotificationService
) {
  def contributionAdjustments(exchange: HttpExchange): Unit = {
    HttpRouteSupport.addCors(exchange)

    try {
      exchange.getRequestMethod.toUpperCase(Locale.ROOT) match {
        case "OPTIONS" =>
          HttpRouteSupport.sendEmpty(exchange, 204)
        case "GET" =>
          val limit = queryParams(exchange).get("limit").flatMap(_.toIntOption).getOrElse(500)
          HttpRouteSupport.sendJson(exchange, 200, renderAdjustments(contributionAdjustmentService.list(limit)))
        case "POST" =>
          parseContributionAdjustmentBody(exchange) match {
            case Left(message) =>
              jsonError(exchange, 400, "bad_request", message)
            case Right(request) =>
              parseContributionAdjustmentCommand(request) match {
                case Left(ContributionAdjustmentCommandParseError.InvalidActor) =>
                  jsonError(exchange, 403, "invalid_actor", "invalid_actor")
                case Left(ContributionAdjustmentCommandParseError.InvalidTarget) =>
                  jsonError(exchange, 400, "invalid_target", "invalid_target")
                case Left(ContributionAdjustmentCommandParseError.InvalidDelta) =>
                  jsonError(exchange, 400, "invalid_delta", "invalid_delta")
                case Right(command) =>
                  val result = contributionAdjustmentService.create(command)
                  HttpRouteSupport.sendJson(exchange, 200, renderAdjustmentResult(result))
              }
          }
        case _ =>
          jsonError(exchange, 405, "method_not_allowed", "Method is not allowed.")
      }
    } finally {
      exchange.close()
    }
  }

  def adminNotifications(exchange: HttpExchange): Unit = {
    HttpRouteSupport.addCors(exchange)

    try {
      exchange.getRequestMethod.toUpperCase(Locale.ROOT) match {
        case "OPTIONS" =>
          HttpRouteSupport.sendEmpty(exchange, 204)
        case "GET" =>
          val query = queryParams(exchange)
          val limit = query.get("limit").flatMap(_.toIntOption).getOrElse(100)
          val rawKind = query.get("kind").map(_.trim).filter(_.nonEmpty)
          val rawTargetType = query.get("targetType").map(_.trim).filter(_.nonEmpty)
          val parsedKind = rawKind.flatMap(GovernanceReviewKind.fromWire)
          val parsedTargetType = rawTargetType.flatMap(GovernanceReviewTargetType.fromWire)
          val records =
            if rawKind.nonEmpty && parsedKind.isEmpty then Vector.empty
            else if rawTargetType.nonEmpty && parsedTargetType.isEmpty then Vector.empty
            else
              notificationService.listReviewNotifications(
                kind = parsedKind,
                targetType = parsedTargetType,
                limit = limit
              )
          HttpRouteSupport.sendJson(exchange, 200, renderNotifications(records))
        case "POST" =>
          parseReviewNotificationBody(exchange) match {
            case Left(message) =>
              jsonError(exchange, 400, "bad_request", message)
            case Right(request) =>
              parseReviewNotificationCommand(request) match {
                case Left(GovernanceReviewNotificationCommandParseError.InvalidKind) =>
                  jsonError(exchange, 400, "invalid_kind", "invalid_kind")
                case Left(GovernanceReviewNotificationCommandParseError.InvalidTarget) =>
                  jsonError(exchange, 400, "invalid_target", "invalid_target")
                case Left(GovernanceReviewNotificationCommandParseError.InvalidBody) =>
                  jsonError(exchange, 400, "invalid_body", "invalid_body")
                case Right(command) =>
                  val result = notificationService.createReviewNotification(command)
                  HttpRouteSupport.sendJson(exchange, 200, renderNotificationResult(result))
              }
          }
        case _ =>
          jsonError(exchange, 405, "method_not_allowed", "Method is not allowed.")
      }
    } finally {
      exchange.close()
    }
  }

  private def parseContributionAdjustmentBody(exchange: HttpExchange): Either[String, ContributionAdjustmentRequest] = {
    val body = HttpRouteSupport.readRequestBody(exchange).trim
    if body.isEmpty || !body.startsWith("{") || !body.endsWith("}") then Left("Request body must be a JSON object.")
    else {
      val stringFields = extractStringFields(body)
      val delta = extractIntField(body, "delta").orElse(stringFields.get("delta").flatMap(_.toIntOption))
      val actorHandle = stringFields.getOrElse("actorHandle", "")
      val targetHandle = stringFields.getOrElse("targetHandle", "")

      if actorHandle.trim.isEmpty || targetHandle.trim.isEmpty || delta.isEmpty then
        Left("Request body must include actorHandle, targetHandle, and delta.")
      else
        Right(
          ContributionAdjustmentRequest(
            actorHandle = actorHandle,
            targetHandle = targetHandle,
            delta = delta.get,
            reason = stringFields.getOrElse("reason", ""),
            sourceLabel = stringFields.getOrElse("sourceLabel", ""),
            sourcePath = stringFields.getOrElse("sourcePath", "")
          )
        )
    }
  }

  private def parseReviewNotificationBody(exchange: HttpExchange): Either[String, GovernanceReviewNotificationRequest] = {
    val body = HttpRouteSupport.readRequestBody(exchange).trim
    if body.isEmpty || !body.startsWith("{") || !body.endsWith("}") then Left("Request body must be a JSON object.")
    else {
      val stringFields = extractStringFields(body)
      val kind = stringFields.getOrElse("kind", "")
      val targetType = stringFields.getOrElse("targetType", "")
      val targetId = stringFields.getOrElse("targetId", "")
      val bodyText = stringFields.getOrElse("body", "")

      if kind.trim.isEmpty || targetType.trim.isEmpty || targetId.trim.isEmpty || bodyText.trim.isEmpty then
        Left("Request body must include kind, targetType, targetId, and body.")
      else
        Right(
          GovernanceReviewNotificationRequest(
            actorHandle = stringFields.getOrElse("actorHandle", ""),
            kind = kind,
            targetType = targetType,
            targetId = targetId,
            targetTitle = stringFields.getOrElse("targetTitle", ""),
            targetPath = stringFields.getOrElse("targetPath", ""),
            body = bodyText
          )
        )
    }
  }

  private def parseContributionAdjustmentCommand(
    request: ContributionAdjustmentRequest
  ): Either[ContributionAdjustmentCommandParseError, ContributionAdjustmentCommand] =
    for {
      actor <- parseAdmin(request.actorHandle)
      target <- parseAdjustmentTarget(request.targetHandle)
      delta <- parseDelta(request.delta)
    } yield ContributionAdjustmentCommand(
      actorHandle = actor,
      targetHandle = target,
      delta = delta,
      reason = GovernanceReason(trimToMax(request.reason, 240)),
      sourceLabel = GovernanceSourceLabel(trimToMax(request.sourceLabel, 120)),
      sourcePath = GovernanceSourcePath(trimToMax(request.sourcePath, 240))
    )

  private def parseReviewNotificationCommand(
    request: GovernanceReviewNotificationRequest
  ): Either[GovernanceReviewNotificationCommandParseError, GovernanceReviewNotificationCommand] =
    for {
      kind <- GovernanceReviewKind.fromWire(request.kind).toRight(GovernanceReviewNotificationCommandParseError.InvalidKind)
      targetType <- GovernanceReviewTargetType.fromWire(request.targetType).toRight(GovernanceReviewNotificationCommandParseError.InvalidTarget)
      targetId <- parseTargetId(request.targetId)
      body <- parseBody(request.body)
    } yield GovernanceReviewNotificationCommand(
      actorHandle = GovernanceActorHandle(defaultReviewActor(trimToMax(request.actorHandle, 32))),
      kind = kind,
      targetType = targetType,
      targetId = targetId,
      targetTitle = GovernanceReviewTargetTitle(trimToMax(request.targetTitle, 160)),
      targetPath = GovernanceReviewTargetPath(trimToMax(request.targetPath, 240)),
      body = body
    )

  private def parseAdmin(value: String): Either[ContributionAdjustmentCommandParseError, AdminHandle] =
    AdminHandle.fromString(value).toRight(ContributionAdjustmentCommandParseError.InvalidActor)

  private def parseAdjustmentTarget(value: String): Either[ContributionAdjustmentCommandParseError, GovernanceTargetHandle] = {
    val trimmed = HandlePolicy.trim(value)
    if trimmed.isEmpty || HandlePolicy.isVisitorLikeHandle(trimmed) then Left(ContributionAdjustmentCommandParseError.InvalidTarget)
    else Right(GovernanceTargetHandle(trimmed))
  }

  private def parseDelta(value: Int): Either[ContributionAdjustmentCommandParseError, ContributionDelta] =
    Either.cond(value != 0, ContributionDelta(value), ContributionAdjustmentCommandParseError.InvalidDelta)

  private def parseTargetId(value: String): Either[GovernanceReviewNotificationCommandParseError, GovernanceReviewTargetId] =
    nonEmptyTrimmed(value).filter(_.length <= 160).map(GovernanceReviewTargetId.apply)
      .toRight(GovernanceReviewNotificationCommandParseError.InvalidTarget)

  private def parseBody(value: String): Either[GovernanceReviewNotificationCommandParseError, GovernanceReviewBody] =
    nonEmptyTrimmed(value).map(_.take(500)).filter(_.nonEmpty).map(GovernanceReviewBody.apply)
      .toRight(GovernanceReviewNotificationCommandParseError.InvalidBody)

  private def renderAdjustmentResult(result: ContributionAdjustmentSubmissionResult): String =
    renderObject(Vector("ok" -> "true", "adjustment" -> renderAdjustment(result.adjustment), "mail" -> renderMail(result.mail)))

  private def renderNotificationResult(result: GovernanceReviewNotificationSubmissionResult): String =
    renderObject(Vector("ok" -> "true", "notification" -> renderNotification(result.notification), "mail" -> renderMail(result.mail)))

  private def renderAdjustments(records: Vector[ContributionAdjustmentRecord]): String =
    renderObject(Vector("adjustments" -> records.map(renderAdjustment).mkString("[", ",", "]")))

  private def renderNotifications(records: Vector[GovernanceReviewNotificationRecord]): String =
    renderObject(Vector("notifications" -> records.map(renderNotification).mkString("[", ",", "]")))

  private def renderAdjustment(record: ContributionAdjustmentRecord): String =
    renderObject(
      Vector(
        "id" -> jsonString(record.id.value),
        "actorHandle" -> jsonString(record.actorHandle.value),
        "targetHandle" -> jsonString(record.targetHandle.value),
        "delta" -> record.delta.value.toString,
        "reason" -> jsonString(record.reason.value),
        "createdAt" -> record.createdAt.value.toString,
        "sourceLabel" -> jsonString(record.sourceLabel.value),
        "sourcePath" -> jsonString(record.sourcePath.value)
      )
    )

  private def renderNotification(record: GovernanceReviewNotificationRecord): String =
    renderObject(
      Vector(
        "id" -> jsonString(record.id.value),
        "actorHandle" -> jsonString(record.actorHandle.value),
        "kind" -> jsonString(GovernanceReviewKind.wireValue(record.kind)),
        "targetType" -> jsonString(GovernanceReviewTargetType.wireValue(record.targetType)),
        "targetId" -> jsonString(record.targetId.value),
        "targetTitle" -> jsonString(record.targetTitle.value),
        "targetPath" -> jsonString(record.targetPath.value),
        "body" -> jsonString(record.body.value),
        "createdAt" -> record.createdAt.value.toString,
        "mailId" -> jsonString(record.mailId.value)
      )
    )

  private def renderMail(mail: GovernanceMailSnapshot): String =
    renderObject(
      Vector(
        "id" -> jsonString(mail.id.value),
        "ownerHandle" -> jsonString(mail.ownerHandle.value),
        "kind" -> jsonString(MailKind.wireValue(mail.kind)),
        "subject" -> jsonString(mail.subject),
        "excerpt" -> jsonString(mail.excerpt),
        "senderLabel" -> jsonString(mail.senderLabel),
        "unread" -> mail.unread.toString,
        "important" -> mail.important.toString,
        "createdAt" -> mail.createdAt.value.toString
      ) ++ governanceMetadataFields(mail)
    )

  private def governanceMetadataFields(mail: GovernanceMailSnapshot): Vector[(String, String)] =
    mail.governanceMetadata.map { metadata =>
      Vector(
        "governanceActorHandle" -> jsonString(metadata.actorHandle),
        "governanceTargetPath" -> jsonString(metadata.targetPath),
        "governanceTargetLabel" -> jsonString(metadata.targetLabel)
      )
    }.getOrElse(Vector.empty)

  private def queryParams(exchange: HttpExchange): Map[String, String] =
    Option(exchange.getRequestURI.getRawQuery).toVector
      .flatMap(_.split("&").toVector)
      .flatMap { pair =>
        pair.split("=", 2).toList match {
          case key :: value :: Nil if key.nonEmpty => Some(decode(key) -> decode(value))
          case key :: Nil if key.nonEmpty          => Some(decode(key) -> "")
          case _                                   => None
        }
      }
      .toMap

  private def extractStringFields(body: String): Map[String, String] =
    stringFieldPattern.findAllMatchIn(body)
      .map(matchResult => matchResult.group(1) -> unescapeJsonString(matchResult.group(2)))
      .toMap

  private def extractIntField(body: String, field: String): Option[Int] =
    s""""$field"\\s*:\\s*(-?\\d+)""".r.findFirstMatchIn(body).flatMap(matchResult => matchResult.group(1).toIntOption)

  private def renderObject(fields: Vector[(String, String)]): String =
    fields.map { case (key, value) => s"${jsonString(key)}:$value" }.mkString("{", ",", "}")

  private def jsonString(value: String): String =
    s""""${HttpRouteSupport.escapeJson(value)}""""

  private def jsonError(exchange: HttpExchange, status: Int, code: String, message: String): Unit =
    HttpRouteSupport.sendJson(exchange, status, s"""{"error":${jsonString(message)},"code":${jsonString(code)}}""")

  private def decode(value: String): String =
    URLDecoder.decode(value, StandardCharsets.UTF_8)

  private def unescapeJsonString(value: String): String =
    value
      .replace("\\\"", "\"")
      .replace("\\\\", "\\")
      .replace("\\/", "/")
      .replace("\\b", "\b")
      .replace("\\f", "\f")
      .replace("\\n", "\n")
      .replace("\\r", "\r")
      .replace("\\t", "\t")

  private def trimToMax(value: String, max: Int): String =
    Option(value).getOrElse("").trim.take(max)

  private def nonEmptyTrimmed(value: String): Option[String] =
    Option(value).map(_.trim).filter(_.nonEmpty)

  private def defaultReviewActor(value: String): String =
    if value.isEmpty then "Visitor" else value

  private val stringFieldPattern =
    "\"([^\"]+)\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"".r
}

private final case class ContributionAdjustmentRequest(
  actorHandle: String,
  targetHandle: String,
  delta: Int,
  reason: String,
  sourceLabel: String,
  sourcePath: String
)

private enum ContributionAdjustmentCommandParseError {
  case InvalidActor
  case InvalidTarget
  case InvalidDelta
}

private final case class GovernanceReviewNotificationRequest(
  actorHandle: String,
  kind: String,
  targetType: String,
  targetId: String,
  targetTitle: String,
  targetPath: String,
  body: String
)

private enum GovernanceReviewNotificationCommandParseError {
  case InvalidKind
  case InvalidTarget
  case InvalidBody
}

object GovernanceRoutes {
  def apply(
    contributionAdjustmentService: ContributionAdjustmentService,
    notificationService: GovernanceNotificationService
  ): GovernanceRoutes =
    new GovernanceRoutes(contributionAdjustmentService, notificationService)
}
