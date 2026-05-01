package slaydemo.backend.battle.api

import slaydemo.backend.battle.objects.*

final case class BattleCommandVector(
  x: Double,
  y: Double
)

final case class BattleCommandRequest(
  battleId: BattleId,
  playerId: PlayerId,
  ticketId: TicketId,
  clientTick: BattleTick,
  clientCommandSeq: ClientCommandSeq,
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

final case class BattleCommandSkillOutcome(
  action: SkillKind,
  outcomeStatus: SkillOutcomeStatus,
  reason: Option[SkillOutcomeReason]
)

final case class BattleCommandAccepted(
  battleId: BattleId,
  acceptedTick: BattleTick,
  acceptedCommandSeq: ClientCommandSeq,
  serverTime: EpochMillis,
  commandStatus: BattleCommandStatus,
  commandReason: Option[BattleCommandReason],
  outcomes: Vector[BattleCommandSkillOutcome]
)
