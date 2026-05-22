package slaydemo.backend.http4s

import cats.effect.IO
import org.http4s.{Method, Request, Uri}

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
import slaydemo.backend.http4s.battle.BattleHttpServices
import slaydemo.backend.http4s.Http4sRouteContractSupport.{RouteResponse, runRoute}
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

object HttpApiModulesCompositionContractTest {
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
      "/api/battle/queue/status",
      "/api/battle/queue/join",
      "/api/battle/queue/leave",
      "/api/battle/rooms/snapshot",
      "/api/battle/rooms/heartbeat",
      "/api/battle/state/stream",
      "/api/battle/state",
      "/api/battle/commands",
      "/api/battle/results"
    )

  private val FrontendProxyStrippedPaths: Vector[String] =
    Vector(
      "/battle/queue/status",
      "/battle/queue/join",
      "/battle/queue/leave",
      "/battle/rooms/snapshot",
      "/battle/rooms/heartbeat",
      "/battle/state/stream",
      "/battle/state",
      "/battle/commands",
      "/battle/results"
    )

  def main(args: Array[String]): Unit = {
    httpApiModulesComposeEverySplitRouteFamily()
    httpApiModulesComposeFrontendProxyStrippedBattlePaths()

    println("Backend http4s API module composition contract checks passed")
  }

  private def httpApiModulesComposeEverySplitRouteFamily(): Unit =
    RepresentativePaths.foreach { path =>
      val response = run(Request[IO](method = Method.OPTIONS, uri = Uri.unsafeFromString(path)))

      assertEquals(s"$path options status", response.status, 204)
      assertEquals(s"$path options body", response.body, "")
    }

  private def httpApiModulesComposeFrontendProxyStrippedBattlePaths(): Unit =
    FrontendProxyStrippedPaths.foreach { path =>
      val response = run(Request[IO](method = Method.OPTIONS, uri = Uri.unsafeFromString(path)))

      assertEquals(s"$path proxy-stripped options status", response.status, 204)
      assertEquals(s"$path proxy-stripped options body", response.body, "")
    }

  private def run(request: Request[IO]): RouteResponse = {
    runRoute(
      HttpApiModules.routes(UnusedHttpApiServices),
      request
    )
  }

  private val UnusedHttpApiServices: HttpApiServices =
    HttpApiServices(
      healthService = UnusedHealthService,
      replayService = UnusedReplayService,
      battleServices = BattleHttpServices(
        queueService = UnusedBattleQueueService,
        joinAuthorizationService = UnusedJoinAuthorizationService,
        resultService = UnusedBattleResultService,
        stateService = UnusedBattleStateService
      ),
      botProfileService = UnusedBotProfileService,
      identityService = UnusedIdentityService,
      mailService = UnusedMailService,
      friendRequestService = UnusedFriendRequestService,
      forumService = UnusedForumService,
      contributionAdjustmentService = UnusedContributionAdjustmentService,
      governanceNotificationService = UnusedGovernanceNotificationService
    )

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

  private def failUnused[A](): A =
    throw AssertionError("unused dependency was called")

  private def assertEquals[A](label: String, actual: A, expected: A): Unit =
    assert(actual == expected, s"$label: expected $expected, got $actual")
}
