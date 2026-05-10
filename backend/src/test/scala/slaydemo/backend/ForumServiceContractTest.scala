package slaydemo.backend

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

import slaydemo.backend.battle.objects.EpochMillis
import slaydemo.backend.forum.database.{FileForumRepository, InMemoryForumRepository}
import slaydemo.backend.forum.database.{ForumRepository, ForumVoteMutationError}
import slaydemo.backend.forum.objects.{
  ForumBody,
  ForumReplyCount,
  ForumReplyId,
  ForumReplyRecord,
  ForumScore,
  ForumTag,
  ForumTitle,
  ForumTopicId,
  ForumTopicRecord,
  ForumTopicView,
  ForumVoteChoice
}
import slaydemo.backend.forum.services.{
  AddForumReplyCommand,
  CreateForumTopicCommand,
  DefaultForumService,
  ForumCreateTopicError,
  ForumTopicMutationError,
  SetForumReplyVoteCommand,
  SetForumTopicVoteCommand
}
import slaydemo.backend.identity.objects.PlayerHandle

object ForumServiceContractTest {
  def main(args: Array[String]): Unit = {
    createReplyAndVoteFlow()
    createTopicErrorsAreExplicit()
    voteMutationsUseRepositoryVoteBoundary()
    missingTopicAndReplyErrorsAreExplicit()
    fileRepositoryPersistsTopicsRepliesVotesAndIds()

    println("Forum service contract checks passed")
  }

  private def createReplyAndVoteFlow(): Unit = {
    var now = 1_000L
    val service = DefaultForumService(InMemoryForumRepository(), () => now)
    val topic = createTopicOrFail(service,
      CreateForumTopicCommand(
        title = ForumTitle("Patch notes"),
        body = ForumBody("Battle queue improvements are live."),
        tag = ForumTag("backend"),
        authorHandle = PlayerHandle("Alice")
      )
    )

    assertEquals("created topic id", topic.id, ForumTopicId("topic-000000000001"))
    assertEquals("created topic author", topic.author, PlayerHandle("Alice"))
    assertEquals("created topic score", topic.score, ForumScore(0))
    assertEquals("created topic viewer vote", topic.viewerVote, None)

    now = 2_000L
    val withReply = service
      .addReply(
        AddForumReplyCommand(
          topicId = topic.id,
          body = ForumBody("Confirmed."),
          authorHandle = PlayerHandle("Bob")
        )
      )
      .fold(error => fail(s"add reply failed: $error"), view => view)

    assertEquals("reply count", withReply.replies.value, 1)
    assertEquals("reply author", withReply.replyItems.map(_.author), Vector(PlayerHandle("Bob")))

    now = 3_000L
    val upvoted = service
      .setTopicVote(SetForumTopicVoteCommand(topic.id, PlayerHandle("Alice"), Some(ForumVoteChoice.Up)))
      .fold(error => fail(s"topic upvote failed: $error"), view => view)
    assertEquals("topic upvote score", upvoted.score, ForumScore(1))
    assertEquals("topic upvote viewer vote", upvoted.viewerVote, Some(ForumVoteChoice.Up))

    now = 4_000L
    val downvoted = service
      .setTopicVote(SetForumTopicVoteCommand(topic.id, PlayerHandle("Alice"), Some(ForumVoteChoice.Down)))
      .fold(error => fail(s"topic downvote failed: $error"), view => view)
    assertEquals("topic downvote replaces prior vote", downvoted.score, ForumScore(-1))
    assertEquals("topic downvote viewer vote", downvoted.viewerVote, Some(ForumVoteChoice.Down))

    now = 5_000L
    val cleared = service
      .setTopicVote(SetForumTopicVoteCommand(topic.id, PlayerHandle("Alice"), None))
      .fold(error => fail(s"topic clear vote failed: $error"), view => view)
    assertEquals("topic vote clear score", cleared.score, ForumScore(0))
    assertEquals("topic vote clear viewer vote", cleared.viewerVote, None)

    now = 6_000L
    val replyVoted = service
      .setReplyVote(
        SetForumReplyVoteCommand(
          topicId = topic.id,
          replyId = withReply.replyItems.head.id,
          authorHandle = PlayerHandle("Alice"),
          vote = Some(ForumVoteChoice.Up)
        )
      )
      .fold(error => fail(s"reply vote failed: $error"), view => view)
    assertEquals("reply vote score", replyVoted.replyItems.head.score, ForumScore(1))
    assertEquals("reply vote viewer vote", replyVoted.replyItems.head.viewerVote, Some(ForumVoteChoice.Up))
  }

