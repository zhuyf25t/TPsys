package slaydemo.backend

import slaydemo.backend.governance.database.InMemoryGovernanceRepository
import slaydemo.backend.governance.objects.*
import slaydemo.backend.governance.services.{
  ContributionAdjustmentCommand,
  DefaultGovernanceService,
  GovernanceReviewNotificationCommand
}
import slaydemo.backend.identity.objects.PlayerHandle
import slaydemo.backend.mail.database.InMemoryMailRepository
import slaydemo.backend.mail.objects.MailKind

object GovernanceServiceContractTest {
  def main(args: Array[String]): Unit = {
    decodesGovernanceReviewWireValuesExplicitly()
    contributionAdjustmentCreatesRecordAndMail()
    reviewNotificationCreatesFilteredRecordAndAdminMail()
    reviewNotificationUsesTargetIdWhenTitleIsBlank()

    println("Governance service contract checks passed")
  }

  private def decodesGovernanceReviewWireValuesExplicitly(): Unit = {
    assertEquals("replay proposal kind wire", GovernanceReviewKind.fromWire("replay_proposal"), Some(GovernanceReviewKind.ReplayProposal))
    assertEquals("replay report kind wire", GovernanceReviewKind.fromWire("replay_report"), Some(GovernanceReviewKind.ReplayReport))
    assertEquals("discussion report kind wire", GovernanceReviewKind.fromWire(" discussion_report "), Some(GovernanceReviewKind.DiscussionReport))
    assertEquals("invalid kind wire is not defaulted", GovernanceReviewKind.fromWire("appeal"), None)
    assertEquals("replay target type wire", GovernanceReviewTargetType.fromWire("replay"), Some(GovernanceReviewTargetType.Replay))
    assertEquals("bot target type wire", GovernanceReviewTargetType.fromWire("bot"), Some(GovernanceReviewTargetType.Bot))
    assertEquals("invalid target type wire is not defaulted", GovernanceReviewTargetType.fromWire("player"), None)
  }

  private def contributionAdjustmentCreatesRecordAndMail(): Unit = {
    val governanceRepository = InMemoryGovernanceRepository()
    val mailRepository = InMemoryMailRepository()
    val service = DefaultGovernanceService(governanceRepository, mailRepository, () => 1_000L)
    val result = service.create(
      ContributionAdjustmentCommand(
        actorHandle = AdminHandle("admin"),
        targetHandle = GovernanceTargetHandle("Alice"),
        delta = ContributionDelta(5),
        reason = GovernanceReason("Helpful replay review"),
        sourceLabel = GovernanceSourceLabel("Replay"),
        sourcePath = GovernanceSourcePath("/replay/r1")
      )
    )

    assertEquals("adjustment id", result.adjustment.id, ContributionAdjustmentId("governance-adjustment-000001"))
    assertEquals("adjustment created at", result.adjustment.createdAt.value, 1_000L)
    assertEquals("adjustment mail id", result.mail.id, GovernanceMailSnapshotId("mail-governance-adjustment-000001"))
    assertEquals("adjustment mail owner", result.mail.ownerHandle, GovernanceTargetHandle("Alice"))
    assertEquals("adjustment mail kind", result.mail.kind, MailKind.Governance)
    assertEquals("adjustment mail unread", result.mail.unread, true)
    assertEquals("adjustment mail important", result.mail.important, true)
    assertEquals("adjustment mail metadata", result.mail.governanceMetadata, None)
    assertContains("adjustment mail subject delta", result.mail.subject, "+5")
    assertContains("adjustment mail excerpt reason", result.mail.excerpt, "Helpful replay review")
    assertContains("adjustment mail excerpt source", result.mail.excerpt, "/replay/r1")
    assertEquals("adjustment list", service.list(10).map(_.id), Vector(result.adjustment.id))
    assertEquals("adjustment limit zero", service.list(0), Vector.empty)

    val mails = mailRepository.listByOwner(PlayerHandle("Alice"))
    assertEquals("adjustment persisted one mail", mails.map(_.id.value), Vector(result.mail.id.value))
    assertEquals("adjustment persisted kind", mails.head.kind, MailKind.Governance)
    assertEquals("adjustment persisted important", mails.head.important, true)
    assertEquals("adjustment persisted metadata", mails.head.governanceMetadata, None)
  }

