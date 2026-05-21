package slaydemo.backend.http4s

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.http4s.headers.`Content-Type`
import org.http4s.MediaType
import org.http4s.{Method, Request, Uri}

import slaydemo.backend.battle.api.{BattleCommandAccepted, BattleCommandRequest}
import slaydemo.backend.battle.objects.*
import slaydemo.backend.battle.services.*
import slaydemo.backend.bots.objects.BotProfileRecord
import slaydemo.backend.bots.services.BotProfileService
import slaydemo.backend.forum.objects.{ForumTopicId, ForumTopicView}
import slaydemo.backend.forum.services.{
  AddForumReplyCommand,
  CreateForumTopicCommand,
  ForumCreateTopicError,
  ForumService,
  ForumTopicMutationError,
  SetForumReplyVoteCommand,
  SetForumTopicVoteCommand
}
import slaydemo.backend.governance.objects.{
  ContributionAdjustmentRecord,
  GovernanceReviewKind,
  GovernanceReviewNotificationRecord,
  GovernanceReviewTargetType
}
import slaydemo.backend.governance.services.{
  ContributionAdjustmentCommand,
  ContributionAdjustmentService,
  ContributionAdjustmentSubmissionResult,
  GovernanceNotificationService,
  GovernanceReviewNotificationCommand,
  GovernanceReviewNotificationSubmissionResult
}
import slaydemo.backend.identity.api.IdentityAccountSummary
import slaydemo.backend.identity.objects.{IdentityAccount, SessionToken}
import slaydemo.backend.identity.services.{
  IdentityCurrentSessionError,
  IdentityRegistrationCommand,
  IdentityRegistrationError,
  IdentityService,
  IdentitySessionCommand,
  IdentitySessionError
}
import slaydemo.backend.identity.objects.PlayerHandle
import slaydemo.backend.mail.objects.{MailId, MailRecord}
import slaydemo.backend.mail.services.{MailReadError, MailService}
import slaydemo.backend.replay.objects.{ReplayCommentRecord, ReplayId, ReplayRecord}
import slaydemo.backend.replay.services.{
  ReplayCommentCommand,
  ReplayCommentError,
  ReplayRecordCommand,
  ReplayRecordError,
  ReplayService
}
import slaydemo.backend.shared.api.HealthResponse
import slaydemo.backend.shared.services.HealthService
import slaydemo.backend.social.objects.{FriendRequestDecision, FriendRequestId, FriendRequestRecord}
import slaydemo.backend.social.services.{
  FriendRequestCreateError,
  FriendRequestRespondError,
  FriendRequestResponseResult,
  FriendRequestService,
  FriendRequestSubmissionResult
}

object BackendHttp4sRoutesCompositionContractTest {
  private val FrontendCommandJson: String =
    """{"battleId":"battle-state-runtime","playerId":"alice","ticketId":"ticket-alice","clientTick":42,"clientCommandSeq":43,"movement":{"x":1.0,"y":0.0},"aim":{"x":1.0,"y":0.0},"primaryHeld":false,"sprint":false,"reloadPressed":false,"castDash":false,"castBlink":false,"castFreeze":false,"pointerWorld":null,"switchWeaponDirection":0,"switchWeaponIndex":null}"""

  private val RepresentativePaths: Vector[String] =
    Vector(
      "/api/healthapi",
      "/api/identity/accounts",
      "/api/mails",
      "/api/social/friend-requests",
      "/api/forum/topics",
      "/api/governance/contribution-adjustments",
      "/api/governance/admin-notifications",
      "/api/replaycatalogapi",
      "/api/bots/profiles",
      "/api/battlequeuestatusapi",
      "/api/battlequeuejoinapi",
      "/api/battlequeueleaveapi",
      "/api/battleroomsnapshotapi",
      "/api/battleroomheartbeatapi",
      "/api/battlestatestreamapi",
      "/api/battlestatereadapi",
      "/api/battlecommandapi",
      "/api/battleresultsapi"
    )

  def main(args: Array[String]): Unit = {
    backendRoutesComposesEverySplitRouteFamily()
    backendRoutesAcceptsFrontendCommandShapeThroughComposedRoutes()

    println("Backend http4s route composition contract checks passed")
  }

  private def backendRoutesComposesEverySplitRouteFamily(): Unit =
    RepresentativePaths.foreach { path =>
      val response = run(Request[IO](method = Method.OPTIONS, uri = Uri.unsafeFromString(path)))

      assertEquals(s"$path options status", response.status, 204)
      assertEquals(s"$path options body", response.body, "")
    }

