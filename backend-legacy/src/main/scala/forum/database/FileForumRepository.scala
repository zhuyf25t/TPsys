package slaydemo.backend.forum.database

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardCopyOption, StandardOpenOption}
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import scala.annotation.tailrec
import scala.jdk.CollectionConverters.*

import slaydemo.backend.forum.objects.{
  ForumReplyRecord,
  ForumReplyVoteRecord,
  ForumTopicRecord,
  ForumVoteChoice,
  ForumVoteRecord
}
import slaydemo.backend.forum.support.ForumJsonSupport
import slaydemo.backend.shared.objects.ThreadId

final class FileForumRepository(storagePath: Path) extends ForumRepository {
  private val lock = new Object
  private val topics = new ConcurrentHashMap[String, ForumTopicRecord]()
  private val replies = new ConcurrentHashMap[String, ForumReplyRecord]()
  private val votes = new ConcurrentHashMap[String, ForumVoteRecord]()
  private val replyVotes = new ConcurrentHashMap[String, ForumReplyVoteRecord]()

  loadFromDisk()

  override def listTopics(): Seq[ForumTopicRecord] = lock.synchronized {
    topics.values().asScala.toSeq.sortBy(_.updatedAt)(Ordering.Long.reverse)
  }

  override def findTopic(threadId: ThreadId): Option[ForumTopicRecord] = lock.synchronized {
    Option(topics.get(normalize(threadId.value)))
  }

  override def saveTopic(topic: ForumTopicRecord): ForumTopicRecord = lock.synchronized {
    topics.put(normalize(topic.threadId.value), topic)
    persist()
    topic
  }

  override def listReplies(threadId: ThreadId): Seq[ForumReplyRecord] = lock.synchronized {
    replies.values().asScala.toSeq
      .filter(reply => normalize(reply.threadId.value) == normalize(threadId.value))
      .sortBy(_.createdAt)
  }

  override def saveReply(reply: ForumReplyRecord): ForumReplyRecord = lock.synchronized {
    replies.put(normalize(reply.replyId), reply)
    persist()
    reply
  }

  override def listVotes(threadId: ThreadId): Seq[ForumVoteRecord] = lock.synchronized {
    votes.values().asScala.toSeq
      .filter(vote => normalize(vote.threadId.value) == normalize(threadId.value))
      .sortBy(vote => (vote.authorHandle.toLowerCase, vote.updatedAt))
  }

  override def upsertVote(threadId: ThreadId, authorHandle: String, vote: Option[ForumVoteChoice], updatedAt: Long): Boolean =
    lock.synchronized {
      val key = voteKey(threadId, authorHandle)
      val existing = Option(votes.get(key))

      vote match {
        case Some(choice) if existing.forall(current => current.vote != choice) =>
          votes.put(key, ForumVoteRecord(threadId, authorHandle, choice, updatedAt))
          persist()
          true
        case Some(_) =>
          false
        case None if existing.isDefined =>
          votes.remove(key)
          persist()
          true
        case None =>
          false
      }
    }

  override def listReplyVotes(replyId: String): Seq[ForumReplyVoteRecord] = lock.synchronized {
    val normalizedReplyId = normalize(replyId)
    replyVotes.values().asScala.toSeq
      .filter(vote => normalize(vote.replyId) == normalizedReplyId)
      .sortBy(vote => (vote.authorHandle.toLowerCase, vote.updatedAt))
  }

  override def upsertReplyVote(replyId: String, authorHandle: String, vote: Option[ForumVoteChoice], updatedAt: Long): Boolean =
    lock.synchronized {
      val normalizedReplyId = replyId.trim
      val key = replyVoteKey(normalizedReplyId, authorHandle)
      val existing = Option(replyVotes.get(key))

      vote match {
        case Some(choice) if existing.forall(current => current.vote != choice) =>
          replyVotes.put(key, ForumReplyVoteRecord(normalizedReplyId, authorHandle, choice, updatedAt))
          persist()
          true
        case Some(_) =>
          false
        case None if existing.isDefined =>
          replyVotes.remove(key)
          persist()
          true
        case None =>
          false
      }
    }

