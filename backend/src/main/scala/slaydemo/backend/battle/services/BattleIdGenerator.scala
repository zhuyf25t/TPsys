package slaydemo.backend.battle.services

import java.util.UUID

import slaydemo.backend.battle.objects.BattleId

private[services] trait BattleIdGenerator {
  def nextBattleId(): BattleId
}

private[services] object RandomBattleIdGenerator extends BattleIdGenerator {
  override def nextBattleId(): BattleId =
    BattleId(s"battle-${UUID.randomUUID().toString}")
}
