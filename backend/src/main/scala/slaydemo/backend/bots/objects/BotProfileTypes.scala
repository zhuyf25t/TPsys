package slaydemo.backend.bots.objects

import java.util.Locale

import slaydemo.backend.identity.objects.{DisplayName, PlayerHandle}

final case class BotId(value: String) extends AnyVal
final case class BotInitialRating(value: Int) extends AnyVal
final case class BotStrategyLabel(value: String) extends AnyVal
final case class BotProfileOrder(value: Int) extends AnyVal
final case class BotAvatarKey(value: String) extends AnyVal
final case class BotTextureKey(value: String) extends AnyVal
final case class BotSkinLabel(value: String) extends AnyVal

enum BotProfileTone {
  case Steady
  case Scrappy
  case Aggressive
  case Patient
  case Opportunist
}

object BotProfileTone {
  def wireValue(value: BotProfileTone): String =
    value match {
      case BotProfileTone.Steady      => "steady"
      case BotProfileTone.Scrappy     => "scrappy"
      case BotProfileTone.Aggressive  => "aggressive"
      case BotProfileTone.Patient     => "patient"
      case BotProfileTone.Opportunist => "opportunist"
    }

  def fromWireValue(value: String): Option[BotProfileTone] =
    Option(value).map(_.trim.toLowerCase(Locale.ROOT)).getOrElse("") match {
      case "steady"      => Some(BotProfileTone.Steady)
      case "scrappy"     => Some(BotProfileTone.Scrappy)
      case "aggressive"  => Some(BotProfileTone.Aggressive)
      case "patient"     => Some(BotProfileTone.Patient)
      case "opportunist" => Some(BotProfileTone.Opportunist)
      case _             => None
    }
}

final case class BotSkinProfile(
  avatarKey: BotAvatarKey,
  textureKey: BotTextureKey,
  label: BotSkinLabel
)

final case class BotProfileRecord(
  botId: BotId,
  handle: PlayerHandle,
  displayName: DisplayName,
  initialRating: BotInitialRating,
  profileTone: BotProfileTone,
  strategyLabel: BotStrategyLabel,
  skin: BotSkinProfile,
  profileOrder: BotProfileOrder
)

object DemoBotProfiles {
  val all: Vector[BotProfileRecord] = Vector(
    BotProfileRecord(
      botId = BotId("bot-1"),
      handle = PlayerHandle("cpu-sable"),
      displayName = DisplayName("Sable"),
      initialRating = BotInitialRating(1010),
      profileTone = BotProfileTone.Steady,
      strategyLabel = BotStrategyLabel("Anchor skirmisher"),
      skin = BotSkinProfile(
        avatarKey = BotAvatarKey("survivor"),
        textureKey = BotTextureKey("hero-survivor"),
        label = BotSkinLabel("Survivor")
      ),
      profileOrder = BotProfileOrder(0)
    ),
    BotProfileRecord(
      botId = BotId("bot-2"),
      handle = PlayerHandle("cpu-rivet"),
      displayName = DisplayName("Rivet"),
      initialRating = BotInitialRating(990),
      profileTone = BotProfileTone.Scrappy,
      strategyLabel = BotStrategyLabel("Close-range looter"),
      skin = BotSkinProfile(
        avatarKey = BotAvatarKey("soldier"),
        textureKey = BotTextureKey("hero-soldier"),
        label = BotSkinLabel("Soldier")
      ),
      profileOrder = BotProfileOrder(1)
    ),
    BotProfileRecord(
      botId = BotId("bot-3"),
      handle = PlayerHandle("cpu-ember"),
      displayName = DisplayName("Ember"),
      initialRating = BotInitialRating(1040),
      profileTone = BotProfileTone.Aggressive,
      strategyLabel = BotStrategyLabel("Pressure duelist"),
      skin = BotSkinProfile(
        avatarKey = BotAvatarKey("brown"),
        textureKey = BotTextureKey("hero-brown"),
        label = BotSkinLabel("Brown jacket")
      ),
      profileOrder = BotProfileOrder(2)
    ),
    BotProfileRecord(
      botId = BotId("bot-4"),
      handle = PlayerHandle("cpu-orbit"),
      displayName = DisplayName("Orbit"),
      initialRating = BotInitialRating(1025),
      profileTone = BotProfileTone.Patient,
      strategyLabel = BotStrategyLabel("Mid-range kiter"),
      skin = BotSkinProfile(
        avatarKey = BotAvatarKey("old"),
        textureKey = BotTextureKey("hero-old"),
        label = BotSkinLabel("Veteran")
      ),
      profileOrder = BotProfileOrder(3)
    ),
    BotProfileRecord(
      botId = BotId("bot-5"),
      handle = PlayerHandle("cpu-nova"),
      displayName = DisplayName("Nova"),
      initialRating = BotInitialRating(980),
      profileTone = BotProfileTone.Opportunist,
      strategyLabel = BotStrategyLabel("Pickup chaser"),
      skin = BotSkinProfile(
        avatarKey = BotAvatarKey("woman"),
        textureKey = BotTextureKey("hero-woman"),
        label = BotSkinLabel("Runner")
      ),
      profileOrder = BotProfileOrder(4)
    )
  )
}
