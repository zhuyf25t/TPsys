package route.contract

import java.nio.file.Files

import io.circe.parser.parse

import services.battle.database.FileBattleResultRepository
import services.battle.objects.*
import services.identity.objects.{DisplayName, PlayerHandle}

private[contract] object BattleResultFileRepositoryContractTest:
  def run(): Unit =
    fileRepositoryRoundTripsBattleResults()

  private def fileRepositoryRoundTripsBattleResults(): Unit = {
    val directory = Files.createTempDirectory("slay-demo-battle-result-file-contract")
    val storagePath = directory.resolve("battle-results.json")
    val record = resultRecord()

    try {
      val writer = FileBattleResultRepository(storagePath)
      writer.save(record)

      val raw = Files.readString(storagePath)
      assert(parse(raw).isRight, s"battle result file must be valid JSON, got $raw")
      ContractAssertions.assertContains("battle result file schema", raw, """"schema" : "slay-demo.battle-results.v1"""")
      ContractAssertions.assertContains("battle result file id", raw, """"resultId" : "battle-file-1:alice"""")
      ContractAssertions.assertContains("battle result file escaped loadout", raw, "rifle \\\"quoted\\\"")

      val reader = FileBattleResultRepository(storagePath)
      ContractAssertions.assertEquals(
        "battle result round trip list",
        reader.list(handle = None, battleId = None, limit = 10),
        Vector(record)
      )
      ContractAssertions.assertEquals(
        "battle result round trip handle filter",
        reader.list(handle = Some(PlayerHandle("Alice")), battleId = None, limit = 10),
        Vector(record)
      )
      ContractAssertions.assertEquals(
        "battle result round trip battle filter",
        reader.list(handle = None, battleId = Some(BattleId("battle-file-1")), limit = 10),
        Vector(record)
      )
    } finally {
      Files.deleteIfExists(storagePath)
      Files.deleteIfExists(storagePath.resolveSibling("battle-results.json.tmp"))
      Files.deleteIfExists(directory)
    }
  }

  private def resultRecord(): BattleResultRecord =
    BattleResultRecord(
      battleId = BattleId("battle-file-1"),
      handle = PlayerHandle("Alice"),
      displayName = DisplayName("Alice \"Ace\""),
      finishedAt = EpochMillis(3000),
      finishedAtLabel = "just now",
      durationMs = DurationMillis(30000),
      score = Score(88),
      placement = Some(BattlePlacement.unsafe(1)),
      survivalOutcome = BattleSurvivalOutcome.Survived,
      ratingBefore = Rating(1200),
      ratingDelta = RatingDelta(12),
      ratingAfter = Rating(1212),
      resultLabel = BattleResultLabel.fromWire("Victory"),
      modeLabel = BattleModeLabel.fromWire("Arena Mode"),
      mapLabel = BattleMapLabel.fromWire("Island"),
      highlightLine = BattleHighlightLine.fromWire("Alice won"),
      playersLine = BattlePlayersLine.fromWire("Alice vs Bob"),
      timelineHint = BattleTimelineHint.fromWire("30s"),
      currentLoadout = Some("rifle \"quoted\"")
    )
