package slaydemo.backend.forum.routes

import java.io.InputStream
import java.nio.charset.StandardCharsets
import com.sun.net.httpserver.HttpExchange

import slaydemo.backend.forum.objects.{ForumTopicView, ForumVoteChoice}
import slaydemo.backend.forum.services.ForumService
import slaydemo.backend.forum.support.ForumJsonSupport
import slaydemo.backend.shared.objects.ThreadId

final class ForumRoutes(service: ForumService) {
  def handle(exchange: HttpExchange): Unit = {
    addCors(exchange)
    try {
      exchange.getRequestMethod.toUpperCase match {
        case "OPTIONS" =>
          exchange.sendResponseHeaders(204, -1)
        case "GET" if isTopicsCollection(exchange) =>
          val viewerHandle = resolveViewerHandle(exchange)
          sendJson(exchange, 200, renderTopics(service.listTopics(viewerHandle)))
        case "GET" =>
          topicIdFrom(exchange) match {
            case Some(topicId) =>
              service.loadTopic(topicId, resolveViewerHandle(exchange)) match {
                case Some(topic) =>
                  sendJson(exchange, 200, renderTopicWrapper(topic))
                case None =>
                  sendJson(exchange, 404, """{"error":"topic_not_found","code":"topic_not_found"}""")
              }
            case None =>
              sendJson(exchange, 404, """{"error":"topic_not_found","code":"topic_not_found"}""")
          }
        case "POST" if isTopicsCollection(exchange) =>
          parseBody(exchange.getRequestBody) match {
            case Right(body) =>
              service.createTopic(
                title = body.getOrElse("title", ""),
                body = body.getOrElse("body", ""),
                tag = body.getOrElse("tag", ""),
                authorHandle = body.getOrElse("author", resolveViewerHandle(exchange).getOrElse("Visitor"))
              ) match {
                case Right(topic) =>
                  sendJson(exchange, 201, renderTopicWrapper(topic))
                case Left(code) =>
                  sendError(exchange, statusFor(code), code, errorMessage(code))
              }
            case Left(error) =>
              sendError(exchange, 400, "bad_request", error)
          }
        case "POST" if isRepliesPath(exchange) =>
          topicIdFrom(exchange) match {
            case Some(topicId) =>
              parseBody(exchange.getRequestBody) match {
                case Right(body) =>
                  service.addReply(
                    threadId = topicId,
                    body = body.getOrElse("body", ""),
                    authorHandle = body.getOrElse("author", resolveViewerHandle(exchange).getOrElse("Visitor"))
                  ) match {
                    case Right(topic) =>
                      sendJson(exchange, 200, renderTopicWrapper(topic))
                    case Left(code) =>
                      sendError(exchange, statusFor(code), code, errorMessage(code))
                  }
                case Left(error) =>
                  sendError(exchange, 400, "bad_request", error)
              }
            case None =>
              sendError(exchange, 404, "topic_not_found", "Topic not found.")
          }
        case "POST" if isReplyVotesPath(exchange) =>
          (topicIdFrom(exchange), replyIdFrom(exchange)) match {
            case (Some(topicId), Some(replyId)) =>
              parseBody(exchange.getRequestBody) match {
                case Right(body) =>
                  parseVote(body) match {
                    case Right(vote) =>
                      service.setReplyVote(
                        threadId = topicId,
                        replyId = replyId,
                        authorHandle = body.getOrElse("author", resolveViewerHandle(exchange).getOrElse("Visitor")),
                        vote = vote
                      ) match {
                        case Right(topic) =>
                          sendJson(exchange, 200, renderTopicWrapper(topic))
                        case Left(code) =>
                          sendError(exchange, statusFor(code), code, errorMessage(code))
                      }
                    case Left(code) =>
                      sendError(exchange, statusFor(code), code, errorMessage(code))
                  }
                case Left(error) =>
                  sendError(exchange, 400, "bad_request", error)
              }
            case _ =>
              sendError(exchange, 404, "reply_not_found", "Reply not found.")
          }
        case "POST" if isVotesPath(exchange) =>
          topicIdFrom(exchange) match {
            case Some(topicId) =>
              parseBody(exchange.getRequestBody) match {
                case Right(body) =>
                  parseVote(body) match {
                    case Right(vote) =>
                      service.setVote(
                        threadId = topicId,
                        authorHandle = body.getOrElse("author", resolveViewerHandle(exchange).getOrElse("Visitor")),
                        vote = vote
                      ) match {
                        case Right(topic) =>
                          sendJson(exchange, 200, renderTopicWrapper(topic))
                        case Left(code) =>
                          sendError(exchange, statusFor(code), code, errorMessage(code))
                      }
                    case Left(code) =>
                      sendError(exchange, statusFor(code), code, errorMessage(code))
                  }
                case Left(error) =>
                  sendError(exchange, 400, "bad_request", error)
              }
            case None =>
              sendError(exchange, 404, "topic_not_found", "Topic not found.")
          }
        case "HEAD" =>
          exchange.sendResponseHeaders(200, -1)
        case _ =>
          sendError(exchange, 405, "method_not_allowed", "Only GET, POST, OPTIONS, and HEAD are supported.")
      }
    } finally {
      exchange.close()
    }
  }

