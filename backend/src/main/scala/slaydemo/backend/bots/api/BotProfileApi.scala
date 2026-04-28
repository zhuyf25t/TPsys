package slaydemo.backend.bots.api

import slaydemo.backend.bots.objects.BotSkinProfile

final case class BotSkinView(
  avatarKey: String,
  textureKey: String,
  label: String
)

final case class BotProfileView(
  botId: String,
  handle: String,
  displayName: String,
  initialRating: Int,
  profileTone: String,
  strategyLabel: String,
  skin: BotSkinView
)

object BotProfileView {
  def fromSkin(skin: BotSkinProfile): BotSkinView =
    BotSkinView(
      avatarKey = skin.avatarKey,
      textureKey = skin.textureKey,
      label = skin.label
    )
}
