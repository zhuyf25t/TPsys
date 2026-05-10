package slaydemo.backend.mail.services

import slaydemo.backend.battle.objects.EpochMillis
import slaydemo.backend.identity.objects.PlayerHandle
import slaydemo.backend.mail.database.{InMemoryMailRepository, MailRepository}
import slaydemo.backend.mail.objects.{MailId, MailImportance, MailKind, MailReadState, MailRecord}
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
    normalizedOwner(ownerHandle) match {
      case None =>
        Vector.empty
      case Some(owner) =>
        val existing = repository.listByOwner(owner)
        if existing.isEmpty then Vector(repository.save(welcomeMail(owner)))
        else existing
    }

  override def markRead(ownerHandle: PlayerHandle, mailId: MailId): Either[MailReadError, MailRecord] =
    normalizedOwner(ownerHandle) match {
      case Some(owner) => markExistingRead(owner, mailId)
      case None        => Left(MailReadError.MailNotFound)
    }

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
      readState = MailReadState.Unread,
      importance = MailImportance.Normal,
      createdAt = EpochMillis(currentTimeMillis()),
      sourceBattleId = None,
      sourcePath = None,
      sourceLabel = None,
      governanceMetadata = None,
      friendRequestMetadata = None
    )

  private def normalizedOwner(handle: PlayerHandle): Option[PlayerHandle] =
    PlayerHandle.forLookup(HandlePolicy.trim(handle.value))

}

object DefaultMailService {
  def apply(repository: MailRepository, currentTimeMillis: () => Long): DefaultMailService =
    new DefaultMailService(repository, currentTimeMillis)
}

object InMemoryMailService {
  def apply(): DefaultMailService =
    DefaultMailService(InMemoryMailRepository(), () => System.currentTimeMillis())
}
