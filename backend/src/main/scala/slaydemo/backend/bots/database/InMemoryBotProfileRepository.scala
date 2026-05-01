package slaydemo.backend.bots.database

import slaydemo.backend.bots.objects.BotProfileRecord

final class InMemoryBotProfileRepository(initialProfiles: Vector[BotProfileRecord]) extends BotProfileRepository {
  private val lock = Object()
  private var profilesById = initialProfiles.map(profile => profile.botId -> profile).toMap

  override def list(): Vector[BotProfileRecord] =
    lock.synchronized {
      profilesById.values.toVector.sortBy(profile => (profile.profileOrder.value, profile.botId.value))
    }

  override def save(record: BotProfileRecord): BotProfileRecord = {
    lock.synchronized {
      profilesById = profilesById.updated(record.botId, record)
    }
    record
  }
}

object InMemoryBotProfileRepository {
  def apply(initialProfiles: Vector[BotProfileRecord]): InMemoryBotProfileRepository =
    new InMemoryBotProfileRepository(initialProfiles)
}
