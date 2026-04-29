package slaydemo.backend.battle.api

import slaydemo.backend.shared.objects.{BattleId, UserId}

final case class BattleCommandVector(
  x: Double,
  y: Double
)

final case class BattleCommandRequest(
  battleId: BattleId,
  playerId: UserId,
  ticketId: Option[String],
  clientTick: Long,
  clientCommandSeq: Long,
  movement: BattleCommandVector,
  aim: BattleCommandVector,
  primaryHeld: Boolean,
  sprint: Boolean,
  reloadPressed: Boolean,
  castDash: Boolean,
  castBlink: Boolean,
  castFreeze: Boolean,
  pointerWorld: Option[BattleCommandVector],
  switchWeaponDirection: Int,
  switchWeaponIndex: Option[Int]
)

final case class BattleCommandAccepted(
  battleId: BattleId,
  acceptedTick: Long,
  acceptedCommandSeq: Long,
  serverTime: Long,
  commandStatus: String = "applied",
  commandReason: Option[String] = None,
  outcomes: Vector[BattleCommandSkillOutcome] = Vector.empty
)

final case class BattleCommandSkillOutcome(
  action: String,
  status: String,
  reason: Option[String] = None
)

trait BattleCommandApi {
  def acceptCommand(request: BattleCommandRequest): Either[String, BattleCommandAccepted]
}
