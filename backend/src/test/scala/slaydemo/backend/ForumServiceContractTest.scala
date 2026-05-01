package slaydemo.backend

import slaydemo.backend.forum.database.InMemoryForumRepository
import slaydemo.backend.forum.objects.{
  ForumBody,
  ForumReplyId,
  ForumScore,
  ForumTag,
  ForumTitle,
  ForumTopicId,
  ForumVoteChoice
}
import slaydemo.backend.forum.services.{
  AddForumReplyCommand,
  CreateForumTopicCommand,
  DefaultForumService,
  ForumTopicMutationError,
  SetForumReplyVoteCommand,
  SetForumTopicVoteCommand
}
import slaydemo.backend.identity.objects.PlayerHandle

object ForumServiceContractTest {
  def main(args: Array[String]): Unit = {
    createReplyAndVoteFlow()
    missingTopicAndReplyErrorsAreExplicit()

    println("Forum service contract checks passed")
  }

  private def createReplyAndVoteFlow(): Unit = {
    var now = 1_000L
    val service = DefaultForumService(InMemoryForumRepository(), () => now)
    val topic = service.createTopic(
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

  private def missingTopicAndReplyErrorsAreExplicit(): Unit = {
    val service = DefaultForumService(InMemoryForumRepository(), () => 1_000L)
    val topic = service.createTopic(
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

  private def assertEquals[A](label: String, actual: A, expected: A): Unit =
    assert(actual == expected, s"$label: expected $expected, got $actual")

  private def fail(message: String): Nothing =
    throw AssertionError(message)
}
