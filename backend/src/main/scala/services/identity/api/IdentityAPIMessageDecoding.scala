package services.identity.api

import io.circe.Decoder

import services.identity.objects.{PlainTextPassword, PlayerHandle, SessionToken, SkinId}

final case class IdentityRegistrationHandleInput(value: Option[PlayerHandle]) extends AnyVal
final case class IdentityLookupHandleInput(value: Option[PlayerHandle]) extends AnyVal

enum IdentitySkinIdInput {
  case Missing
  case Selected(value: SkinId)
  case Invalid
}

private[api] object IdentityAPIMessageDecoding {
  given registrationHandleInputDecoder: Decoder[IdentityRegistrationHandleInput] =
    Decoder.decodeString.map(value => IdentityRegistrationHandleInput(PlayerHandle.forRegistration(value)))

  given lookupHandleInputDecoder: Decoder[IdentityLookupHandleInput] =
    Decoder.decodeString.map(value => IdentityLookupHandleInput(PlayerHandle.forLookup(value)))

  given optionalPasswordDecoder: Decoder[Option[PlainTextPassword]] =
    Decoder.decodeOption(Decoder.decodeString).map(_.flatMap(PlainTextPassword.fromString))

  given optionalSessionTokenDecoder: Decoder[Option[SessionToken]] =
    Decoder.decodeOption(Decoder.decodeString).map(_.flatMap(SessionToken.fromString))

  given skinIdInputDecoder: Decoder[IdentitySkinIdInput] =
    Decoder.decodeString.map(value =>
      SkinId.fromString(value)
        .map(IdentitySkinIdInput.Selected.apply)
        .getOrElse(IdentitySkinIdInput.Invalid)
    )
}
