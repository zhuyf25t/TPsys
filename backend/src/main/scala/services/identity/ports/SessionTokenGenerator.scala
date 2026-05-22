package services.identity.ports

import java.util.UUID

import services.identity.objects.{PlayerHandle, SessionToken}

trait SessionTokenGenerator {
  def nextSessionToken(handle: PlayerHandle): SessionToken
}

final class UuidSessionTokenGenerator extends SessionTokenGenerator {
  override def nextSessionToken(handle: PlayerHandle): SessionToken = {
    val suffix = UUID.randomUUID().toString.replace("-", "").take(16)
    SessionToken(s"session-${handle.key}-$suffix")
  }
}
