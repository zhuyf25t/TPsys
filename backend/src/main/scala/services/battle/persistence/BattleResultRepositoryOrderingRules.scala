package services.battle.persistence

import services.battle.objects.BattleResultRecord

private[persistence] object BattleResultRepositoryOrderingRules {
  def recentFirst(left: BattleResultRecord, right: BattleResultRecord): Boolean =
    if left.finishedAt.value != right.finishedAt.value then left.finishedAt.value > right.finishedAt.value
    else left.resultId.value < right.resultId.value
}
