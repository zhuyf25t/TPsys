package slaydemo.backend.forum.routes

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

import com.sun.net.httpserver.HttpExchange

import slaydemo.backend.forum.objects.*
import slaydemo.backend.forum.services.{
  AddForumReplyCommand,
  CreateForumTopicCommand,
  ForumService,
  ForumTopicMutationError,
  SetForumReplyVoteCommand,
  SetForumTopicVoteCommand
}
import slaydemo.backend.identity.objects.PlayerHandle
import slaydemo.backend.shared.policies.HandlePolicy
import slaydemo.backend.shared.routes.HttpRouteSupport

final class ForumRoutes(service: ForumService) {
  def handle(exchange: HttpExchange): Unit = {
    HttpRouteSupport.addCors(exchange)

    try {
      exchange.getRequestMethod.toUpperCase(Locale.ROOT) match {
        case "OPTIONS" =>
          HttpRouteSupport.sendEmpty(exchange, 204)
        case "HEAD" =>
          HttpRouteSupport.sendEmpty(exchange, 200)
        case "GET" if isTopicsCollection(exchange) =>
          val topics = service.listTopics(resolveViewerHandle(exchange))
          HttpRouteSupport.sendJson(exchange, 200, renderTopics(topics))
        case "GET" =>
          topicIdFrom(exchange) match {
            case None =>
              jsonError(exchange, 404, "topic_not_found", "topic_not_found")
            case Some(topicId) =>
              parseTopicId(topicId) match {
                case None =>
                  jsonError(exchange, 404, "topic_not_found", "topic_not_found")
                case Some(parsedTopicId) =>
                  service.loadTopic(parsedTopicId, resolveViewerHandle(exchange)) match {
                    case Some(topic) =>
                      HttpRouteSupport.sendJson(exchange, 200, renderTopicWrapper(topic))
                    case None =>
                      jsonError(exchange, 404, "topic_not_found", "topic_not_found")
                  }
              }
          }
        case "POST" if isTopicsCollection(exchange) =>
          createTopic(exchange)
        case "POST" if isReplyVotesPath(exchange) =>
          setReplyVote(exchange)
        case "POST" if isRepliesPath(exchange) =>
          addReply(exchange)
        case "POST" if isTopicVotesPath(exchange) =>
          setTopicVote(exchange)
        case _ =>
          jsonError(exchange, 405, "method_not_allowed", "Method is not allowed.")
      }
    } finally {
      exchange.close()
    }
  }

  private def createTopic(exchange: HttpExchange): Unit =
    parseBody(exchange) match {
      case Left(message) =>
        jsonError(exchange, 400, "bad_request", message)
      case Right(fields) =>
        parseCreateTopicCommand(fields) match {
          case Right(command) =>
            val topic = service.createTopic(command)
            HttpRouteSupport.sendJson(exchange, 201, renderTopicWrapper(topic))
          case Left(error) =>
            val code = createErrorCode(error)
            jsonError(exchange, createStatusFor(error), code, code)
        }
    }

  private def addReply(exchange: HttpExchange): Unit =
    topicIdFrom(exchange) match {
      case None =>
        jsonError(exchange, 404, "topic_not_found", "topic_not_found")
      case Some(topicId) =>
        parseBody(exchange) match {
          case Left(message) =>
            jsonError(exchange, 400, "bad_request", message)
          case Right(fields) =>
            parseAddReplyCommand(topicId, fields) match {
              case Left(error) =>
                val code = mutationErrorCode(error)
                jsonError(exchange, mutationStatusFor(error), code, code)
              case Right(command) =>
                service.addReply(command) match {
                  case Right(topic) =>
                    HttpRouteSupport.sendJson(exchange, 200, renderTopicWrapper(topic))
                  case Left(error) =>
                    val code = mutationErrorCode(error)
                    jsonError(exchange, mutationStatusFor(error), code, code)
                }
            }
        }
    }

