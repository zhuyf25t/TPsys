package slaydemo.backend

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

import slaydemo.backend.battle.objects.EpochMillis
import slaydemo.backend.identity.objects.PlayerHandle
import slaydemo.backend.mail.database.{FileMailRepository, InMemoryMailRepository}
import slaydemo.backend.mail.objects.{
  FriendRequestMailMetadata,
  GovernanceMailActorHandle,
  GovernanceMailMetadata,
  GovernanceMailTargetLabel,
  GovernanceMailTargetPath,
  MailFriendRequestId,
  MailFriendRequestStatus,
  MailId,
  MailImportance,
  MailKind,
  MailReadState,
  MailRecord
}
import slaydemo.backend.mail.services.{DefaultMailService, MailReadError}

object MailServiceContractTest {
  def main(args: Array[String]): Unit = {
    decodesMailWireValuesExplicitly()
    listCreatesWelcomeMailOnce()
    listAndMarkReadNormalizeOwnerHandle()
    markReadScopesByOwnerAndPersistsUnreadState()
    fileRepositoryPersistsOwnerScopedRecordsAndMetadata()

    println("Mail service contract checks passed")
  }

  private def decodesMailWireValuesExplicitly(): Unit = {
    assertEquals("system mail kind wire", MailKind.fromWire("system"), Some(MailKind.System))
    assertEquals("battle mail kind wire", MailKind.fromWire("battle"), Some(MailKind.Battle))
    assertEquals("governance mail kind wire", MailKind.fromWire(" Governance "), Some(MailKind.Governance))
    assertEquals("invalid mail kind wire is not defaulted", MailKind.fromWire("announcement"), None)
    assertEquals("blank mail kind wire is invalid", MailKind.fromWire(" "), None)
    assertEquals("pending friend request mail status wire", MailFriendRequestStatus.fromWire("pending"), Some(MailFriendRequestStatus.Pending))
    assertEquals("accepted friend request mail status wire", MailFriendRequestStatus.fromWire("accepted"), Some(MailFriendRequestStatus.Accepted))
    assertEquals("invalid friend request mail status wire", MailFriendRequestStatus.fromWire("archived"), None)
  }

  private def listCreatesWelcomeMailOnce(): Unit = {
    val repository = InMemoryMailRepository()
    val service = DefaultMailService(repository, () => 1_234L)
    val alice = PlayerHandle("Alice")

    val first = service.list(alice)
    val second = service.list(alice)

    assertEquals("welcome list length", first.length, 1)
    assertEquals("welcome id", first.head.id, MailId("mail-system-welcome-alice"))
    assertEquals("welcome owner", first.head.ownerHandle, alice)
    assertEquals("welcome kind", first.head.kind, MailKind.System)
    assertEquals("welcome unread", first.head.unread, true)
    assertEquals("welcome created at", first.head.createdAt, EpochMillis(1_234L))
    assertEquals("welcome is idempotent", second.map(_.id), first.map(_.id))
    assertEquals("repository has one welcome", repository.listByOwner(alice).map(_.id), Vector(first.head.id))
  }

  private def listAndMarkReadNormalizeOwnerHandle(): Unit = {
    val repository = InMemoryMailRepository()
    val service = DefaultMailService(repository, () => 1_500L)
    val alice = PlayerHandle("Alice")

    val first = service.list(PlayerHandle(" Alice "))
    val read = service.markRead(PlayerHandle(" ALICE "), first.head.id).fold(error => fail(s"mark normalized mail read failed: $error"), value => value)

    assertEquals("normalized welcome owner", first.head.ownerHandle, alice)
    assertEquals("normalized welcome id", first.head.id, MailId("mail-system-welcome-alice"))
    assertEquals("raw owner does not get a separate welcome", repository.listByOwner(PlayerHandle(" Alice ")), Vector.empty)
    assertEquals("normalized mark read owner", read.ownerHandle, alice)
    assertEquals("normalized mark read persists", repository.listByOwner(alice).head.unread, false)
  }

