package services.battle.microservices.runtime.objects.command

import _root_.services.battle.objects.core.{BattleId, BattleTick, ClientCommandSeq, EpochMillis, PlayerId}
import _root_.services.battle.microservices.queue.objects.queue.TicketId
import services.battle.microservices.abilities.objects.skill.{
  BattleCommandSkillIntents,
  BattleCommandSkillOutcome
}
import services.battle.microservices.combat.objects.weapon.{BattleWeaponSwitchDirection, BattleWeaponSwitchIndex}

final case class BattleCommandVector(
  x: Double,
  y: Double
)

enum BattlePrimaryInput {
  case Held
  case Released
}

object BattlePrimaryInput {
  def fromWire(value: Boolean): BattlePrimaryInput =
    if value then BattlePrimaryInput.Held else BattlePrimaryInput.Released

  def held(value: BattlePrimaryInput): Boolean =
    value == BattlePrimaryInput.Held
}

enum BattleSprintInput {
  case Requested
  case Released
}

object BattleSprintInput {
  def fromWire(value: Boolean): BattleSprintInput =
    if value then BattleSprintInput.Requested else BattleSprintInput.Released

  def requested(value: BattleSprintInput): Boolean =
    value == BattleSprintInput.Requested
}

enum BattleReloadInput {
  case Pressed
  case Released
}

object BattleReloadInput {
  def fromWire(value: Boolean): BattleReloadInput =
    if value then BattleReloadInput.Pressed else BattleReloadInput.Released

  def pressed(value: BattleReloadInput): Boolean =
    value == BattleReloadInput.Pressed
}

final case class BattleCommandInputState(
  primary: BattlePrimaryInput,
  sprint: BattleSprintInput,
  reload: BattleReloadInput
) {
  def primaryHeld: Boolean =
    BattlePrimaryInput.held(primary)

  def sprintRequested: Boolean =
    BattleSprintInput.requested(sprint)

  def reloadPressed: Boolean =
    BattleReloadInput.pressed(reload)
}

object BattleCommandInputState {
  val Released: BattleCommandInputState =
    BattleCommandInputState(
      primary = BattlePrimaryInput.Released,
      sprint = BattleSprintInput.Released,
      reload = BattleReloadInput.Released
    )

  def fromWire(
    primaryHeld: Boolean,
    sprint: Boolean,
    reloadPressed: Boolean
  ): BattleCommandInputState =
    BattleCommandInputState(
      primary = BattlePrimaryInput.fromWire(primaryHeld),
      sprint = BattleSprintInput.fromWire(sprint),
      reload = BattleReloadInput.fromWire(reloadPressed)
    )
}

final case class BattleCommandRequest(
  battleId: BattleId,
  playerId: PlayerId,
  ticketId: TicketId,
  clientTick: BattleTick,
  clientCommandSeq: ClientCommandSeq,
  movement: BattleCommandVector,
  aim: BattleCommandVector,
  inputState: BattleCommandInputState,
  skillIntents: BattleCommandSkillIntents,
  pointerWorld: Option[BattleCommandVector],
  switchWeaponDirection: BattleWeaponSwitchDirection,
  switchWeaponIndex: Option[BattleWeaponSwitchIndex]
) {
  def primaryHeld: Boolean =
    inputState.primaryHeld

  def sprint: Boolean =
    inputState.sprintRequested

  def reloadPressed: Boolean =
    inputState.reloadPressed
}

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
