package services.battle.database.queue

import services.battle.objects.BattleQueueJoinCommand
import services.identity.services.IdentityService
import system.policies.HandlePolicy

enum BattleQueueJoinAuthorizationError {
  case InvalidSession
  case HandleMismatch
}

trait BattleQueueJoinAuthorizationService {
  /** 中文名：authorize（authorize）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗�?*/
  def authorize(command: BattleQueueJoinCommand): Either[BattleQueueJoinAuthorizationError, Unit]
}

final class DefaultBattleQueueJoinAuthorizationService(
  identityService: IdentityService
) extends BattleQueueJoinAuthorizationService {
  /** 中文名：authorize（authorize）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗�?*/
  override def authorize(command: BattleQueueJoinCommand): Either[BattleQueueJoinAuthorizationError, Unit] =
    identityService.current(Some(command.sessionToken)) match {
      case Left(_) =>
        Left(BattleQueueJoinAuthorizationError.InvalidSession)
      case Right(account)
          if HandlePolicy.normalizeKey(account.handle.value) == command.handle.key =>
        Right(())
      case Right(_) =>
        Left(BattleQueueJoinAuthorizationError.HandleMismatch)
    }
}

object DefaultBattleQueueJoinAuthorizationService {
  /** 中文名：应用（apply）。游戏职责：在后端队列域中管理匹配、房间等待、心跳和房间快照，衔接玩家进入战斗�?*/
  def apply(identityService: IdentityService): DefaultBattleQueueJoinAuthorizationService =
    new DefaultBattleQueueJoinAuthorizationService(identityService)
}
