package slaydemo.backend.mail.routes

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

import com.sun.net.httpserver.HttpExchange

import slaydemo.backend.identity.objects.PlayerHandle
import slaydemo.backend.mail.objects.{MailFriendRequestStatus, MailId, MailKind, MailRecord}
import slaydemo.backend.mail.services.{MailReadError, MailService}
import slaydemo.backend.shared.json.JsonObjectParser
import slaydemo.backend.shared.policies.HandlePolicy
import slaydemo.backend.shared.routes.HttpRouteSupport

final class MailRoutes(service: MailService) {
  def mails(exchange: HttpExchange): Unit = {
    HttpRouteSupport.addCors(exchange)

    try {
      exchange.getRequestMethod.toUpperCase(Locale.ROOT) match {
        case "OPTIONS" =>
          HttpRouteSupport.sendEmpty(exchange, 204)
        case "GET" =>
          parseOwner(queryParams(exchange).get("ownerHandle")) match {
            case Left(MailRouteOwnerError.MissingOwner) =>
              jsonError(exchange, 400, "missing_owner", "missing_owner")
            case Left(MailRouteOwnerError.VisitorNotAllowed) =>
              jsonError(exchange, 403, "visitor_not_allowed", "visitor_not_allowed")
            case Left(MailRouteOwnerError.InvalidOwner) =>
              jsonError(exchange, 400, "invalid_owner", "invalid_owner")
            case Right(ownerHandle) =>
              HttpRouteSupport.sendJson(exchange, 200, renderMails(service.list(ownerHandle)))
          }
        case _ =>
          jsonError(exchange, 405, "method_not_allowed", "Method is not allowed.")
      }
    } finally {
      exchange.close()
    }
  }

  def read(exchange: HttpExchange): Unit = {
    HttpRouteSupport.addCors(exchange)

    try {
      exchange.getRequestMethod.toUpperCase(Locale.ROOT) match {
        case "OPTIONS" =>
          HttpRouteSupport.sendEmpty(exchange, 204)
        case "POST" =>
          JsonObjectParser.parseStringFields(HttpRouteSupport.readRequestBody(exchange)) match {
            case Left(_) =>
              jsonError(exchange, 400, "bad_request", "Request body must be a JSON object with string fields.")
            case Right(fields) =>
              parseReadCommand(fields.get("ownerHandle"), fields.get("mailId")) match {
                case Left(MailRouteReadError.MissingOwner) =>
                  jsonError(exchange, 400, "missing_owner", "missing_owner")
                case Left(MailRouteReadError.VisitorNotAllowed) =>
                  jsonError(exchange, 403, "visitor_not_allowed", "visitor_not_allowed")
                case Left(MailRouteReadError.InvalidOwner) =>
                  jsonError(exchange, 400, "invalid_owner", "invalid_owner")
                case Left(MailRouteReadError.MissingMailId) =>
                  jsonError(exchange, 400, "missing_mail_id", "missing_mail_id")
                case Right((ownerHandle, mailId)) =>
                  service.markRead(ownerHandle, mailId) match {
                    case Right(_) =>
                      HttpRouteSupport.sendJson(exchange, 200, """{"ok":true}""")
                    case Left(MailReadError.MailNotFound) =>
                      jsonError(exchange, 404, "mail_not_found", "mail_not_found")
                  }
              }
          }
        case _ =>
          jsonError(exchange, 405, "method_not_allowed", "Method is not allowed.")
      }
    } finally {
      exchange.close()
    }
  }

  private def queryParams(exchange: HttpExchange): Map[String, String] =
    Option(exchange.getRequestURI.getRawQuery).toVector
      .flatMap(_.split("&").toVector)
      .flatMap { pair =>
        pair.split("=", 2).toList match {
          case key :: value :: Nil if key.nonEmpty => Some(decode(key) -> decode(value))
          case key :: Nil if key.nonEmpty          => Some(decode(key) -> "")
          case _                                   => None
        }
      }
      .toMap

