package slaydemo.backend.social.routes

import slaydemo.backend.mail.objects.{MailFriendRequestStatus, MailKind, MailRecord}
import slaydemo.backend.shared.routes.HttpRouteSupport
import slaydemo.backend.social.objects.{FriendRequestRecord, FriendRequestStatus}
import slaydemo.backend.social.services.{FriendRequestResponseResult, FriendRequestSubmissionResult}

private[routes] object SocialRouteJsonRenderer {
  def renderCreateResult(result: FriendRequestSubmissionResult): String =
    renderObject(
      Vector(
        "created" -> (result match {
          case FriendRequestSubmissionResult.Created(_, _) => "true"
          case FriendRequestSubmissionResult.AlreadySent(_) => "false"
        }),
        "alreadySent" -> (result match {
          case FriendRequestSubmissionResult.Created(_, _) => "false"
          case FriendRequestSubmissionResult.AlreadySent(_) => "true"
        }),
        "request" -> renderRequest(result.friendRequest),
        "mail" -> result.notificationMail.map(renderMail).getOrElse("null")
      )
    )

  def renderResponseResult(result: FriendRequestResponseResult): String =
    renderObject(
      Vector(
        "request" -> renderRequest(result.friendRequest),
        "mail" -> result.notificationMail.map(renderMail).getOrElse("null")
      )
    )

  def renderRequests(records: Vector[FriendRequestRecord]): String =
    renderObject(Vector("requests" -> records.map(renderRequest).mkString("[", ",", "]")))

  private def renderRequest(request: FriendRequestRecord): String =
    renderObject(
      Vector(
        "id" -> jsonString(request.id.value),
        "sourceHandle" -> jsonString(request.sourceHandle.value),
        "targetHandle" -> jsonString(request.targetHandle.value),
        "createdAt" -> request.createdAt.value.toString,
        "status" -> jsonString(FriendRequestStatus.wireValue(request.status)),
        "respondedAt" -> request.respondedAt.map(_.value.toString).getOrElse("null")
      )
    )

  private def renderMail(mail: MailRecord): String =
    renderObject(
      Vector(
        "id" -> jsonString(mail.id.value),
        "ownerHandle" -> jsonString(mail.ownerHandle.value),
        "kind" -> jsonString(MailKind.wireValue(mail.kind)),
        "subject" -> jsonString(mail.subject),
        "excerpt" -> jsonString(mail.excerpt),
        "senderLabel" -> jsonString(mail.senderLabel),
        "unread" -> mail.unread.toString,
        "important" -> mail.important.toString,
        "createdAt" -> mail.createdAt.value.toString
      ) ++ optionalStringField("sourceBattleId", mail.sourceBattleId) ++
        optionalStringField("sourcePath", mail.sourcePath) ++
        optionalStringField("sourceLabel", mail.sourceLabel) ++
        friendRequestMetadataFields(mail)
    )

  private def friendRequestMetadataFields(mail: MailRecord): Vector[(String, String)] =
    mail.friendRequestMetadata.map { metadata =>
      Vector(
        "friendRequestId" -> jsonString(metadata.requestId.value),
        "friendRequestStatus" -> jsonString(MailFriendRequestStatus.wireValue(metadata.status)),
        "friendRequestSourceHandle" -> jsonString(metadata.sourceHandle.value)
      )
    }.getOrElse(Vector.empty)

  private def optionalStringField(key: String, value: Option[String]): Vector[(String, String)] =
    value.filter(_.trim.nonEmpty).map(text => Vector(key -> jsonString(text))).getOrElse(Vector.empty)

  private def renderObject(fields: Vector[(String, String)]): String =
    fields.map { case (key, value) => s"${jsonString(key)}:$value" }.mkString("{", ",", "}")

  private def jsonString(value: String): String =
    s""""${HttpRouteSupport.escapeJson(value)}""""
}