  private def isTopicsCollection(exchange: HttpExchange): Boolean = {
    val path = normalizePath(exchange)
    path == "/forum/topics"
  }

  private def isRepliesPath(exchange: HttpExchange): Boolean = {
    val path = normalizePath(exchange)
    path.endsWith("/replies") && path.startsWith("/forum/topics/")
  }

  private def isVotesPath(exchange: HttpExchange): Boolean = {
    val path = normalizePath(exchange)
    path.endsWith("/votes") && path.startsWith("/forum/topics/")
  }

  private def isReplyVotesPath(exchange: HttpExchange): Boolean = {
    val segments = pathSegments(exchange)
    segments.length == 6 &&
    segments(0) == "forum" &&
    segments(1) == "topics" &&
    segments(3) == "replies" &&
    segments(5) == "votes"
  }

  private def topicIdFrom(exchange: HttpExchange): Option[ThreadId] = {
    val path = normalizePath(exchange)
    val prefix = "/forum/topics/"
    if (!path.startsWith(prefix)) {
      None
    } else {
      val withoutPrefix = path.drop(prefix.length)
      val topicId = withoutPrefix.split('/').headOption.getOrElse("").trim
      if (topicId.isEmpty || topicId == "replies" || topicId == "votes") {
        None
      } else {
        Some(ThreadId(ForumJsonSupport.urlDecode(topicId)))
      }
    }
  }

  private def replyIdFrom(exchange: HttpExchange): Option[String] = {
    val segments = pathSegments(exchange)
    if (segments.length == 6 && segments(3) == "replies" && segments(5) == "votes") {
      val replyId = ForumJsonSupport.urlDecode(segments(4)).trim
      Option.when(replyId.nonEmpty)(replyId)
    } else {
      None
    }
  }

  private def pathSegments(exchange: HttpExchange): Vector[String] =
    normalizePath(exchange).split('/').toVector.filter(_.nonEmpty)

  private def normalizePath(exchange: HttpExchange): String =
    Option(exchange.getRequestURI.getPath).getOrElse("/").stripSuffix("/")

  private def resolveViewerHandle(exchange: HttpExchange): Option[String] = {
    val query = ForumJsonSupport.parseQuery(exchange.getRequestURI.getRawQuery)
    query.get("author").orElse(query.get("viewer")).map(_.trim).filter(_.nonEmpty)
  }

  private def parseBody(input: InputStream): Either[String, Map[String, String]] = {
    val body = new String(input.readAllBytes(), StandardCharsets.UTF_8).trim
    if (body.isEmpty) {
      Right(Map.empty)
    } else {
      val stringPattern = "\"([^\"]+)\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"".r
      val stringPairs = stringPattern.findAllMatchIn(body).map(matchResult => matchResult.group(1) -> ForumJsonSupport.unescape(matchResult.group(2))).toMap
      val voteField = ForumJsonSupport.extractNullableString(body, "vote")
      val fields = voteField match {
        case Some(null) => stringPairs + ("vote" -> "null")
        case Some(value) => stringPairs + ("vote" -> value)
        case None        => stringPairs
      }

      if (fields.nonEmpty || body == "{}") Right(fields)
      else Left("Request body must be a JSON object.")
    }
  }