  private def parseReadCommand(
    ownerHandle: Option[String],
    mailId: Option[String]
  ): Either[MailRouteReadError, (PlayerHandle, MailId)] =
    parseOwner(ownerHandle) match {
      case Left(MailRouteOwnerError.MissingOwner) =>
        Left(MailRouteReadError.MissingOwner)
      case Left(MailRouteOwnerError.VisitorNotAllowed) =>
        Left(MailRouteReadError.VisitorNotAllowed)
      case Left(MailRouteOwnerError.InvalidOwner) =>
        Left(MailRouteReadError.InvalidOwner)
      case Right(owner) =>
        parseMailId(mailId).map(mailId => owner -> mailId)
    }

  private def parseOwner(value: Option[String]): Either[MailRouteOwnerError, PlayerHandle] = {
    val trimmed = value.map(HandlePolicy.trim).getOrElse("")
    if trimmed.isEmpty then Left(MailRouteOwnerError.MissingOwner)
    else if !HandlePolicy.isPlayableIdentityHandle(trimmed) then Left(MailRouteOwnerError.VisitorNotAllowed)
    else PlayerHandle.forLookup(trimmed).toRight(MailRouteOwnerError.InvalidOwner)
  }

  private def parseMailId(value: Option[String]): Either[MailRouteReadError, MailId] =
    value.map(_.trim).filter(_.nonEmpty).map(MailId.apply).toRight(MailRouteReadError.MissingMailId)

  private def renderMails(records: Vector[MailRecord]): String =
    renderObject(Vector("mails" -> records.map(renderMail).mkString("[", ",", "]")))

  private def renderMail(record: MailRecord): String =
    renderObject(
      Vector(
        "id" -> jsonString(record.id.value),
        "ownerHandle" -> jsonString(record.ownerHandle.value),
        "kind" -> jsonString(MailKind.wireValue(record.kind)),
        "subject" -> jsonString(record.subject),
        "excerpt" -> jsonString(record.excerpt),
        "senderLabel" -> jsonString(record.senderLabel),
        "unread" -> record.unread.toString,
        "important" -> record.important.toString,
        "createdAt" -> record.createdAt.value.toString
      ) ++ optionalStringField("sourceBattleId", record.sourceBattleId) ++
        optionalStringField("sourcePath", record.sourcePath) ++
        optionalStringField("sourceLabel", record.sourceLabel) ++
        friendRequestMetadataFields(record) ++
        governanceMetadataFields(record)
    )

  private def friendRequestMetadataFields(record: MailRecord): Vector[(String, String)] =
    record.friendRequestMetadata.map { metadata =>
      Vector(
        "friendRequestId" -> jsonString(metadata.requestId.value),
        "friendRequestStatus" -> jsonString(MailFriendRequestStatus.wireValue(metadata.status)),
        "friendRequestSourceHandle" -> jsonString(metadata.sourceHandle.value)
      )
    }.getOrElse(Vector.empty)

  private def governanceMetadataFields(record: MailRecord): Vector[(String, String)] =
    record.governanceMetadata.map { metadata =>
      Vector(
        "governanceActorHandle" -> jsonString(metadata.actorHandle),
        "governanceTargetPath" -> jsonString(metadata.targetPath),
        "governanceTargetLabel" -> jsonString(metadata.targetLabel)
      )
    }.getOrElse(Vector.empty)

  private def optionalStringField(key: String, value: Option[String]): Vector[(String, String)] =
    value.filter(_.trim.nonEmpty).map(text => Vector(key -> jsonString(text))).getOrElse(Vector.empty)

  private def renderObject(fields: Vector[(String, String)]): String =
    fields.map { case (key, value) => s"${jsonString(key)}:$value" }.mkString("{", ",", "}")

  private def jsonString(value: String): String =
    s""""${HttpRouteSupport.escapeJson(value)}""""

  private def jsonError(exchange: HttpExchange, status: Int, code: String, message: String): Unit =
    HttpRouteSupport.sendJson(exchange, status, s"""{"error":${jsonString(message)},"code":${jsonString(code)}}""")

  private def decode(value: String): String =
    URLDecoder.decode(value, StandardCharsets.UTF_8)
}

object MailRoutes {
  def apply(service: MailService): MailRoutes =
    new MailRoutes(service)
}

private enum MailRouteOwnerError {
  case MissingOwner
  case VisitorNotAllowed
  case InvalidOwner
}

private enum MailRouteReadError {
  case MissingOwner
  case VisitorNotAllowed
  case InvalidOwner
  case MissingMailId
}
