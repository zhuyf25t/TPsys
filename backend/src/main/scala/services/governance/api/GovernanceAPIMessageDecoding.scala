package services.governance.api

import io.circe.Decoder

import services.governance.objects.{
  GovernanceActorHandle,
  GovernanceListLimit,
  GovernanceReason,
  GovernanceReviewTargetPath,
  GovernanceReviewTargetTitle,
  GovernanceSourceLabel,
  GovernanceSourcePath
}

private[api] object GovernanceAPIMessageDecoding {
  given Decoder[GovernanceAdminInput] =
    requiredNonEmptyStringDecoder.map(GovernanceAdminInput.fromWire)

  given Decoder[GovernanceAdjustmentTargetInput] =
    requiredNonEmptyStringDecoder.map(GovernanceAdjustmentTargetInput.fromWire)

  given Decoder[ContributionDeltaInput] =
    Decoder.decodeInt.map(ContributionDeltaInput.fromWire)

  given Decoder[GovernanceReason] =
    Decoder.decodeString.map(GovernanceTextInput.reason)

  given Decoder[GovernanceSourceLabel] =
    Decoder.decodeString.map(GovernanceTextInput.sourceLabel)

  given Decoder[GovernanceSourcePath] =
    Decoder.decodeString.map(GovernanceTextInput.sourcePath)

  given Decoder[GovernanceActorHandle] =
    Decoder.decodeString.map(GovernanceTextInput.reviewActor)

  given Decoder[GovernanceReviewKindInput] =
    requiredNonEmptyStringDecoder.map(GovernanceReviewKindInput.fromWire)

  given Decoder[GovernanceReviewTargetTypeInput] =
    requiredNonEmptyStringDecoder.map(GovernanceReviewTargetTypeInput.fromWire)

  given Decoder[GovernanceReviewTargetIdInput] =
    requiredNonEmptyStringDecoder.map(GovernanceReviewTargetIdInput.fromWire)

  given Decoder[GovernanceReviewTargetTitle] =
    Decoder.decodeString.map(GovernanceTextInput.targetTitle)

  given Decoder[GovernanceReviewTargetPath] =
    Decoder.decodeString.map(GovernanceTextInput.targetPath)

  given Decoder[GovernanceReviewBodyInput] =
    requiredNonEmptyStringDecoder.map(GovernanceReviewBodyInput.fromWire)

  given Decoder[GovernanceReviewKindQuery] =
    Decoder.decodeString.map(value => GovernanceReviewKindQuery.fromWire(Some(value)))

  given Decoder[GovernanceReviewTargetTypeQuery] =
    Decoder.decodeString.map(value => GovernanceReviewTargetTypeQuery.fromWire(Some(value)))

  given Decoder[GovernanceListLimit] =
    Decoder.decodeInt.map(GovernanceListLimit.apply)

  private def requiredNonEmptyStringDecoder: Decoder[String] =
    Decoder.decodeString.emap { value =>
      Either.cond(
        Option(value).exists(_.trim.nonEmpty),
        value,
        "required field must be non-empty"
      )
    }
}
