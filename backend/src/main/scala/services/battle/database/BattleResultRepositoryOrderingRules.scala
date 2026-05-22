package services.battle.database

import services.battle.objects.BattleResultRecord

private[database] object BattleResultRepositoryOrderingRules {
  def recentFirst(left: BattleResultRecord, right: BattleResultRecord): Boolean =
    if left.finishedAt.value != right.finishedAt.value then left.finishedAt.value > right.finishedAt.value
    else left.resultId.value < right.resultId.value
}
