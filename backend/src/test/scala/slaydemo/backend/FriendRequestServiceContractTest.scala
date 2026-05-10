package slaydemo.backend

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

import slaydemo.backend.battle.objects.EpochMillis
import slaydemo.backend.identity.objects.PlayerHandle
import slaydemo.backend.mail.database.InMemoryMailRepository
import slaydemo.backend.mail.objects.{MailFriendRequestStatus, MailId}
import slaydemo.backend.social.database.{FileFriendRequestRepository, FriendRequestStoreCreateResult, InMemoryFriendRequestRepository}
import slaydemo.backend.social.objects.{FriendRequestDecision, FriendRequestId, FriendRequestRecord, FriendRequestStatus}
import slaydemo.backend.social.services.{
  DefaultFriendRequestService,
  FriendRequestCreateError,
  FriendRequestRespondError,
  FriendRequestResponseResult,
  FriendRequestSubmissionResult
}

object FriendRequestServiceContractTest {
  def main(args: Array[String]): Unit = {
    decodesFriendRequestStatusWireValuesExplicitly()
    rejectsInvalidCreateHandles()
    normalizesHandlesAtServiceBoundary()
    hidesVisitorLikeStoredRecords()
    createDuplicateRespondFlow()
    fileRepositoryPersistsRequestsAndDoesNotReuseIds()

    println("FriendRequest service contract checks passed")
  }

  private def decodesFriendRequestStatusWireValuesExplicitly(): Unit = {
    assertEquals("pending status wire", FriendRequestStatus.fromWire("pending"), Some(FriendRequestStatus.Pending))
    assertEquals("accepted status wire", FriendRequestStatus.fromWire("accepted"), Some(FriendRequestStatus.Accepted))
    assertEquals("rejected status wire", FriendRequestStatus.fromWire("rejected"), Some(FriendRequestStatus.Rejected))
    assertEquals("status wire trims and normalizes", FriendRequestStatus.fromWire(" Accepted "), Some(FriendRequestStatus.Accepted))
    assertEquals("invalid status wire is not defaulted", FriendRequestStatus.fromWire("archived"), None)
    assertEquals("blank status wire is invalid", FriendRequestStatus.fromWire(" "), None)
  }

  private def rejectsInvalidCreateHandles(): Unit = {
    val service = DefaultFriendRequestService(InMemoryFriendRequestRepository(), InMemoryMailRepository(), () => 1_000L)

    assertEquals(
      "self-request is rejected",
      service.create(PlayerHandle("Alice"), PlayerHandle("alice")),
      Left(FriendRequestCreateError.InvalidHandles)
    )
    assertEquals(
      "whitespace self-request is rejected",
      service.create(PlayerHandle(" Alice "), PlayerHandle("alice")),
      Left(FriendRequestCreateError.InvalidHandles)
    )
    assertEquals(
      "visitor source is rejected",
      service.create(PlayerHandle("Visitor"), PlayerHandle("Alice")),
      Left(FriendRequestCreateError.InvalidHandles)
    )
    assertEquals(
      "visitor target is rejected",
      service.create(PlayerHandle("Alice"), PlayerHandle("guest")),
      Left(FriendRequestCreateError.InvalidHandles)
    )
    assertEquals("visitor owner list is hidden", service.list(PlayerHandle("anonymous")), Vector.empty)
  }

