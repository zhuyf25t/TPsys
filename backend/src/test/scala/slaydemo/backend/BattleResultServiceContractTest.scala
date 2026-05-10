package slaydemo.backend

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

import slaydemo.backend.battle.database.{FileBattleResultRepository, InMemoryBattleResultRepository}
import slaydemo.backend.battle.objects.*
import slaydemo.backend.battle.services.{BattleResultRecordCommand, BattleResultRecordError, DefaultBattleResultService}
import slaydemo.backend.identity.objects.{DisplayName, PlayerHandle}

object BattleResultServiceContractTest {
  def main(args: Array[String]): Unit = {
    recordPersistsAndNormalizesLoadout()
    recordErrorsAreExplicitAndDoNotPersist()
    listAppliesNewestFirstFiltersAndLimit()
    fileRepositoryPersistsUpsertsAndFilters()

    println("BattleResult service contract checks passed")
  }

  private def recordPersistsAndNormalizesLoadout(): Unit = {
    val repository = InMemoryBattleResultRepository()
    val service = DefaultBattleResultService(repository)
    val blankLoadout = recordOrFail(service, command(BattleId("battle-one"), PlayerHandle("Alice"), EpochMillis(1_000L), Some("   ")))
    val savedLoadout = recordOrFail(service, command(BattleId("battle-two"), PlayerHandle("Alice"), EpochMillis(2_000L), Some("Pistol")))

    assertEquals("blank loadout normalized", blankLoadout.currentLoadout, None)
    assertEquals("non-empty loadout preserved", savedLoadout.currentLoadout, Some("Pistol"))
    assertEquals(
      "repository persists normalized records",
      repository.list(Some(PlayerHandle("Alice")), None, 10).map(record => record.battleId -> record.currentLoadout),
      Vector(BattleId("battle-two") -> Some("Pistol"), BattleId("battle-one") -> None)
    )
  }

  private def recordErrorsAreExplicitAndDoNotPersist(): Unit = {
    val repository = InMemoryBattleResultRepository()
    val service = DefaultBattleResultService(repository)

    assertEquals(
      "visitor result record error",
      service.record(command(BattleId("battle-visitor"), PlayerHandle("visitor"), EpochMillis(1_000L), Some("Pistol"))),
      Left(BattleResultRecordError.VisitorNotAllowed)
    )
    assertEquals(
      "blank handle result record error",
      service.record(command(BattleId("battle-blank"), PlayerHandle(" "), EpochMillis(2_000L), Some("Pistol"))),
      Left(BattleResultRecordError.InvalidHandle)
    )
    assertEquals("invalid result records are not persisted", repository.list(None, None, 10), Vector.empty)
  }

  private def listAppliesNewestFirstFiltersAndLimit(): Unit = {
    val service = DefaultBattleResultService(InMemoryBattleResultRepository())
    val aliceOne = recordOrFail(service, command(BattleId("battle-one"), PlayerHandle("Alice"), EpochMillis(1_000L), Some("Pistol")))
    val bobOne = recordOrFail(service, command(BattleId("battle-one"), PlayerHandle("Bob"), EpochMillis(2_000L), Some("Shotgun")))
    val aliceTwo = recordOrFail(service, command(BattleId("battle-two"), PlayerHandle("Alice"), EpochMillis(3_000L), Some("RocketLauncher")))

    assertEquals(
      "global newest first with limit",
      service.list(None, None, 2).map(_.resultId),
      Vector(aliceTwo.resultId, bobOne.resultId)
    )
    assertEquals(
      "handle filter",
      service.list(Some(PlayerHandle("Alice")), None, 10).map(_.resultId),
      Vector(aliceTwo.resultId, aliceOne.resultId)
    )
    assertEquals(
      "battle filter",
      service.list(None, Some(BattleId("battle-one")), 10).map(_.resultId),
      Vector(bobOne.resultId, aliceOne.resultId)
    )
    assertEquals("zero limit", service.list(None, None, 0), Vector.empty)
    assertEquals("negative limit", service.list(None, None, -5), Vector.empty)
  }

