package services.identity.api

import io.circe.{Decoder, DecodingFailure, Encoder, HCursor}
import io.circe.generic.semiauto.deriveEncoder

import services.identity.objects.{IdentityAccount, SkinId}
import services.identity.services.{
  IdentityCurrentSessionError,
  IdentityRegistrationCommand,
  IdentityRegistrationError,
  IdentitySessionCommand,
  IdentitySessionError
}

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

enum IdentityApiRequestDecodeError {
  case InvalidJsonObject
}

enum IdentityApiErrorCode {
  case PostMethodNotAllowed
  case GetMethodNotAllowed
  case InvalidJsonObject
  case InvalidHandle
  case InvalidPassword
  case InvalidSkin
  case HandleTaken
  case InvalidCredentials
  case MissingSession
  case InvalidSession
}

object IdentityApiErrorCode {
  def fromRegistrationParseError(error: IdentityRegistrationCommandParseError): IdentityApiErrorCode =
    error match {
      case IdentityRegistrationCommandParseError.InvalidHandle   => IdentityApiErrorCode.InvalidHandle
      case IdentityRegistrationCommandParseError.InvalidPassword => IdentityApiErrorCode.InvalidPassword
      case IdentityRegistrationCommandParseError.InvalidSkin     => IdentityApiErrorCode.InvalidSkin
    }

  def fromRegistrationServiceError(error: IdentityRegistrationError): IdentityApiErrorCode =
    error match {
      case IdentityRegistrationError.HandleTaken => IdentityApiErrorCode.HandleTaken
    }

  def fromSessionParseError(error: IdentitySessionCommandParseError): IdentityApiErrorCode =
    error match {
      case IdentitySessionCommandParseError.InvalidCredentials => IdentityApiErrorCode.InvalidCredentials
    }

  def fromSessionServiceError(error: IdentitySessionError): IdentityApiErrorCode =
    error match {
      case IdentitySessionError.InvalidCredentials => IdentityApiErrorCode.InvalidCredentials
    }

  def fromCurrentSessionError(error: IdentityCurrentSessionError): IdentityApiErrorCode =
    error match {
      case IdentityCurrentSessionError.MissingSession => IdentityApiErrorCode.MissingSession
      case IdentityCurrentSessionError.InvalidSession => IdentityApiErrorCode.InvalidSession
    }

  def wireValue(code: IdentityApiErrorCode): String =
    code match {
      case IdentityApiErrorCode.PostMethodNotAllowed => "method_not_allowed"
      case IdentityApiErrorCode.GetMethodNotAllowed  => "method_not_allowed"
      case IdentityApiErrorCode.InvalidJsonObject    => "bad_request"
      case IdentityApiErrorCode.InvalidHandle        => "invalid_handle"
      case IdentityApiErrorCode.InvalidPassword      => "invalid_password"
      case IdentityApiErrorCode.InvalidSkin          => "invalid_skin"
      case IdentityApiErrorCode.HandleTaken          => "handle_taken"
      case IdentityApiErrorCode.InvalidCredentials   => "invalid_credentials"
      case IdentityApiErrorCode.MissingSession       => "missing_session"
      case IdentityApiErrorCode.InvalidSession       => "invalid_session"
    }

  def message(code: IdentityApiErrorCode): String =
    code match {
      case IdentityApiErrorCode.PostMethodNotAllowed => "Only POST and OPTIONS are supported."
      case IdentityApiErrorCode.GetMethodNotAllowed  => "Only GET and OPTIONS are supported."
      case IdentityApiErrorCode.InvalidJsonObject    => "Request body must be a JSON object with string fields."
      case IdentityApiErrorCode.InvalidHandle        => "Handle must be 3-16 characters and use letters, numbers, -, _."
      case IdentityApiErrorCode.InvalidPassword      => "Password must be at least 4 characters."
      case IdentityApiErrorCode.InvalidSkin          => "Skin must be one of: blue, old, soldier, survivor."
      case IdentityApiErrorCode.HandleTaken          => "Handle already exists."
      case IdentityApiErrorCode.InvalidCredentials   => "Handle or password is incorrect."
      case IdentityApiErrorCode.MissingSession       => "Session token is required."
      case IdentityApiErrorCode.InvalidSession       => "Current session is not valid."
    }

  def statusCode(code: IdentityApiErrorCode): Int =
    code match {
      case IdentityApiErrorCode.PostMethodNotAllowed => 405
      case IdentityApiErrorCode.GetMethodNotAllowed  => 405
      case IdentityApiErrorCode.HandleTaken          => 409
      case IdentityApiErrorCode.InvalidCredentials   => 401
      case IdentityApiErrorCode.MissingSession       => 401
      case IdentityApiErrorCode.InvalidSession       => 401
      case _                                        => 400
    }
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
