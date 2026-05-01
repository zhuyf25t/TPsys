package slaydemo.backend

import slaydemo.backend.bots.database.InMemoryBotProfileRepository
import slaydemo.backend.bots.objects.*
import slaydemo.backend.bots.services.{DefaultBotProfileService, StaticBotProfileService}
import slaydemo.backend.identity.objects.{DisplayName, PlayerHandle}

object BotProfileServiceContractTest {
  def main(args: Array[String]): Unit = {
    demoCatalogIsDeterministic()
    repositoryOrdersAndReplacesByBotId()

    println("BotProfile service contract checks passed")
  }

  private def demoCatalogIsDeterministic(): Unit = {
    val profiles = StaticBotProfileService.demo().list()

    assertEquals("demo profile count", profiles.length, 5)
    assertEquals("demo ids", profiles.map(_.botId.value), Vector("bot-1", "bot-2", "bot-3", "bot-4", "bot-5"))
    assertEquals("demo handles", profiles.map(_.handle.value), Vector("cpu-sable", "cpu-rivet", "cpu-ember", "cpu-orbit", "cpu-nova"))
    assertEquals("demo order", profiles.map(_.profileOrder.value), Vector(0, 1, 2, 3, 4))
    assertEquals(
      "demo tones",
      profiles.map(profile => BotProfileTone.wireValue(profile.profileTone)),
      Vector("steady", "scrappy", "aggressive", "patient", "opportunist")
    )
  }

  private def repositoryOrdersAndReplacesByBotId(): Unit = {
    val repository = InMemoryBotProfileRepository(
      Vector(
        profile(BotId("bot-b"), PlayerHandle("cpu-b"), BotProfileOrder(2), DisplayName("Bot B")),
        profile(BotId("bot-a"), PlayerHandle("cpu-a"), BotProfileOrder(1), DisplayName("Bot A"))
      )
    )
    val service = DefaultBotProfileService(repository)

    assertEquals("initial ordering", service.list().map(_.botId.value), Vector("bot-a", "bot-b"))

    repository.save(profile(BotId("bot-b"), PlayerHandle("cpu-b-new"), BotProfileOrder(0), DisplayName("Bot B New")))

    val updated = service.list()
    assertEquals("replace keeps two rows", updated.length, 2)
    assertEquals("updated ordering", updated.map(_.botId.value), Vector("bot-b", "bot-a"))
    assertEquals("updated handle", updated.head.handle, PlayerHandle("cpu-b-new"))
    assertEquals("updated display name", updated.head.displayName, DisplayName("Bot B New"))
  }

  private def profile(
    botId: BotId,
    handle: PlayerHandle,
    order: BotProfileOrder,
    displayName: DisplayName
  ): BotProfileRecord =
    BotProfileRecord(
      botId = botId,
      handle = handle,
      displayName = displayName,
      initialRating = BotInitialRating(1_000),
      profileTone = BotProfileTone.Steady,
      strategyLabel = BotStrategyLabel("Test strategy"),
      skin = BotSkinProfile(
        avatarKey = BotAvatarKey("test-avatar"),
        textureKey = BotTextureKey("test-texture"),
        label = BotSkinLabel("Test skin")
      ),
      profileOrder = order
    )

  private def assertEquals[A](label: String, actual: A, expected: A): Unit =
    assert(actual == expected, s"$label: expected $expected, got $actual")
}