  private def setTopicVote(exchange: HttpExchange): Unit =
    topicIdFrom(exchange) match {
      case None =>
        jsonError(exchange, 404, "topic_not_found", "topic_not_found")
      case Some(topicId) =>
        parseBody(exchange) match {
          case Left(message) =>
            jsonError(exchange, 400, "bad_request", message)
          case Right(fields) =>
            parseVote(fields) match {
              case Left(code) =>
                jsonError(exchange, 400, code, code)
              case Right(vote) =>
                parseSetTopicVoteCommand(topicId, fields, vote) match {
                  case Left(error) =>
                    val code = mutationErrorCode(error)
                    jsonError(exchange, mutationStatusFor(error), code, code)
                  case Right(command) =>
                    service.setTopicVote(command) match {
                      case Right(topic) =>
                        HttpRouteSupport.sendJson(exchange, 200, renderTopicWrapper(topic))
                      case Left(error) =>
                        val code = mutationErrorCode(error)
                        jsonError(exchange, mutationStatusFor(error), code, code)
                    }
                }
            }
        }
    }

  private def setReplyVote(exchange: HttpExchange): Unit =
    (topicIdFrom(exchange), replyIdFrom(exchange)) match {
      case (Some(topicId), Some(replyId)) =>
        parseBody(exchange) match {
          case Left(message) =>
            jsonError(exchange, 400, "bad_request", message)
          case Right(fields) =>
            parseVote(fields) match {
              case Left(code) =>
                jsonError(exchange, 400, code, code)
              case Right(vote) =>
                parseSetReplyVoteCommand(topicId, replyId, fields, vote) match {
                  case Left(error) =>
                    val code = mutationErrorCode(error)
                    jsonError(exchange, mutationStatusFor(error), code, code)
                  case Right(command) =>
                    service.setReplyVote(command) match {
                      case Right(topic) =>
                        HttpRouteSupport.sendJson(exchange, 200, renderTopicWrapper(topic))
                      case Left(error) =>
                        val code = mutationErrorCode(error)
                        jsonError(exchange, mutationStatusFor(error), code, code)
                    }
                }
            }
        }
      case _ =>
        jsonError(exchange, 404, "reply_not_found", "reply_not_found")
    }

  private def isTopicsCollection(exchange: HttpExchange): Boolean =
    normalizePath(exchange) == "/forum/topics"

  private def isRepliesPath(exchange: HttpExchange): Boolean = {
    val segments = pathSegments(exchange)
    segments.length == 4 &&
    segments(0) == "forum" &&
    segments(1) == "topics" &&
    segments(3) == "replies"
  }

  private def isTopicVotesPath(exchange: HttpExchange): Boolean = {
    val segments = pathSegments(exchange)
    segments.length == 4 &&
    segments(0) == "forum" &&
    segments(1) == "topics" &&
    segments(3) == "votes"
  }

  private def isReplyVotesPath(exchange: HttpExchange): Boolean = {
    val segments = pathSegments(exchange)
    segments.length == 6 &&
    segments(0) == "forum" &&
    segments(1) == "topics" &&
    segments(3) == "replies" &&
    segments(5) == "votes"
  }

  private def topicIdFrom(exchange: HttpExchange): Option[String] = {
    val segments = pathSegments(exchange)
    if segments.length >= 3 && segments(0) == "forum" && segments(1) == "topics" then {
      val topicId = decode(segments(2)).trim
      Option.when(topicId.nonEmpty)(topicId)
    } else {
      None
    }
  }

  private def replyIdFrom(exchange: HttpExchange): Option[String] = {
    val segments = pathSegments(exchange)
    if isReplyVotesPath(exchange) then {
      val replyId = decode(segments(4)).trim
      Option.when(replyId.nonEmpty)(replyId)
    } else {
      None
    }
  }

  private def resolveViewerHandle(exchange: HttpExchange): Option[PlayerHandle] =
    queryParams(exchange).get("author").orElse(queryParams(exchange).get("viewer"))
      .map(HandlePolicy.trim)
      .filter(HandlePolicy.isPlayableIdentityHandle)
      .flatMap(PlayerHandle.forLookup)

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

  private def pathSegments(exchange: HttpExchange): Vector[String] =
    normalizePath(exchange).split('/').toVector.filter(_.nonEmpty)

