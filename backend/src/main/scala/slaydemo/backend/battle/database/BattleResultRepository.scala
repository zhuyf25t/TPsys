package slaydemo.backend.battle.database

import slaydemo.backend.battle.objects.{BattleId, BattleResultRecord}
import slaydemo.backend.identity.objects.PlayerHandle

trait BattleResultRepository {
  def save(record: BattleResultRecord): BattleResultRecord
  def list(handle: Option[PlayerHandle], battleId: Option[BattleId], limit: Int): Vector[BattleResultRecord]
}
