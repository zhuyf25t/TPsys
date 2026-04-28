package slaydemo.backend.bots.services

import slaydemo.backend.bots.api.{BotProfileView, BotSkinView}
import slaydemo.backend.bots.database.BotProfileRepository

final class DefaultBotProfileService(repository: BotProfileRepository) extends BotProfileService {
  override def list(): Seq[BotProfileView] =
    repository.list().map { profile =>
      BotProfileView(
        botId = profile.botId,
        handle = profile.handle,
        displayName = profile.displayName,
        initialRating = profile.initialRating,
        profileTone = profile.profileTone,
        strategyLabel = profile.strategyLabel,
        skin = BotSkinView(
          avatarKey = profile.skin.avatarKey,
          textureKey = profile.skin.textureKey,
          label = profile.skin.label
        )
      )
    }
}
