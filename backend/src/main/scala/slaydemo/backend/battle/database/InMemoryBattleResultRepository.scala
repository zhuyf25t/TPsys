package slaydemo.backend.battle.database

import slaydemo.backend.battle.objects.{BattleId, BattleResultId, BattleResultRecord}
import slaydemo.backend.identity.objects.PlayerHandle

final class InMemoryBattleResultRepository extends BattleResultRepository {
  private val lock = Object()
  private var recordsById: Map[BattleResultId, BattleResultRecord] = Map.empty

  override def save(record: BattleResultRecord): BattleResultRecord = {
    lock.synchronized {
      recordsById = recordsById.updated(record.resultId, record)
    }
    record
  }

  override def list(
    handle: Option[PlayerHandle],
    battleId: Option[BattleId],
    limit: Int
  ): Vector[BattleResultRecord] =
    lock.synchronized {
      recordsById.values.toVector
    }.filter(record => handle.forall(_.key == record.handle.key))
      .filter(record => battleId.forall(_.value == record.battleId.value))
      .sortWith(BattleResultRepositoryOrderingRules.recentFirst)
      .take(math.max(0, limit))
}

object InMemoryBattleResultRepository {
  def apply(): InMemoryBattleResultRepository =
    new InMemoryBattleResultRepository()
}