  private def createTopicErrorsAreExplicit(): Unit = {
    val service = DefaultForumService(InMemoryForumRepository(), () => 1_000L)

    assertEquals(
      "blank title create topic error",
      service.createTopic(
        CreateForumTopicCommand(
          title = ForumTitle(" "),
          body = ForumBody("Body"),
          tag = ForumTag("general"),
          authorHandle = PlayerHandle("Alice")
        )
      ),
      Left(ForumCreateTopicError.InvalidTitle)
    )
    assertEquals(
      "visitor create topic error",
      service.createTopic(
        CreateForumTopicCommand(
          title = ForumTitle("Topic"),
          body = ForumBody("Body"),
          tag = ForumTag("general"),
          authorHandle = PlayerHandle("visitor")
        )
      ),
      Left(ForumCreateTopicError.VisitorNotAllowed)
    )
    assertEquals("invalid creates are not persisted", service.listTopics(None), Vector.empty)
  }

  private def voteMutationsUseRepositoryVoteBoundary(): Unit = {
    val repository = RecordingVoteRepository()
    val service = DefaultForumService(repository, () => 10_000L)

    val topicVote = service
      .setTopicVote(
        SetForumTopicVoteCommand(repository.topicId, PlayerHandle("Alice"), Some(ForumVoteChoice.Up))
      )
      .fold(error => fail(s"recorded topic vote failed: $error"), view => view)

    assertEquals("topic vote boundary score", topicVote.score, ForumScore(1))
    assertEquals("topic vote boundary viewer vote", topicVote.viewerVote, Some(ForumVoteChoice.Up))
    assertEquals("topic vote calls repository vote method", repository.topicVoteCalls, Vector(PlayerHandle("Alice") -> Some(ForumVoteChoice.Up)))
    assertEquals("topic vote does not save whole topic", repository.saveTopicCalls, 0)

    val replyVote = service
      .setReplyVote(
        SetForumReplyVoteCommand(repository.topicId, repository.replyId, PlayerHandle("Bob"), Some(ForumVoteChoice.Down))
      )
      .fold(error => fail(s"recorded reply vote failed: $error"), view => view)

    assertEquals("reply vote boundary reply count", replyVote.replies, ForumReplyCount(1))
    assertEquals("reply vote boundary score", replyVote.replyItems.head.score, ForumScore(-1))
    assertEquals("reply vote boundary viewer vote", replyVote.replyItems.head.viewerVote, Some(ForumVoteChoice.Down))
    assertEquals("reply vote calls repository vote method", repository.replyVoteCalls, Vector(PlayerHandle("Bob") -> Some(ForumVoteChoice.Down)))
    assertEquals("reply vote does not save whole topic", repository.saveTopicCalls, 0)
  }

