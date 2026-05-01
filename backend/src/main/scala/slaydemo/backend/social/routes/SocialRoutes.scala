package slaydemo.backend.social.routes

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

import com.sun.net.httpserver.HttpExchange

import slaydemo.backend.identity.objects.PlayerHandle
import slaydemo.backend.mail.objects.{MailFriendRequestStatus, MailKind, MailRecord}
import slaydemo.backend.shared.json.JsonObjectParser
import slaydemo.backend.shared.policies.HandlePolicy
import slaydemo.backend.shared.routes.HttpRouteSupport
import slaydemo.backend.social.objects.{FriendRequestDecision, FriendRequestId, FriendRequestRecord, FriendRequestStatus}
import slaydemo.backend.social.services.{
  FriendRequestCreateError,
  FriendRequestRespondError,
  FriendRequestResponseResult,
  FriendRequestService,
  FriendRequestSubmissionResult
}

final class SocialRoutes(service: FriendRequestService) {
  def friendRequests(exchange: HttpExchange): Unit = {
    HttpRouteSupport.addCors(exchange)

    try {
      exchange.getRequestMethod.toUpperCase(Locale.ROOT) match {
        case "OPTIONS" =>
          HttpRouteSupport.sendEmpty(exchange, 204)
        case "GET" if !exchange.getRequestURI.getPath.endsWith("/respond") =>
          list(exchange)
        case "POST" if exchange.getRequestURI.getPath.endsWith("/respond") =>
          respond(exchange)
        case "POST" =>
          create(exchange)
        case _ =>
          jsonError(exchange, 405, "method_not_allowed", "Method is not allowed.")
      }
    } finally {
      exchange.close()
    }
  }

  private def list(exchange: HttpExchange): Unit =
    parseOwner(queryParams(exchange).get("ownerHandle")) match {
      case Left(SocialRouteHandleError.Missing) =>
        jsonError(exchange, 400, "missing_owner", "missing_owner")
      case Left(SocialRouteHandleError.VisitorNotAllowed) =>
        jsonError(exchange, 403, "visitor_not_allowed", "visitor_not_allowed")
      case Left(SocialRouteHandleError.Invalid) =>
        jsonError(exchange, 400, "invalid_owner", "invalid_owner")
      case Right(ownerHandle) =>
        HttpRouteSupport.sendJson(exchange, 200, renderRequests(service.list(ownerHandle)))
    }

  private def create(exchange: HttpExchange): Unit =
    JsonObjectParser.parseStringFields(HttpRouteSupport.readRequestBody(exchange)) match {
      case Left(_) =>
        jsonError(exchange, 400, "bad_request", "Request body must be a JSON object with string fields.")
      case Right(fields) =>
        parseCreateHandles(fields.get("sourceHandle"), fields.get("targetHandle")) match {
          case Left(SocialRouteCreateError.InvalidHandles) =>
            jsonError(exchange, 400, "invalid_handles", "invalid_handles")
          case Left(SocialRouteCreateError.VisitorNotAllowed) =>
            jsonError(exchange, 403, "visitor_not_allowed", "visitor_not_allowed")
          case Right((sourceHandle, targetHandle)) =>
            service.create(sourceHandle, targetHandle) match {
              case Right(result) =>
                HttpRouteSupport.sendJson(exchange, 200, renderCreateResult(result))
              case Left(FriendRequestCreateError.InvalidHandles) =>
                jsonError(exchange, 400, "invalid_handles", "invalid_handles")
            }
        }
    }

  private def respond(exchange: HttpExchange): Unit =
    JsonObjectParser.parseStringFields(HttpRouteSupport.readRequestBody(exchange)) match {
      case Left(_) =>
        jsonError(exchange, 400, "bad_request", "Request body must be a JSON object with string fields.")
      case Right(fields) =>
        FriendRequestDecision.fromWire(fields.getOrElse("decision", "")) match {
          case None =>
            jsonError(exchange, 400, "invalid_decision", "invalid_decision")
          case Some(decision) =>
            parseRespondCommand(fields.get("requestId"), fields.get("actorHandle")) match {
              case Left(SocialRouteRespondError.MissingFields) =>
                jsonError(exchange, 400, "missing_fields", "missing_fields")
              case Left(SocialRouteRespondError.InvalidActorHandle) =>
                jsonError(exchange, 400, "invalid_actor", "invalid_actor")
              case Left(SocialRouteRespondError.VisitorNotAllowed) =>
                jsonError(exchange, 403, "visitor_not_allowed", "visitor_not_allowed")
              case Right((requestId, actorHandle)) =>
                service.respond(requestId, actorHandle, decision) match {
                  case Right(result) =>
                    HttpRouteSupport.sendJson(exchange, 200, renderResponseResult(result))
                  case Left(FriendRequestRespondError.RequestNotFound) =>
                    jsonError(exchange, 404, "request_not_found", "request_not_found")
                  case Left(FriendRequestRespondError.Forbidden) =>
                    jsonError(exchange, 403, "forbidden", "forbidden")
                }
            }
        }
    }

