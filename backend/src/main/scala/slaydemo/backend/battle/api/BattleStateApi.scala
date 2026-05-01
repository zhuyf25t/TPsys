package slaydemo.backend.battle.api

import slaydemo.backend.battle.objects.BattleAggregateState

final case class BattleStateView(
  state: BattleAggregateState
)
