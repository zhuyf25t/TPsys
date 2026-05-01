package slaydemo.backend

import slaydemo.backend.battle.database.InMemoryBattleResultRepository
import slaydemo.backend.battle.objects.*
import slaydemo.backend.battle.services.{BattleResultRecordCommand, DefaultBattleResultService}
import slaydemo.backend.identity.objects.{DisplayName, PlayerHandle}

object BattleResultServiceContractTest {
  def main(args: Array[String]): Unit = {
    recordPersistsAndNormalizesLoadout()
    listAppliesNewestFirstFiltersAndLimit()

    println("BattleResult service contract checks passed")
  }

  private def recordPersistsAndNormalizesLoadout(): Unit = {
    val repository = InMemoryBattleResultRepository()
    val service = DefaultBattleResultService(repository)
    val blankLoadout = service.record(command(BattleId("battle-one"), PlayerHandle("Alice"), EpochMillis(1_000L), Some("   ")))
    val savedLoadout = service.record(command(BattleId("battle-two"), PlayerHandle("Alice"), EpochMillis(2_000L), Some("Pistol")))

    assertEquals("blank loadout normalized", blankLoadout.currentLoadout, None)
    assertEquals("non-empty loadout preserved", savedLoadout.currentLoadout, Some("Pistol"))
    assertEquals(
      "repository persists normalized records",
      repository.list(Some(PlayerHandle("Alice")), None, 10).map(record => record.battleId -> record.currentLoadout),
      Vector(BattleId("battle-two") -> Some("Pistol"), BattleId("battle-one") -> None)
    )
  }

  private def listAppliesNewestFirstFiltersAndLimit(): Unit = {
    val service = DefaultBattleResultService(InMemoryBattleResultRepository())
    val aliceOne = service.record(command(BattleId("battle-one"), PlayerHandle("Alice"), EpochMillis(1_000L), Some("Pistol")))
    val bobOne = service.record(command(BattleId("battle-one"), PlayerHandle("Bob"), EpochMillis(2_000L), Some("Shotgun")))
    val aliceTwo = service.record(command(BattleId("battle-two"), PlayerHandle("Alice"), EpochMillis(3_000L), Some("RocketLauncher")))

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
      placement = Some(1),
      aliveAtEnd = true,
      ratingBefore = Rating(1200),
      ratingDelta = 12,
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
}
