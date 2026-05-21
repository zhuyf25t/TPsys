package slaydemo.backend.governance.routes

import slaydemo.backend.governance.objects.apiTypes.{ContributionAdjustmentRequest, GovernanceReviewNotificationRequest}

object GovernanceRequestBodyParser {
  def parseContributionAdjustmentBody(rawBody: String): Either[String, ContributionAdjustmentRequest] = {
    val body = Option(rawBody).getOrElse("").trim
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

  def parseReviewNotificationBody(rawBody: String): Either[String, GovernanceReviewNotificationRequest] = {
    val body = Option(rawBody).getOrElse("").trim
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

  private def extractStringFields(body: String): Map[String, String] =
    stringFieldPattern.findAllMatchIn(body)
      .map(matchResult => matchResult.group(1) -> unescapeJsonString(matchResult.group(2)))
      .toMap

  private def extractIntField(body: String, field: String): Option[Int] =
    s""""$field"\\s*:\\s*(-?\\d+)""".r.findFirstMatchIn(body).flatMap(matchResult => matchResult.group(1).toIntOption)

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

  private val stringFieldPattern =
    "\"([^\"]+)\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"".r
}
