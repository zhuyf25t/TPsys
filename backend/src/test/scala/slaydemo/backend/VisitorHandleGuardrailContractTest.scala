package slaydemo.backend

import slaydemo.backend.battle.database.InMemoryBattleResultRepository
import slaydemo.backend.battle.objects.*
import slaydemo.backend.battle.services.{BattleResultRecordCommand, BattleResultRecordError, DefaultBattleResultService}
import slaydemo.backend.identity.objects.{DisplayName, PlayerHandle}
import slaydemo.backend.mail.database.InMemoryMailRepository
import slaydemo.backend.mail.objects.{MailId, MailImportance, MailKind, MailReadState, MailRecord}
import slaydemo.backend.mail.services.{DefaultMailService, MailReadError}
import slaydemo.backend.replay.database.InMemoryReplayRepository
import slaydemo.backend.replay.objects.{ReplayFrameCount, ReplayId, ReplayPlaybackAvailability}
import slaydemo.backend.replay.services.{DefaultReplayService, ReplayCommentCommand, ReplayCommentError, ReplayRecordCommand}

object VisitorHandleGuardrailContractTest {
  def main(args: Array[String]): Unit = {
    battleResultsHideVisitorOwners()
    replaysHideVisitorOwnersAndComments()
    mailsHideVisitorOwnersAndAvoidWelcomeWrites()

    println("Visitor handle guardrail contract checks passed")
  }

  private def battleResultsHideVisitorOwners(): Unit = {
    val repository = InMemoryBattleResultRepository()
    val service = DefaultBattleResultService(repository)

    val visitorRecord = service.record(resultCommand(BattleId("battle-visitor-result"), PlayerHandle("Visitor")))
    service.record(resultCommand(BattleId("battle-playable-result"), PlayerHandle("Alice")))
      .fold(error => throw AssertionError(s"playable battle result record failed: $error"), value => value)

    assertEquals("visitor result is rejected", visitorRecord, Left(BattleResultRecordError.VisitorNotAllowed))
    assertEquals("visitor result is not persisted", repository.list(None, None, 20).map(_.handle.value), Vector("Alice"))
    assertEquals("all result listing hides visitor owners", service.list(None, None, 20).map(_.handle.value), Vector("Alice"))
    assertEquals(
      "visitor handle query returns no results",
      service.list(Some(PlayerHandle(" Visitor ")), None, 20),
      Vector.empty
    )
  }

  private def replaysHideVisitorOwnersAndComments(): Unit = {
    val repository = InMemoryReplayRepository()
    val service = DefaultReplayService(repository, () => 42L)

    service.record(replayCommand(ReplayId("replay-visitor"), PlayerHandle("Visitor")))
    val playableReplay = service.record(replayCommand(ReplayId("replay-playable"), PlayerHandle("Alice")))
      .fold(error => throw AssertionError(s"playable replay record failed: $error"), value => value)
    val visitorComment = service.addComment(
      ReplayCommentCommand(playableReplay.replayId, PlayerHandle("guest"), "visitor comment")
    )
    val playableComment = service.addComment(
      ReplayCommentCommand(playableReplay.replayId, PlayerHandle("Bob"), "playable comment")
    )

    assertEquals("visitor replay is not persisted", repository.listReplays(20).map(_.handle.value), Vector("Alice"))
    assertEquals("visitor replay load is hidden", service.load(ReplayId("replay-visitor")), None)
    assertEquals("visitor comment is rejected", visitorComment, Left(ReplayCommentError.InvalidAuthor))
    assert(playableComment.isRight, "playable comment should be accepted")
    assertEquals(
      "comment listing hides visitor authors",
      service.listComments(playableReplay.replayId, 20).map(_.authorHandle.value),
      Vector("Bob")
    )
  }

  private def mailsHideVisitorOwnersAndAvoidWelcomeWrites(): Unit = {
    val repository = InMemoryMailRepository()
    val service = DefaultMailService(repository, () => 99L)
    val visitor = PlayerHandle("anonymous")
    val alice = PlayerHandle("Alice")
    val visitorMail = MailRecord(
      id = MailId("mail-visitor"),
      ownerHandle = visitor,
      kind = MailKind.System,
      subject = "Hidden",
      excerpt = "Hidden",
      senderLabel = "System",
      readState = MailReadState.Unread,
      importance = MailImportance.Normal,
      createdAt = EpochMillis(10L),
      sourceBattleId = None,
      sourcePath = None,
      sourceLabel = None,
      governanceMetadata = None,
      friendRequestMetadata = None
    )

    repository.save(visitorMail)

    assertEquals("visitor list returns no rows", service.list(visitor), Vector.empty)
    assertEquals("visitor list does not create welcome mail", repository.listByOwner(visitor), Vector(visitorMail))
    assertEquals(
      "visitor markRead does not touch storage",
      service.markRead(visitor, visitorMail.id),
      Left(MailReadError.MailNotFound)
    )
    assertEquals("visitor mail remains unread", repository.listByOwner(visitor).head.unread, true)
    assertEquals("playable owner still gets welcome mail", service.list(alice).map(_.ownerHandle.value), Vector("Alice"))
  }

  private def resultCommand(battleId: BattleId, handle: PlayerHandle): BattleResultRecordCommand =
    BattleResultRecordCommand(
      battleId = battleId,
      handle = handle,
      displayName = DisplayName(handle.value),
      finishedAt = EpochMillis(1000L),
      finishedAtLabel = "Finished",
      durationMs = DurationMillis(1800L),
      score = Score(12),
      placement = Some(BattlePlacement.unsafe(1)),
      survivalOutcome = BattleSurvivalOutcome.Survived,
      ratingBefore = Rating(1200),
      ratingDelta = RatingDelta(12),
      ratingAfter = Rating(1212),
      resultLabel = "Victory",
      modeLabel = "Authoritative",
      mapLabel = "Arena",
      highlightLine = "Victory",
      playersLine = handle.value,
      timelineHint = "Done",
      currentLoadout = Some("Pistol")
    )

  private def replayCommand(replayId: ReplayId, handle: PlayerHandle): ReplayRecordCommand =
    ReplayRecordCommand(
      replayId = replayId,
      battleId = BattleId(s"battle-${replayId.value}"),
      handle = handle,
      displayName = DisplayName(handle.value),
      finishedAt = EpochMillis(1000L),
      finishedAtLabel = "Finished",
      title = "Replay",
      modeLabel = "Authoritative",
      resultLabel = "Victory",
      mapLabel = "Arena",
      highlightLine = "Victory",
      coverLabel = "Cover",
      playersLine = handle.value,
      timelineHint = "Done",
      score = Score(12),
      placement = Some(BattlePlacement.unsafe(1)),
      durationMs = DurationMillis(1800L),
      survivalOutcome = BattleSurvivalOutcome.Survived,
      thumbnailDataUrl = None,
      currentLoadout = Some("Pistol"),
      frameCount = ReplayFrameCount.fromWire(2),
      requestedPlaybackAvailability = ReplayPlaybackAvailability.Available,
      framesJson = "[]"
    )

  private def assertEquals[A](label: String, actual: A, expected: A): Unit =
    assert(actual == expected, s"$label: expected $expected, got $actual")
}
