package slaydemo.backend.http4s

import cats.effect.IO
import org.http4s.implicits.uri
import org.http4s.{Method, Request}

import slaydemo.backend.battle.objects.EpochMillis
import slaydemo.backend.governance.objects.*
import slaydemo.backend.governance.services.*
import slaydemo.backend.http4s.governance.GovernanceHttp4sRoutes
import slaydemo.backend.http4s.Http4sRouteContractSupport.{RouteResponse, runRoute}
import slaydemo.backend.mail.objects.{
  GovernanceMailActorHandle,
  GovernanceMailMetadata,
  GovernanceMailTargetLabel,
  GovernanceMailTargetPath,
  MailImportance,
  MailKind,
  MailReadState
}

object GovernanceHttp4sContractTest {
  def main(args: Array[String]): Unit = {
    contributionAdjustmentsListAndCreate()
    contributionAdjustmentValidationErrors()
    adminNotificationsListAndCreate()
    adminNotificationValidationErrors()

    println("Governance http4s contract checks passed")
  }

  private def contributionAdjustmentsListAndCreate(): Unit = {
    val contributionService = RecordingContributionAdjustmentService()
    contributionService.adjustments = Vector(adjustmentRecord())

    val list = run(
      contributionService,
      RecordingGovernanceNotificationService(),
      Request[IO](method = Method.GET, uri = uri"/api/governance/contribution-adjustments?limit=3")
    )
    val created = run(
      contributionService,
      RecordingGovernanceNotificationService(),
      Request[IO](method = Method.POST, uri = uri"/governance/contribution-adjustments")
        .withEntity(
          """{"actorHandle":"admin","targetHandle":"Alice","delta":5,"reason":" Helpful replay ","sourceLabel":"Replay","sourcePath":"/replay/r1"}"""
        )
    )

    assertEquals("adjustment list status", list.status, 200)
    assertContains("adjustment list wrapper", list.body, """"adjustments":[""")
    assertContains("adjustment list id", list.body, """"id":"adjustment-route"""")
    assertEquals("adjustment list call", contributionService.listCalls, Vector(3))

    assertEquals("adjustment create status", created.status, 200)
    assertContains("adjustment create ok", created.body, """"ok":true""")
    assertContains("adjustment create mail", created.body, """"mail":{""")
    assertEquals("adjustment create command count", contributionService.createCommands.length, 1)
    val command = contributionService.createCommands.head
    assertEquals("adjustment actor", command.actorHandle, AdminHandle("admin"))
    assertEquals("adjustment target", command.targetHandle, GovernanceTargetHandle("Alice"))
    assertEquals("adjustment delta", command.delta, ContributionDelta(5))
    assertEquals("adjustment reason trim", command.reason, GovernanceReason("Helpful replay"))
    assertEquals("adjustment source path", command.sourcePath, GovernanceSourcePath("/replay/r1"))
  }

  private def contributionAdjustmentValidationErrors(): Unit = {
    val contributionService = RecordingContributionAdjustmentService()

    val invalidActor = run(
      contributionService,
      RecordingGovernanceNotificationService(),
      Request[IO](method = Method.POST, uri = uri"/governance/contribution-adjustments")
        .withEntity("""{"actorHandle":"moderator","targetHandle":"Alice","delta":5}""")
    )
    val invalidDelta = run(
      contributionService,
      RecordingGovernanceNotificationService(),
      Request[IO](method = Method.POST, uri = uri"/governance/contribution-adjustments")
        .withEntity("""{"actorHandle":"admin","targetHandle":"Alice","delta":0}""")
    )

    assertEquals("invalid actor status", invalidActor.status, 403)
    assertContains("invalid actor code", invalidActor.body, """"code":"invalid_actor"""")
    assertEquals("invalid delta status", invalidDelta.status, 400)
    assertContains("invalid delta code", invalidDelta.body, """"code":"invalid_delta"""")
    assertEquals("invalid adjustments do not call service", contributionService.createCommands, Vector.empty)
  }

  private def adminNotificationsListAndCreate(): Unit = {
    val notificationService = RecordingGovernanceNotificationService()
    notificationService.notifications = Vector(notificationRecord())

    val list = run(
      RecordingContributionAdjustmentService(),
      notificationService,
      Request[IO](method = Method.GET, uri = uri"/governance/admin-notifications?kind=replay_report&targetType=replay&limit=4")
    )
    val invalidFilter = run(
      RecordingContributionAdjustmentService(),
      notificationService,
      Request[IO](method = Method.GET, uri = uri"/api/governance/admin-notifications?kind=appeal")
    )
    val created = run(
      RecordingContributionAdjustmentService(),
      notificationService,
      Request[IO](method = Method.POST, uri = uri"/governance/admin-notifications")
        .withEntity(
          """{"actorHandle":"Alice","kind":"replay_report","targetType":"replay","targetId":"replay-1","targetTitle":"Suspicious replay","targetPath":"/replay/replay-1","body":"Please review."}"""
        )
    )

    assertEquals("notification list status", list.status, 200)
    assertContains("notification list wrapper", list.body, """"notifications":[""")
    assertContains("notification list kind", list.body, """"kind":"replay_report"""")
    assertEquals(
      "notification list call",
      notificationService.listCalls,
      Vector((Some(GovernanceReviewKind.ReplayReport), Some(GovernanceReviewTargetType.Replay), 4))
    )

    assertEquals("invalid filter status", invalidFilter.status, 200)
    assertEquals("invalid filter body", invalidFilter.body, """{"notifications":[]}""")
    assertEquals("invalid filter does not add call", notificationService.listCalls.length, 1)

    assertEquals("notification create status", created.status, 200)
    assertContains("notification create ok", created.body, """"ok":true""")
    assertContains("notification create metadata", created.body, """"governanceTargetPath":"/replay/replay-1"""")
    assertEquals("notification create command count", notificationService.createCommands.length, 1)
    val command = notificationService.createCommands.head
    assertEquals("notification actor", command.actorHandle, GovernanceActorHandle("Alice"))
    assertEquals("notification kind", command.kind, GovernanceReviewKind.ReplayReport)
    assertEquals("notification target type", command.targetType, GovernanceReviewTargetType.Replay)
    assertEquals("notification target id", command.targetId, GovernanceReviewTargetId("replay-1"))
    assertEquals("notification body", command.body, GovernanceReviewBody("Please review."))
  }

  private def adminNotificationValidationErrors(): Unit = {
    val notificationService = RecordingGovernanceNotificationService()

    val invalidKind = run(
      RecordingContributionAdjustmentService(),
      notificationService,
      Request[IO](method = Method.POST, uri = uri"/governance/admin-notifications")
        .withEntity("""{"kind":"appeal","targetType":"replay","targetId":"r1","body":"Review."}""")
    )
    val invalidBody = run(
      RecordingContributionAdjustmentService(),
      notificationService,
      Request[IO](method = Method.POST, uri = uri"/api/governance/admin-notifications")
        .withEntity("""{"kind":"replay_report","targetType":"replay","targetId":"r1","body":" "}""")
    )

    assertEquals("invalid kind status", invalidKind.status, 400)
    assertContains("invalid kind code", invalidKind.body, """"code":"invalid_kind"""")
    assertEquals("invalid body status", invalidBody.status, 400)
    assertContains("invalid body code", invalidBody.body, """"code":"bad_request"""")
    assertEquals("invalid notifications do not call service", notificationService.createCommands, Vector.empty)
  }

  private def run(
    contributionService: ContributionAdjustmentService,
    notificationService: GovernanceNotificationService,
    request: Request[IO]
  ): RouteResponse = {
    runRoute(GovernanceHttp4sRoutes.routes(contributionService, notificationService), request)
  }

  private final class RecordingContributionAdjustmentService extends ContributionAdjustmentService {
    var adjustments: Vector[ContributionAdjustmentRecord] = Vector.empty
    var listCalls: Vector[Int] = Vector.empty
    var createCommands: Vector[ContributionAdjustmentCommand] = Vector.empty

    override def list(limit: Int): Vector[ContributionAdjustmentRecord] = {
      listCalls = listCalls :+ limit
      adjustments.take(limit)
    }

    override def create(command: ContributionAdjustmentCommand): ContributionAdjustmentSubmissionResult = {
      createCommands = createCommands :+ command
      ContributionAdjustmentSubmissionResult(
        adjustment = adjustmentRecord(
          actorHandle = command.actorHandle,
          targetHandle = command.targetHandle,
          delta = command.delta,
          reason = command.reason,
          sourceLabel = command.sourceLabel,
          sourcePath = command.sourcePath
        ),
        mail = mailSnapshot(ownerHandle = command.targetHandle)
      )
    }
  }

  private final class RecordingGovernanceNotificationService extends GovernanceNotificationService {
    var notifications: Vector[GovernanceReviewNotificationRecord] = Vector.empty
    var listCalls: Vector[(Option[GovernanceReviewKind], Option[GovernanceReviewTargetType], Int)] = Vector.empty
    var createCommands: Vector[GovernanceReviewNotificationCommand] = Vector.empty

    override def listReviewNotifications(
      kind: Option[GovernanceReviewKind],
      targetType: Option[GovernanceReviewTargetType],
      limit: Int
    ): Vector[GovernanceReviewNotificationRecord] = {
      listCalls = listCalls :+ (kind, targetType, limit)
      notifications.take(limit)
    }

    override def createReviewNotification(
      command: GovernanceReviewNotificationCommand
    ): GovernanceReviewNotificationSubmissionResult = {
      createCommands = createCommands :+ command
      val notification = notificationRecord(
        actorHandle = command.actorHandle,
        kind = command.kind,
        targetType = command.targetType,
        targetId = command.targetId,
        targetTitle = command.targetTitle,
        targetPath = command.targetPath,
        body = command.body
      )
      GovernanceReviewNotificationSubmissionResult(
        notification = notification,
        mail = mailSnapshot(
          ownerHandle = GovernanceTargetHandle("admin"),
          metadata = Some(
            GovernanceMailMetadata(
              actorHandle = GovernanceMailActorHandle(command.actorHandle.value),
              targetPath = GovernanceMailTargetPath(command.targetPath.value),
              targetLabel = GovernanceMailTargetLabel(command.targetTitle.value)
            )
          )
        )
      )
    }
  }

  private def adjustmentRecord(
    id: ContributionAdjustmentId = ContributionAdjustmentId("adjustment-route"),
    actorHandle: AdminHandle = AdminHandle("admin"),
    targetHandle: GovernanceTargetHandle = GovernanceTargetHandle("Alice"),
    delta: ContributionDelta = ContributionDelta(5),
    reason: GovernanceReason = GovernanceReason("Helpful replay"),
    createdAt: EpochMillis = EpochMillis(1_000L),
    sourceLabel: GovernanceSourceLabel = GovernanceSourceLabel("Replay"),
    sourcePath: GovernanceSourcePath = GovernanceSourcePath("/replay/r1")
  ): ContributionAdjustmentRecord =
    ContributionAdjustmentRecord(
      id = id,
      actorHandle = actorHandle,
      targetHandle = targetHandle,
      delta = delta,
      reason = reason,
      createdAt = createdAt,
      sourceLabel = sourceLabel,
      sourcePath = sourcePath
    )

  private def notificationRecord(
    id: GovernanceReviewNotificationId = GovernanceReviewNotificationId("notification-route"),
    actorHandle: GovernanceActorHandle = GovernanceActorHandle("Alice"),
    kind: GovernanceReviewKind = GovernanceReviewKind.ReplayReport,
    targetType: GovernanceReviewTargetType = GovernanceReviewTargetType.Replay,
    targetId: GovernanceReviewTargetId = GovernanceReviewTargetId("replay-1"),
    targetTitle: GovernanceReviewTargetTitle = GovernanceReviewTargetTitle("Suspicious replay"),
    targetPath: GovernanceReviewTargetPath = GovernanceReviewTargetPath("/replay/replay-1"),
    body: GovernanceReviewBody = GovernanceReviewBody("Please review."),
    createdAt: EpochMillis = EpochMillis(2_000L),
    mailId: GovernanceMailSnapshotId = GovernanceMailSnapshotId("mail-notification-route")
  ): GovernanceReviewNotificationRecord =
    GovernanceReviewNotificationRecord(
      id = id,
      actorHandle = actorHandle,
      kind = kind,
      targetType = targetType,
      targetId = targetId,
      targetTitle = targetTitle,
      targetPath = targetPath,
      body = body,
      createdAt = createdAt,
      mailId = mailId
    )

  private def mailSnapshot(
    id: GovernanceMailSnapshotId = GovernanceMailSnapshotId("mail-route"),
    ownerHandle: GovernanceTargetHandle = GovernanceTargetHandle("Alice"),
    metadata: Option[GovernanceMailMetadata] = None
  ): GovernanceMailSnapshot =
    GovernanceMailSnapshot(
      id = id,
      ownerHandle = ownerHandle,
      kind = MailKind.Governance,
      subject = "Governance update",
      excerpt = "Governance mail",
      senderLabel = "Governance",
      readState = MailReadState.Unread,
      importance = MailImportance.Important,
      createdAt = EpochMillis(3_000L),
      governanceMetadata = metadata
    )

  private def assertEquals[A](label: String, actual: A, expected: A): Unit =
    assert(actual == expected, s"$label: expected $expected, got $actual")

  private def assertContains(label: String, actual: String, expectedSubstring: String): Unit =
    assert(actual.contains(expectedSubstring), s"$label: expected body to contain $expectedSubstring, got $actual")
}
