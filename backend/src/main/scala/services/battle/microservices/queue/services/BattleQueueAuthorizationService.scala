package services.battle.microservices.queue.services

import cats.effect.IO

import services.battle.microservices.queue.objects.queue.*

import services.identity.services.IdentityService
import system.policies.HandlePolicy

enum BattleQueueJoinAuthorizationError {
  case InvalidSession
  case HandleMismatch
}

trait BattleQueueJoinAuthorizationService {
  /** 中文名：authorize（authorize）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗�?*/
  def authorize(command: BattleQueueJoinCommand): IO[Either[BattleQueueJoinAuthorizationError, Unit]]
}

final class DefaultBattleQueueJoinAuthorizationService(
  identityService: IdentityService
) extends BattleQueueJoinAuthorizationService {
  /** 中文名：authorize（authorize）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗�?*/
  override def authorize(command: BattleQueueJoinCommand): IO[Either[BattleQueueJoinAuthorizationError, Unit]] =
    for
      currentAccount <- identityService.current(Some(command.sessionToken))
      result <- IO.pure(
        currentAccount match {
          case Left(_) =>
            Left(BattleQueueJoinAuthorizationError.InvalidSession)
          case Right(account)
              if HandlePolicy.normalizeKey(account.handle.value) == command.handle.key =>
            Right(())
          case Right(_) =>
            Left(BattleQueueJoinAuthorizationError.HandleMismatch)
        }
      )
    yield result
}

object DefaultBattleQueueJoinAuthorizationService {
  /** 中文名：应用（apply）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗�?*/
  def apply(identityService: IdentityService): DefaultBattleQueueJoinAuthorizationService =
    new DefaultBattleQueueJoinAuthorizationService(identityService)
}
