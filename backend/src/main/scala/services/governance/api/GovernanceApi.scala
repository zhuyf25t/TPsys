package services.governance.api

import services.governance.objects.*
import services.governance.objects.apiTypes.{
  ContributionAdjustmentApiRequest,
  ContributionAdjustmentListApiRequest,
  GovernanceReviewNotificationApiRequest,
  GovernanceReviewNotificationListApiRequest
}
import services.governance.services.{ContributionAdjustmentCommand, GovernanceReviewNotificationCommand}
import system.policies.HandlePolicy

object GovernanceRequestTarget {
  private val ContributionAdjustmentPaths: Set[String] =
    Set("/governance/contribution-adjustments", "/api/governance/contribution-adjustments")
  private val AdminNotificationPaths: Set[String] =
    Set("/governance/admin-notifications", "/api/governance/admin-notifications")

  def isContributionAdjustmentPath(path: String): Boolean =
    ContributionAdjustmentPaths.contains(path)

  def isAdminNotificationPath(path: String): Boolean =
    AdminNotificationPaths.contains(path)

  def contributionAdjustmentLimitFromQuery(query: Map[String, String]): Int =
    GovernanceQueryParsers.parseContributionAdjustmentLimit(query)

  def notificationListFromQuery(query: Map[String, String]): GovernanceNotificationListQueryParseResult =
    GovernanceQueryParsers.parseNotificationListQuery(query)
}

enum GovernanceApiErrorCode {
  case MethodNotAllowed
  case InvalidJsonObject
  case InvalidActor
  case InvalidTarget
  case InvalidDelta
  case InvalidKind
  case InvalidBody
}

object GovernanceApiErrorCode {
  def fromContributionAdjustmentError(
    error: ContributionAdjustmentCommandParseError
  ): GovernanceApiErrorCode =
    error match {
      case ContributionAdjustmentCommandParseError.InvalidActor  => GovernanceApiErrorCode.InvalidActor
      case ContributionAdjustmentCommandParseError.InvalidTarget => GovernanceApiErrorCode.InvalidTarget
      case ContributionAdjustmentCommandParseError.InvalidDelta  => GovernanceApiErrorCode.InvalidDelta
    }

  def fromReviewNotificationError(
    error: GovernanceReviewNotificationCommandParseError
  ): GovernanceApiErrorCode =
    error match {
      case GovernanceReviewNotificationCommandParseError.InvalidKind   => GovernanceApiErrorCode.InvalidKind
      case GovernanceReviewNotificationCommandParseError.InvalidTarget => GovernanceApiErrorCode.InvalidTarget
      case GovernanceReviewNotificationCommandParseError.InvalidBody   => GovernanceApiErrorCode.InvalidBody
    }

  def wireValue(code: GovernanceApiErrorCode): String =
    code match {
      case GovernanceApiErrorCode.MethodNotAllowed  => "method_not_allowed"
      case GovernanceApiErrorCode.InvalidJsonObject => "bad_request"
      case GovernanceApiErrorCode.InvalidActor      => "invalid_actor"
      case GovernanceApiErrorCode.InvalidTarget     => "invalid_target"
      case GovernanceApiErrorCode.InvalidDelta      => "invalid_delta"
      case GovernanceApiErrorCode.InvalidKind       => "invalid_kind"
      case GovernanceApiErrorCode.InvalidBody       => "invalid_body"
    }

  def message(code: GovernanceApiErrorCode): String =
    code match {
      case GovernanceApiErrorCode.MethodNotAllowed  => "Method is not allowed."
      case GovernanceApiErrorCode.InvalidJsonObject => "Request body must be a JSON object."
      case _                                        => wireValue(code)
    }

  def statusCode(code: GovernanceApiErrorCode): Int =
    code match {
      case GovernanceApiErrorCode.MethodNotAllowed => 405
      case GovernanceApiErrorCode.InvalidActor     => 403
      case _                                       => 400
    }
}

final case class ContributionAdjustmentRequest(
  actorHandle: String,
  targetHandle: String,
  delta: Int,
  reason: String,
  sourceLabel: String,
  sourcePath: String
)

object ContributionAdjustmentRequest {
  def fromApi(request: ContributionAdjustmentApiRequest): ContributionAdjustmentRequest =
    ContributionAdjustmentRequest(
      actorHandle = request.actorHandle,
      targetHandle = request.targetHandle,
      delta = request.delta,
      reason = request.reason,
      sourceLabel = request.sourceLabel,
      sourcePath = request.sourcePath
    )
}

final case class GovernanceReviewNotificationRequest(
  actorHandle: String,
  kind: String,
  targetType: String,
  targetId: String,
  targetTitle: String,
  targetPath: String,
  body: String
)

object GovernanceReviewNotificationRequest {
  def fromApi(request: GovernanceReviewNotificationApiRequest): GovernanceReviewNotificationRequest =
    GovernanceReviewNotificationRequest(
      actorHandle = request.actorHandle,
      kind = request.kind,
      targetType = request.targetType,
      targetId = request.targetId,
      targetTitle = request.targetTitle,
      targetPath = request.targetPath,
      body = request.body
    )
}

