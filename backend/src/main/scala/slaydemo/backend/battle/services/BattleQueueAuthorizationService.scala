package slaydemo.backend.battle.services

import slaydemo.backend.identity.services.IdentityService
import slaydemo.backend.shared.policies.HandlePolicy

enum BattleQueueJoinAuthorizationError {
  case InvalidSession
  case HandleMismatch
}

trait BattleQueueJoinAuthorizationService {
  def authorize(command: BattleQueueJoinCommand): Either[BattleQueueJoinAuthorizationError, Unit]
}

final class DefaultBattleQueueJoinAuthorizationService(
  identityService: IdentityService
) extends BattleQueueJoinAuthorizationService {
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
  def apply(identityService: IdentityService): DefaultBattleQueueJoinAuthorizationService =
    new DefaultBattleQueueJoinAuthorizationService(identityService)
}
