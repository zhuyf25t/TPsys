package slaydemo.backend.http4s

import cats.effect.IO
import cats.syntax.all.*
import org.http4s.HttpRoutes

import slaydemo.backend.battle.services.{
  BattleQueueJoinAuthorizationService,
  BattleQueueService,
  BattleStateService,
  BattleResultService
}
import slaydemo.backend.bots.services.BotProfileService
import slaydemo.backend.forum.services.ForumService
import slaydemo.backend.governance.services.{ContributionAdjustmentService, GovernanceNotificationService}
import slaydemo.backend.identity.services.IdentityService
import slaydemo.backend.mail.services.MailService
import slaydemo.backend.replay.services.ReplayService
import slaydemo.backend.social.services.FriendRequestService
import slaydemo.backend.shared.services.HealthService

object BackendHttp4sRoutes {
  def healthRoutes(service: HealthService): HttpRoutes[IO] =
    HealthHttp4sRoutes.routes(service)

  def backendRoutes(
    healthService: HealthService,
    replayService: ReplayService,
    battleQueueService: BattleQueueService,
    battleJoinAuthorizationService: BattleQueueJoinAuthorizationService,
    battleResultService: BattleResultService,
    battleStateService: BattleStateService,
    botProfileService: BotProfileService,
    identityService: IdentityService,
    mailService: MailService,
    friendRequestService: FriendRequestService,
    forumService: ForumService,
    contributionAdjustmentService: ContributionAdjustmentService,
    governanceNotificationService: GovernanceNotificationService
  ): HttpRoutes[IO] =
    healthRoutes(healthService) <+>
      identityRoutes(identityService) <+>
      mailRoutes(mailService) <+>
      socialRoutes(friendRequestService) <+>
      forumRoutes(forumService) <+>
      governanceRoutes(contributionAdjustmentService, governanceNotificationService) <+>
      replayCatalogRoutes(replayService) <+>
      botProfileRoutes(botProfileService) <+>
      BattleQueueHttp4sRoutes.statusRoutes(battleQueueService) <+>
      BattleQueueHttp4sRoutes.joinRoutes(battleQueueService, battleJoinAuthorizationService) <+>
      BattleQueueHttp4sRoutes.leaveRoutes(battleQueueService) <+>
      battleRoomSnapshotRoutes(battleQueueService) <+>
      battleRoomHeartbeatRoutes(battleQueueService) <+>
      battleStateStreamRoutes(battleStateService) <+>
      battleStateReadRoutes(battleStateService) <+>
      battleCommandRoutes(battleStateService) <+>
      battleResultRoutes(battleResultService)

  def replayCatalogRoutes(service: ReplayService): HttpRoutes[IO] =
    ReplayHttp4sRoutes.catalogRoutes(service)

  def botProfileRoutes(service: BotProfileService): HttpRoutes[IO] =
    BotProfileHttp4sRoutes.routes(service)

  def identityRoutes(service: IdentityService): HttpRoutes[IO] =
    IdentityHttp4sRoutes.routes(service)

  def mailRoutes(service: MailService): HttpRoutes[IO] =
    MailHttp4sRoutes.routes(service)

  def socialRoutes(service: FriendRequestService): HttpRoutes[IO] =
    SocialHttp4sRoutes.routes(service)

  def forumRoutes(service: ForumService): HttpRoutes[IO] =
    ForumHttp4sRoutes.routes(service)

  def governanceRoutes(
    contributionAdjustmentService: ContributionAdjustmentService,
    notificationService: GovernanceNotificationService
  ): HttpRoutes[IO] =
    GovernanceHttp4sRoutes.routes(contributionAdjustmentService, notificationService)

  def battleRoomSnapshotRoutes(queueService: BattleQueueService): HttpRoutes[IO] =
    BattleRoomHttp4sRoutes.snapshotRoutes(queueService)

  def battleRoomHeartbeatRoutes(queueService: BattleQueueService): HttpRoutes[IO] =
    BattleRoomHttp4sRoutes.heartbeatRoutes(queueService)

  def battleStateReadRoutes(battleStateService: BattleStateService): HttpRoutes[IO] =
    BattleStateHttp4sRoutes.readRoutes(battleStateService)

  def battleStateStreamRoutes(battleStateService: BattleStateService): HttpRoutes[IO] =
    BattleStateHttp4sRoutes.streamRoutes(battleStateService)

  def battleCommandRoutes(battleStateService: BattleStateService): HttpRoutes[IO] =
    BattleCommandHttp4sRoutes.routes(battleStateService)

  def battleResultRoutes(service: BattleResultService): HttpRoutes[IO] =
    BattleResultHttp4sRoutes.routes(service)

}
