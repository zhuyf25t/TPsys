package slaydemo.backend.governance.objects.apiTypes

import slaydemo.backend.governance.objects.*
import slaydemo.backend.governance.services.{ContributionAdjustmentCommand, GovernanceReviewNotificationCommand}
import slaydemo.backend.shared.policies.HandlePolicy

object GovernanceCommandParsers {
  def parseContributionAdjustmentCommand(
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

  def parseReviewNotificationCommand(
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