  private def renderCreateResult(result: FriendRequestSubmissionResult): String =
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

  private def renderResponseResult(result: FriendRequestResponseResult): String =
    renderObject(
      Vector(
        "request" -> renderRequest(result.friendRequest),
        "mail" -> result.notificationMail.map(renderMail).getOrElse("null")
      )
    )

  private def renderRequests(records: Vector[FriendRequestRecord]): String =
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

  private def parseCreateHandles(
    sourceHandle: Option[String],
    targetHandle: Option[String]
  ): Either[SocialRouteCreateError, (PlayerHandle, PlayerHandle)] =
    parseCreateHandle(sourceHandle) match {
      case Left(error) =>
        Left(error)
      case Right(source) =>
        parseCreateHandle(targetHandle).map(target => source -> target)
    }

  private def parseCreateHandle(value: Option[String]): Either[SocialRouteCreateError, PlayerHandle] = {
    val trimmed = value.map(HandlePolicy.trim).getOrElse("")
    if trimmed.isEmpty then Left(SocialRouteCreateError.InvalidHandles)
    else if !HandlePolicy.isPlayableIdentityHandle(trimmed) then Left(SocialRouteCreateError.VisitorNotAllowed)
    else PlayerHandle.forLookup(trimmed).toRight(SocialRouteCreateError.InvalidHandles)
  }

  private def parseOwner(value: Option[String]): Either[SocialRouteHandleError, PlayerHandle] = {
    val trimmed = value.map(HandlePolicy.trim).getOrElse("")
    if trimmed.isEmpty then Left(SocialRouteHandleError.Missing)
    else if !HandlePolicy.isPlayableIdentityHandle(trimmed) then Left(SocialRouteHandleError.VisitorNotAllowed)
    else PlayerHandle.forLookup(trimmed).toRight(SocialRouteHandleError.Invalid)
  }

  private def parseRespondCommand(
    requestId: Option[String],
    actorHandle: Option[String]
  ): Either[SocialRouteRespondError, (FriendRequestId, PlayerHandle)] =
    parseRequestId(requestId) match {
      case Left(error) =>
        Left(error)
      case Right(parsedRequestId) =>
        parseRespondActor(actorHandle).map(actor => parsedRequestId -> actor)
    }

  private def parseRequestId(value: Option[String]): Either[SocialRouteRespondError, FriendRequestId] =
    value.map(_.trim).filter(_.nonEmpty).map(FriendRequestId.apply).toRight(SocialRouteRespondError.MissingFields)

  private def parseRespondActor(value: Option[String]): Either[SocialRouteRespondError, PlayerHandle] = {
    val trimmed = value.map(HandlePolicy.trim).getOrElse("")
    if trimmed.isEmpty then Left(SocialRouteRespondError.MissingFields)
    else if !HandlePolicy.isPlayableIdentityHandle(trimmed) then Left(SocialRouteRespondError.VisitorNotAllowed)
    else PlayerHandle.forLookup(trimmed).toRight(SocialRouteRespondError.InvalidActorHandle)
  }

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

object SocialRoutes {
  def apply(service: FriendRequestService): SocialRoutes =
    new SocialRoutes(service)
}

private enum SocialRouteHandleError {
  case Missing
  case VisitorNotAllowed
  case Invalid
}

private enum SocialRouteCreateError {
  case InvalidHandles
  case VisitorNotAllowed
}

private enum SocialRouteRespondError {
  case MissingFields
  case InvalidActorHandle
  case VisitorNotAllowed
}
