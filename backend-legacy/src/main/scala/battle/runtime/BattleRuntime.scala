package slaydemo.backend.battle.runtime

import slaydemo.backend.battle.api.{BattleCommandRequest, BattleCommandSkillOutcome}
import slaydemo.backend.battle.objects.BattleAggregateState
import slaydemo.backend.battle.objects.BattleSessionDescriptor

final case class BattleCommandApplication(
  state: BattleAggregateState,
  commandStatus: String,
  commandReason: Option[String],
  outcomes: Vector[BattleCommandSkillOutcome]
)

object BattleCommandApplication {
  def applied(state: BattleAggregateState, outcomes: Vector[BattleCommandSkillOutcome] = Vector.empty): BattleCommandApplication =
    BattleCommandApplication(
      state = state,
      commandStatus = "applied",
      commandReason = None,
      outcomes = outcomes
    )

  def ignored(state: BattleAggregateState, reason: String): BattleCommandApplication =
    BattleCommandApplication(
      state = state,
      commandStatus = "ignored",
      commandReason = Some(reason),
      outcomes = Vector.empty
    )
}

trait BattleRuntime {
  def createBattle(roomId: String, descriptor: BattleSessionDescriptor, now: Long): BattleAggregateState
  def step(state: BattleAggregateState, deltaMs: Long, now: Long): BattleAggregateState
  def applyCommand(state: BattleAggregateState, request: BattleCommandRequest, now: Long): Either[String, BattleCommandApplication]
}
