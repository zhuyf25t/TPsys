package slaydemo.backend.bots.database

import slaydemo.backend.bots.objects.BotProfileRecord

trait BotProfileRepository {
  def list(): Vector[BotProfileRecord]
  def save(record: BotProfileRecord): BotProfileRecord
}
