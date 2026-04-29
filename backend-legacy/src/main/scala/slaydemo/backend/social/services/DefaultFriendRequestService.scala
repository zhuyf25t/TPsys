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
            createdAt = System.currentTimeMillis(),
            status = "pending",
            respondedAt = None
          )

          repository.save(request)
          val mail = mailService.create(buildRequestMail(request))
          Right(FriendRequestSubmissionResult(created = true, alreadySent = false, request, Some(mail)))
      }
    }
  }

  override def respond(
    requestId: String,
    actorHandle: String,
    decision: String
  ): Either[String, FriendRequestResponseResult] = {
    val normalizedRequestId = requestId.trim
    val normalizedActor = actorHandle.trim
    val normalizedDecision = decision.trim.toLowerCase

    if (normalizedRequestId.isEmpty || normalizedActor.isEmpty) {
      Left("missing_fields")
    } else if (normalizedDecision != "accepted" && normalizedDecision != "rejected") {
      Left("invalid_decision")
    } else {
      repository.findById(normalizedRequestId) match {
        case None =>
          Left("request_not_found")
        case Some(request) if !request.targetHandle.equalsIgnoreCase(normalizedActor) =>
          Left("forbidden")
        case Some(request) if request.status != "pending" =>
          Right(FriendRequestResponseResult(request, None))
        case Some(request) =>
          val respondedAt = System.currentTimeMillis()
          repository.updateStatus(request.id, normalizedDecision, respondedAt) match {
            case Some(updated) =>
              mailService.markRead(updated.targetHandle, s"mail-friend-${updated.id}")
              val mail = mailService.create(buildResponseMail(updated, normalizedDecision, respondedAt))
              Right(FriendRequestResponseResult(updated, Some(mail)))
            case None =>
              repository.findById(request.id) match {
                case Some(existing) => Right(FriendRequestResponseResult(existing, None))
                case None           => Left("request_not_found")
              }
          }
      }
    }
  }

  override def list(ownerHandle: String): Seq[FriendRequestRecord] =
    repository.listByOwner(ownerHandle.trim)

  override def find(requestId: String): Option[FriendRequestRecord] =
    repository.findById(requestId.trim)

  private def buildRequestMail(request: FriendRequestRecord): MailRecord = {
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

  private def buildResponseMail(request: FriendRequestRecord, decision: String, createdAt: Long): MailRecord = {
    val accepted = decision == "accepted"
    MailRecord(
      id = s"mail-friend-response-${request.id}-$decision",
      ownerHandle = request.sourceHandle,
      kind = "friend",
      subject = if (accepted) "好友申请已通过" else "好友申请已拒绝",
      excerpt = if (accepted) s"@${request.targetHandle} 接受了你的好友申请。" else s"@${request.targetHandle} 拒绝了你的好友申请。",
      senderLabel = "好友申请结果",
      unread = true,
      important = false,
      createdAt = createdAt
    )
  }
}