  private def missingTopicAndReplyErrorsAreExplicit(): Unit = {
    val service = DefaultForumService(InMemoryForumRepository(), () => 1_000L)
    val topic = createTopicOrFail(service,
      CreateForumTopicCommand(
        title = ForumTitle("Topic"),
        body = ForumBody("Body"),
        tag = ForumTag("general"),
        authorHandle = PlayerHandle("Alice")
      )
    )

    assertEquals(
      "add reply missing topic",
      service.addReply(AddForumReplyCommand(ForumTopicId("missing"), ForumBody("Body"), PlayerHandle("Bob"))),
      Left(ForumTopicMutationError.TopicNotFound)
    )
    assertEquals(
      "topic vote missing topic",
      service.setTopicVote(SetForumTopicVoteCommand(ForumTopicId("missing"), PlayerHandle("Alice"), Some(ForumVoteChoice.Up))),
      Left(ForumTopicMutationError.TopicNotFound)
    )
    assertEquals(
      "reply vote missing topic",
      service.setReplyVote(
        SetForumReplyVoteCommand(ForumTopicId("missing"), ForumReplyId("missing"), PlayerHandle("Alice"), Some(ForumVoteChoice.Up))
      ),
      Left(ForumTopicMutationError.TopicNotFound)
    )
    assertEquals(
      "reply vote missing reply",
      service.setReplyVote(
        SetForumReplyVoteCommand(topic.id, ForumReplyId("missing"), PlayerHandle("Alice"), Some(ForumVoteChoice.Up))
      ),
      Left(ForumTopicMutationError.ReplyNotFound)
    )
  }

  private def fileRepositoryPersistsTopicsRepliesVotesAndIds(): Unit = {
    val directory = Files.createTempDirectory("slay-demo-forum-file-contract")
    try {
      val storagePath = directory.resolve("forum.json")
      var now = 1_000L
      val service = DefaultForumService(FileForumRepository(storagePath), () => now)
      val topic = createTopicOrFail(service,
        CreateForumTopicCommand(
          title = ForumTitle("File topic"),
          body = ForumBody("Body with {braces} and \"quotes\""),
          tag = ForumTag("storage"),
          authorHandle = PlayerHandle("Alice")
        )
      )

      now = 2_000L
      val withReply = service
        .addReply(AddForumReplyCommand(topic.id, ForumBody("Reply body"), PlayerHandle("Bob")))
        .fold(error => fail(s"file forum add reply failed: $error"), value => value)

      now = 3_000L
      service
        .setTopicVote(SetForumTopicVoteCommand(topic.id, PlayerHandle("Alice"), Some(ForumVoteChoice.Up)))
        .fold(error => fail(s"file forum topic vote failed: $error"), value => value)
      now = 4_000L
      service
        .setTopicVote(SetForumTopicVoteCommand(topic.id, PlayerHandle("Bob"), Some(ForumVoteChoice.Down)))
        .fold(error => fail(s"file forum second topic vote failed: $error"), value => value)
      now = 5_000L
      service
        .setReplyVote(
          SetForumReplyVoteCommand(topic.id, withReply.replyItems.head.id, PlayerHandle("Alice"), Some(ForumVoteChoice.Up))
        )
        .fold(error => fail(s"file forum reply vote failed: $error"), value => value)

      val reloadedRepository = FileForumRepository(storagePath)
      val reloadedService = DefaultForumService(reloadedRepository, () => 6_000L)
      val loaded = reloadedService.loadTopic(topic.id, Some(PlayerHandle("Alice"))).getOrElse(fail("missing file forum topic"))

      assertEquals("file forum topic id persists", loaded.id, topic.id)
      assertEquals("file forum body round trips", loaded.body, ForumBody("Body with {braces} and \"quotes\""))
      assertEquals("file forum reply persists", loaded.replyItems.map(_.body), Vector(ForumBody("Reply body")))
      assertEquals("file forum topic votes persist", loaded.score, ForumScore(0))
      assertEquals("file forum viewer topic vote persists", loaded.viewerVote, Some(ForumVoteChoice.Up))
      assertEquals("file forum reply vote persists", loaded.replyItems.head.score, ForumScore(1))
      assertEquals("file forum reply viewer vote persists", loaded.replyItems.head.viewerVote, Some(ForumVoteChoice.Up))
      assertEquals(
        "file forum missing reply remains explicit",
        reloadedService.setReplyVote(
          SetForumReplyVoteCommand(topic.id, ForumReplyId("missing"), PlayerHandle("Alice"), Some(ForumVoteChoice.Up))
        ),
        Left(ForumTopicMutationError.ReplyNotFound)
      )
      assertEquals("file forum next topic id advances", reloadedRepository.nextTopicId(), ForumTopicId("topic-000000000002"))
      assertEquals("file forum next reply id advances", reloadedRepository.nextReplyId(), ForumReplyId("reply-000000000002"))
    } finally {
      deleteRecursively(directory)
    }
  }

