package slaydemo.backend.mail.services

import slaydemo.backend.battle.objects.EpochMillis
import slaydemo.backend.identity.objects.PlayerHandle
import slaydemo.backend.mail.database.{InMemoryMailRepository, MailRepository}
import slaydemo.backend.mail.objects.{MailId, MailKind, MailRecord}
import slaydemo.backend.shared.policies.HandlePolicy

enum MailReadError {
  case MailNotFound
}

trait MailService {
  def list(ownerHandle: PlayerHandle): Vector[MailRecord]
  def markRead(ownerHandle: PlayerHandle, mailId: MailId): Either[MailReadError, MailRecord]
}

final class DefaultMailService(repository: MailRepository, currentTimeMillis: () => Long) extends MailService {
  override def list(ownerHandle: PlayerHandle): Vector[MailRecord] =
    if !isPlayable(ownerHandle) then Vector.empty
    else {
      val existing = repository.listByOwner(ownerHandle)
      if existing.isEmpty then Vector(repository.save(welcomeMail(ownerHandle)))
      else existing
    }

  override def markRead(ownerHandle: PlayerHandle, mailId: MailId): Either[MailReadError, MailRecord] =
    if isPlayable(ownerHandle) then markExistingRead(ownerHandle, mailId)
    else Left(MailReadError.MailNotFound)

  private def markExistingRead(owner: PlayerHandle, mailId: MailId): Either[MailReadError, MailRecord] =
    repository.markRead(owner, mailId).toRight(MailReadError.MailNotFound)

  private def welcomeMail(owner: PlayerHandle): MailRecord =
    MailRecord(
      id = MailId(s"mail-system-welcome-${owner.key}"),
      ownerHandle = owner,
      kind = MailKind.System,
      subject = "Welcome to Slay Demo",
      excerpt = "Your backend mailbox is ready.",
      senderLabel = "System",
      unread = true,
      important = false,
      createdAt = EpochMillis(currentTimeMillis())
    )

  private def isPlayable(handle: PlayerHandle): Boolean =
    HandlePolicy.isPlayableIdentityHandle(handle.value)

}

object DefaultMailService {
  def apply(repository: MailRepository, currentTimeMillis: () => Long): DefaultMailService =
    new DefaultMailService(repository, currentTimeMillis)
}

object InMemoryMailService {
  def apply(): DefaultMailService =
    DefaultMailService(InMemoryMailRepository(), () => System.currentTimeMillis())
}
