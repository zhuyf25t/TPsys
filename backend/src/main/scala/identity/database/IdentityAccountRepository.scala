package slaydemo.backend.identity.database

import slaydemo.backend.identity.objects.IdentityAccount

trait IdentityAccountRepository {
  def findByHandle(handle: String): Option[IdentityAccount]
  def findBySessionToken(sessionToken: String): Option[IdentityAccount]
  def listActiveAccounts(): Seq[IdentityAccount]
  def exists(handle: String): Boolean
  def create(handle: String, password: String, skinId: String): IdentityAccount
  def authenticate(handle: String, password: String): Option[IdentityAccount]
  def updateSession(handle: String, sessionToken: String): Option[IdentityAccount]
}
