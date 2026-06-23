package services.governance.api

import services.governance.objects.*
import services.governance.services.{ContributionAdjustmentCommand, GovernanceReviewNotificationCommand}

object GovernanceCommandParsers {
  def parseContributionAdjustmentMessage(
    message: ContributionAdjustmentCreateAPIMessage
  ): Either[ContributionAdjustmentCommandParseError, ContributionAdjustmentCommand] =
    for
      actor <- parseAdmin(message.actorHandle)
      target <- parseAdjustmentTarget(message.targetHandle)
      delta <- parseDelta(message.delta)
    yield ContributionAdjustmentCommand(
      actorHandle = actor,
      targetHandle = target,
      delta = delta,
      reason = message.reason.getOrElse(GovernanceTextInput.reason("")),
      sourceLabel = message.sourceLabel.getOrElse(GovernanceTextInput.sourceLabel("")),
      sourcePath = message.sourcePath.getOrElse(GovernanceTextInput.sourcePath(""))
    )

  def parseReviewNotificationMessage(
    message: GovernanceReviewNotificationCreateAPIMessage
  ): Either[GovernanceReviewNotificationCommandParseError, GovernanceReviewNotificationCommand] =
    for
      kind <- parseReviewKind(message.kind)
      targetType <- parseReviewTargetType(message.targetType)
      targetId <- parseTargetId(message.targetId)
      body <- parseBody(message.body)
    yield GovernanceReviewNotificationCommand(
      actorHandle = message.actorHandle.getOrElse(GovernanceTextInput.reviewActor("")),
      kind = kind,
      targetType = targetType,
      targetId = targetId,
      targetTitle = message.targetTitle.getOrElse(GovernanceTextInput.targetTitle("")),
      targetPath = message.targetPath.getOrElse(GovernanceTextInput.targetPath("")),
      body = body
    )

  private def parseAdmin(value: GovernanceAdminInput): Either[ContributionAdjustmentCommandParseError, AdminHandle] =
    value match {
      case GovernanceAdminInput.Valid(handle) => Right(handle)
      case GovernanceAdminInput.Invalid       => Left(ContributionAdjustmentCommandParseError.InvalidActor)
    }

  private def parseAdjustmentTarget(
    value: GovernanceAdjustmentTargetInput
  ): Either[ContributionAdjustmentCommandParseError, GovernanceTargetHandle] =
    value match {
      case GovernanceAdjustmentTargetInput.Valid(handle) => Right(handle)
      case GovernanceAdjustmentTargetInput.Invalid       => Left(ContributionAdjustmentCommandParseError.InvalidTarget)
    }

  private def parseDelta(value: ContributionDeltaInput): Either[ContributionAdjustmentCommandParseError, ContributionDelta] =
    value match {
      case ContributionDeltaInput.Valid(delta) => Right(delta)
      case ContributionDeltaInput.Invalid      => Left(ContributionAdjustmentCommandParseError.InvalidDelta)
    }

  private def parseReviewKind(
    value: GovernanceReviewKindInput
  ): Either[GovernanceReviewNotificationCommandParseError, GovernanceReviewKind] =
    value match {
      case GovernanceReviewKindInput.Valid(kind) => Right(kind)
      case GovernanceReviewKindInput.Invalid     => Left(GovernanceReviewNotificationCommandParseError.InvalidKind)
    }

  private def parseReviewTargetType(
    value: GovernanceReviewTargetTypeInput
  ): Either[GovernanceReviewNotificationCommandParseError, GovernanceReviewTargetType] =
    value match {
      case GovernanceReviewTargetTypeInput.Valid(targetType) => Right(targetType)
      case GovernanceReviewTargetTypeInput.Invalid           => Left(GovernanceReviewNotificationCommandParseError.InvalidTarget)
    }

  private def parseTargetId(
    value: GovernanceReviewTargetIdInput
  ): Either[GovernanceReviewNotificationCommandParseError, GovernanceReviewTargetId] =
    value match {
      case GovernanceReviewTargetIdInput.Valid(targetId) => Right(targetId)
      case GovernanceReviewTargetIdInput.Invalid         => Left(GovernanceReviewNotificationCommandParseError.InvalidTarget)
    }

  private def parseBody(
    value: GovernanceReviewBodyInput
  ): Either[GovernanceReviewNotificationCommandParseError, GovernanceReviewBody] =
    value match {
      case GovernanceReviewBodyInput.Valid(body) => Right(body)
      case GovernanceReviewBodyInput.Invalid     => Left(GovernanceReviewNotificationCommandParseError.InvalidBody)
    }
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
