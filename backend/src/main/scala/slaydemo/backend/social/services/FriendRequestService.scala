package slaydemo.backend.social.services

import slaydemo.backend.battle.objects.EpochMillis
import slaydemo.backend.identity.objects.PlayerHandle
import slaydemo.backend.mail.database.{InMemoryMailRepository, MailRepository}
import slaydemo.backend.mail.objects.{
  FriendRequestMailMetadata,
  MailFriendRequestId,
  MailFriendRequestStatus,
  MailId,
  MailKind,
  MailRecord
}
import slaydemo.backend.shared.policies.HandlePolicy
import slaydemo.backend.social.database.{
  FriendRequestRepository,
  FriendRequestStoreCreateResult,
  InMemoryFriendRequestRepository
}
import slaydemo.backend.social.objects.{
  FriendRequestDecision,
  FriendRequestId,
  FriendRequestRecord,
  FriendRequestStatus
}

enum FriendRequestCreateError {
  case InvalidHandles
}

enum FriendRequestRespondError {
  case RequestNotFound
  case Forbidden
}

enum FriendRequestSubmissionResult {
  case Created(request: FriendRequestRecord, mail: MailRecord)
  case AlreadySent(request: FriendRequestRecord)

  def friendRequest: FriendRequestRecord =
    this match {
      case FriendRequestSubmissionResult.Created(request, _) => request
      case FriendRequestSubmissionResult.AlreadySent(request) => request
    }

  def notificationMail: Option[MailRecord] =
    this match {
      case FriendRequestSubmissionResult.Created(_, mail) => Some(mail)
      case FriendRequestSubmissionResult.AlreadySent(_)   => None
    }
}

enum FriendRequestResponseResult {
  case Updated(request: FriendRequestRecord, mail: MailRecord)
  case AlreadyResolved(request: FriendRequestRecord)

  def friendRequest: FriendRequestRecord =
    this match {
      case FriendRequestResponseResult.Updated(request, _) => request
      case FriendRequestResponseResult.AlreadyResolved(request) => request
    }

  def notificationMail: Option[MailRecord] =
    this match {
      case FriendRequestResponseResult.Updated(_, mail) => Some(mail)
      case FriendRequestResponseResult.AlreadyResolved(_) => None
    }
}

trait FriendRequestService {
  def create(
    sourceHandle: PlayerHandle,
    targetHandle: PlayerHandle
  ): Either[FriendRequestCreateError, FriendRequestSubmissionResult]
  def respond(
    requestId: FriendRequestId,
    actorHandle: PlayerHandle,
    decision: FriendRequestDecision
  ): Either[FriendRequestRespondError, FriendRequestResponseResult]
  def list(ownerHandle: PlayerHandle): Vector[FriendRequestRecord]
  def find(requestId: FriendRequestId): Option[FriendRequestRecord]
}

