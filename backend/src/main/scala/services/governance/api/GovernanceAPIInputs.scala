package services.governance.api

import services.governance.objects.{
  AdminHandle,
  ContributionDelta,
  GovernanceActorHandle,
  GovernanceReviewBody,
  GovernanceReviewKind,
  GovernanceReviewTargetId,
  GovernanceReviewTargetPath,
  GovernanceReviewTargetTitle,
  GovernanceReviewTargetType,
  GovernanceSourceLabel,
  GovernanceSourcePath,
  GovernanceTargetHandle
}
import system.policies.HandlePolicy

enum GovernanceAdminInput {
  case Valid(handle: AdminHandle)
  case Invalid
}

object GovernanceAdminInput {
  def fromWire(value: String): GovernanceAdminInput =
    AdminHandle.fromString(value).map(GovernanceAdminInput.Valid.apply).getOrElse(GovernanceAdminInput.Invalid)
}

enum GovernanceAdjustmentTargetInput {
  case Valid(handle: GovernanceTargetHandle)
  case Invalid
}

object GovernanceAdjustmentTargetInput {
  def fromWire(value: String): GovernanceAdjustmentTargetInput = {
    val trimmed = HandlePolicy.trim(value)
    if trimmed.isEmpty || HandlePolicy.isVisitorLikeHandle(trimmed) then GovernanceAdjustmentTargetInput.Invalid
    else GovernanceAdjustmentTargetInput.Valid(GovernanceTargetHandle(trimmed))
  }
}

enum ContributionDeltaInput {
  case Valid(delta: ContributionDelta)
  case Invalid
}

object ContributionDeltaInput {
  def fromWire(value: Int): ContributionDeltaInput =
    if value == 0 then ContributionDeltaInput.Invalid else ContributionDeltaInput.Valid(ContributionDelta(value))
}

object GovernanceTextInput {
  def reason(value: String): services.governance.objects.GovernanceReason =
    services.governance.objects.GovernanceReason(trimToMax(value, 240))

  def sourceLabel(value: String): GovernanceSourceLabel =
    GovernanceSourceLabel(trimToMax(value, 120))

  def sourcePath(value: String): GovernanceSourcePath =
    GovernanceSourcePath(trimToMax(value, 240))

  def reviewActor(value: String): GovernanceActorHandle =
    GovernanceActorHandle(defaultReviewActor(trimToMax(value, 32)))

  def targetTitle(value: String): GovernanceReviewTargetTitle =
    GovernanceReviewTargetTitle(trimToMax(value, 160))

  def targetPath(value: String): GovernanceReviewTargetPath =
    GovernanceReviewTargetPath(trimToMax(value, 240))

  private def trimToMax(value: String, max: Int): String =
    Option(value).getOrElse("").trim.take(max)

  private def defaultReviewActor(value: String): String =
    if value.isEmpty then "Visitor" else value
}

enum GovernanceReviewKindInput {
  case Valid(kind: GovernanceReviewKind)
  case Invalid
}

object GovernanceReviewKindInput {
  def fromWire(value: String): GovernanceReviewKindInput =
    GovernanceReviewKind.fromWire(value).map(GovernanceReviewKindInput.Valid.apply).getOrElse(GovernanceReviewKindInput.Invalid)
}

enum GovernanceReviewTargetTypeInput {
  case Valid(targetType: GovernanceReviewTargetType)
  case Invalid
}

object GovernanceReviewTargetTypeInput {
  def fromWire(value: String): GovernanceReviewTargetTypeInput =
    GovernanceReviewTargetType.fromWire(value).map(GovernanceReviewTargetTypeInput.Valid.apply).getOrElse(GovernanceReviewTargetTypeInput.Invalid)
}

enum GovernanceReviewTargetIdInput {
  case Valid(targetId: GovernanceReviewTargetId)
  case Invalid
}

object GovernanceReviewTargetIdInput {
  def fromWire(value: String): GovernanceReviewTargetIdInput =
    nonEmptyTrimmed(value)
      .filter(_.length <= 160)
      .map(GovernanceReviewTargetId.apply)
      .map(GovernanceReviewTargetIdInput.Valid.apply)
      .getOrElse(GovernanceReviewTargetIdInput.Invalid)
}

enum GovernanceReviewBodyInput {
  case Valid(body: GovernanceReviewBody)
  case Invalid
}

object GovernanceReviewBodyInput {
  def fromWire(value: String): GovernanceReviewBodyInput =
    nonEmptyTrimmed(value)
      .map(_.take(500))
      .filter(_.nonEmpty)
      .map(GovernanceReviewBody.apply)
      .map(GovernanceReviewBodyInput.Valid.apply)
      .getOrElse(GovernanceReviewBodyInput.Invalid)
}

enum GovernanceReviewKindQuery {
  case Missing
  case Valid(kind: GovernanceReviewKind)
  case Invalid
}

object GovernanceReviewKindQuery {
  def fromWire(value: Option[String]): GovernanceReviewKindQuery =
    value.map(_.trim).filter(_.nonEmpty) match {
      case None        => GovernanceReviewKindQuery.Missing
      case Some(value) => GovernanceReviewKind.fromWire(value).map(GovernanceReviewKindQuery.Valid.apply).getOrElse(GovernanceReviewKindQuery.Invalid)
    }
}

enum GovernanceReviewTargetTypeQuery {
  case Missing
  case Valid(targetType: GovernanceReviewTargetType)
  case Invalid
}

object GovernanceReviewTargetTypeQuery {
  def fromWire(value: Option[String]): GovernanceReviewTargetTypeQuery =
    value.map(_.trim).filter(_.nonEmpty) match {
      case None        => GovernanceReviewTargetTypeQuery.Missing
      case Some(value) => GovernanceReviewTargetType.fromWire(value).map(GovernanceReviewTargetTypeQuery.Valid.apply).getOrElse(GovernanceReviewTargetTypeQuery.Invalid)
    }
}

private def nonEmptyTrimmed(value: String): Option[String] =
  Option(value).map(_.trim).filter(_.nonEmpty)
