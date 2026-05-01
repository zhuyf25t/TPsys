package slaydemo.backend.identity.ports

import java.util.UUID

import slaydemo.backend.identity.objects.{PlayerHandle, SessionToken}

trait SessionTokenGenerator {
  def nextSessionToken(handle: PlayerHandle): SessionToken
}

final class UuidSessionTokenGenerator extends SessionTokenGenerator {
  override def nextSessionToken(handle: PlayerHandle): SessionToken = {
    val suffix = UUID.randomUUID().toString.replace("-", "").take(16)
    SessionToken(s"session-${handle.key}-$suffix")
  }
}