  private def normalizePath(exchange: HttpExchange): String =
    routePath(exchange.getRequestURI.getPath).stripSuffix("/")

  private def routePath(path: String): String = {
    val raw = Option(path).getOrElse("/")
    if raw == "/api" then "/"
    else if raw.startsWith("/api/") then raw.stripPrefix("/api")
    else raw
  }

  private def parseBody(exchange: HttpExchange): Either[String, ForumRequestFields] = {
    val body = HttpRouteSupport.readRequestBody(exchange).trim
    if body.isEmpty || body == "{}" then Right(ForumRequestFields(Map.empty, voteSeen = false))
    else if !body.startsWith("{") || !body.endsWith("}") then Left("Request body must be a JSON object.")
    else {
      val stringFields = stringFieldPattern.findAllMatchIn(body).map { matchResult =>
        matchResult.group(1) -> unescapeJsonString(matchResult.group(2))
      }.toMap

      voteFieldPattern.findFirstMatchIn(body) match {
        case Some(matchResult) if matchResult.group(1) == "null" =>
          Right(ForumRequestFields(stringFields.updated("vote", ""), voteSeen = true))
        case Some(matchResult) =>
          Right(ForumRequestFields(stringFields.updated("vote", unescapeJsonString(matchResult.group(2))), voteSeen = true))
        case None if stringFields.nonEmpty =>
          Right(ForumRequestFields(stringFields, voteSeen = false))
        case None =>
          Left("Request body must be a JSON object with string fields.")
      }
    }
  }

  private def parseVote(fields: ForumRequestFields): Either[String, Option[ForumVoteChoice]] =
    fields.fields.get("vote") match {
      case None if !fields.voteSeen =>
        Right(None)
      case Some(raw) if raw.trim.isEmpty =>
        Right(None)
      case Some(raw) =>
        ForumVoteChoice.fromWire(raw).map(Some(_)).toRight("invalid_vote")
      case None =>
        Right(None)
    }

  private def parseCreateTopicCommand(fields: ForumRequestFields): Either[ForumCreateTopicParseError, CreateForumTopicCommand] =
    for {
      title <- parseTitle(fields.stringValue("title"))
      body <- parseCreateBody(fields.stringValue("body"))
      tag <- parseTag(fields.stringValue("tag"))
      author <- parseCreateAuthor(fields.stringValue("author"))
    } yield CreateForumTopicCommand(
      title = title,
      body = body,
      tag = tag,
      authorHandle = author
    )

  private def parseAddReplyCommand(
    topicId: String,
    fields: ForumRequestFields
  ): Either[ForumTopicMutationParseError, AddForumReplyCommand] =
    for {
      parsedTopicId <- parseMutationTopicId(topicId)
      body <- parseReplyBody(fields.stringValue("body"))
      author <- parseMutationAuthor(fields.stringValue("author"))
    } yield AddForumReplyCommand(
      topicId = parsedTopicId,
      body = body,
      authorHandle = author
    )

  private def parseSetTopicVoteCommand(
    topicId: String,
    fields: ForumRequestFields,
    vote: Option[ForumVoteChoice]
  ): Either[ForumTopicMutationParseError, SetForumTopicVoteCommand] =
    for {
      parsedTopicId <- parseMutationTopicId(topicId)
      author <- parseMutationAuthor(fields.stringValue("author"))
    } yield SetForumTopicVoteCommand(
      topicId = parsedTopicId,
      authorHandle = author,
      vote = vote
    )

  private def parseSetReplyVoteCommand(
    topicId: String,
    replyId: String,
    fields: ForumRequestFields,
    vote: Option[ForumVoteChoice]
  ): Either[ForumTopicMutationParseError, SetForumReplyVoteCommand] =
    for {
      parsedTopicId <- parseMutationTopicId(topicId)
      parsedReplyId <- parseReplyId(replyId)
      author <- parseMutationAuthor(fields.stringValue("author"))
    } yield SetForumReplyVoteCommand(
      topicId = parsedTopicId,
      replyId = parsedReplyId,
      authorHandle = author,
      vote = vote
    )

