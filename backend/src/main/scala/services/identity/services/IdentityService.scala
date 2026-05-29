package services.identity.services

import scala.collection.concurrent.TrieMap

import cats.effect.IO

import services.identity.database.{IdentityAccountCreateResult, IdentityAccountRepository}
import services.identity.objects.{
  IdentityAccount,
  IdentityAccountSummary,
  PlainTextPassword,
  PlayerHandle,
  SessionToken,
  SkinId
}
import services.identity.ports.{IdentityIdGenerator, PasswordHasher, PasswordVerification, SessionTokenGenerator}
import system.policies.HandlePolicy

enum IdentityRegistrationError {
  case HandleTaken
}

enum IdentitySessionError {
  case InvalidCredentials
}

enum IdentityCurrentSessionError {
  case MissingSession
  case InvalidSession
}

trait IdentityService {
  def register(command: IdentityRegistrationCommand): IO[Either[IdentityRegistrationError, IdentityAccount]]
  def issueSession(command: IdentitySessionCommand): IO[Either[IdentitySessionError, IdentityAccount]]
  def current(sessionToken: Option[SessionToken]): IO[Either[IdentityCurrentSessionError, IdentityAccount]]
  def listActiveAccounts(): IO[Vector[IdentityAccountSummary]]
}

final case class IdentityRegistrationCommand(
  handle: PlayerHandle,
  password: PlainTextPassword,
  skinId: SkinId
)

final case class IdentitySessionCommand(
  handle: PlayerHandle,
  password: PlainTextPassword
)

final class DefaultIdentityService(
  repository: IdentityAccountRepository,
  identityIdGenerator: IdentityIdGenerator,
  sessionTokenGenerator: SessionTokenGenerator,
  passwordHasher: PasswordHasher
) extends IdentityService {
  private val builtinSessions = TrieMap.empty[SessionToken, IdentityAccount]

  override def register(command: IdentityRegistrationCommand): IO[Either[IdentityRegistrationError, IdentityAccount]] =
    if BuiltinAdminIdentity.isHandle(command.handle) then IO.pure(Left(IdentityRegistrationError.HandleTaken))
    else
      for
        sessionToken <- IO.blocking(sessionTokenGenerator.nextSessionToken(command.handle))
        userId <- IO.blocking(identityIdGenerator.nextUserId())
        account <- IO.pure(
          IdentityAccount.active(
            userId = userId,
            handle = command.handle,
            skinId = command.skinId,
            sessionToken = Some(sessionToken)
          )
        )
        createResult <- IO.blocking(repository.create(account, passwordHasher.hash(command.password)))
        result <- IO.pure(
          createResult match {
            case IdentityAccountCreateResult.Created(saved) =>
              Right(saved)
            case IdentityAccountCreateResult.HandleAlreadyExists(_) =>
              Left(IdentityRegistrationError.HandleTaken)
          }
        )
      yield result

  override def issueSession(command: IdentitySessionCommand): IO[Either[IdentitySessionError, IdentityAccount]] =
    if BuiltinAdminIdentity.isHandle(command.handle) then
      for
        verification <- IO.blocking(passwordHasher.verify(command.password, BuiltinAdminIdentity.passwordHash))
        result <- verification match {
          case PasswordVerification.Verified =>
            for
              sessionToken <- IO.blocking(sessionTokenGenerator.nextSessionToken(BuiltinAdminIdentity.handle))
              account <- IO.pure(BuiltinAdminIdentity.account(Some(sessionToken)))
              _ <- IO.blocking(builtinSessions.put(sessionToken, account))
            yield Right(account)
          case PasswordVerification.Rejected =>
            IO.pure(Left(IdentitySessionError.InvalidCredentials))
        }
      yield result
    else
      for
        maybeAccount <- IO.blocking(authenticateStoredAccount(command))
        result <- maybeAccount match {
          case None =>
            IO.pure(Left(IdentitySessionError.InvalidCredentials))
          case Some(account) =>
            for
              sessionToken <- IO.blocking(sessionTokenGenerator.nextSessionToken(account.handle))
              saved <- IO.blocking(repository.updateSession(account.handle, sessionToken).getOrElse(account.copy(sessionToken = Some(sessionToken))))
            yield Right(saved)
        }
      yield result

  override def current(sessionToken: Option[SessionToken]): IO[Either[IdentityCurrentSessionError, IdentityAccount]] =
    sessionToken match {
      case None =>
        IO.pure(Left(IdentityCurrentSessionError.MissingSession))
      case Some(token) =>
        for
          builtinAccount <- IO.blocking(builtinSessions.get(token))
          storedAccount <- IO.blocking(repository.findBySessionToken(token).filter(isPlayableStoredAccount))
          result <- IO.pure(builtinAccount.orElse(storedAccount).toRight(IdentityCurrentSessionError.InvalidSession))
        yield result
    }

  override def listActiveAccounts(): IO[Vector[IdentityAccountSummary]] =
    IO.blocking {
      (repository
        .listActiveAccounts()
        .filter(isPlayableStoredAccount)
        .filterNot(account => BuiltinAdminIdentity.isHandle(account.handle)) :+ BuiltinAdminIdentity.account(None))
        .sortBy(_.handle.key)
        .map(toSummary)
    }

  private def toSummary(account: IdentityAccount): IdentityAccountSummary =
    IdentityAccountSummary(
      handle = account.handle,
      displayName = account.displayName,
      skinId = account.skinId
    )

  private def isPlayableStoredAccount(account: IdentityAccount): Boolean =
    HandlePolicy.isPlayableIdentityHandle(account.handle.value)

  private def authenticateStoredAccount(command: IdentitySessionCommand): Option[IdentityAccount] = {
    val storedHashAccount =
      repository
        .findPasswordHashByHandle(command.handle)
        .filter(storedHash => passwordHasher.verify(command.password, storedHash) == PasswordVerification.Verified)
        .flatMap { storedHash =>
          val upgraded =
            if passwordHasher.needsRehash(storedHash) then
              repository.replacePasswordHash(command.handle, passwordHasher.hash(command.password))
            else None
          upgraded.orElse(repository.findByHandle(command.handle))
        }

    storedHashAccount
      .orElse {
        repository.authenticateLegacyPlaintext(command.handle, command.password).map { account =>
          val passwordHash = passwordHasher.hash(command.password)
          repository.replacePasswordHash(account.handle, passwordHash).getOrElse(account)
        }
      }
  }
}

object DefaultIdentityService {
  def apply(
    repository: IdentityAccountRepository,
    identityIdGenerator: IdentityIdGenerator,
    sessionTokenGenerator: SessionTokenGenerator,
    passwordHasher: PasswordHasher
  ): DefaultIdentityService =
    new DefaultIdentityService(repository, identityIdGenerator, sessionTokenGenerator, passwordHasher)
}
