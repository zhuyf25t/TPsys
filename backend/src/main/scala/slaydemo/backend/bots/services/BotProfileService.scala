package slaydemo.backend.bots.services

import slaydemo.backend.bots.database.{BotProfileRepository, InMemoryBotProfileRepository}
import slaydemo.backend.bots.objects.{BotProfileRecord, DemoBotProfiles}

trait BotProfileService {
  def list(): Vector[BotProfileRecord]
}

final class DefaultBotProfileService(repository: BotProfileRepository) extends BotProfileService {
  override def list(): Vector[BotProfileRecord] =
    repository.list()
}

object DefaultBotProfileService {
  def apply(repository: BotProfileRepository): DefaultBotProfileService =
    new DefaultBotProfileService(repository)
}

final class StaticBotProfileService(profiles: Vector[BotProfileRecord]) extends BotProfileService {
  private val repository = InMemoryBotProfileRepository(profiles)

  override def list(): Vector[BotProfileRecord] =
    repository.list()
}

object StaticBotProfileService {
  def demo(): StaticBotProfileService =
    StaticBotProfileService(DemoBotProfiles.all)

  def apply(profiles: Vector[BotProfileRecord]): StaticBotProfileService =
    new StaticBotProfileService(profiles)
}
