package slaydemo.backend.identity.services

import scala.collection.concurrent.TrieMap

import slaydemo.backend.identity.api.IdentityAccountSummary
import slaydemo.backend.identity.database.{IdentityAccountCreateResult, IdentityAccountRepository}
import slaydemo.backend.identity.objects.{
  IdentityAccount,
  PlainTextPassword,
  PlayerHandle,
  SessionToken,
  SkinId
}
import slaydemo.backend.identity.ports.{IdentityIdGenerator, PasswordHasher, PasswordVerification, SessionTokenGenerator}
import slaydemo.backend.shared.policies.HandlePolicy

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
  def register(command: IdentityRegistrationCommand): Either[IdentityRegistrationError, IdentityAccount]
  def issueSession(command: IdentitySessionCommand): Either[IdentitySessionError, IdentityAccount]
  def current(sessionToken: Option[SessionToken]): Either[IdentityCurrentSessionError, IdentityAccount]
  def listActiveAccounts(): Vector[IdentityAccountSummary]
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

  override def register(command: IdentityRegistrationCommand): Either[IdentityRegistrationError, IdentityAccount] =
    for {
      _ <- Either.cond(!BuiltinAdminIdentity.isHandle(command.handle), (), IdentityRegistrationError.HandleTaken)
      sessionToken = sessionTokenGenerator.nextSessionToken(command.handle)
      account = IdentityAccount.active(
        userId = identityIdGenerator.nextUserId(),
        handle = command.handle,
        skinId = command.skinId,
        sessionToken = Some(sessionToken)
      )
      created <- repository.create(account, passwordHasher.hash(command.password)) match {
        case IdentityAccountCreateResult.Created(saved) =>
          Right(saved)
        case IdentityAccountCreateResult.HandleAlreadyExists(_) =>
          Left(IdentityRegistrationError.HandleTaken)
      }
    } yield created

  override def issueSession(command: IdentitySessionCommand): Either[IdentitySessionError, IdentityAccount] =
    if BuiltinAdminIdentity.isHandle(command.handle) then
      passwordHasher.verify(command.password, BuiltinAdminIdentity.passwordHash) match {
        case PasswordVerification.Verified =>
          val sessionToken = sessionTokenGenerator.nextSessionToken(BuiltinAdminIdentity.handle)
          val account = BuiltinAdminIdentity.account(Some(sessionToken))
          builtinSessions.put(sessionToken, account)
          Right(account)
        case PasswordVerification.Rejected =>
          Left(IdentitySessionError.InvalidCredentials)
      }
    else
      authenticateStoredAccount(command) match {
        case None =>
          Left(IdentitySessionError.InvalidCredentials)
        case Some(account) =>
          val sessionToken = sessionTokenGenerator.nextSessionToken(account.handle)
          Right(repository.updateSession(account.handle, sessionToken).getOrElse(account.copy(sessionToken = Some(sessionToken))))
      }

  override def current(sessionToken: Option[SessionToken]): Either[IdentityCurrentSessionError, IdentityAccount] =
    sessionToken match {
      case None =>
        Left(IdentityCurrentSessionError.MissingSession)
      case Some(token) =>
        builtinSessions
          .get(token)
          .orElse(repository.findBySessionToken(token).filter(isPlayableStoredAccount))
          .toRight(IdentityCurrentSessionError.InvalidSession)
    }

  override def listActiveAccounts(): Vector[IdentityAccountSummary] =
    (repository
      .listActiveAccounts()
      .filter(isPlayableStoredAccount)
      .filterNot(account => BuiltinAdminIdentity.isHandle(account.handle)) :+ BuiltinAdminIdentity.account(None))
      .sortBy(_.handle.key)
      .map(toSummary)

  private def toSummary(account: IdentityAccount): IdentityAccountSummary =
    IdentityAccountSummary(
      handle = account.handle.value,
      displayName = account.displayName.value,
      skinId = SkinId.wireValue(account.skinId)
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