  private def reviewNotificationCreatesFilteredRecordAndAdminMail(): Unit = {
    val governanceRepository = InMemoryGovernanceRepository()
    val mailRepository = InMemoryMailRepository()
    val service = DefaultGovernanceService(governanceRepository, mailRepository, () => 2_000L)
    val result = service.createReviewNotification(
      GovernanceReviewNotificationCommand(
        actorHandle = GovernanceActorHandle("Alice"),
        kind = GovernanceReviewKind.ReplayReport,
        targetType = GovernanceReviewTargetType.Replay,
        targetId = GovernanceReviewTargetId("replay-1"),
        targetTitle = GovernanceReviewTargetTitle("Suspicious replay"),
        targetPath = GovernanceReviewTargetPath("/replay/replay-1"),
        body = GovernanceReviewBody("Please review this replay.")
      )
    )

    assertEquals("notification id", result.notification.id, GovernanceReviewNotificationId("governance-review-000001"))
    assertEquals("notification mail id", result.notification.mailId, GovernanceMailSnapshotId("mail-governance-review-000001"))
    assertEquals("notification created at", result.notification.createdAt.value, 2_000L)
    assertEquals("notification mail owner", result.mail.ownerHandle, GovernanceTargetHandle("admin"))
    assertEquals("notification mail kind", result.mail.kind, MailKind.Governance)
    assertEquals("notification mail unread", result.mail.unread, true)
    assertEquals("notification mail important", result.mail.important, true)
    assertEquals(
      "notification kind filter",
      service.listReviewNotifications(Some(GovernanceReviewKind.ReplayReport), None, 10).map(_.id),
      Vector(result.notification.id)
    )
    assertEquals(
      "notification target type filter",
      service.listReviewNotifications(None, Some(GovernanceReviewTargetType.Discussion), 10),
      Vector.empty
    )
    assertEquals(
      "notification wrong kind filter",
      service.listReviewNotifications(Some(GovernanceReviewKind.ReplayProposal), None, 10),
      Vector.empty
    )
    assertEquals("notification limit zero", service.listReviewNotifications(None, None, 0), Vector.empty)

    val adminMails = mailRepository.listByOwner(PlayerHandle("admin"))
    val metadata = adminMails.head.governanceMetadata.getOrElse(fail("missing governance metadata"))
    assertEquals("review persisted one admin mail", adminMails.map(_.id.value), Vector(result.mail.id.value))
    assertEquals("review mail kind", adminMails.head.kind, MailKind.Governance)
    assertEquals("review metadata actor", metadata.actorHandle, "Alice")
    assertEquals("review metadata target path", metadata.targetPath, "/replay/replay-1")
    assertEquals("review metadata target label", metadata.targetLabel, "Suspicious replay")
  }

  private def reviewNotificationUsesTargetIdWhenTitleIsBlank(): Unit = {
    val mailRepository = InMemoryMailRepository()
    val service = DefaultGovernanceService(InMemoryGovernanceRepository(), mailRepository, () => 3_000L)
    val result = service.createReviewNotification(
      GovernanceReviewNotificationCommand(
        actorHandle = GovernanceActorHandle("Alice"),
        kind = GovernanceReviewKind.BotSuggestion,
        targetType = GovernanceReviewTargetType.Bot,
        targetId = GovernanceReviewTargetId("bot-alpha"),
        targetTitle = GovernanceReviewTargetTitle("   "),
        targetPath = GovernanceReviewTargetPath("/bots/bot-alpha"),
        body = GovernanceReviewBody("Consider this bot profile.")
      )
    )

    val snapshotMetadata = result.mail.governanceMetadata.getOrElse(fail("missing snapshot metadata"))
    assertEquals("blank title snapshot target label", snapshotMetadata.targetLabel, "bot-alpha")
    assertContains("blank title subject target label", result.mail.subject, "bot-alpha")
    assertContains("blank title excerpt target label", result.mail.excerpt, "bot-alpha")

    val adminMails = mailRepository.listByOwner(PlayerHandle("admin"))
    val persistedMetadata = adminMails.head.governanceMetadata.getOrElse(fail("missing persisted metadata"))
    assertEquals("blank title persisted target label", persistedMetadata.targetLabel, "bot-alpha")
  }

  private def assertEquals[A](label: String, actual: A, expected: A): Unit =
    assert(actual == expected, s"$label: expected $expected, got $actual")

  private def assertContains(label: String, actual: String, expectedPart: String): Unit =
    assert(actual.contains(expectedPart), s"$label: expected '$actual' to contain '$expectedPart'")

  private def fail(message: String): Nothing =
    throw AssertionError(message)
}
