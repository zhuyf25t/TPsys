package slaydemo.backend.battle.api

import slaydemo.backend.shared.objects.{BattleId, UserId}

final case class BattleCommandRequest(
  battleId: BattleId,
  playerId: UserId,
  tick: Long,
  payload: String
)

final case class BattleCommandAccepted(
  battleId: BattleId,
  acceptedTick: Long
)

trait BattleCommandApi {
  def acceptCommand(request: BattleCommandRequest): BattleCommandAccepted
}