object GovernanceCommandParsers {
  def parseContributionAdjustmentApiRequest(
    request: ContributionAdjustmentApiRequest
  ): Either[ContributionAdjustmentCommandParseError, ContributionAdjustmentCommand] =
    parseContributionAdjustmentCommand(ContributionAdjustmentRequest.fromApi(request))

  def parseReviewNotificationApiRequest(
    request: GovernanceReviewNotificationApiRequest
  ): Either[GovernanceReviewNotificationCommandParseError, GovernanceReviewNotificationCommand] =
    parseReviewNotificationCommand(GovernanceReviewNotificationRequest.fromApi(request))

  def parseContributionAdjustmentCommand(
    request: ContributionAdjustmentRequest
  ): Either[ContributionAdjustmentCommandParseError, ContributionAdjustmentCommand] =
    for
      actor <- parseAdmin(request.actorHandle)
      target <- parseAdjustmentTarget(request.targetHandle)
      delta <- parseDelta(request.delta)
    yield ContributionAdjustmentCommand(
      actorHandle = actor,
      targetHandle = target,
      delta = delta,
      reason = GovernanceReason(trimToMax(request.reason, 240)),
      sourceLabel = GovernanceSourceLabel(trimToMax(request.sourceLabel, 120)),
      sourcePath = GovernanceSourcePath(trimToMax(request.sourcePath, 240))
    )

  def parseReviewNotificationCommand(
    request: GovernanceReviewNotificationRequest
  ): Either[GovernanceReviewNotificationCommandParseError, GovernanceReviewNotificationCommand] =
    for
      kind <- GovernanceReviewKind.fromWire(request.kind).toRight(GovernanceReviewNotificationCommandParseError.InvalidKind)
      targetType <- GovernanceReviewTargetType.fromWire(request.targetType).toRight(GovernanceReviewNotificationCommandParseError.InvalidTarget)
      targetId <- parseTargetId(request.targetId)
      body <- parseBody(request.body)
    yield GovernanceReviewNotificationCommand(
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

  private def trimToMax(value: String, max: Int): String =
    Option(value).getOrElse("").trim.take(max)

  private def nonEmptyTrimmed(value: String): Option[String] =
    Option(value).map(_.trim).filter(_.nonEmpty)

  private def defaultReviewActor(value: String): String =
    if value.isEmpty then "Visitor" else value
}

enum ContributionAdjustmentCommandParseError {
  case InvalidActor
  case InvalidTarget
  case InvalidDelta
}

enum GovernanceReviewNotificationCommandParseError {
  case InvalidKind
  case InvalidTarget
  case InvalidBody
}

object GovernanceQueryParsers {
  def parseContributionAdjustmentLimit(query: Map[String, String]): Int =
    query.get("limit").flatMap(_.toIntOption).getOrElse(500)

  def parseContributionAdjustmentLimitRequest(request: ContributionAdjustmentListApiRequest): Int =
    parseContributionAdjustmentLimit(request.limit.map(value => "limit" -> value.toString).toMap)

  def parseNotificationListRequest(
    request: GovernanceReviewNotificationListApiRequest
  ): GovernanceNotificationListQueryParseResult =
    parseNotificationListQuery(
      Vector(
        request.kind.map(value => "kind" -> value),
        request.targetType.map(value => "targetType" -> value),
        request.limit.map(value => "limit" -> value.toString)
      ).flatten.toMap
    )

  def parseNotificationListQuery(query: Map[String, String]): GovernanceNotificationListQueryParseResult = {
    val limit = query.get("limit").flatMap(_.toIntOption).getOrElse(100)
    val rawKind = query.get("kind").map(_.trim).filter(_.nonEmpty)
    val rawTargetType = query.get("targetType").map(_.trim).filter(_.nonEmpty)
    val parsedKind = rawKind.flatMap(GovernanceReviewKind.fromWire)
    val parsedTargetType = rawTargetType.flatMap(GovernanceReviewTargetType.fromWire)

    if rawKind.nonEmpty && parsedKind.isEmpty then GovernanceNotificationListQueryParseResult.EmptyResults
    else if rawTargetType.nonEmpty && parsedTargetType.isEmpty then GovernanceNotificationListQueryParseResult.EmptyResults
    else
      GovernanceNotificationListQueryParseResult.Query(
        GovernanceNotificationListQuery(
          kind = parsedKind,
          targetType = parsedTargetType,
          limit = limit
        )
      )
  }
}

final case class GovernanceNotificationListQuery(
  kind: Option[GovernanceReviewKind],
  targetType: Option[GovernanceReviewTargetType],
  limit: Int
)

enum GovernanceNotificationListQueryParseResult {
  case Query(query: GovernanceNotificationListQuery)
  case EmptyResults
}
