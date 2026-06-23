package services.identity.api

import services.identity.objects.{PlainTextPassword, PlayerHandle, SkinId}
import services.identity.services.{IdentityRegistrationCommand, IdentitySessionCommand}

object IdentityCommandParsers {
  def parseRegistrationCommand(
    message: IdentityRegisterAPIMessage
  ): Either[IdentityRegistrationCommandParseError, IdentityRegistrationCommand] =
    parseRegistrationFields(
      handle = message.handle,
      password = message.password,
      skinId = message.skinId
    )

  def parseRegistrationFields(
    handle: Option[IdentityRegistrationHandleInput],
    password: Option[PlainTextPassword],
    skinId: Option[IdentitySkinIdInput]
  ): Either[IdentityRegistrationCommandParseError, IdentityRegistrationCommand] =
    for {
      handle <- handle
        .flatMap(_.value)
        .toRight(IdentityRegistrationCommandParseError.InvalidHandle)
      password <- password
        .toRight(IdentityRegistrationCommandParseError.InvalidPassword)
      skinId <- parseSkinId(skinId)
    } yield IdentityRegistrationCommand(
      handle = handle,
      password = password,
      skinId = skinId
    )

  def parseSessionCommand(
    message: IdentitySessionAPIMessage
  ): Either[IdentitySessionCommandParseError, IdentitySessionCommand] =
    parseSessionFields(
      handle = message.handle,
      password = message.password
    )

  def parseSessionFields(
    handle: Option[IdentityLookupHandleInput],
    password: Option[PlainTextPassword]
  ): Either[IdentitySessionCommandParseError, IdentitySessionCommand] =
    for {
      handle <- handle
        .flatMap(_.value)
        .toRight(IdentitySessionCommandParseError.InvalidCredentials)
      password <- password
        .toRight(IdentitySessionCommandParseError.InvalidCredentials)
    } yield IdentitySessionCommand(
      handle = handle,
      password = password
    )

  private def parseSkinId(value: Option[IdentitySkinIdInput]): Either[IdentityRegistrationCommandParseError, SkinId] =
    value match {
      case None | Some(IdentitySkinIdInput.Missing) =>
        Right(SkinId.Blue)
      case Some(IdentitySkinIdInput.Selected(skinId)) =>
        Right(skinId)
      case Some(IdentitySkinIdInput.Invalid) =>
        Left(IdentityRegistrationCommandParseError.InvalidSkin)
    }
}

enum IdentityRegistrationCommandParseError {
  case InvalidHandle
  case InvalidPassword
  case InvalidSkin
}

enum IdentitySessionCommandParseError {
  case InvalidCredentials
}
