package services.battle.database.runtime

import services.battle.objects.DurationMillis

private[battle] object BattleRuntimeCatalog {
  val DefaultBattleDuration: DurationMillis = DurationMillis(5L * 60L * 1000L)
  val TickStep: DurationMillis = DurationMillis(33L)
}
