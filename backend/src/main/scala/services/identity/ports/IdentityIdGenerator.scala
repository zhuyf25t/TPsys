package services.identity.ports

import java.util.UUID

import system.objects.UserId

trait IdentityIdGenerator {
  def nextUserId(): UserId
}

final class UuidIdentityIdGenerator extends IdentityIdGenerator {
  override def nextUserId(): UserId =
    UserId(UUID.randomUUID().toString)
}
