package slaydemo.backend.identity.api

import io.circe.{Decoder, DecodingFailure, Encoder, HCursor}
import io.circe.generic.semiauto.deriveEncoder

import slaydemo.backend.identity.objects.{IdentityAccount, SkinId}
import slaydemo.backend.identity.services.{IdentityRegistrationCommand, IdentitySessionCommand}

object IdentityRequestTarget {
  private val RegisterPaths: Set[String] =
    Set("/identity/register", "/api/identity/register")
  private val SessionPaths: Set[String] =
    Set("/identity/session", "/api/identity/session")
  private val CurrentPaths: Set[String] =
    Set("/identity/me", "/api/identity/me")
  private val AccountsPaths: Set[String] =
    Set("/identity/accounts", "/api/identity/accounts")

  def isRegisterPath(path: String): Boolean =
    RegisterPaths.contains(path)

  def isSessionPath(path: String): Boolean =
    SessionPaths.contains(path)

  def isCurrentPath(path: String): Boolean =
    CurrentPaths.contains(path)

  def isAccountsPath(path: String): Boolean =
    AccountsPaths.contains(path)
}

final case class IdentityRegistrationApiRequest(
  handle: Option[String],
  password: Option[String],
  skinId: Option[String]
) {
  def toCommand: Either[IdentityRegistrationCommandParseError, IdentityRegistrationCommand] =
    IdentityCommandParsers.parseRegistrationCommand(this)
}

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
) {
  def toCommand: Either[IdentitySessionCommandParseError, IdentitySessionCommand] =
    IdentityCommandParsers.parseSessionCommand(this)
}

object IdentitySessionApiRequest {
  given Decoder[IdentitySessionApiRequest] = (cursor: HCursor) =>
    requireObject(cursor).flatMap { _ =>
      for
        handle <- optionalString(cursor, "handle")
        password <- optionalString(cursor, "password")
      yield IdentitySessionApiRequest(handle = handle, password = password)
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

final case class IdentityAccountSummary(
  handle: String,
  displayName: String,
  skinId: String
)

object IdentityAccountSummary {
  given Encoder[IdentityAccountSummary] = deriveEncoder
}

final case class IdentityAccountsResponse(accounts: Vector[IdentityAccountSummary])

object IdentityAccountsResponse {
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
