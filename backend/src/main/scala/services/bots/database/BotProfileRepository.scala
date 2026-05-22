package services.bots.database

import services.bots.objects.BotProfileRecord

trait BotProfileRepository {
  def list(): Vector[BotProfileRecord]
  def save(record: BotProfileRecord): BotProfileRecord
}