  private def parseTitle(value: String): Either[ForumCreateTopicParseError, ForumTitle] =
    Option(value).map(_.trim).filter(_.nonEmpty).map(ForumTitle.apply).toRight(ForumCreateTopicParseError.InvalidTitle)

  private def parseCreateBody(value: String): Either[ForumCreateTopicParseError, ForumBody] =
    Option(value).map(_.trim).filter(_.nonEmpty).map(ForumBody.apply).toRight(ForumCreateTopicParseError.InvalidBody)

  private def parseReplyBody(value: String): Either[ForumTopicMutationParseError, ForumBody] =
    Option(value).map(_.trim).filter(_.nonEmpty).map(ForumBody.apply).toRight(ForumTopicMutationParseError.InvalidBody)

  private def parseTag(value: String): Either[ForumCreateTopicParseError, ForumTag] =
    Option(value).map(_.trim).filter(_.nonEmpty).map(ForumTag.apply).toRight(ForumCreateTopicParseError.InvalidTag)

  private def parseCreateAuthor(value: String): Either[ForumCreateTopicParseError, PlayerHandle] = {
    val trimmed = HandlePolicy.trim(value)
    if trimmed.isEmpty then Left(ForumCreateTopicParseError.InvalidAuthor)
    else if HandlePolicy.isVisitorLikeHandle(trimmed) then Left(ForumCreateTopicParseError.VisitorNotAllowed)
    else PlayerHandle.forLookup(trimmed).toRight(ForumCreateTopicParseError.InvalidAuthor)
  }

  private def parseMutationAuthor(value: String): Either[ForumTopicMutationParseError, PlayerHandle] = {
    val trimmed = HandlePolicy.trim(value)
    if trimmed.isEmpty then Left(ForumTopicMutationParseError.InvalidAuthor)
    else if HandlePolicy.isVisitorLikeHandle(trimmed) then Left(ForumTopicMutationParseError.VisitorNotAllowed)
    else PlayerHandle.forLookup(trimmed).toRight(ForumTopicMutationParseError.InvalidAuthor)
  }

  private def parseTopicId(value: String): Option[ForumTopicId] =
    Option(value).map(_.trim).filter(_.nonEmpty).map(ForumTopicId.apply)

  private def parseMutationTopicId(value: String): Either[ForumTopicMutationParseError, ForumTopicId] =
    parseTopicId(value).toRight(ForumTopicMutationParseError.TopicNotFound)

  private def parseReplyId(value: String): Either[ForumTopicMutationParseError, ForumReplyId] =
    Option(value).map(_.trim).filter(_.nonEmpty).map(ForumReplyId.apply).toRight(ForumTopicMutationParseError.ReplyNotFound)

  private def renderTopics(topics: Vector[ForumTopicView]): String =
    renderObject(Vector("topics" -> topics.map(renderTopic).mkString("[", ",", "]")))

  private def renderTopicWrapper(topic: ForumTopicView): String =
    renderObject(Vector("topic" -> renderTopic(topic)))

  private def renderTopic(topic: ForumTopicView): String =
    renderObject(
      Vector(
        "id" -> jsonString(topic.id.value),
        "title" -> jsonString(topic.title.value),
        "author" -> jsonString(topic.author.value),
        "excerpt" -> jsonString(topic.excerpt),
        "tag" -> jsonString(topic.tag.value),
        "replies" -> topic.replies.value.toString,
        "updatedAt" -> topic.updatedAt.value.toString,
        "createdAt" -> topic.createdAt.value.toString,
        "body" -> jsonString(topic.body.value),
        "replyItems" -> topic.replyItems.map(renderReply).mkString("[", ",", "]"),
        "viewerVote" -> renderVote(topic.viewerVote),
        "score" -> topic.score.value.toString
      )
    )

  private def renderReply(reply: ForumReplyView): String =
    renderObject(
      Vector(
        "id" -> jsonString(reply.id.value),
        "author" -> jsonString(reply.author.value),
        "body" -> jsonString(reply.body.value),
        "publishedAt" -> reply.publishedAt.value.toString,
        "viewerVote" -> renderVote(reply.viewerVote),
        "score" -> reply.score.value.toString
      )
    )

