package slaydemo.backend.social.services

import java.util.UUID

import slaydemo.backend.mails.objects.MailRecord
import slaydemo.backend.mails.services.MailService
import slaydemo.backend.social.database.FriendRequestRepository
import slaydemo.backend.social.objects.FriendRequestRecord

final class DefaultFriendRequestService(
  repository: FriendRequestRepository,
  mailService: MailService
) extends FriendRequestService {
  override def create(sourceHandle: String, targetHandle: String): Either[String, FriendRequestSubmissionResult] = {
    val normalizedSource = sourceHandle.trim
    val normalizedTarget = targetHandle.trim

    if (normalizedSource.isEmpty || normalizedTarget.isEmpty || normalizedSource.equalsIgnoreCase(normalizedTarget)) {
      Left("invalid_handles")
    } else {
      repository.findByHandles(normalizedSource, normalizedTarget) match {
        case Some(existing) =>
          Right(FriendRequestSubmissionResult(created = false, alreadySent = true, existing, None))
        case None =>
          val request = FriendRequestRecord(
            id = s"friend-${UUID.randomUUID().toString.replace("-", "").take(12)}",
            sourceHandle = normalizedSource,
            targetHandle = normalizedTarget,
            createdAt = System.currentTimeMillis()
          )

          repository.save(request)
          val mail = mailService.create(buildMail(request))
          Right(FriendRequestSubmissionResult(created = true, alreadySent = false, request, Some(mail)))
      }
    }
  }

  private def buildMail(request: FriendRequestRecord): MailRecord = {
    MailRecord(
      id = s"mail-friend-${request.id}",
      ownerHandle = request.targetHandle,
      kind = "friend",
      subject = "好友申请",
      excerpt = s"@${request.sourceHandle} 想加你为好友。",
      senderLabel = "好友申请",
      unread = true,
      important = false,
      createdAt = request.createdAt
    )
  }
}
