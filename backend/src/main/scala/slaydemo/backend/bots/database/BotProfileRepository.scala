package slaydemo.backend.bots.database

import slaydemo.backend.bots.objects.BotProfileRecord

trait BotProfileRepository {
  def list(): Seq[BotProfileRecord]

  def save(record: BotProfileRecord): BotProfileRecord
}
