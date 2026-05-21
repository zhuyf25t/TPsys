package slaydemo.backend.http4s

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.http4s.implicits.uri
import org.http4s.{Method, Request}

import slaydemo.backend.battle.objects.EpochMillis
import slaydemo.backend.forum.objects.{
  ForumBody,
  ForumReplyCount,
  ForumReplyId,
  ForumReplyView,
  ForumScore,
  ForumTag,
  ForumTitle,
  ForumTopicId,
  ForumTopicView,
  ForumVoteChoice
}
import slaydemo.backend.forum.services.{
  AddForumReplyCommand,
  CreateForumTopicCommand,
  ForumCreateTopicError,
  ForumService,
  ForumTopicMutationError,
  SetForumReplyVoteCommand,
  SetForumTopicVoteCommand
}
import slaydemo.backend.identity.objects.PlayerHandle

object ForumHttp4sContractTest {
  def main(args: Array[String]): Unit = {
    listAndDetailResolveViewerAndRenderVotes()
    createTopicParsesAndValidatesRequest()
    replyAndVoteMutationsReachService()
    mutationErrorsAreMapped()

    println("Forum http4s contract checks passed")
  }

  private def listAndDetailResolveViewerAndRenderVotes(): Unit = {
    val service = RecordingForumService()
    service.topics = Vector(topicView(viewerVote = Some(ForumVoteChoice.Up), score = ForumScore(1)))
    service.loadedTopicsById = Map(
      ForumTopicId("topic-route") -> topicView(
        viewerVote = Some(ForumVoteChoice.Down),
        score = ForumScore(-1),
        replyItems = Vector(replyView(viewerVote = Some(ForumVoteChoice.Up), score = ForumScore(1)))
      )
    )

    val list = run(service, Request[IO](method = Method.GET, uri = uri"/api/forum/topics?viewer=Alice"))
    val detail = run(service, Request[IO](method = Method.GET, uri = uri"/forum/topics/topic-route?author=Bob"))

    assertEquals("list status", list.status, 200)
    assertContains("list contains topics", list.body, """"topics":[""")
    assertContains("list viewer vote", list.body, """"viewerVote":"up"""")
    assertEquals("list viewer", service.listViewerCalls, Vector(Some(PlayerHandle("Alice"))))

    assertEquals("detail status", detail.status, 200)
    assertContains("detail topic wrapper", detail.body, """"topic":{""")
    assertContains("detail topic id", detail.body, """"id":"topic-route"""")
    assertContains("detail viewer vote", detail.body, """"viewerVote":"down"""")
    assertContains("detail reply viewer vote", detail.body, """"viewerVote":"up"""")
    assertEquals("detail load calls", service.loadCalls, Vector(ForumTopicId("topic-route") -> Some(PlayerHandle("Bob"))))
  }

  private def createTopicParsesAndValidatesRequest(): Unit = {
    val service = RecordingForumService()

    val success = run(
      service,
      Request[IO](method = Method.POST, uri = uri"/api/forum/topics")
        .withEntity("""{"title":" Patch notes ","body":" Battle queue is live. ","tag":" backend ","author":"Alice"}""")
    )
    val visitor = run(
      service,
      Request[IO](method = Method.POST, uri = uri"/forum/topics")
        .withEntity("""{"title":"Patch notes","body":"Body","tag":"backend","author":"visitor"}""")
    )
    val invalidBody = run(
      service,
      Request[IO](method = Method.POST, uri = uri"/forum/topics")
        .withEntity("""{"title":"Patch notes","body":" ","tag":"backend","author":"Alice"}""")
    )
    val nonStringField = run(
      service,
      Request[IO](method = Method.POST, uri = uri"/forum/topics")
        .withEntity("""{"title":"Patch notes","body":"Body","tag":"backend","author":"Alice","pinned":true}""")
    )

    assertEquals("create status", success.status, 201)
    assertContains("create response title", success.body, """"title":"Patch notes"""")
    assertEquals("create command count", service.createCommands.length, 1)
    val command = service.createCommands.head
    assertEquals("create command title trim", command.title, ForumTitle("Patch notes"))
    assertEquals("create command body trim", command.body, ForumBody("Battle queue is live."))
    assertEquals("create command tag trim", command.tag, ForumTag("backend"))
    assertEquals("create command author", command.authorHandle, PlayerHandle("Alice"))

    assertEquals("visitor status", visitor.status, 403)
    assertContains("visitor code", visitor.body, """"code":"visitor_not_allowed"""")
    assertEquals("invalid body status", invalidBody.status, 400)
    assertContains("invalid body code", invalidBody.body, """"code":"invalid_body"""")
    assertEquals("non-string field status", nonStringField.status, 400)
    assertEquals(
      "non-string field body",
      nonStringField.body,
      """{"error":"Request body must be a JSON object with string fields.","code":"bad_request"}"""
    )
    assertEquals("invalid creates do not call service", service.createCommands.length, 1)

    val failingService = RecordingForumService()
    failingService.createResults = Vector(Left(ForumCreateTopicError.VisitorNotAllowed))
    val serviceFailure = run(
      failingService,
      Request[IO](method = Method.POST, uri = uri"/forum/topics")
        .withEntity("""{"title":"Patch notes","body":"Body","tag":"backend","author":"Alice"}""")
    )

    assertEquals("service create error status", serviceFailure.status, 403)
    assertContains("service create error code", serviceFailure.body, """"code":"visitor_not_allowed"""")
  }

  private def replyAndVoteMutationsReachService(): Unit = {
    val service = RecordingForumService()

    val reply = run(
      service,
      Request[IO](method = Method.POST, uri = uri"/forum/topics/topic-route/replies")
        .withEntity("""{"body":" Confirmed. ","author":"Bob"}""")
    )
    val topicVote = run(
      service,
      Request[IO](method = Method.POST, uri = uri"/api/forum/topics/topic-route/votes")
        .withEntity("""{"author":"Alice","vote":"up"}""")
    )
    val clearTopicVote = run(
      service,
      Request[IO](method = Method.POST, uri = uri"/forum/topics/topic-route/votes")
        .withEntity("""{"author":"Alice","vote":null}""")
    )
    val replyVote = run(
      service,
      Request[IO](method = Method.POST, uri = uri"/forum/topics/topic-route/replies/reply-route/votes")
        .withEntity("""{"author":"Alice","vote":"down"}""")
    )

    assertEquals("reply status", reply.status, 200)
    assertContains("reply response body", reply.body, """"body":"Confirmed."""")
    assertEquals("reply command count", service.addReplyCommands.length, 1)
    assertEquals("reply topic id", service.addReplyCommands.head.topicId, ForumTopicId("topic-route"))
    assertEquals("reply body trim", service.addReplyCommands.head.body, ForumBody("Confirmed."))
    assertEquals("reply author", service.addReplyCommands.head.authorHandle, PlayerHandle("Bob"))

    assertEquals("topic vote status", topicVote.status, 200)
    assertEquals("clear topic vote status", clearTopicVote.status, 200)
    assertEquals("topic vote commands", service.topicVoteCommands.map(_.vote), Vector(Some(ForumVoteChoice.Up), None))
    assertContains("clear vote renders null", clearTopicVote.body, """"viewerVote":null""")

    assertEquals("reply vote status", replyVote.status, 200)
    assertEquals("reply vote command count", service.replyVoteCommands.length, 1)
    assertEquals("reply vote topic id", service.replyVoteCommands.head.topicId, ForumTopicId("topic-route"))
    assertEquals("reply vote reply id", service.replyVoteCommands.head.replyId, ForumReplyId("reply-route"))
    assertEquals("reply vote choice", service.replyVoteCommands.head.vote, Some(ForumVoteChoice.Down))
  }

  private def mutationErrorsAreMapped(): Unit = {
    val service = RecordingForumService()
    service.addReplyResults = Vector(Left(ForumTopicMutationError.TopicNotFound))
    service.replyVoteResults = Vector(Left(ForumTopicMutationError.ReplyNotFound))

    val missingTopic = run(
      service,
      Request[IO](method = Method.POST, uri = uri"/forum/topics/missing/replies")
        .withEntity("""{"body":"Reply","author":"Alice"}""")
    )
    val invalidVote = run(
      service,
      Request[IO](method = Method.POST, uri = uri"/forum/topics/topic-route/votes")
        .withEntity("""{"author":"Alice","vote":"sideways"}""")
    )
    val missingReply = run(
      service,
      Request[IO](method = Method.POST, uri = uri"/forum/topics/topic-route/replies/missing/votes")
        .withEntity("""{"author":"Alice","vote":"up"}""")
    )

    assertEquals("missing topic status", missingTopic.status, 404)
    assertContains("missing topic code", missingTopic.body, """"code":"topic_not_found"""")
    assertEquals("invalid vote status", invalidVote.status, 400)
    assertContains("invalid vote code", invalidVote.body, """"code":"invalid_vote"""")
    assertEquals("invalid vote does not call service", service.topicVoteCommands, Vector.empty)
    assertEquals("missing reply status", missingReply.status, 404)
    assertContains("missing reply code", missingReply.body, """"code":"reply_not_found"""")
  }

  private def run(service: ForumService, request: Request[IO]): RouteResponse = {
    val response = BackendHttp4sRoutes.forumRoutes(service).orNotFound.run(request).unsafeRunSync()
    RouteResponse(response.status.code, response.as[String].unsafeRunSync())
  }

  private final case class RouteResponse(status: Int, body: String)

  private final class RecordingForumService extends ForumService {
    var topics: Vector[ForumTopicView] = Vector(topicView())
    var loadedTopicsById: Map[ForumTopicId, ForumTopicView] = Map(ForumTopicId("topic-route") -> topicView())
    var createResults: Vector[Either[ForumCreateTopicError, ForumTopicView]] = Vector.empty
    var addReplyResults: Vector[Either[ForumTopicMutationError, ForumTopicView]] = Vector.empty
    var topicVoteResults: Vector[Either[ForumTopicMutationError, ForumTopicView]] = Vector.empty
    var replyVoteResults: Vector[Either[ForumTopicMutationError, ForumTopicView]] = Vector.empty
    var listViewerCalls: Vector[Option[PlayerHandle]] = Vector.empty
    var loadCalls: Vector[(ForumTopicId, Option[PlayerHandle])] = Vector.empty
    var createCommands: Vector[CreateForumTopicCommand] = Vector.empty
    var addReplyCommands: Vector[AddForumReplyCommand] = Vector.empty
    var topicVoteCommands: Vector[SetForumTopicVoteCommand] = Vector.empty
    var replyVoteCommands: Vector[SetForumReplyVoteCommand] = Vector.empty

    override def listTopics(viewerHandle: Option[PlayerHandle]): Vector[ForumTopicView] = {
      listViewerCalls = listViewerCalls :+ viewerHandle
      topics
    }

    override def loadTopic(topicId: ForumTopicId, viewerHandle: Option[PlayerHandle]): Option[ForumTopicView] = {
      loadCalls = loadCalls :+ (topicId -> viewerHandle)
      loadedTopicsById.get(topicId)
    }

    override def createTopic(command: CreateForumTopicCommand): Either[ForumCreateTopicError, ForumTopicView] = {
      createCommands = createCommands :+ command
      takeCreateResult(
        createResults,
        remaining => createResults = remaining,
        Right(
          topicView(
            title = command.title,
            author = command.authorHandle,
            body = command.body,
            tag = command.tag,
            replies = ForumReplyCount(0),
            replyItems = Vector.empty,
            viewerVote = None,
            score = ForumScore(0)
          )
        )
      )
    }

    override def addReply(command: AddForumReplyCommand): Either[ForumTopicMutationError, ForumTopicView] = {
      addReplyCommands = addReplyCommands :+ command
      takeResult(
        addReplyResults,
        remaining => addReplyResults = remaining,
        Right(topicView(replyItems = Vector(replyView(author = command.authorHandle, body = command.body)), replies = ForumReplyCount(1)))
      )
    }

    override def setTopicVote(command: SetForumTopicVoteCommand): Either[ForumTopicMutationError, ForumTopicView] = {
      topicVoteCommands = topicVoteCommands :+ command
      takeResult(
        topicVoteResults,
        remaining => topicVoteResults = remaining,
        Right(topicView(viewerVote = command.vote, score = scoreFor(command.vote)))
      )
    }

    override def setReplyVote(command: SetForumReplyVoteCommand): Either[ForumTopicMutationError, ForumTopicView] = {
      replyVoteCommands = replyVoteCommands :+ command
      takeResult(
        replyVoteResults,
        remaining => replyVoteResults = remaining,
        Right(topicView(replyItems = Vector(replyView(viewerVote = command.vote, score = scoreFor(command.vote)))))
      )
    }

    private def takeResult(
      results: Vector[Either[ForumTopicMutationError, ForumTopicView]],
      saveRemaining: Vector[Either[ForumTopicMutationError, ForumTopicView]] => Unit,
      default: Either[ForumTopicMutationError, ForumTopicView]
    ): Either[ForumTopicMutationError, ForumTopicView] =
      results match {
        case head +: tail =>
          saveRemaining(tail)
          head
        case _ =>
          default
      }

    private def takeCreateResult(
      results: Vector[Either[ForumCreateTopicError, ForumTopicView]],
      saveRemaining: Vector[Either[ForumCreateTopicError, ForumTopicView]] => Unit,
      default: Either[ForumCreateTopicError, ForumTopicView]
    ): Either[ForumCreateTopicError, ForumTopicView] =
      results match {
        case head +: tail =>
          saveRemaining(tail)
          head
        case _ =>
          default
      }
  }

  private def topicView(
    id: ForumTopicId = ForumTopicId("topic-route"),
    title: ForumTitle = ForumTitle("Route Topic"),
    author: PlayerHandle = PlayerHandle("Alice"),
    body: ForumBody = ForumBody("Route body"),
    tag: ForumTag = ForumTag("backend"),
    replies: ForumReplyCount = ForumReplyCount(1),
    updatedAt: EpochMillis = EpochMillis(2_000L),
    createdAt: EpochMillis = EpochMillis(1_000L),
    replyItems: Vector[ForumReplyView] = Vector(replyView()),
    viewerVote: Option[ForumVoteChoice] = None,
    score: ForumScore = ForumScore(0)
  ): ForumTopicView =
    ForumTopicView(
      id = id,
      title = title,
      author = author,
      excerpt = body.value,
      tag = tag,
      replies = replies,
      updatedAt = updatedAt,
      createdAt = createdAt,
      body = body,
      replyItems = replyItems,
      viewerVote = viewerVote,
      score = score
    )

  private def replyView(
    id: ForumReplyId = ForumReplyId("reply-route"),
    author: PlayerHandle = PlayerHandle("Bob"),
    body: ForumBody = ForumBody("Reply body"),
    publishedAt: EpochMillis = EpochMillis(1_500L),
    viewerVote: Option[ForumVoteChoice] = None,
    score: ForumScore = ForumScore(0)
  ): ForumReplyView =
    ForumReplyView(
      id = id,
      author = author,
      body = body,
      publishedAt = publishedAt,
      viewerVote = viewerVote,
      score = score
    )

  private def scoreFor(vote: Option[ForumVoteChoice]): ForumScore =
    vote match {
      case Some(ForumVoteChoice.Up)   => ForumScore(1)
      case Some(ForumVoteChoice.Down) => ForumScore(-1)
      case None                       => ForumScore(0)
    }

  private def assertEquals[A](label: String, actual: A, expected: A): Unit =
    assert(actual == expected, s"$label: expected $expected, got $actual")

  private def assertContains(label: String, actual: String, expectedSubstring: String): Unit =
    assert(actual.contains(expectedSubstring), s"$label: expected body to contain $expectedSubstring, got $actual")
}
