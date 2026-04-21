package slaydemo.backend.battle.planners

import slaydemo.backend.battle.api.{BattleCommandAccepted, BattleCommandRequest}

trait BattleRuntimePlanner {
  def planCommand(request: BattleCommandRequest): BattleCommandAccepted
}
