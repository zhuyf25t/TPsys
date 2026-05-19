package slaydemo.backend.battle.services.runtime

import slaydemo.backend.battle.services.*

import slaydemo.backend.battle.objects.DurationMillis

private[services] object BattleRuntimeCatalog {
  val DefaultBattleDuration: DurationMillis = DurationMillis(5L * 60L * 1000L)
  val TickStep: DurationMillis = DurationMillis(33L)
}
