package services.bots.objects

import java.util.Locale

import services.identity.objects.{DisplayName, PlayerHandle}

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
  private val zombieSkin: BotSkinProfile =
    BotSkinProfile(
      avatarKey = BotAvatarKey("zombie"),
      textureKey = BotTextureKey("hero-zombie"),
      label = BotSkinLabel("Zombie")
    )

  val all: Vector[BotProfileRecord] = Vector(
    BotProfileRecord(
      botId = BotId("bot-1"),
      handle = PlayerHandle("cpu-zombie-1"),
      displayName = DisplayName("Zombie 1"),
      initialRating = BotInitialRating(1010),
      profileTone = BotProfileTone.Steady,
      strategyLabel = BotStrategyLabel("Infected anchor"),
      skin = zombieSkin,
      profileOrder = BotProfileOrder(0)
    ),
    BotProfileRecord(
      botId = BotId("bot-2"),
      handle = PlayerHandle("cpu-zombie-2"),
      displayName = DisplayName("Zombie 2"),
      initialRating = BotInitialRating(990),
      profileTone = BotProfileTone.Scrappy,
      strategyLabel = BotStrategyLabel("Infected rush"),
      skin = zombieSkin,
      profileOrder = BotProfileOrder(1)
    ),
    BotProfileRecord(
      botId = BotId("bot-3"),
      handle = PlayerHandle("cpu-zombie-3"),
      displayName = DisplayName("Zombie 3"),
      initialRating = BotInitialRating(1040),
      profileTone = BotProfileTone.Aggressive,
      strategyLabel = BotStrategyLabel("Infected pressure"),
      skin = zombieSkin,
      profileOrder = BotProfileOrder(2)
    ),
    BotProfileRecord(
      botId = BotId("bot-4"),
      handle = PlayerHandle("cpu-zombie-4"),
      displayName = DisplayName("Zombie 4"),
      initialRating = BotInitialRating(1025),
      profileTone = BotProfileTone.Patient,
      strategyLabel = BotStrategyLabel("Infected stalker"),
      skin = zombieSkin,
      profileOrder = BotProfileOrder(3)
    ),
    BotProfileRecord(
      botId = BotId("bot-5"),
      handle = PlayerHandle("cpu-zombie-5"),
      displayName = DisplayName("Zombie 5"),
      initialRating = BotInitialRating(980),
      profileTone = BotProfileTone.Opportunist,
      strategyLabel = BotStrategyLabel("Infected scavenger"),
      skin = zombieSkin,
      profileOrder = BotProfileOrder(4)
    )
  )
}
