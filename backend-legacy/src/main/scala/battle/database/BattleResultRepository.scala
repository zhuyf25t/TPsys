package slaydemo.backend.battle.database

import slaydemo.backend.battle.objects.BattleResultRecord

trait BattleResultRepository {
  def save(record: BattleResultRecord): BattleResultRecord
  def list(limit: Int): Seq[BattleResultRecord]
  def listByHandle(handle: String, limit: Int): Seq[BattleResultRecord]
  def listByBattleId(battleId: String, limit: Int): Seq[BattleResultRecord]
  def listByHandleAndBattleId(handle: String, battleId: String, limit: Int): Seq[BattleResultRecord]
}
