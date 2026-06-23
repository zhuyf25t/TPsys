package services.identity.api

import cats.effect.IO

import services.identity.objects.IdentityAccount
import services.identity.services.{IdentityCurrentSessionError, IdentityRegistrationError, IdentitySessionError}
import system.api.APIMessageError

private[api] object IdentityAPIMessageErrors {
  def registrationParse(error: IdentityRegistrationCommandParseError): APIMessageError =
    IdentityAPIMessageSupport.error(IdentityApiErrorCode.fromRegistrationParseError(error))

  def sessionParse(error: IdentitySessionCommandParseError): APIMessageError =
    IdentityAPIMessageSupport.error(IdentityApiErrorCode.fromSessionParseError(error))

  def registrationService(result: Either[IdentityRegistrationError, IdentityAccount]): IO[IdentityAccount] =
    result.fold(
      error => IO.raiseError(IdentityAPIMessageSupport.error(IdentityApiErrorCode.fromRegistrationServiceError(error))),
      IO.pure
    )

  def sessionService(result: Either[IdentitySessionError, IdentityAccount]): IO[IdentityAccount] =
    result.fold(
      error => IO.raiseError(IdentityAPIMessageSupport.error(IdentityApiErrorCode.fromSessionServiceError(error))),
      IO.pure
    )

  def currentSession(result: Either[IdentityCurrentSessionError, IdentityAccount]): IO[IdentityAccount] =
    result.fold(
      error => IO.raiseError(IdentityAPIMessageSupport.error(IdentityApiErrorCode.fromCurrentSessionError(error))),
      IO.pure
    )
}
