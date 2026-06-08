package services.battle.microservices.session.objects.command

import _root_.services.battle.objects.core.{BattleId, BattleTick, ClientCommandSeq, EpochMillis, PlayerId}
import _root_.services.battle.microservices.queue.objects.queue.TicketId
import services.battle.microservices.abilities.objects.skill.{
  BattleCommandSkillIntents,
  SkillKind,
  SkillOutcomeReason,
  SkillOutcomeStatus
}
import services.battle.microservices.combat.objects.weapon.{BattleWeaponSwitchDirection, BattleWeaponSwitchIndex}

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

enum BattleCommandAcceptPath {
  case Fresh
  case Serialized
}

final case class BattleCommandServerDiagnostics(
  path: BattleCommandAcceptPath,
  receivedAt: EpochMillis,
  completedAt: EpochMillis,
  durationMs: Long,
  lockWaitMs: Long,
  lockHeldMs: Long,
  advanceMs: Long,
  commitRetryCount: Int,
  clientTick: BattleTick,
  acceptedTick: BattleTick,
  acceptedTickLag: Long,
  clientCommandSeq: ClientCommandSeq,
  acceptedCommandSeq: ClientCommandSeq,
  acceptedCommandSeqLag: Long
)

final case class BattleCommandAccepted(
  battleId: BattleId,
  acceptedTick: BattleTick,
  acceptedCommandSeq: ClientCommandSeq,
  serverTime: EpochMillis,
  commandStatus: BattleCommandStatus,
  commandReason: Option[BattleCommandReason],
  outcomes: Vector[BattleCommandSkillOutcome],
  serverDiagnostics: Option[BattleCommandServerDiagnostics] = None
)
