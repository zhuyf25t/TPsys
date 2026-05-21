package slaydemo.backend.http4s

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.http4s.implicits.uri
import org.http4s.{Method, Request}

import slaydemo.backend.forum.services.{ForumService, InMemoryForumService}

object ForumHttp4sContractTest {
  def main(args: Array[String]): Unit = {
    createListDetailReplyAndVoteRoundTrip()
    validationAndMissingTargetsAreMapped()

    println("Forum http4s contract checks passed")
  }

  private def createListDetailReplyAndVoteRoundTrip(): Unit = {
    val service = InMemoryForumService()
    val created = run(
      service,
      Request[IO](method = Method.POST, uri = uri"/api/forum/topics")
        .withEntity("""{"title":" Patch notes ","body":" Battle queue is live. ","tag":" backend ","author":"Alice"}""")
    )
    val list = run(service, Request[IO](method = Method.GET, uri = uri"/forum/topics?viewer=Alice"))
    val detail = run(service, Request[IO](method = Method.GET, uri = uri"/api/forum/topics/topic-000000000001?author=Alice"))
    val reply = run(
      service,
      Request[IO](method = Method.POST, uri = uri"/forum/topics/topic-000000000001/replies")
        .withEntity("""{"body":" Confirmed. ","author":"Bob"}""")
    )
    val topicVote = run(
      service,
      Request[IO](method = Method.POST, uri = uri"/forum/topics/topic-000000000001/votes")
        .withEntity("""{"author":"Alice","vote":"up"}""")
    )
    val clearVote = run(
      service,
      Request[IO](method = Method.POST, uri = uri"/forum/topics/topic-000000000001/votes")
        .withEntity("""{"author":"Alice","vote":null}""")
    )
    val replyVote = run(
      service,
      Request[IO](method = Method.POST, uri = uri"/forum/topics/topic-000000000001/replies/reply-000000000001/votes")
        .withEntity("""{"author":"Alice","vote":"down"}""")
    )

    assertEquals("create status", created.status, 201)
    assertContains("create title", created.body, """"title":"Patch notes"""")
    assertEquals("list status", list.status, 200)
    assertContains("list wrapper", list.body, """"topics":[""")
    assertEquals("detail status", detail.status, 200)
    assertContains("detail wrapper", detail.body, """"topic":{""")
    assertEquals("reply status", reply.status, 200)
    assertContains("reply body", reply.body, """"body":"Confirmed."""")
    assertEquals("topic vote status", topicVote.status, 200)
    assertContains("topic vote renders up", topicVote.body, """"viewerVote":"up"""")
    assertEquals("clear vote status", clearVote.status, 200)
    assertContains("clear vote renders null", clearVote.body, """"viewerVote":null""")
    assertEquals("reply vote status", replyVote.status, 200)
    assertContains("reply vote renders down", replyVote.body, """"viewerVote":"down"""")
  }

  private def validationAndMissingTargetsAreMapped(): Unit = {
    val service = InMemoryForumService()
    val visitor = run(
      service,
      Request[IO](method = Method.POST, uri = uri"/forum/topics")
        .withEntity("""{"title":"Patch notes","body":"Body","tag":"backend","author":"visitor"}""")
    )
    val invalidVote = run(
      service,
      Request[IO](method = Method.POST, uri = uri"/forum/topics/topic-000000000001/votes")
        .withEntity("""{"author":"Alice","vote":"sideways"}""")
    )
    val missingTopic = run(
      service,
      Request[IO](method = Method.POST, uri = uri"/forum/topics/missing/replies")
        .withEntity("""{"body":"Reply","author":"Alice"}""")
    )

    assertEquals("visitor status", visitor.status, 403)
    assertContains("visitor code", visitor.body, """"code":"visitor_not_allowed"""")
    assertEquals("invalid vote status", invalidVote.status, 400)
    assertContains("invalid vote code", invalidVote.body, """"code":"invalid_vote"""")
    assertEquals("missing topic status", missingTopic.status, 404)
    assertContains("missing topic code", missingTopic.body, """"code":"topic_not_found"""")
  }

  private def run(service: ForumService, request: Request[IO]): RouteResponse = {
    val response = BackendHttp4sRoutes.forumRoutes(service).orNotFound.run(request).unsafeRunSync()
    RouteResponse(response.status.code, response.as[String].unsafeRunSync())
  }

  private final case class RouteResponse(status: Int, body: String)

  private def assertEquals[A](label: String, actual: A, expected: A): Unit =
    assert(actual == expected, s"$label: expected $expected, got $actual")

  private def assertContains(label: String, actual: String, expectedSubstring: String): Unit =
    assert(actual.contains(expectedSubstring), s"$label: expected body to contain $expectedSubstring, got $actual")
}
