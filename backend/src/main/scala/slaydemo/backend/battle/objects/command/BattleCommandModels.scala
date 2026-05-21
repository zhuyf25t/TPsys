package slaydemo.backend.battle.objects.command

import slaydemo.backend.battle.objects.{
  BattleCommandReason,
  BattleCommandStatus,
  SkillKind,
  SkillOutcomeReason,
  SkillOutcomeStatus
}
import slaydemo.backend.battle.objects.core.{BattleId, BattleTick, ClientCommandSeq, EpochMillis, PlayerId, TicketId}
import slaydemo.backend.battle.objects.skill.BattleCommandSkillIntents
import slaydemo.backend.battle.objects.weapon.{BattleWeaponSwitchDirection, BattleWeaponSwitchIndex}

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
  skillIntents: BattleCommandSkillIntents,
  pointerWorld: Option[BattleCommandVector],
  switchWeaponDirection: BattleWeaponSwitchDirection,
  switchWeaponIndex: Option[BattleWeaponSwitchIndex]
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
