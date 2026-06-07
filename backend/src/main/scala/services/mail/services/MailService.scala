package services.mail.services

import cats.effect.IO

import services.battle.objects.EpochMillis
import services.identity.objects.PlayerHandle
import services.mail.database.{InMemoryMailRepository, MailRepository}
import services.mail.objects.{MailId, MailImportance, MailKind, MailReadState, MailRecord}
import system.policies.HandlePolicy

enum MailReadError {
  case MailNotFound
}

trait MailService {
  def list(ownerHandle: PlayerHandle): IO[Vector[MailRecord]]
  def markRead(ownerHandle: PlayerHandle, mailId: MailId): IO[Either[MailReadError, MailRecord]]
}

final class DefaultMailService(repository: MailRepository, currentTimeMillis: () => Long) extends MailService {
  override def list(ownerHandle: PlayerHandle): IO[Vector[MailRecord]] =
    IO.blocking {
      normalizedOwner(ownerHandle) match {
        case None =>
          Vector.empty
        case Some(owner) =>
          val existing = repository.listByOwner(owner)
          if existing.isEmpty then Vector(repository.save(welcomeMail(owner)))
          else existing
      }
    }

  override def markRead(ownerHandle: PlayerHandle, mailId: MailId): IO[Either[MailReadError, MailRecord]] =
    IO.blocking {
      normalizedOwner(ownerHandle) match {
        case Some(owner) => markExistingRead(owner, mailId)
        case None        => Left(MailReadError.MailNotFound)
      }
    }

  private def markExistingRead(owner: PlayerHandle, mailId: MailId): Either[MailReadError, MailRecord] =
    repository.markRead(owner, mailId).toRight(MailReadError.MailNotFound)

  private def welcomeMail(owner: PlayerHandle): MailRecord =
    MailRecord(
      id = MailId(s"mail-system-welcome-${owner.key}"),
      ownerHandle = owner,
      kind = MailKind.System,
      subject = "欢迎来到 Battle Rift",
      excerpt = "后端站内信已连接，可以接收战报、评分变化和好友消息。",
      senderLabel = "系统",
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
