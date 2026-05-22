package services.battle.database

import services.battle.objects.{BattleId, BattleResultRecord}
import services.identity.objects.PlayerHandle

trait BattleResultRepository {
  def save(record: BattleResultRecord): BattleResultRecord
  def list(handle: Option[PlayerHandle], battleId: Option[BattleId], limit: Int): Vector[BattleResultRecord]
}