  private def fileRepositoryPersistsUpsertsAndFilters(): Unit = {
    val directory = Files.createTempDirectory("slay-demo-battle-result-file-contract")
    try {
      val storagePath = directory.resolve("battle-results.json")
      val service = DefaultBattleResultService(FileBattleResultRepository(storagePath))
      val aliceOriginal = recordOrFail(service, command(BattleId("battle-file"), PlayerHandle("Alice"), EpochMillis(1_000L), Some("Pistol")))
      val bob = recordOrFail(service, command(BattleId("battle-file"), PlayerHandle("Bob"), EpochMillis(2_000L), None))
      val aliceReplacement = recordOrFail(service,
        command(BattleId("battle-file"), PlayerHandle("Alice"), EpochMillis(3_000L), Some("Shotgun"))
          .copy(score = Score(42), placement = None)
      )

      assertEquals("same battle and handle has same result id", aliceReplacement.resultId, aliceOriginal.resultId)

      val reloaded = FileBattleResultRepository(storagePath)
      assertEquals(
        "file battle results reload newest first and upsert by result id",
        reloaded.list(None, None, 10).map(record => record.resultId -> record.score),
        Vector(aliceReplacement.resultId -> Score(42), bob.resultId -> Score(12))
      )
      assertEquals(
        "file battle results handle filter is case-insensitive",
        reloaded.list(Some(PlayerHandle("alice")), None, 10).map(_.resultId),
        Vector(aliceReplacement.resultId)
      )
      assertEquals(
        "file battle results battle filter",
        reloaded.list(None, Some(BattleId("battle-file")), 10).map(_.resultId),
        Vector(aliceReplacement.resultId, bob.resultId)
      )
      assertEquals("file battle results limit", reloaded.list(None, None, 1).map(_.resultId), Vector(aliceReplacement.resultId))
      assertEquals("file battle results nullable placement", reloaded.list(Some(PlayerHandle("Alice")), None, 10).head.placement, None)
      assertEquals(
        "file battle results current loadout round trips",
        reloaded.list(Some(PlayerHandle("Alice")), None, 10).head.currentLoadout,
        Some("Shotgun")
      )
    } finally {
      deleteRecursively(directory)
    }
  }

  private def command(
    battleId: BattleId,
    handle: PlayerHandle,
    finishedAt: EpochMillis,
    currentLoadout: Option[String]
  ): BattleResultRecordCommand =
    BattleResultRecordCommand(
      battleId = battleId,
      handle = handle,
      displayName = DisplayName(handle.value),
      finishedAt = finishedAt,
      finishedAtLabel = "Finished",
      durationMs = DurationMillis(1_800L),
      score = Score(12),
      placement = Some(BattlePlacement.unsafe(1)),
      survivalOutcome = BattleSurvivalOutcome.Survived,
      ratingBefore = Rating(1200),
      ratingDelta = RatingDelta(12),
      ratingAfter = Rating(1212),
      resultLabel = "Victory",
      modeLabel = "Authoritative",
      mapLabel = "Arena",
      highlightLine = "Victory",
      playersLine = "Alice / Bob",
      timelineHint = "Done",
      currentLoadout = currentLoadout
    )

  private def assertEquals[A](label: String, actual: A, expected: A): Unit =
    assert(actual == expected, s"$label: expected $expected, got $actual")

  private def deleteRecursively(path: Path): Unit =
    if Files.exists(path) then {
      val stream = Files.walk(path)
      try {
        stream
          .iterator()
          .asScala
          .toVector
          .sortBy(_.toString.length)
          .reverse
          .foreach(Files.deleteIfExists)
      } finally {
      stream.close()
      }
    }

  private def recordOrFail(service: DefaultBattleResultService, command: BattleResultRecordCommand): BattleResultRecord =
    service.record(command).fold(error => throw AssertionError(s"record battle result failed: $error"), value => value)
}