  private def backendRoutesAcceptsFrontendCommandShapeThroughComposedRoutes(): Unit = {
    val stateService = RecordingBattleStateService()
    val request = Request[IO](method = Method.POST, uri = Uri.unsafeFromString("/api/battlecommandapi"))
      .withEntity(FrontendCommandJson)
      .putHeaders(`Content-Type`(MediaType.application.json))
    val response = run(request, battleStateService = stateService)

    assertEquals("composed command route status", response.status, 200)
    assertEquals("composed command reaches state service", stateService.requests.length, 1)
    assertEquals("composed command nullable pointerWorld", stateService.requests.head.pointerWorld, None)
    assertEquals("composed command nullable switchWeaponIndex", stateService.requests.head.switchWeaponIndex, None)
  }

  private def run(
    request: Request[IO],
    battleStateService: BattleStateService = UnusedBattleStateService
  ): RouteResponse = {
    val response = BackendHttp4sRoutes
      .backendRoutes(
        healthService = UnusedHealthService,
        replayService = UnusedReplayService,
        battleQueueService = UnusedBattleQueueService,
        battleJoinAuthorizationService = UnusedJoinAuthorizationService,
        battleResultService = UnusedBattleResultService,
        battleStateService = battleStateService,
        botProfileService = UnusedBotProfileService,
        identityService = UnusedIdentityService,
        mailService = UnusedMailService,
        friendRequestService = UnusedFriendRequestService,
        forumService = UnusedForumService,
        contributionAdjustmentService = UnusedContributionAdjustmentService,
        governanceNotificationService = UnusedGovernanceNotificationService
      )
      .orNotFound
      .run(request)
      .unsafeRunSync()
    RouteResponse(response.status.code, response.as[String].unsafeRunSync())
  }

  private final case class RouteResponse(status: Int, body: String)

  private object UnusedHealthService extends HealthService {
    override def current: HealthResponse =
      failUnused()
  }

  private object UnusedIdentityService extends IdentityService {
    override def register(command: IdentityRegistrationCommand): Either[IdentityRegistrationError, IdentityAccount] =
      failUnused()

    override def issueSession(command: IdentitySessionCommand): Either[IdentitySessionError, IdentityAccount] =
      failUnused()

    override def current(sessionToken: Option[SessionToken]): Either[IdentityCurrentSessionError, IdentityAccount] =
      failUnused()

    override def listActiveAccounts(): Vector[IdentityAccountSummary] =
      failUnused()
  }

  private object UnusedMailService extends MailService {
    override def list(ownerHandle: PlayerHandle): Vector[MailRecord] =
      failUnused()

    override def markRead(ownerHandle: PlayerHandle, mailId: MailId): Either[MailReadError, MailRecord] =
      failUnused()
  }

  private object UnusedFriendRequestService extends FriendRequestService {
    override def create(
      sourceHandle: PlayerHandle,
      targetHandle: PlayerHandle
    ): Either[FriendRequestCreateError, FriendRequestSubmissionResult] =
      failUnused()

    override def respond(
      requestId: FriendRequestId,
      actorHandle: PlayerHandle,
      decision: FriendRequestDecision
    ): Either[FriendRequestRespondError, FriendRequestResponseResult] =
      failUnused()

    override def list(ownerHandle: PlayerHandle): Vector[FriendRequestRecord] =
      failUnused()

    override def find(requestId: FriendRequestId): Option[FriendRequestRecord] =
      failUnused()
  }

  private object UnusedForumService extends ForumService {
    override def listTopics(viewerHandle: Option[PlayerHandle]): Vector[ForumTopicView] =
      failUnused()

    override def loadTopic(topicId: ForumTopicId, viewerHandle: Option[PlayerHandle]): Option[ForumTopicView] =
      failUnused()

    override def createTopic(command: CreateForumTopicCommand): Either[ForumCreateTopicError, ForumTopicView] =
      failUnused()

    override def addReply(command: AddForumReplyCommand): Either[ForumTopicMutationError, ForumTopicView] =
      failUnused()

    override def setTopicVote(command: SetForumTopicVoteCommand): Either[ForumTopicMutationError, ForumTopicView] =
      failUnused()

    override def setReplyVote(command: SetForumReplyVoteCommand): Either[ForumTopicMutationError, ForumTopicView] =
      failUnused()
  }

