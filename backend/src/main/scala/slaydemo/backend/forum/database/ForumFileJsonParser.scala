package slaydemo.backend.forum.database

import java.nio.charset.StandardCharsets
import java.util.Base64

import slaydemo.backend.battle.objects.EpochMillis
import slaydemo.backend.forum.objects.{
  ForumBody,
  ForumReplyId,
  ForumReplyRecord,
  ForumTag,
  ForumTitle,
  ForumTopicId,
  ForumTopicRecord,
  ForumVoteChoice,
  ForumVoterKey,
  ForumVotes
}
import slaydemo.backend.identity.objects.PlayerHandle

private[database] object ForumFileJsonParser {
  def parseTopics(raw: String): Vector[ForumTopicRecord] =
    ForumFileJsonObjectScanner.extractArrayObjects(raw, "topics").flatMap(parseTopic)

  def parseReplies(raw: String): Vector[(ForumTopicId, ForumReplyRecord)] =
    ForumFileJsonObjectScanner.extractArrayObjects(raw, "replies").flatMap(parseReply)

  def parseTopicVotes(raw: String): Vector[(ForumTopicId, ForumVoterKey, ForumVoteChoice)] =
    ForumFileJsonObjectScanner.extractArrayObjects(raw, "votes").flatMap(parseTopicVote)

  def parseReplyVotes(raw: String): Vector[(ForumReplyId, ForumVoterKey, ForumVoteChoice)] =
    ForumFileJsonObjectScanner.extractArrayObjects(raw, "replyVotes").flatMap(parseReplyVote)

  private def parseTopic(chunk: String): Option[ForumTopicRecord] =
    for {
      topicId <- decodeStringField(chunk, "topicId").orElse(decodeStringField(chunk, "threadId"))
      title <- decodeStringField(chunk, "title")
      body <- decodeStringField(chunk, "body")
      tag <- decodeStringField(chunk, "tag")
      authorHandle <- decodeStringField(chunk, "authorHandle")
      createdAt <- extractLong(chunk, "createdAt")
      updatedAt <- extractLong(chunk, "updatedAt")
    } yield ForumTopicRecord(
      id = ForumTopicId(topicId),
      title = ForumTitle(title),
      body = ForumBody(body),
      tag = ForumTag(tag),
      authorHandle = PlayerHandle(authorHandle),
      createdAt = EpochMillis(createdAt),
      updatedAt = EpochMillis(updatedAt),
      replies = Vector.empty,
      votes = ForumVotes.empty
    )

  private def parseReply(chunk: String): Option[(ForumTopicId, ForumReplyRecord)] =
    for {
      replyId <- decodeStringField(chunk, "replyId")
      topicId <- decodeStringField(chunk, "topicId").orElse(decodeStringField(chunk, "threadId"))
      authorHandle <- decodeStringField(chunk, "authorHandle")
      body <- decodeStringField(chunk, "body")
      createdAt <- extractLong(chunk, "createdAt")
    } yield ForumTopicId(topicId) -> ForumReplyRecord(
      id = ForumReplyId(replyId),
      authorHandle = PlayerHandle(authorHandle),
      body = ForumBody(body),
      createdAt = EpochMillis(createdAt),
      votes = ForumVotes.empty
    )

  private def parseTopicVote(chunk: String): Option[(ForumTopicId, ForumVoterKey, ForumVoteChoice)] =
    for {
      topicId <- decodeStringField(chunk, "topicId").orElse(decodeStringField(chunk, "threadId"))
      authorHandle <- decodeStringField(chunk, "authorHandle")
      voteText <- extractString(chunk, "vote")
      vote <- ForumVoteChoice.fromWire(voteText)
    } yield (ForumTopicId(topicId), ForumVoterKey(authorHandle.toLowerCase), vote)

  private def parseReplyVote(chunk: String): Option[(ForumReplyId, ForumVoterKey, ForumVoteChoice)] =
    for {
      replyId <- decodeStringField(chunk, "replyId")
      authorHandle <- decodeStringField(chunk, "authorHandle")
      voteText <- extractString(chunk, "vote")
      vote <- ForumVoteChoice.fromWire(voteText)
    } yield (ForumReplyId(replyId), ForumVoterKey(authorHandle.toLowerCase), vote)

  private def decodeStringField(raw: String, field: String): Option[String] =
    extractString(raw, field).flatMap(decode)

  private def extractString(raw: String, field: String): Option[String] = {
    val pattern = s""""$field"\\s*:\\s*"((?:\\\\.|[^"\\\\])*)"""".r
    pattern.findFirstMatchIn(raw).map(_.group(1))
  }

  private def extractLong(raw: String, field: String): Option[Long] = {
    val pattern = s""""$field"\\s*:\\s*(-?\\d+)""".r
    pattern.findFirstMatchIn(raw).map(_.group(1).toLong)
  }

  private def decode(value: String): Option[String] =
    try Some(new String(Base64.getUrlDecoder.decode(value), StandardCharsets.UTF_8))
    catch {
      case _: IllegalArgumentException => None
    }
}
