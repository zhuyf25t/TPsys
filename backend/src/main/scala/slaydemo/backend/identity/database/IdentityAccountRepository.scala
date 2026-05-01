package slaydemo.backend.identity.database

import slaydemo.backend.identity.objects.{IdentityAccount, PasswordHash, PlainTextPassword, PlayerHandle, SessionToken}

enum IdentityAccountCreateResult {
  case Created(account: IdentityAccount)
  case HandleAlreadyExists(existing: IdentityAccount)
}

trait IdentityAccountRepository {
  def findByHandle(handle: PlayerHandle): Option[IdentityAccount]
  def findBySessionToken(sessionToken: SessionToken): Option[IdentityAccount]
  def authenticate(handle: PlayerHandle, passwordHash: PasswordHash): Option[IdentityAccount]
  def authenticateLegacyPlaintext(handle: PlayerHandle, password: PlainTextPassword): Option[IdentityAccount]
  def create(account: IdentityAccount, passwordHash: PasswordHash): IdentityAccountCreateResult
  def replacePasswordHash(handle: PlayerHandle, passwordHash: PasswordHash): Option[IdentityAccount]
  def updateSession(handle: PlayerHandle, sessionToken: SessionToken): Option[IdentityAccount]
  def listActiveAccounts(): Vector[IdentityAccount]
}