  private def createTopicOrFail(service: DefaultForumService, command: CreateForumTopicCommand): ForumTopicView =
    service.createTopic(command).fold(error => fail(s"create topic failed: $error"), value => value)

  private def assertEquals[A](label: String, actual: A, expected: A): Unit =
    assert(actual == expected, s"$label: expected $expected, got $actual")

  private def fail(message: String): Nothing =
    throw AssertionError(message)

  private def deleteRecursively(path: Path): Unit =
    if Files.exists(path) then {
      val stream = Files.walk(path)
      try {
        stream
          .iterator()
          .asScala
          .toVector
          .sortBy(_.toString.length)
          .reverse
          .foreach(Files.deleteIfExists)
      } finally {
        stream.close()
      }
    }

  private final class RecordingVoteRepository extends ForumRepository {
    val topicId: ForumTopicId = ForumTopicId("topic-recording")
    val replyId: ForumReplyId = ForumReplyId("reply-recording")
    var saveTopicCalls: Int = 0
    var topicVoteCalls: Vector[(PlayerHandle, Option[ForumVoteChoice])] = Vector.empty
    var replyVoteCalls: Vector[(PlayerHandle, Option[ForumVoteChoice])] = Vector.empty
    private var topic: ForumTopicRecord =
      ForumTopicRecord
        .create(
          id = topicId,
          title = ForumTitle("Recorded topic"),
          body = ForumBody("Body"),
          tag = ForumTag("test"),
          authorHandle = PlayerHandle("Author"),
          createdAt = EpochMillis(1_000L)
        )
        .copy(
          replies = Vector(
            ForumReplyRecord.create(
              id = replyId,
              authorHandle = PlayerHandle("ReplyAuthor"),
              body = ForumBody("Reply"),
              createdAt = EpochMillis(1_500L)
            )
          )
        )

    override def nextTopicId(): ForumTopicId =
      ForumTopicId("unused-topic-id")

    override def nextReplyId(): ForumReplyId =
      ForumReplyId("unused-reply-id")

    override def listTopics(): Vector[ForumTopicRecord] =
      Vector(topic)

    override def findTopic(topicId: ForumTopicId): Option[ForumTopicRecord] =
      Option.when(topicId == this.topicId)(topic)

    override def saveTopic(topic: ForumTopicRecord): ForumTopicRecord = {
      saveTopicCalls += 1
      this.topic = topic
      topic
    }

    override def setTopicVote(
      topicId: ForumTopicId,
      authorHandle: PlayerHandle,
      vote: Option[ForumVoteChoice],
      updatedAt: EpochMillis
    ): Either[ForumVoteMutationError, ForumTopicRecord] =
      if topicId != this.topicId then Left(ForumVoteMutationError.TopicNotFound)
      else {
        topicVoteCalls = topicVoteCalls :+ (authorHandle -> vote)
        topic = ForumTopicRecord.setVote(topic, authorHandle, vote, updatedAt)
        Right(topic)
      }

    override def setReplyVote(
      topicId: ForumTopicId,
      replyId: ForumReplyId,
      authorHandle: PlayerHandle,
      vote: Option[ForumVoteChoice],
      updatedAt: EpochMillis
    ): Either[ForumVoteMutationError, ForumTopicRecord] =
      if topicId != this.topicId then Left(ForumVoteMutationError.TopicNotFound)
      else {
        ForumTopicRecord.setReplyVote(topic, replyId, authorHandle, vote, updatedAt) match {
          case Left(_) =>
            Left(ForumVoteMutationError.ReplyNotFound)
          case Right(updated) =>
            replyVoteCalls = replyVoteCalls :+ (authorHandle -> vote)
            topic = updated
            Right(topic)
        }
      }
  }
}
