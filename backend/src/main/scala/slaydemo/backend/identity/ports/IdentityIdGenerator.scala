package slaydemo.backend.identity.ports

import java.util.UUID

import slaydemo.backend.shared.objects.UserId

trait IdentityIdGenerator {
  def nextUserId(): UserId
}

final class UuidIdentityIdGenerator extends IdentityIdGenerator {
  override def nextUserId(): UserId =
    UserId(UUID.randomUUID().toString)
}
