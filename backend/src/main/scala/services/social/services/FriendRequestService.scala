package services.social.services

import cats.effect.IO

import services.battle.objects.EpochMillis
import services.identity.objects.PlayerHandle
import services.mail.database.{InMemoryMailRepository, MailRepository}
import services.mail.objects.{MailReadState, MailRecord}
import services.social.database.{
  FriendRequestRepository,
  FriendRequestStoreCreateResult,
  InMemoryFriendRequestRepository
}
import services.social.objects.{
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
  ): IO[Either[FriendRequestCreateError, FriendRequestSubmissionResult]]
  def respond(
    requestId: FriendRequestId,
    actorHandle: PlayerHandle,
    decision: FriendRequestDecision
  ): IO[Either[FriendRequestRespondError, FriendRequestResponseResult]]
  def list(ownerHandle: PlayerHandle): IO[Vector[FriendRequestRecord]]
  def find(requestId: FriendRequestId): IO[Option[FriendRequestRecord]]
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
  ): IO[Either[FriendRequestCreateError, FriendRequestSubmissionResult]] =
    IO.blocking(createParsed(sourceHandle, targetHandle))

  override def respond(
    requestId: FriendRequestId,
    actorHandle: PlayerHandle,
    decision: FriendRequestDecision
  ): IO[Either[FriendRequestRespondError, FriendRequestResponseResult]] =
    IO.blocking(respondParsed(requestId, actorHandle, decision))

  override def list(ownerHandle: PlayerHandle): IO[Vector[FriendRequestRecord]] =
    IO.blocking {
      normalizedHandle(ownerHandle) match {
        case Some(owner) =>
          repository.listByOwner(owner).filter(FriendRequestVisibilityRules.isVisible)
        case None =>
          Vector.empty
      }
    }

  override def find(requestId: FriendRequestId): IO[Option[FriendRequestRecord]] =
    IO.blocking(repository.findById(requestId).filter(FriendRequestVisibilityRules.isVisible))

  private def createParsed(
    source: PlayerHandle,
    target: PlayerHandle
  ): Either[FriendRequestCreateError, FriendRequestSubmissionResult] =
    (normalizedHandle(source), normalizedHandle(target)) match {
      case (Some(normalizedSource), Some(normalizedTarget))
          if FriendRequestVisibilityRules.canCreate(normalizedSource, normalizedTarget) =>
        val request = FriendRequestRecord.pending(
          id = repository.nextRequestId(),
          sourceHandle = normalizedSource,
          targetHandle = normalizedTarget,
          createdAt = EpochMillis(currentTimeMillis())
        )
        repository.createIfAbsent(request) match {
          case FriendRequestStoreCreateResult.Created(created) =>
            val mail = mailRepository.save(FriendRequestMailFactory.requestMail(created, MailReadState.Unread))
            Right(FriendRequestSubmissionResult.Created(created, mail))
          case FriendRequestStoreCreateResult.AlreadyExists(existing) =>
            Right(FriendRequestSubmissionResult.AlreadySent(existing))
        }
      case _ =>
        Left(FriendRequestCreateError.InvalidHandles)
    }

  private def respondParsed(
    requestId: FriendRequestId,
    actor: PlayerHandle,
    decision: FriendRequestDecision
  ): Either[FriendRequestRespondError, FriendRequestResponseResult] =
    repository.findById(requestId).filter(FriendRequestVisibilityRules.isVisible) match {
      case None =>
        Left(FriendRequestRespondError.RequestNotFound)
      case Some(request) =>
        normalizedHandle(actor) match {
          case Some(normalizedActor) =>
            respondAsNormalizedActor(request, normalizedActor, decision)
          case None =>
            Left(FriendRequestRespondError.Forbidden)
        }
    }

  private def respondAsNormalizedActor(
    request: FriendRequestRecord,
    actor: PlayerHandle,
    decision: FriendRequestDecision
  ): Either[FriendRequestRespondError, FriendRequestResponseResult] =
    if request.targetHandle.key != actor.key then
      Left(FriendRequestRespondError.Forbidden)
    else if request.status != FriendRequestStatus.Pending then
      Right(FriendRequestResponseResult.AlreadyResolved(request))
    else {
      val updated = FriendRequestRecord.respond(request, decision, EpochMillis(currentTimeMillis()))
      val saved = repository.save(updated)
      mailRepository.save(FriendRequestMailFactory.requestMail(saved, MailReadState.Read))
      val responseMail = mailRepository.save(FriendRequestMailFactory.responseMail(saved, decision))
      Right(FriendRequestResponseResult.Updated(saved, responseMail))
    }

  private def normalizedHandle(handle: PlayerHandle): Option[PlayerHandle] =
    PlayerHandle.forLookup(handle.value)

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