final class DefaultFriendRequestService(
  repository: FriendRequestRepository,
  mailRepository: MailRepository,
  currentTimeMillis: () => Long
)
    extends FriendRequestService {
  override def create(
    sourceHandle: PlayerHandle,
    targetHandle: PlayerHandle
  ): Either[FriendRequestCreateError, FriendRequestSubmissionResult] =
    createParsed(sourceHandle, targetHandle)

  override def respond(
    requestId: FriendRequestId,
    actorHandle: PlayerHandle,
    decision: FriendRequestDecision
  ): Either[FriendRequestRespondError, FriendRequestResponseResult] =
    respondParsed(requestId, actorHandle, decision)

  override def list(ownerHandle: PlayerHandle): Vector[FriendRequestRecord] =
    if isPlayable(ownerHandle) then repository.listByOwner(ownerHandle).filter(isVisible)
    else Vector.empty

  override def find(requestId: FriendRequestId): Option[FriendRequestRecord] =
    repository.findById(requestId).filter(isVisible)

  private def createParsed(
    source: PlayerHandle,
    target: PlayerHandle
  ): Either[FriendRequestCreateError, FriendRequestSubmissionResult] =
    if source.key == target.key || !isPlayable(source) || !isPlayable(target) then
      Left(FriendRequestCreateError.InvalidHandles)
    else {
      val request = FriendRequestRecord.pending(
        id = repository.nextRequestId(),
        sourceHandle = source,
        targetHandle = target,
        createdAt = EpochMillis(currentTimeMillis())
      )
      repository.createIfAbsent(request) match {
        case FriendRequestStoreCreateResult.Created(created) =>
          val mail = mailRepository.save(buildRequestMail(created, MailFriendRequestStatus.Pending, unread = true))
          Right(FriendRequestSubmissionResult.Created(created, mail))
        case FriendRequestStoreCreateResult.AlreadyExists(existing) =>
          Right(FriendRequestSubmissionResult.AlreadySent(existing))
      }
    }

  private def respondParsed(
    requestId: FriendRequestId,
    actor: PlayerHandle,
    decision: FriendRequestDecision
  ): Either[FriendRequestRespondError, FriendRequestResponseResult] =
    repository.findById(requestId).filter(isVisible) match {
      case None =>
        Left(FriendRequestRespondError.RequestNotFound)
      case Some(request) if request.targetHandle.key != actor.key =>
        Left(FriendRequestRespondError.Forbidden)
      case Some(request) if request.status != FriendRequestStatus.Pending =>
        Right(FriendRequestResponseResult.AlreadyResolved(request))
      case Some(request) =>
        val updated = FriendRequestRecord.respond(request, decision, EpochMillis(currentTimeMillis()))
        val saved = repository.save(updated)
        mailRepository.save(buildRequestMail(saved, mailStatusFor(saved.status), unread = false))
        val responseMail = mailRepository.save(buildResponseMail(saved, decision))
        Right(FriendRequestResponseResult.Updated(saved, responseMail))
    }

  private def isVisible(request: FriendRequestRecord): Boolean =
    isPlayable(request.sourceHandle) && isPlayable(request.targetHandle)

  private def isPlayable(handle: PlayerHandle): Boolean =
    HandlePolicy.isPlayableIdentityHandle(handle.value)

  private def buildRequestMail(
    request: FriendRequestRecord,
    status: MailFriendRequestStatus,
    unread: Boolean
  ): MailRecord =
    MailRecord(
      id = MailId(s"mail-friend-${request.id.value}"),
      ownerHandle = request.targetHandle,
      kind = MailKind.Friend,
      subject = "Friend request",
      excerpt = s"@${request.sourceHandle.value} wants to add you as a friend.",
      senderLabel = "Friend request",
      unread = unread,
      important = false,
      createdAt = request.createdAt,
      sourcePath = Some("/social"),
      sourceLabel = Some("Friend request"),
      friendRequestMetadata = Some(friendRequestMetadata(request, status, request.sourceHandle))
    )

  private def buildResponseMail(request: FriendRequestRecord, decision: FriendRequestDecision): MailRecord = {
    val accepted = decision == FriendRequestDecision.Accepted
    MailRecord(
      id = MailId(s"mail-friend-response-${request.id.value}-${FriendRequestDecision.wireValue(decision)}"),
      ownerHandle = request.sourceHandle,
      kind = MailKind.Friend,
      subject = if accepted then "Friend request accepted" else "Friend request rejected",
      excerpt =
        if accepted then s"@${request.targetHandle.value} accepted your friend request."
        else s"@${request.targetHandle.value} rejected your friend request.",
      senderLabel = "Friend request result",
      unread = true,
      important = false,
      createdAt = request.respondedAt.getOrElse(request.createdAt),
      sourcePath = Some("/social"),
      sourceLabel = Some("Friend request"),
      friendRequestMetadata = Some(friendRequestMetadata(request, mailStatusFor(request.status), request.targetHandle))
    )
  }

  private def friendRequestMetadata(
    request: FriendRequestRecord,
    status: MailFriendRequestStatus,
    sourceHandle: PlayerHandle
  ): FriendRequestMailMetadata =
    FriendRequestMailMetadata(
      requestId = MailFriendRequestId(request.id.value),
      status = status,
      sourceHandle = sourceHandle
    )

  private def mailStatusFor(status: FriendRequestStatus): MailFriendRequestStatus =
    status match {
      case FriendRequestStatus.Pending  => MailFriendRequestStatus.Pending
      case FriendRequestStatus.Accepted => MailFriendRequestStatus.Accepted
      case FriendRequestStatus.Rejected => MailFriendRequestStatus.Rejected
    }

}

object DefaultFriendRequestService {
  def apply(
    repository: FriendRequestRepository,
    mailRepository: MailRepository,
    currentTimeMillis: () => Long
  ): DefaultFriendRequestService =
    new DefaultFriendRequestService(repository, mailRepository, currentTimeMillis)
}

object InMemoryFriendRequestService {
  def apply(): DefaultFriendRequestService =
    DefaultFriendRequestService(InMemoryFriendRequestRepository(), InMemoryMailRepository(), () => System.currentTimeMillis())
}
