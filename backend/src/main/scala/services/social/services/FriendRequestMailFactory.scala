package services.social.services

import services.identity.objects.PlayerHandle
import services.mail.objects.{
  FriendRequestMailMetadata,
  MailFriendRequestId,
  MailFriendRequestStatus,
  MailId,
  MailImportance,
  MailKind,
  MailReadState,
  MailRecord
}
import services.social.objects.{FriendRequestDecision, FriendRequestRecord, FriendRequestStatus}

private[services] object FriendRequestMailFactory {
  def requestMail(
    request: FriendRequestRecord,
    readState: MailReadState
  ): MailRecord =
    MailRecord(
      id = MailId(s"mail-friend-${request.id.value}"),
      ownerHandle = request.targetHandle,
      kind = MailKind.Friend,
      subject = "Friend request",
      excerpt = s"@${request.sourceHandle.value} wants to add you as a friend.",
      senderLabel = "Friend request",
      readState = readState,
      importance = MailImportance.Normal,
      createdAt = request.createdAt,
      sourceBattleId = None,
      sourcePath = Some("/social"),
      sourceLabel = Some("Friend request"),
      governanceMetadata = None,
      friendRequestMetadata = Some(friendRequestMetadata(request, mailStatusFor(request.status), request.sourceHandle))
    )

  def responseMail(request: FriendRequestRecord, decision: FriendRequestDecision): MailRecord = {
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
      readState = MailReadState.Unread,
      importance = MailImportance.Normal,
      createdAt = request.respondedAt.getOrElse(request.createdAt),
      sourceBattleId = None,
      sourcePath = Some("/social"),
      sourceLabel = Some("Friend request"),
      governanceMetadata = None,
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
