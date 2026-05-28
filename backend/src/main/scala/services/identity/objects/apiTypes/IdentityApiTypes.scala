package services.identity.objects.apiTypes

import io.circe.{Decoder, DecodingFailure, Encoder, HCursor}
import io.circe.generic.semiauto.deriveEncoder

import services.identity.objects.{IdentityAccount, IdentityAccountSummary, SkinId}

final case class IdentityRegistrationApiRequest(
  handle: Option[String],
  password: Option[String],
  skinId: Option[String]
)

object IdentityRegistrationApiRequest {
  given Decoder[IdentityRegistrationApiRequest] = (cursor: HCursor) =>
    requireObject(cursor).flatMap { _ =>
      for
        handle <- optionalString(cursor, "handle")
        password <- optionalString(cursor, "password")
        skinId <- optionalString(cursor, "skinId")
      yield IdentityRegistrationApiRequest(handle = handle, password = password, skinId = skinId)
    }
}

final case class IdentitySessionApiRequest(
  handle: Option[String],
  password: Option[String]
)

object IdentitySessionApiRequest {
  given Decoder[IdentitySessionApiRequest] = (cursor: HCursor) =>
    requireObject(cursor).flatMap { _ =>
      for
        handle <- optionalString(cursor, "handle")
        password <- optionalString(cursor, "password")
      yield IdentitySessionApiRequest(handle = handle, password = password)
    }
}

final case class IdentityCurrentApiRequest(session: Option[String])

object IdentityCurrentApiRequest {
  given Decoder[IdentityCurrentApiRequest] = (cursor: HCursor) =>
    requireObject(cursor).flatMap { _ =>
      optionalString(cursor, "session").map(IdentityCurrentApiRequest.apply)
    }
}

final case class IdentityAuthResponse(
  handle: String,
  skinId: String,
  session: String
)

object IdentityAuthResponse {
  given Encoder[IdentityAuthResponse] = deriveEncoder

  def fromAccount(account: IdentityAccount): IdentityAuthResponse =
    IdentityAuthResponse(
      handle = account.handle.value,
      skinId = SkinId.wireValue(account.skinId),
      session = account.sessionToken.map(_.value).getOrElse("")
    )
}

final case class IdentityAccountsResponse(accounts: Vector[IdentityAccountSummary])

object IdentityAccountsResponse {
  private given Encoder[IdentityAccountSummary] =
    Encoder.forProduct3("handle", "displayName", "skinId")(summary =>
      (summary.handle.value, summary.displayName.value, SkinId.wireValue(summary.skinId))
    )

  given Encoder[IdentityAccountsResponse] = deriveEncoder
}

private def requireObject(cursor: HCursor): Decoder.Result[Unit] =
  cursor.value.asObject match {
    case Some(_) => Right(())
    case None    => Left(DecodingFailure("identity request must be a JSON object.", cursor.history))
  }

private def optionalString(cursor: HCursor, field: String): Decoder.Result[Option[String]] =
  cursor.downField(field).focus match {
    case None =>
      Right(None)
    case Some(value) if value.isString =>
      Right(value.asString)
    case Some(_) =>
      Left(DecodingFailure(s"$field must be a string.", cursor.history))
  }
