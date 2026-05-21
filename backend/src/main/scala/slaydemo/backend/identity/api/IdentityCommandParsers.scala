package slaydemo.backend.identity.api

import slaydemo.backend.identity.objects.{PlainTextPassword, PlayerHandle, SkinId}
import slaydemo.backend.identity.services.{IdentityRegistrationCommand, IdentitySessionCommand}

object IdentityCommandParsers {
  def parseRegistrationCommand(
    request: IdentityRegistrationApiRequest
  ): Either[IdentityRegistrationCommandParseError, IdentityRegistrationCommand] =
    for {
      handle <- PlayerHandle.forRegistration(request.handle.getOrElse(""))
        .toRight(IdentityRegistrationCommandParseError.InvalidHandle)
      password <- PlainTextPassword.fromString(request.password.getOrElse(""))
        .toRight(IdentityRegistrationCommandParseError.InvalidPassword)
      skinId <- SkinId.fromString(request.skinId.getOrElse("blue"))
        .toRight(IdentityRegistrationCommandParseError.InvalidSkin)
    } yield IdentityRegistrationCommand(
      handle = handle,
      password = password,
      skinId = skinId
    )

  def parseSessionCommand(
    request: IdentitySessionApiRequest
  ): Either[IdentitySessionCommandParseError, IdentitySessionCommand] =
    for {
      handle <- PlayerHandle.forLookup(request.handle.getOrElse(""))
        .toRight(IdentitySessionCommandParseError.InvalidCredentials)
      password <- PlainTextPassword.fromString(request.password.getOrElse(""))
        .toRight(IdentitySessionCommandParseError.InvalidCredentials)
    } yield IdentitySessionCommand(
      handle = handle,
      password = password
    )
}

enum IdentityRegistrationCommandParseError {
  case InvalidHandle
  case InvalidPassword
  case InvalidSkin
}

enum IdentitySessionCommandParseError {
  case InvalidCredentials
}