  private def normalizesHandlesAtServiceBoundary(): Unit = {
    var now = 1_000L
    val mailRepository = InMemoryMailRepository()
    val service = DefaultFriendRequestService(InMemoryFriendRequestRepository(), mailRepository, () => now)

    val created = service
      .create(PlayerHandle(" Alice "), PlayerHandle(" Bob "))
      .fold(error => fail(s"normalized create failed: $error"), result => result)
    val request = created.friendRequest

    assertEquals("normalized request source", request.sourceHandle, PlayerHandle("Alice"))
    assertEquals("normalized request target", request.targetHandle, PlayerHandle("Bob"))
    assertEquals("normalized target mail owner", created.notificationMail.map(_.ownerHandle), Some(PlayerHandle("Bob")))
    assertEquals(
      "normalized duplicate create detects existing pair",
      service.create(PlayerHandle("alice"), PlayerHandle("bob")).map(_.friendRequest.id),
      Right(request.id)
    )
    assertEquals("normalized source list finds request", service.list(PlayerHandle(" ALICE ")).map(_.id), Vector(request.id))
    assertEquals("normalized target list finds request", service.list(PlayerHandle(" BOB ")).map(_.id), Vector(request.id))

    now = 2_000L
    val accepted = service
      .respond(request.id, PlayerHandle(" BOB "), FriendRequestDecision.Accepted)
      .fold(error => fail(s"normalized respond failed: $error"), result => result)

    assertEquals("normalized actor can respond", accepted.friendRequest.status, FriendRequestStatus.Accepted)
    assertEquals("normalized response mail owner", accepted.notificationMail.map(_.ownerHandle), Some(PlayerHandle("Alice")))
  }

  private def hidesVisitorLikeStoredRecords(): Unit = {
    val repository = InMemoryFriendRequestRepository()
    val service = DefaultFriendRequestService(repository, InMemoryMailRepository(), () => 1_000L)
    val dirty = FriendRequestRecord.pending(
      id = FriendRequestId("friend-dirty"),
      sourceHandle = PlayerHandle("Visitor"),
      targetHandle = PlayerHandle("Alice"),
      createdAt = EpochMillis(1L)
    )

    repository.save(dirty)

    assertEquals("dirty visitor request is hidden from owner list", service.list(PlayerHandle("Alice")), Vector.empty)
    assertEquals("dirty visitor request cannot be found", service.find(dirty.id), None)
    assertEquals(
      "dirty visitor request cannot be responded to",
      service.respond(dirty.id, PlayerHandle("Alice"), FriendRequestDecision.Accepted),
      Left(FriendRequestRespondError.RequestNotFound)
    )
  }