  private def markReadScopesByOwnerAndPersistsUnreadState(): Unit = {
    val repository = InMemoryMailRepository()
    val service = DefaultMailService(repository, () => 2_000L)
    val alice = PlayerHandle("Alice")
    val bob = PlayerHandle("Bob")
    val mail = MailRecord(
      id = MailId("mail-alice-1"),
      ownerHandle = alice,
      kind = MailKind.Battle,
      subject = "Battle settlement ready",
      excerpt = "Victory",
      senderLabel = "Battle archive",
      readState = MailReadState.Unread,
      importance = MailImportance.Important,
      createdAt = EpochMillis(1_000L),
      sourceBattleId = None,
      sourcePath = None,
      sourceLabel = None,
      governanceMetadata = None,
      friendRequestMetadata = None
    )

    repository.save(mail)

    assertEquals(
      "wrong owner cannot mark mail",
      service.markRead(bob, mail.id),
      Left(MailReadError.MailNotFound)
    )
    assertEquals(
      "missing mail is explicit",
      service.markRead(alice, MailId("missing")),
      Left(MailReadError.MailNotFound)
    )

    val read = service.markRead(alice, mail.id).fold(error => fail(s"mark read failed: $error"), value => value)

    assertEquals("mark read returns same id", read.id, mail.id)
    assertEquals("mark read clears unread", read.unread, false)
    assertEquals("repository unread is persisted", repository.listByOwner(alice).find(_.id == mail.id).map(_.unread), Some(false))
    assertEquals("wrong owner still has no rows", repository.listByOwner(bob), Vector.empty)
  }

  private def fileRepositoryPersistsOwnerScopedRecordsAndMetadata(): Unit = {
    val directory = Files.createTempDirectory("slay-demo-mail-file-contract")
    try {
      val storagePath = directory.resolve("mails.json")
      val repository = FileMailRepository(storagePath)
      val alice = PlayerHandle("Alice")
      val bob = PlayerHandle("Bob")
      val sharedId = MailId("mail-shared")
      val aliceMail = MailRecord(
        id = sharedId,
        ownerHandle = alice,
        kind = MailKind.Governance,
        subject = "Review needed",
        excerpt = "Please review replay",
        senderLabel = "Governance",
        readState = MailReadState.Unread,
        importance = MailImportance.Important,
        createdAt = EpochMillis(2_000L),
        sourceBattleId = Some("battle-1"),
        sourcePath = Some("/replay/battle-1"),
        sourceLabel = Some("Replay"),
        governanceMetadata = Some(
          GovernanceMailMetadata(
            actorHandle = GovernanceMailActorHandle("admin"),
            targetPath = GovernanceMailTargetPath("/replay/battle-1"),
            targetLabel = GovernanceMailTargetLabel("Replay battle-1")
          )
        ),
        friendRequestMetadata = Some(
          FriendRequestMailMetadata(
            requestId = MailFriendRequestId("friend-1"),
            status = MailFriendRequestStatus.Pending,
            sourceHandle = bob
          )
        )
      )
      val bobMail = aliceMail.copy(
        ownerHandle = bob,
        subject = "Bob copy",
        createdAt = EpochMillis(1_000L),
        governanceMetadata = None,
        friendRequestMetadata = None
      )

      repository.save(aliceMail)
      repository.save(bobMail)
      repository.save(aliceMail.copy(subject = "Review updated", createdAt = EpochMillis(3_000L)))

      val reloaded = FileMailRepository(storagePath)
      val aliceRows = reloaded.listByOwner(PlayerHandle("alice"))
      assertEquals("file mail owner scoped row count", aliceRows.length, 1)
      assertEquals("file mail upsert replaces by owner and id", aliceRows.head.subject, "Review updated")
      assertEquals("file mail battle source round trips", aliceRows.head.sourceBattleId, Some("battle-1"))
      assertEquals(
        "file mail governance metadata round trips",
        aliceRows.head.governanceMetadata.map(_.targetLabel),
        Some(GovernanceMailTargetLabel("Replay battle-1"))
      )
      assertEquals("file mail friend metadata round trips", aliceRows.head.friendRequestMetadata.map(_.requestId), Some(MailFriendRequestId("friend-1")))

      val read = reloaded.markRead(PlayerHandle("ALICE"), sharedId).getOrElse(fail("file mail mark read failed"))
      assertEquals("file mail mark read returns updated record", read.unread, false)
      assertEquals("file mail wrong id returns none", reloaded.markRead(alice, MailId("missing")), None)

      val finalReload = FileMailRepository(storagePath)
      assertEquals("file mail unread state persists", finalReload.listByOwner(alice).head.unread, false)
      assertEquals("file mail same id for different owner remains separate", finalReload.listByOwner(bob).head.unread, true)
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
