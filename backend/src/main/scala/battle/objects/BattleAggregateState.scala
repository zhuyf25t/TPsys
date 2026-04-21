package slaydemo.backend.battle.objects

import slaydemo.backend.shared.objects.{BattleId, UserId}

final case class BattleHeroState(
  playerId: UserId,
  hp: Int,
  alive: Boolean
)

final case class BattleAggregateState(
  battleId: BattleId,
  phase: String,
  heroes: Vector[BattleHeroState],
  tick: Long
)
