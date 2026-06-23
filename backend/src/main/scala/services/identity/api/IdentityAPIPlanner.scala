package services.identity.api

import cats.effect.IO

import services.identity.services.IdentityService

object IdentityAPIPlanner {
  def planRegister(service: IdentityService, message: IdentityRegisterAPIMessage): IO[IdentityAuthResponse] =
    for
      command <- IO.fromEither(
        IdentityCommandParsers
          .parseRegistrationCommand(message)
          .left
          .map(IdentityAPIMessageErrors.registrationParse)
      )
      account <- service.register(command).flatMap(IdentityAPIMessageErrors.registrationService)
    yield IdentityAuthResponse.fromAccount(account)

  def planSession(service: IdentityService, message: IdentitySessionAPIMessage): IO[IdentityAuthResponse] =
    for
      command <- IO.fromEither(
        IdentityCommandParsers
          .parseSessionCommand(message)
          .left
          .map(IdentityAPIMessageErrors.sessionParse)
      )
      account <- service.issueSession(command).flatMap(IdentityAPIMessageErrors.sessionService)
    yield IdentityAuthResponse.fromAccount(account)

  def planCurrent(service: IdentityService, message: IdentityCurrentAPIMessage): IO[IdentityAuthResponse] =
    for
      account <- service.current(message.session).flatMap(IdentityAPIMessageErrors.currentSession)
    yield IdentityAuthResponse.fromAccount(account)

  def planAccounts(service: IdentityService): IO[IdentityAccountsResponse] =
    for
      accounts <- service.listActiveAccounts()
      response <- IO.pure(IdentityAccountsResponse(accounts))
    yield response
}