  private def renderVote(value: Option[ForumVoteChoice]): String =
    value.map(choice => jsonString(ForumVoteChoice.wireValue(choice))).getOrElse("null")

  private def createErrorCode(error: ForumCreateTopicParseError): String =
    error match {
      case ForumCreateTopicParseError.InvalidTitle      => "invalid_title"
      case ForumCreateTopicParseError.InvalidBody       => "invalid_body"
      case ForumCreateTopicParseError.InvalidTag        => "invalid_tag"
      case ForumCreateTopicParseError.InvalidAuthor     => "invalid_author"
      case ForumCreateTopicParseError.VisitorNotAllowed => "visitor_not_allowed"
    }

  private def createStatusFor(error: ForumCreateTopicParseError): Int =
    error match {
      case ForumCreateTopicParseError.VisitorNotAllowed => 403
      case _                                           => 400
    }

  private def mutationErrorCode(error: ForumTopicMutationError): String =
    error match {
      case ForumTopicMutationError.TopicNotFound => "topic_not_found"
      case ForumTopicMutationError.ReplyNotFound => "reply_not_found"
    }

  private def mutationStatusFor(error: ForumTopicMutationError): Int =
    error match {
      case ForumTopicMutationError.TopicNotFound => 404
      case ForumTopicMutationError.ReplyNotFound => 404
    }

  private def mutationErrorCode(error: ForumTopicMutationParseError): String =
    error match {
      case ForumTopicMutationParseError.TopicNotFound     => "topic_not_found"
      case ForumTopicMutationParseError.ReplyNotFound     => "reply_not_found"
      case ForumTopicMutationParseError.InvalidBody       => "invalid_body"
      case ForumTopicMutationParseError.InvalidAuthor     => "invalid_author"
      case ForumTopicMutationParseError.VisitorNotAllowed => "visitor_not_allowed"
    }

  private def mutationStatusFor(error: ForumTopicMutationParseError): Int =
    error match {
      case ForumTopicMutationParseError.TopicNotFound     => 404
      case ForumTopicMutationParseError.ReplyNotFound     => 404
      case ForumTopicMutationParseError.VisitorNotAllowed => 403
      case _                                             => 400
    }

  private def renderObject(fields: Vector[(String, String)]): String =
    fields.map { case (key, value) => s"${jsonString(key)}:$value" }.mkString("{", ",", "}")

  private def jsonString(value: String): String =
    s""""${HttpRouteSupport.escapeJson(value)}""""

  private def jsonError(exchange: HttpExchange, status: Int, code: String, message: String): Unit =
    HttpRouteSupport.sendJson(exchange, status, s"""{"error":${jsonString(message)},"code":${jsonString(code)}}""")

  private def decode(value: String): String =
    URLDecoder.decode(value, StandardCharsets.UTF_8)

  private def unescapeJsonString(value: String): String =
    value
      .replace("\\\"", "\"")
      .replace("\\\\", "\\")
      .replace("\\/", "/")
      .replace("\\b", "\b")
      .replace("\\f", "\f")
      .replace("\\n", "\n")
      .replace("\\r", "\r")
      .replace("\\t", "\t")

  private val stringFieldPattern =
    "\"([^\"]+)\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"".r

  private val voteFieldPattern =
    "\"vote\"\\s*:\\s*(null|\"((?:\\\\.|[^\"\\\\])*)\")".r
}

private final case class ForumRequestFields(fields: Map[String, String], voteSeen: Boolean) {
  def stringValue(name: String): String =
    fields.getOrElse(name, "")
}

private enum ForumCreateTopicParseError {
  case InvalidTitle
  case InvalidBody
  case InvalidTag
  case InvalidAuthor
  case VisitorNotAllowed
}

private enum ForumTopicMutationParseError {
  case TopicNotFound
  case ReplyNotFound
  case InvalidBody
  case InvalidAuthor
  case VisitorNotAllowed
}

object ForumRoutes {
  def apply(service: ForumService): ForumRoutes =
    new ForumRoutes(service)
}
