package route.contract

import java.lang.reflect.{InvocationHandler, Method as JavaMethod, Proxy}
import java.nio.file.{Files, Path, Paths}
import java.security.SecureRandom
import java.sql.Connection

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.http4s.implicits.uri
import org.http4s.{Header, Headers, HttpRoutes, Method, Request}
import org.typelevel.ci.CIString
import scala.jdk.CollectionConverters.*

import services.battle.routes.BattleHttp4sRoutes
import route.bots.BotProfileHttp4sRoutes
import route.governance.GovernanceHttp4sRoutes
import route.health.{HealthHttp4sRoutes, HealthHttpModule}
import route.identity.IdentityHttp4sRoutes
import route.mail.MailHttp4sRoutes
import route.forum.ForumHttp4sRoutes
import route.replay.{ReplayHttp4sRoutes, ReplayHttpModule}
import route.social.SocialHttp4sRoutes
import services.{BackendRepositories, BackendRepositoryFactories}
import services.battle.persistence.{BattleResultRepository, FileBattleResultRepository, InMemoryBattleResultRepository}
import services.battle.objects.*
import services.bots.objects.*
import services.bots.database.{FileBotProfileRepository, InMemoryBotProfileRepository}
import services.forum.database.{FileForumRepository, InMemoryForumRepository}
import services.forum.objects.*
import services.governance.database.{FileGovernanceRepository, InMemoryGovernanceRepository}
import services.governance.objects.*
import services.identity.database.{FileIdentityAccountRepository, InMemoryIdentityAccountRepository}
import services.identity.api.IdentityAccountSummary
import services.identity.objects.{IdentityAccount, PasswordHash, PlainTextPassword, PlayerHandle, SessionToken, SkinId}
import services.identity.ports.{PasswordVerification, Pbkdf2PasswordHasher, Sha256PasswordHasher}
import services.mail.database.{FileMailRepository, InMemoryMailRepository}
import services.mail.objects.*
import services.replay.database.{FileReplayRepository, InMemoryReplayRepository, ReplayRepository}
import services.replay.objects.*
import services.social.database.{FileFriendRequestRepository, InMemoryFriendRequestRepository}
import services.social.objects.{FriendRequestDecision, FriendRequestId, FriendRequestRecord, FriendRequestStatus}
import services.battle.application.{
  BattleCommandOwnership,
  BattleCommandSubmitError,
  BattleQueueJoinAuthorizationError,
  BattleQueueJoinAuthorizationService,
  BattleQueueJoinCommand,
  BattleQueueLeaveOutcome,
  BattleQueueService,
  BattleQueueStatusError,
  BattleRoomError,
  BattleResultRecordCommand,
  BattleResultRecordError,
  BattleResultService,
  BattleFinishProjector,
  BattleRoomLifecycleSink,
  BattleSessionLookup,
  BattleSessionSeed,
  BattleStateReadError,
  BattleStateService,
  BattleFinishProjectionFailureReporter,
  BattleFinishProjectionOutcome,
  DefaultBattleFinishProjector,
  InMemoryBattleStateService,
  RealtimeRoomHeartbeatCommand
}
import services.identity.services.{
  IdentityCurrentSessionError,
  IdentityRegistrationCommand,
  IdentityRegistrationError,
  IdentityService,
  IdentitySessionCommand,
  IdentitySessionError
}
import services.mail.services.{MailReadError, MailService}
import services.forum.services.{
  AddForumReplyCommand,
  CreateForumTopicCommand,
  ForumCreateTopicError,
  ForumService,
  ForumTopicMutationError,
  SetForumReplyVoteCommand,
  SetForumTopicVoteCommand
}
import services.governance.services.{
  ContributionAdjustmentCommand,
  ContributionAdjustmentService,
  ContributionAdjustmentSubmissionResult,
  GovernanceNotificationService,
  GovernanceReviewNotificationCommand,
  GovernanceReviewNotificationSubmissionResult
}
import services.replay.services.{
  ReplayCommentCommand,
  ReplayCommentError,
  ReplayRecordCommand,
  ReplayRecordError,
  ReplayService
}
import services.bots.services.BotProfileService
import services.social.services.{
  FriendRequestCreateError,
  FriendRequestRespondError,
  FriendRequestResponseResult,
  FriendRequestService,
  FriendRequestSubmissionResult
}
import services.identity.objects.DisplayName
import system.objects.{HealthResponse, HealthStatus}
import system.objects.{ServiceName, ServicePort, UserId}
import system.database.PostgresSupport
import system.services.HealthService
import system.storage.*