  private def parseVote(body: Map[String, String]): Either[String, Option[ForumVoteChoice]] = {
    val vote = body.get("vote").flatMap(raw => Option(raw).map(_.trim)).flatMap {
      case "" | "null" => None
      case other       => ForumVoteChoice.fromString(other)
    }

    if (body.contains("vote") && body.get("vote").exists(raw => raw.trim.nonEmpty && raw.trim.toLowerCase != "null" && vote.isEmpty)) {
      Left("invalid_vote")
    } else {
      Right(vote)
    }
  }

  private def renderTopics(topics: Seq[ForumTopicView]): String = {
    s"""{"topics":[${topics.map(renderTopic).mkString(",")}]}"""
  }

  private def renderTopicWrapper(topic: ForumTopicView): String = {
    s"""{"topic":${renderTopic(topic)}}"""
  }

  private def renderTopic(topic: ForumTopicView): String = {
    val replies = topic.replyItems.map(renderReply).mkString(",")
    val viewerVote = topic.viewerVote.map(value => s""""${ForumJsonSupport.escape(value)}"""").getOrElse("null")
    s"""{
       |"id":"${ForumJsonSupport.escape(topic.id)}",
       |"title":"${ForumJsonSupport.escape(topic.title)}",
       |"author":"${ForumJsonSupport.escape(topic.author)}",
       |"excerpt":"${ForumJsonSupport.escape(topic.excerpt)}",
       |"tag":"${ForumJsonSupport.escape(topic.tag)}",
       |"replies":${topic.replies},
       |"updatedAt":${topic.updatedAt},
       |"createdAt":${topic.createdAt},
       |"body":"${ForumJsonSupport.escape(topic.body)}",
       |"replyItems":[${replies}],
       |"viewerVote":$viewerVote,
       |"score":${topic.score}
       |}""".stripMargin.replace("\n", "")
  }

  private def renderReply(reply: slaydemo.backend.forum.objects.ForumReplyView): String = {
    s"""{
       |"id":"${ForumJsonSupport.escape(reply.id)}",
       |"author":"${ForumJsonSupport.escape(reply.author)}",
       |"body":"${ForumJsonSupport.escape(reply.body)}",
       |"publishedAt":${reply.publishedAt},
       |"viewerVote":${reply.viewerVote.map(value => s""""${ForumJsonSupport.escape(value)}"""").getOrElse("null")},
       |"score":${reply.score}
       |}""".stripMargin.replace("\n", "")
  }

  private def sendError(exchange: HttpExchange, status: Int, code: String, message: String): Unit = {
    sendJson(exchange, status, s"""{"error":"${ForumJsonSupport.escape(message)}","code":"${ForumJsonSupport.escape(code)}"}""")
  }

  private def statusFor(code: String): Int = code match {
    case "topic_not_found" => 404
    case "reply_not_found" => 404
    case _                 => 400
  }

  private def errorMessage(code: String): String = code match {
    case "invalid_title"   => "Title is required."
    case "invalid_body"    => "Body is required."
    case "invalid_tag"     => "Tag is required."
    case "invalid_author"  => "Author is required."
    case "invalid_vote"    => "Vote must be up, down, or null."
    case "topic_not_found" => "Topic not found."
    case "reply_not_found" => "Reply not found."
    case _                 => "Request rejected."
  }

  private def addCors(exchange: HttpExchange): Unit = {
    val headers = exchange.getResponseHeaders
    headers.set("Access-Control-Allow-Origin", "*")
    headers.set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Session-Token")
    headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS, HEAD")
    headers.set("Content-Type", "application/json; charset=utf-8")
  }

  private def sendJson(exchange: HttpExchange, status: Int, json: String): Unit = {
    val bytes = json.getBytes(StandardCharsets.UTF_8)
    exchange.sendResponseHeaders(status, bytes.length.toLong)
    val output = exchange.getResponseBody
    try output.write(bytes)
    finally output.close()
  }
}
