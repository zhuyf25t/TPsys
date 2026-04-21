package slaydemo.backend.identity.services

import slaydemo.backend.identity.objects.IdentityAccount

trait IdentityService {
  def register(handle: String, password: String, skinId: String): Either[String, IdentityAccount]
  def issueSession(handle: String, password: String): Either[String, IdentityAccount]
  def loadAccount(handle: String): Option[IdentityAccount]
  def loadAccountBySessionToken(sessionToken: String): Option[IdentityAccount]
  def listActiveAccounts(): Seq[IdentityAccount]
}
