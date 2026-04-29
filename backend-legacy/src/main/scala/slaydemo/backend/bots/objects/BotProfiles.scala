package slaydemo.backend.bots.objects

final case class BotSkinProfile(
  avatarKey: String,
  textureKey: String,
  label: String
)

final case class BotProfileRecord(
  botId: String,
  handle: String,
  displayName: String,
  initialRating: Int,
  profileTone: String,
  strategyLabel: String,
  skin: BotSkinProfile,
  profileOrder: Int
)

object DemoBotProfiles {
  val all: Vector[BotProfileRecord] = Vector(
    BotProfileRecord(
      botId = "bot-1",
      handle = "cpu-sable",
      displayName = "Sable",
      initialRating = 1010,
      profileTone = "steady",
      strategyLabel = "Anchor skirmisher",
      skin = BotSkinProfile(
        avatarKey = "survivor",
        textureKey = "hero-survivor",
        label = "Survivor"
      ),
      profileOrder = 0
    ),
    BotProfileRecord(
      botId = "bot-2",
      handle = "cpu-rivet",
      displayName = "Rivet",
      initialRating = 990,
      profileTone = "scrappy",
      strategyLabel = "Close-range looter",
      skin = BotSkinProfile(
        avatarKey = "soldier",
        textureKey = "hero-soldier",
        label = "Soldier"
      ),
      profileOrder = 1
    ),
    BotProfileRecord(
      botId = "bot-3",
      handle = "cpu-ember",
      displayName = "Ember",
      initialRating = 1040,
      profileTone = "aggressive",
      strategyLabel = "Pressure duelist",
      skin = BotSkinProfile(
        avatarKey = "brown",
        textureKey = "hero-brown",
        label = "Brown jacket"
      ),
      profileOrder = 2
    ),
    BotProfileRecord(
      botId = "bot-4",
      handle = "cpu-orbit",
      displayName = "Orbit",
      initialRating = 1025,
      profileTone = "patient",
      strategyLabel = "Mid-range kiter",
      skin = BotSkinProfile(
        avatarKey = "old",
        textureKey = "hero-old",
        label = "Veteran"
      ),
      profileOrder = 3
    ),
    BotProfileRecord(
      botId = "bot-5",
      handle = "cpu-nova",
      displayName = "Nova",
      initialRating = 980,
      profileTone = "opportunist",
      strategyLabel = "Pickup chaser",
      skin = BotSkinProfile(
        avatarKey = "woman",
        textureKey = "hero-woman",
        label = "Runner"
      ),
      profileOrder = 4
    )
  )
}
