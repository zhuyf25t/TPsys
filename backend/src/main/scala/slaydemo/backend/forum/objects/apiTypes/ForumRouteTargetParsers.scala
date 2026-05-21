package slaydemo.backend.forum.objects.apiTypes

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

import slaydemo.backend.identity.objects.PlayerHandle
import slaydemo.backend.shared.policies.HandlePolicy

object ForumRouteTargetParsers {
  def isTopicsCollection(path: String): Boolean =
    normalizePath(path) == "/forum/topics"

  def isRepliesPath(path: String): Boolean = {
    val segments = pathSegments(path)
    segments.length == 4 &&
    segments(0) == "forum" &&
    segments(1) == "topics" &&
    segments(3) == "replies"
  }

  def isTopicVotesPath(path: String): Boolean = {
    val segments = pathSegments(path)
    segments.length == 4 &&
    segments(0) == "forum" &&
    segments(1) == "topics" &&
    segments(3) == "votes"
  }

  def isReplyVotesPath(path: String): Boolean = {
    val segments = pathSegments(path)
    segments.length == 6 &&
    segments(0) == "forum" &&
    segments(1) == "topics" &&
    segments(3) == "replies" &&
    segments(5) == "votes"
  }

  def topicIdFrom(path: String): Option[String] = {
    val segments = pathSegments(path)
    if segments.length >= 3 && segments(0) == "forum" && segments(1) == "topics" then {
      val topicId = decode(segments(2)).trim
      Option.when(topicId.nonEmpty)(topicId)
    } else {
      None
    }
  }

  def replyIdFrom(path: String): Option[String] = {
    val segments = pathSegments(path)
    if isReplyVotesPath(path) then {
      val replyId = decode(segments(4)).trim
      Option.when(replyId.nonEmpty)(replyId)
    } else {
      None
    }
  }

  def resolveViewerHandle(query: Map[String, String]): Option[PlayerHandle] =
    query.get("author").orElse(query.get("viewer"))
      .map(HandlePolicy.trim)
      .filter(HandlePolicy.isPlayableIdentityHandle)
      .flatMap(PlayerHandle.forLookup)

  private def pathSegments(path: String): Vector[String] =
    normalizePath(path).split('/').toVector.filter(_.nonEmpty)

  private def normalizePath(path: String): String =
    routePath(path).stripSuffix("/")

  private def routePath(path: String): String = {
    val raw = Option(path).getOrElse("/")
    if raw == "/api" then "/"
    else if raw.startsWith("/api/") then raw.stripPrefix("/api")
    else raw
  }

  private def decode(value: String): String =
    URLDecoder.decode(value, StandardCharsets.UTF_8)
}
