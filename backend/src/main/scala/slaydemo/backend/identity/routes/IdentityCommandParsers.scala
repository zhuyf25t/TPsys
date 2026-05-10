package slaydemo.backend.identity.routes

import slaydemo.backend.identity.objects.{PlainTextPassword, PlayerHandle, SkinId}
import slaydemo.backend.identity.services.{IdentityRegistrationCommand, IdentitySessionCommand}

private[routes] object IdentityCommandParsers {
  def parseRegistrationCommand(
    fields: Map[String, String]
  ): Either[IdentityRegistrationCommandParseError, IdentityRegistrationCommand] =
    for {
      handle <- PlayerHandle.forRegistration(fields.getOrElse("handle", ""))
        .toRight(IdentityRegistrationCommandParseError.InvalidHandle)
      password <- PlainTextPassword.fromString(fields.getOrElse("password", ""))
        .toRight(IdentityRegistrationCommandParseError.InvalidPassword)
      skinId <- SkinId.fromString(fields.getOrElse("skinId", "blue"))
        .toRight(IdentityRegistrationCommandParseError.InvalidSkin)
    } yield IdentityRegistrationCommand(
      handle = handle,
      password = password,
      skinId = skinId
    )

  def parseSessionCommand(
    fields: Map[String, String]
  ): Either[IdentitySessionCommandParseError, IdentitySessionCommand] =
    for {
      handle <- PlayerHandle.forLookup(fields.getOrElse("handle", ""))
        .toRight(IdentitySessionCommandParseError.InvalidCredentials)
      password <- PlainTextPassword.fromString(fields.getOrElse("password", ""))
        .toRight(IdentitySessionCommandParseError.InvalidCredentials)
    } yield IdentitySessionCommand(
      handle = handle,
      password = password
    )
}

private[routes] enum IdentityRegistrationCommandParseError {
  case InvalidHandle
  case InvalidPassword
  case InvalidSkin
}

private[routes] enum IdentitySessionCommandParseError {
  case InvalidCredentials
}
