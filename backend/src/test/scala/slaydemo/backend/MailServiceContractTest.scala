package slaydemo.backend

import slaydemo.backend.battle.objects.EpochMillis
import slaydemo.backend.identity.objects.PlayerHandle
import slaydemo.backend.mail.database.InMemoryMailRepository
import slaydemo.backend.mail.objects.{MailFriendRequestStatus, MailId, MailKind, MailRecord}
import slaydemo.backend.mail.services.{DefaultMailService, MailReadError}

object MailServiceContractTest {
  def main(args: Array[String]): Unit = {
    decodesMailWireValuesExplicitly()
    listCreatesWelcomeMailOnce()
    markReadScopesByOwnerAndPersistsUnreadState()

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
      unread = true,
      important = true,
      createdAt = EpochMillis(1_000L)
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

  private def assertEquals[A](label: String, actual: A, expected: A): Unit =
    assert(actual == expected, s"$label: expected $expected, got $actual")

  private def fail(message: String): Nothing =
    throw AssertionError(message)
}