  private def loadFromDisk(): Unit = lock.synchronized {
    if (!Files.exists(storagePath)) {
      return
    }

    val raw = Files.readString(storagePath, StandardCharsets.UTF_8).trim
    if (raw.isEmpty) {
      return
    }

    extractSection(raw, "topics").flatMap(parseTopicRecord).foreach { topic =>
      topics.put(normalize(topic.threadId.value), topic)
    }

    extractSection(raw, "replies").flatMap(parseReplyRecord).foreach { reply =>
      replies.put(normalize(reply.replyId), reply)
    }

    extractSection(raw, "votes").flatMap(parseVoteRecord).foreach { vote =>
      votes.put(voteKey(vote.threadId, vote.authorHandle), vote)
    }

    extractSection(raw, "replyVotes").flatMap(parseReplyVoteRecord).foreach { vote =>
      replyVotes.put(replyVoteKey(vote.replyId, vote.authorHandle), vote)
    }
  }

  private def persist(): Unit = {
    try {
      val payload = renderPayload()
      Option(storagePath.getParent).foreach(path => Files.createDirectories(path))

      val tempPath = storagePath.resolveSibling(s"${storagePath.getFileName.toString}.tmp")
      Files.writeString(
        tempPath,
        payload,
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING,
        StandardOpenOption.WRITE
      )

      try {
        Files.move(tempPath, storagePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
      } catch {
        case _: java.nio.file.AtomicMoveNotSupportedException =>
          Files.move(tempPath, storagePath, StandardCopyOption.REPLACE_EXISTING)
      }
    } catch {
      case error: Throwable =>
        Console.err.println(s"[forum] failed to persist forum store at ${storagePath.toAbsolutePath}: ${error.getMessage}")
    }
  }

  private def renderPayload(): String = {
    val renderedTopics = topics.values().asScala.toSeq.sortBy(_.updatedAt)(Ordering.Long.reverse).map(renderTopic).mkString(",\n")
    val renderedReplies = replies.values().asScala.toSeq.sortBy(_.createdAt).map(renderReply).mkString(",\n")
    val renderedVotes = votes.values().asScala.toSeq.sortBy(vote => (vote.threadId.value.toLowerCase, vote.authorHandle.toLowerCase)).map(renderVote).mkString(",\n")
    val renderedReplyVotes = replyVotes
      .values()
      .asScala
      .toSeq
      .sortBy(vote => (vote.replyId.toLowerCase, vote.authorHandle.toLowerCase))
      .map(renderReplyVote)
      .mkString(",\n")

    s"""{
       |  "schema": "slay-demo.forum.v1",
       |  "topics": [
       |$renderedTopics
       |  ],
       |  "replies": [
       |$renderedReplies
       |  ],
       |  "votes": [
       |$renderedVotes
       |  ],
       |  "replyVotes": [
       |$renderedReplyVotes
       |  ]
       |}
       |""".stripMargin
  }

  private def renderTopic(topic: ForumTopicRecord): String = {
    s"""    {
       |      "threadId": "${encode(topic.threadId.value)}",
       |      "title": "${encode(topic.title)}",
       |      "body": "${encode(topic.body)}",
       |      "tag": "${encode(topic.tag)}",
       |      "authorHandle": "${encode(topic.authorHandle)}",
       |      "createdAt": ${topic.createdAt},
       |      "updatedAt": ${topic.updatedAt}
       |    }""".stripMargin
  }

  private def renderReply(reply: ForumReplyRecord): String = {
    s"""    {
       |      "replyId": "${encode(reply.replyId)}",
       |      "threadId": "${encode(reply.threadId.value)}",
       |      "authorHandle": "${encode(reply.authorHandle)}",
       |      "body": "${encode(reply.body)}",
       |      "createdAt": ${reply.createdAt}
       |    }""".stripMargin
  }

  private def renderVote(vote: ForumVoteRecord): String = {
    s"""    {
       |      "threadId": "${encode(vote.threadId.value)}",
       |      "authorHandle": "${encode(vote.authorHandle)}",
       |      "vote": "${vote.vote.value}",
       |      "updatedAt": ${vote.updatedAt}
       |    }""".stripMargin
  }

  private def renderReplyVote(vote: ForumReplyVoteRecord): String = {
    s"""    {
       |      "replyId": "${encode(vote.replyId)}",
       |      "authorHandle": "${encode(vote.authorHandle)}",
       |      "vote": "${vote.vote.value}",
       |      "updatedAt": ${vote.updatedAt}
       |    }""".stripMargin
  }

  private def extractSection(raw: String, field: String): Seq[String] = {
    val marker = raw.indexOf(s""""$field"""")
    if (marker < 0) {
      return Seq.empty
    }

    val start = raw.indexOf('[', marker)
    if (start < 0) {
      return Seq.empty
    }

    val end = findMatchingBracket(raw, start)
    if (end < 0 || end <= start) {
      return Seq.empty
    }

    val section = raw.substring(start + 1, end)
    "\\{([^{}]*)\\}".r.findAllMatchIn(section).map(_.group(1)).toSeq
  }

  private def findMatchingBracket(raw: String, start: Int): Int =
    scanBracket(raw, depth = 0, index = start)

  @tailrec
  private def scanBracket(raw: String, depth: Int, index: Int): Int =
    if (index >= raw.length) {
      -1
    } else {
      raw.charAt(index) match {
        case '[' =>
          scanBracket(raw, depth + 1, index + 1)
        case ']' =>
          val nextDepth = depth - 1
          if (nextDepth == 0) index else scanBracket(raw, nextDepth, index + 1)
        case _ =>
          scanBracket(raw, depth, index + 1)
      }
    }

  private def parseTopicRecord(chunk: String): Option[ForumTopicRecord] = {
    for {
      threadId <- decodeString(chunk, "threadId")
      title <- decodeString(chunk, "title")
      body <- decodeString(chunk, "body")
      tag <- decodeString(chunk, "tag")
      authorHandle <- decodeString(chunk, "authorHandle")
      createdAt <- ForumJsonSupport.extractLong(chunk, "createdAt")
      updatedAt <- ForumJsonSupport.extractLong(chunk, "updatedAt")
    } yield ForumTopicRecord(ThreadId(threadId), title, body, tag, authorHandle, createdAt, updatedAt)
  }

  private def parseReplyRecord(chunk: String): Option[ForumReplyRecord] = {
    for {
      replyId <- decodeString(chunk, "replyId")
      threadId <- decodeString(chunk, "threadId")
      authorHandle <- decodeString(chunk, "authorHandle")
      body <- decodeString(chunk, "body")
      createdAt <- ForumJsonSupport.extractLong(chunk, "createdAt")
    } yield ForumReplyRecord(replyId, ThreadId(threadId), authorHandle, body, createdAt)
  }

  private def parseVoteRecord(chunk: String): Option[ForumVoteRecord] = {
    for {
      threadId <- decodeString(chunk, "threadId")
      authorHandle <- decodeString(chunk, "authorHandle")
      voteValue <- ForumJsonSupport.extractString(chunk, "vote")
      vote <- ForumVoteChoice.fromString(voteValue)
      updatedAt <- ForumJsonSupport.extractLong(chunk, "updatedAt")
    } yield ForumVoteRecord(ThreadId(threadId), authorHandle, vote, updatedAt)
  }

  private def parseReplyVoteRecord(chunk: String): Option[ForumReplyVoteRecord] = {
    for {
      replyId <- decodeString(chunk, "replyId")
      authorHandle <- decodeString(chunk, "authorHandle")
      voteValue <- ForumJsonSupport.extractString(chunk, "vote")
      vote <- ForumVoteChoice.fromString(voteValue)
      updatedAt <- ForumJsonSupport.extractLong(chunk, "updatedAt")
    } yield ForumReplyVoteRecord(replyId, authorHandle, vote, updatedAt)
  }

  private def decodeString(raw: String, field: String): Option[String] = {
    ForumJsonSupport.extractString(raw, field).map(decode)
  }

  private def encode(value: String): String =
    Base64.getUrlEncoder.withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8))

  private def decode(value: String): String =
    new String(Base64.getUrlDecoder.decode(value), StandardCharsets.UTF_8)

  private def voteKey(threadId: ThreadId, authorHandle: String): String =
    s"${normalize(threadId.value)}::${authorHandle.trim.toLowerCase}"

  private def replyVoteKey(replyId: String, authorHandle: String): String =
    s"${normalize(replyId)}::${authorHandle.trim.toLowerCase}"

  private def normalize(value: String): String =
    value.trim.toLowerCase
}