private[contract] object BattleContractFixtures:
  def queueSnapshot(
    handle: PlayerHandle,
    rating: Option[Rating],
    avatar: Option[String],
    skin: Option[String]
  ): BattleQueueSnapshot =
    BattleQueueSnapshot(
      ticketId = TicketId("ticket-1"),
      playerId = PlayerId("player-1"),
      roomId = RoomId("room-1"),
      createdAt = EpochMillis(1000),
      startsAt = EpochMillis(6000),
      deadline = EpochMillis(7000),
      serverTime = EpochMillis(1000),
      participants = Vector(
        BattleQueueParticipant(
          playerId = PlayerId("player-1"),
          handle = handle,
          joinedAt = EpochMillis(1000),
          lastSeen = EpochMillis(1000),
          rating = rating,
          avatar = avatar,
          skin = skin
        )
      ),
      capacity = BattleCapacity(6),
      durationMs = DurationMillis(5000),
      phase = MatchmakingRoomPhase.Waiting,
      finishedAt = None,
      battleSession = None
    )

  def aggregateState(battleId: BattleId): BattleAggregateState =
    BattleAggregateState(
      battleId = battleId,
      roomId = RoomId("room-1"),
      phase = BattlePhase.Active,
      serverTime = EpochMillis(2000),
      startedAt = EpochMillis(1000),
      durationMs = DurationMillis(180000),
      elapsedMs = ElapsedMillis(1000),
      endsAt = EpochMillis(181000),
      worldSize = BattleVector2(3200, 2400),
      tick = BattleTick(12),
      artifactStatus = BattleArtifactStatus.Pending,
      players = Vector.empty,
      projectiles = Vector.empty,
      projectileTerminals = Vector.empty,
      slowFields = Vector.empty,
      pickups = Vector.empty,
      replayFrames = Vector.empty,
      events = Vector.empty,
      winnerPlayerId = None,
      winnerHeroId = None
    )

  def roomSnapshot(roomId: RoomId): RealtimeRoomSnapshot =
    RealtimeRoomSnapshot(
      roomId = roomId,
      serverTime = EpochMillis(1500),
      participants = Vector(
        BattleQueueParticipant(
          playerId = PlayerId("player-1"),
          handle = PlayerHandle("Alice"),
          joinedAt = EpochMillis(1000),
          lastSeen = EpochMillis(1500),
          rating = Some(Rating(1200)),
          avatar = Some("fox"),
          skin = Some("soldier")
        )
      ),
      capacity = BattleCapacity(6),
      phase = MatchmakingRoomPhase.Waiting,
      finishedAt = None,
      battleSession = None
    )

  def resultRecord(battleId: BattleId, handle: PlayerHandle): BattleResultRecord =
    BattleResultRecord(
      battleId = battleId,
      handle = handle,
      displayName = DisplayName(handle.value),
      finishedAt = EpochMillis(3000),
      finishedAtLabel = "now",
      durationMs = DurationMillis(120000),
      score = Score(42),
      placement = BattlePlacement.fromWire(1),
      survivalOutcome = BattleSurvivalOutcome.Survived,
      ratingBefore = Rating(1200),
      ratingDelta = RatingDelta(15),
      ratingAfter = Rating(1215),
      resultLabel = BattleResultLabel.fromWire("Victory"),
      modeLabel = BattleModeLabel.fromWire("Arena"),
      mapLabel = BattleMapLabel.fromWire("Island"),
      highlightLine = BattleHighlightLine.fromWire("Alice won"),
      playersLine = BattlePlayersLine.fromWire("Alice"),
      timelineHint = BattleTimelineHint.fromWire("2m"),
      currentLoadout = Some("Pistol")
    )

private[contract] object RouteContractSupport:
  final case class RouteResponse(status: Int, body: String)

  def runRoute(routes: HttpRoutes[IO], request: Request[IO]): RouteResponse =
    val response = routes.orNotFound.run(request).unsafeRunSync()
    RouteResponse(
      status = response.status.code,
      body = response.as[String].unsafeRunSync()
    )

private[contract] object ContractAssertions:
  def assertEquals[A](label: String, actual: A, expected: A): Unit =
    assert(actual == expected, s"$label: expected $expected, got $actual")

  def assertContains(label: String, actual: String, expectedSubstring: String): Unit =
    assert(actual.contains(expectedSubstring), s"$label: expected body to contain $expectedSubstring, got $actual")
