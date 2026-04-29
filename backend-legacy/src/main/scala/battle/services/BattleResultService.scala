package slaydemo.backend.battle.services

import slaydemo.backend.battle.api.BattleResultSubmissionRequest
import slaydemo.backend.battle.objects.BattleResultRecord

trait BattleResultService {
  def record(request: BattleResultSubmissionRequest): Either[String, BattleResultRecord]
  def list(handle: Option[String], battleId: Option[String], limit: Int): Seq[BattleResultRecord]
}
