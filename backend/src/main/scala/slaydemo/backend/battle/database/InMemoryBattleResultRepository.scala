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
      .sortWith(compareRecentFirst)
      .take(math.max(0, limit))

  private def compareRecentFirst(left: BattleResultRecord, right: BattleResultRecord): Boolean =
    if left.finishedAt.value != right.finishedAt.value then left.finishedAt.value > right.finishedAt.value
    else left.resultId.value < right.resultId.value
}

object InMemoryBattleResultRepository {
  def apply(): InMemoryBattleResultRepository =
    new InMemoryBattleResultRepository()
}