  private object UnusedContributionAdjustmentService extends ContributionAdjustmentService {
    override def list(limit: Int): Vector[ContributionAdjustmentRecord] =
      failUnused()

    override def create(command: ContributionAdjustmentCommand): ContributionAdjustmentSubmissionResult =
      failUnused()
  }

  private object UnusedGovernanceNotificationService extends GovernanceNotificationService {
    override def listReviewNotifications(
      kind: Option[GovernanceReviewKind],
      targetType: Option[GovernanceReviewTargetType],
      limit: Int
    ): Vector[GovernanceReviewNotificationRecord] =
      failUnused()

    override def createReviewNotification(
      command: GovernanceReviewNotificationCommand
    ): GovernanceReviewNotificationSubmissionResult =
      failUnused()
  }

  private object UnusedReplayService extends ReplayService {
    override def record(command: ReplayRecordCommand): Either[ReplayRecordError, ReplayRecord] =
      failUnused()

    override def list(limit: Int): Vector[ReplayRecord] =
      failUnused()

    override def load(replayId: ReplayId): Option[ReplayRecord] =
      failUnused()

    override def addComment(command: ReplayCommentCommand): Either[ReplayCommentError, ReplayCommentRecord] =
      failUnused()

    override def listComments(replayId: ReplayId, limit: Int): Vector[ReplayCommentRecord] =
      failUnused()
  }

  private object UnusedBotProfileService extends BotProfileService {
    override def list(): Vector[BotProfileRecord] =
      failUnused()
  }

  private object UnusedBattleQueueService extends BattleQueueService {
    override def join(command: BattleQueueJoinCommand): BattleQueueSnapshot =
      failUnused()

    override def status(ticketId: TicketId): Either[BattleQueueStatusError, BattleQueueSnapshot] =
      failUnused()

    override def leave(ticketId: TicketId): BattleQueueLeaveOutcome =
      failUnused()

    override def roomSnapshot(roomId: RoomId): Either[BattleRoomError, RealtimeRoomSnapshot] =
      failUnused()

    override def heartbeat(request: RealtimeRoomHeartbeatCommand): Either[BattleRoomError, RealtimeRoomSnapshot] =
      failUnused()

    override def activeBattleSession(battleId: BattleId): Option[BattleSessionSeed] =
      failUnused()

    override def markBattleFinished(roomId: RoomId, finishedAt: EpochMillis): Unit =
      failUnused()
  }

  private object UnusedJoinAuthorizationService extends BattleQueueJoinAuthorizationService {
    override def authorize(command: BattleQueueJoinCommand): Either[BattleQueueJoinAuthorizationError, Unit] =
      failUnused()
  }

  private object UnusedBattleResultService extends BattleResultService {
    override def record(command: BattleResultRecordCommand): Either[BattleResultRecordError, BattleResultRecord] =
      failUnused()

    override def list(handle: Option[PlayerHandle], battleId: Option[BattleId], limit: Int): Vector[BattleResultRecord] =
      failUnused()
  }

  private object UnusedBattleStateService extends BattleStateService {
    override def currentState(battleId: BattleId): Either[BattleStateReadError, BattleAggregateState] =
      failUnused()

    override def acceptCommand(request: BattleCommandRequest): Either[BattleCommandSubmitError, BattleCommandAccepted] =
      failUnused()
  }

  private final class RecordingBattleStateService extends BattleStateService {
    private var recordedRequests: Vector[BattleCommandRequest] = Vector.empty

    def requests: Vector[BattleCommandRequest] =
      recordedRequests

    override def currentState(battleId: BattleId): Either[BattleStateReadError, BattleAggregateState] =
      failUnused()

    override def acceptCommand(request: BattleCommandRequest): Either[BattleCommandSubmitError, BattleCommandAccepted] = {
      recordedRequests = recordedRequests :+ request
      Right(
        BattleCommandAccepted(
          battleId = request.battleId,
          acceptedTick = request.clientTick,
          acceptedCommandSeq = request.clientCommandSeq,
          serverTime = EpochMillis(1_234L),
          commandStatus = BattleCommandStatus.Applied,
          commandReason = None,
          outcomes = Vector.empty
        )
      )
    }
  }

  private object RecordingBattleStateService {
    def apply(): RecordingBattleStateService =
      new RecordingBattleStateService()
  }

  private def failUnused[A](): A =
    throw AssertionError("unused dependency was called")

  private def assertEquals[A](label: String, actual: A, expected: A): Unit =
    assert(actual == expected, s"$label: expected $expected, got $actual")
}