  private def createDuplicateRespondFlow(): Unit = {
    var now = 1_000L
    val mailRepository = InMemoryMailRepository()
    val service = DefaultFriendRequestService(InMemoryFriendRequestRepository(), mailRepository, () => now)

    val created = service
      .create(PlayerHandle("Alice"), PlayerHandle("Bob"))
      .fold(error => fail(s"create failed: $error"), result => result)
    val request = created.friendRequest

    created match {
      case FriendRequestSubmissionResult.Created(_, mail) =>
        assertEquals("request mail goes to target", mail.ownerHandle, PlayerHandle("Bob"))
        assertEquals("request mail id", mail.id, MailId(s"mail-friend-${request.id.value}"))
        assertEquals("request mail metadata id", mail.friendRequestMetadata.map(_.requestId.value), Some(request.id.value))
        assertEquals("request mail metadata status", mail.friendRequestMetadata.map(_.status), Some(MailFriendRequestStatus.Pending))
        assertEquals("request mail metadata source", mail.friendRequestMetadata.map(_.sourceHandle), Some(PlayerHandle("Alice")))
      case other =>
        fail(s"expected created submission, got $other")
    }
    assertEquals(
      "request mail is persisted to target mailbox",
      mailRepository.listByOwner(PlayerHandle("Bob")).map(_.id),
      Vector(MailId(s"mail-friend-${request.id.value}"))
    )
    assertEquals("created request is pending", request.status, FriendRequestStatus.Pending)
    assertEquals("created request has no respondedAt", request.respondedAt, None)

    assertEquals(
      "duplicate create returns already sent",
      service.create(PlayerHandle("Alice"), PlayerHandle("Bob")).map(_.friendRequest.id),
      Right(request.id)
    )
    assertEquals(
      "source cannot respond to own outbound request",
      service.respond(request.id, PlayerHandle("Alice"), FriendRequestDecision.Accepted),
      Left(FriendRequestRespondError.Forbidden)
    )

    now = 2_000L
    val accepted = service
      .respond(request.id, PlayerHandle("Bob"), FriendRequestDecision.Accepted)
      .fold(error => fail(s"respond failed: $error"), result => result)
    val acceptedRequest = accepted.friendRequest

    accepted match {
      case FriendRequestResponseResult.Updated(_, mail) =>
        assertEquals("response mail goes to source", mail.ownerHandle, PlayerHandle("Alice"))
        assertEquals("response mail metadata status", mail.friendRequestMetadata.map(_.status), Some(MailFriendRequestStatus.Accepted))
      case other =>
        fail(s"expected updated response, got $other")
    }
    val targetRequestMail = mailRepository
      .listByOwner(PlayerHandle("Bob"))
      .find(_.id == MailId(s"mail-friend-${request.id.value}"))
      .getOrElse(fail("missing target request mail after response"))
    assertEquals("target request mail is read after response", targetRequestMail.unread, false)
    assertEquals(
      "target request mail status updated after response",
      targetRequestMail.friendRequestMetadata.map(_.status),
      Some(MailFriendRequestStatus.Accepted)
    )
    assertEquals(
      "response mail is persisted to source mailbox",
      mailRepository.listByOwner(PlayerHandle("Alice")).map(_.id),
      Vector(MailId(s"mail-friend-response-${request.id.value}-accepted"))
    )
    assertEquals("accepted request status", acceptedRequest.status, FriendRequestStatus.Accepted)
    assertEquals("accepted request response time", acceptedRequest.respondedAt, Some(EpochMillis(2_000L)))
    assertEquals(
      "already resolved returns current accepted request",
      service.respond(request.id, PlayerHandle("Bob"), FriendRequestDecision.Rejected).map(_.friendRequest.status),
      Right(FriendRequestStatus.Accepted)
    )
    assertEquals("alice can list the request", service.list(PlayerHandle("Alice")).map(_.id), Vector(request.id))
    assertEquals("bob can list the request", service.list(PlayerHandle("Bob")).map(_.id), Vector(request.id))
  }

  private def fileRepositoryPersistsRequestsAndDoesNotReuseIds(): Unit = {
    val directory = Files.createTempDirectory("slay-demo-friend-request-file-contract")
    try {
      val storagePath = directory.resolve("friend-requests.json")
      val repository = FileFriendRequestRepository(storagePath)
      val firstId = repository.nextRequestId()
      val request = FriendRequestRecord.pending(
        id = firstId,
        sourceHandle = PlayerHandle("Alice"),
        targetHandle = PlayerHandle("Bob"),
        createdAt = EpochMillis(1_000L)
      )

      assertEquals(
        "file friend request create stores record",
        repository.createIfAbsent(request),
        FriendRequestStoreCreateResult.Created(request)
      )
      assertEquals(
        "file friend request duplicate pair is idempotent",
        repository.createIfAbsent(request.copy(id = repository.nextRequestId(), createdAt = EpochMillis(2_000L))),
        FriendRequestStoreCreateResult.AlreadyExists(request)
      )

      val accepted = FriendRequestRecord.respond(request, FriendRequestDecision.Accepted, EpochMillis(3_000L))
      repository.save(accepted)

      val reloaded = FileFriendRequestRepository(storagePath)
      assertEquals("file friend request reload find by id", reloaded.findById(firstId), Some(accepted))
      assertEquals(
        "file friend request reload find by handles",
        reloaded.findByHandles(PlayerHandle("alice"), PlayerHandle("bob")),
        Some(accepted)
      )
      assertEquals("file friend request owner list", reloaded.listByOwner(PlayerHandle("BOB")).map(_.id), Vector(firstId))
      assertEquals("file friend request next id advances after reload", reloaded.nextRequestId(), FriendRequestId("friend-000000000002"))
    } finally {
      deleteRecursively(directory)
    }
  }

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
}
