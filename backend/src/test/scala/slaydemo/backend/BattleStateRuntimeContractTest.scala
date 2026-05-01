package slaydemo.backend

import slaydemo.backend.battle.api.{BattleCommandRequest, BattleCommandVector}
import slaydemo.backend.battle.objects.*
import slaydemo.backend.battle.services.{
  BattleCommandOwnership,
  BattleCommandSubmitError,
  BattleFinishProjectionOutcome,
  BattleFinishProjector,
  BattleRoomLifecycleSink,
  BattleSessionLookup,
  BattleSessionSeed,
  BattleStateReadError,
  InMemoryBattleStateService
}
import slaydemo.backend.identity.objects.{DisplayName, PlayerHandle}

object BattleStateRuntimeContractTest {
  private val frontendSpawnPoints: Vector[BattleVector2] = Vector(
    BattleVector2(704.0, 800.0),
    BattleVector2(512.0, 544.0),
    BattleVector2(512.0, 1056.0),
    BattleVector2(1600.0, 320.0),
    BattleVector2(1600.0, 1280.0),
    BattleVector2(2048.0, 800.0)
  )

  def main(args: Array[String]): Unit = {
    currentStateLazilyBootstrapsFromSessionLookup()
    projectileTerminalReasonWireValuesMatchLegacy()
    spawnPointsMatchFrontendBattleMap()
    acceptCommandEnforcesOwnershipAndBotBoundaries()
    acceptedCommandSequenceIsMonotonic()
    walkingUsesFrontendBaseMoveSpeed()
    movementStopsAtArenaObstacle()
    sprintConsumesAndRecoversStamina()
    pistolCooldownReloadAndPickupAreAuthoritative()
    pistolDamageWaitsForVisibleProjectileTravel()
    heldPrimaryContinuesPistolFireDuringRuntimeAdvance()
    fixedStepCatchUpAdvancesHeldFireAcrossLargeReadGap()
    projectilesDoNotExpireAtOldShortRange()
    projectileObstacleTerminalUsesFirstIntersection()
    projectileTerminalHistoryIsBounded()
    battleEventHistoryIsBounded()
    replayFramesCaptureRuntimeAndFinish()
    replayFrameHistoryIsBounded()
    botRuntimeControlMovesAimsAndRespectsOpeningDelay()
    emptyMagazineStartsAutomaticReload()
    weaponAndMedkitPickupsMatchFrontendMap()
    medkitHealsDamagedPlayer()
    nonPistolWeaponsFireAuthoritatively()
    nonPistolActiveProjectilesDamageTargets()
    eliminationDoesNotRespawnAndFinishesBattle()
    eliminationClearsDeadPlayerRuntimeBeforeBattleFinish()
    dashAndBlinkRespectArenaCollision()
    skillCommandSuppressesPrimaryFire()
    freezeUsesFrontendContentConstants()
    expiringSlowFieldDoesNotAffectMovementOnExpiryTick()
    finishedStateClearsRuntimeButPreservesSlowFieldsWithoutInventingTimeoutWinner()
    finishedStateProjectsArtifactsOnce()
    finishedStateTracksPartialArtifactReadiness()
    ignoredFinishedCommandUsesStoredClientSequence()
    finishedStateMarksQueueRoomFinished()

    println("BattleState runtime contract checks passed")
  }

  private def projectileTerminalReasonWireValuesMatchLegacy(): Unit = {
    assertEquals("hit terminal reason wire", ProjectileTerminalReason.wireValue(ProjectileTerminalReason.Hit), "hit")
    assertEquals("blocked terminal reason wire", ProjectileTerminalReason.wireValue(ProjectileTerminalReason.Blocked), "obstacle")
    assertEquals("out-of-bounds terminal reason wire", ProjectileTerminalReason.wireValue(ProjectileTerminalReason.OutOfBounds), "world")
    assertEquals("expired terminal reason wire", ProjectileTerminalReason.wireValue(ProjectileTerminalReason.Expired), "ttl")
  }

  private def currentStateLazilyBootstrapsFromSessionLookup(): Unit = {
    val clock = TestClock(1_000L)
    val service = battleStateService(clock = clock)

    assertEquals(
      "unknown battle",
      service.currentState(BattleId("missing")),
      Left(BattleStateReadError.BattleNotFound)
    )

    val state = service.currentState(BattleId("battle-state-runtime")).fold(error => fail(s"state not found: $error"), value => value)

    assertEquals("battle id", state.battleId, BattleId("battle-state-runtime"))
    assertEquals("room id", state.roomId, RoomId("room-state-runtime"))
    assertEquals("phase", state.phase, BattlePhase.Active)
    assertEquals("artifact status", state.artifactStatus, BattleArtifactStatus.Pending)
    assertEquals("player ids", state.players.map(_.playerId), Vector(PlayerId("alice"), PlayerId("bot-one")))
    assertEquals("bot flags", state.players.map(_.isBot), Vector(false, true))
    assertEquals("initial weapons", state.players.map(_.currentWeaponKind), Vector(WeaponKind.Pistol, WeaponKind.Pistol))
  }

  private def spawnPointsMatchFrontendBattleMap(): Unit = {
    val clock = TestClock(1_000L)
    val seats = frontendSpawnPoints.zipWithIndex.map { case (_, index) =>
      seat(
        playerId = PlayerId(s"player-$index"),
        heroId = HeroId(s"hero-$index"),
        handle = PlayerHandle(s"Player$index"),
        displayName = DisplayName(s"Player $index"),
        seat = SeatIndex(index),
        isBot = index != 0,
        spawnPointIndex = Some(SpawnPointIndex(index))
      )
    }
    val service = battleStateService(clock = clock, seed = sessionSeedWithSeats(seats))

    val state = service.currentState(BattleId("battle-state-runtime"))
      .fold(error => fail(s"state not found: $error"), value => value)

    assertEquals("frontend spawn positions", state.players.map(_.position), frontendSpawnPoints)
  }

  private def acceptCommandEnforcesOwnershipAndBotBoundaries(): Unit = {
    val clock = TestClock(1_000L)
    val service = battleStateService(clock = clock)

    assertEquals(
      "wrong ticket",
      service.acceptCommand(command(PlayerId("alice"), TicketId("wrong-ticket"), 1L)),
      Left(BattleCommandSubmitError.CommandNotAuthorized)
    )
    assertEquals(
      "bot command",
      service.acceptCommand(command(PlayerId("bot-one"), TicketId("ticket-bot"), 2L)),
      Left(BattleCommandSubmitError.BotCommandsNotSupported)
    )

    val accepted = service.acceptCommand(command(PlayerId("alice"), TicketId("ticket-alice"), 3L))
      .fold(error => fail(s"valid command failed: $error"), value => value)

    assertEquals("accepted battle id", accepted.battleId, BattleId("battle-state-runtime"))
    assertEquals("accepted seq", accepted.acceptedCommandSeq, ClientCommandSeq(3L))
    assertEquals("accepted status", accepted.commandStatus, BattleCommandStatus.Applied)
    assertEquals("accepted reason", accepted.commandReason, None)

    val state = service.currentState(BattleId("battle-state-runtime")).fold(error => fail(s"state not found: $error"), value => value)
    val alice = state.players.find(_.playerId == PlayerId("alice")).getOrElse(fail("missing alice"))
    assertEquals("state stores last command seq", alice.lastClientCommandSeq, ClientCommandSeq(3L))
  }

  private def acceptedCommandSequenceIsMonotonic(): Unit = {
    val clock = TestClock(1_000L)
    val service = battleStateService(clock = clock)

    val first = service.acceptCommand(command(PlayerId("alice"), TicketId("ticket-alice"), 10L))
      .fold(error => fail(s"first command failed: $error"), value => value)
    assertEquals("first accepted seq", first.acceptedCommandSeq, ClientCommandSeq(10L))

    val older = service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 7L,
        movement = BattleCommandVector(-1.0, 0.0)
      )
    ).fold(error => fail(s"older command failed: $error"), value => value)

    assertEquals("older command still applies", older.commandStatus, BattleCommandStatus.Applied)
    assertEquals("accepted seq does not move backwards", older.acceptedCommandSeq, ClientCommandSeq(10L))

    val state = service.currentState(BattleId("battle-state-runtime")).fold(error => fail(s"state not found: $error"), value => value)
    val alice = state.players.find(_.playerId == PlayerId("alice")).getOrElse(fail("missing alice"))
    assertEquals("stored seq does not move backwards", alice.lastClientCommandSeq, ClientCommandSeq(10L))
  }

  private def movementStopsAtArenaObstacle(): Unit = {
    val clock = TestClock(1_000L)
    val service = battleStateService(
      clock = clock,
      seed = sessionSeed(aliceSpawnPointIndex = SpawnPointIndex(3))
    )

    val initial = service.currentState(BattleId("battle-state-runtime"))
      .fold(error => fail(s"state not found: $error"), value => value)
    val initialAlice = initial.players.find(_.playerId == PlayerId("alice")).getOrElse(fail("missing alice"))
    assertEquals("front right spawn", initialAlice.position, BattleVector2(1600.0, 320.0))

    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 4L,
        movement = BattleCommandVector(0.0, 1.0)
      )
    ).fold(error => fail(s"movement command failed: $error"), value => value)

    clock.now = 3_000L
    val moved = service.currentState(BattleId("battle-state-runtime"))
      .fold(error => fail(s"state not found: $error"), value => value)
    val alice = moved.players.find(_.playerId == PlayerId("alice")).getOrElse(fail("missing alice"))

    assertEquals("movement keeps x lane", alice.position.x, 1600.0)
    assert(
      alice.position.y > initialAlice.position.y && alice.position.y <= 590.0,
      s"expected alice to stop before right lane wall, got ${alice.position}"
    )
  }

  private def walkingUsesFrontendBaseMoveSpeed(): Unit = {
    val clock = TestClock(1_000L)
    val service = battleStateService(clock = clock)

    val initial = service.currentState(BattleId("battle-state-runtime"))
      .fold(error => fail(s"state not found: $error"), value => value)
    val initialAlice = initial.players.find(_.playerId == PlayerId("alice")).getOrElse(fail("missing alice"))

    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 5L,
        movement = BattleCommandVector(1.0, 0.0)
      )
    ).fold(error => fail(s"walk command failed: $error"), value => value)

    clock.now = 2_000L
    val walked = service.currentState(BattleId("battle-state-runtime"))
      .fold(error => fail(s"state not found: $error"), value => value)
    val walkedAlice = walked.players.find(_.playerId == PlayerId("alice")).getOrElse(fail("missing alice"))

    assertClose("walk distance", walkedAlice.position.x - initialAlice.position.x, 252.45, 0.1)
    assertEquals("walk does not consume stamina", walkedAlice.stamina, Stamina(100))
    assertEquals("walk is not sprint", walkedAlice.sprint, false)
  }

  private def sprintConsumesAndRecoversStamina(): Unit = {
    val clock = TestClock(1_000L)
    val service = battleStateService(clock = clock)

    val initial = service.currentState(BattleId("battle-state-runtime"))
      .fold(error => fail(s"state not found: $error"), value => value)
    val initialAlice = initial.players.find(_.playerId == PlayerId("alice")).getOrElse(fail("missing alice"))

    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 6L,
        movement = BattleCommandVector(1.0, 0.0),
        sprint = true
      )
    ).fold(error => fail(s"sprint command failed: $error"), value => value)

    clock.now = 2_000L
    val sprinted = service.currentState(BattleId("battle-state-runtime"))
      .fold(error => fail(s"state not found: $error"), value => value)
    val sprintedAlice = sprinted.players.find(_.playerId == PlayerId("alice")).getOrElse(fail("missing alice"))

    assertEquals("sprint remains effective while stamina exists", sprintedAlice.sprint, true)
    assertClose("sprint drains precise stamina", sprintedAlice.stamina.value, 62.38, 0.001)
    assertClose("sprint distance", sprintedAlice.position.x - initialAlice.position.x, 441.7875, 0.1)

    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 7L
      )
    ).fold(error => fail(s"idle command failed: $error"), value => value)

    clock.now = 3_000L
    val recovered = service.currentState(BattleId("battle-state-runtime"))
      .fold(error => fail(s"state not found: $error"), value => value)
    val recoveredAlice = recovered.players.find(_.playerId == PlayerId("alice")).getOrElse(fail("missing alice"))

    assertEquals("idle clears effective sprint", recoveredAlice.sprint, false)
    assertClose("idle recovers precise stamina", recoveredAlice.stamina.value, 86.14, 0.001)
    assertEquals("idle keeps x position", recoveredAlice.position.x, sprintedAlice.position.x)
  }

  private def pistolCooldownReloadAndPickupAreAuthoritative(): Unit = {
    val clock = TestClock(1_000L)
    val service = battleStateService(clock = clock)

    val pistolInitial = service.currentState(BattleId("battle-state-runtime"))
      .fold(error => fail(s"state not found: $error"), value => value)
    val initialAlice = pistolInitial.players.find(_.playerId == PlayerId("alice")).getOrElse(fail("missing initial alice"))

    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 7L,
        primaryHeld = true
      )
    ).fold(error => fail(s"first shot failed: $error"), value => value)

    val commandFrame = battleState(service, "after first shot command frame")
    val commandFrameAlice = commandFrame.players.find(_.playerId == PlayerId("alice")).getOrElse(fail("missing command frame alice"))
    assertEquals("first shot command records held primary", commandFrameAlice.primaryHeld, true)
    assertEquals("first shot command does not consume ammo", commandFrameAlice.weapons.head.ammoInMagazine, AmmoCount(12))
    assertEquals("first shot command does not create projectile", commandFrame.projectiles.exists(_.ownerHeroId == initialAlice.heroId), false)

    clock.now = 1_033L
    val afterFirstShotState = battleState(service, "after first shot runtime step")
    val afterFirstShotAlice = afterFirstShotState.players.find(_.playerId == PlayerId("alice")).getOrElse(fail("missing alice after first shot"))
    val afterFirstShot = afterFirstShotAlice.weapons.head
    assertEquals("first shot consumes ammo", afterFirstShot.ammoInMagazine, AmmoCount(11))
    assertEquals("first shot sets cooldown", afterFirstShot.fireCooldownMs, CooldownMillis(260))
    assertClose("pistol recoil moves shooter backward", initialAlice.position.x - afterFirstShotAlice.position.x, 3.6, 0.01)
    assertClose("pistol recoil keeps lane", afterFirstShotAlice.position.y, initialAlice.position.y, 0.01)
    val firstProjectile = afterFirstShotState.projectiles.lastOption.getOrElse(fail("missing first projectile"))
    assertEquals("pistol projectile damage", firstProjectile.damage, Damage(12))
    assertEquals("pistol projectile radius", firstProjectile.radius, Radius(8.0))
    assertClose("pistol projectile speed", math.hypot(firstProjectile.velocity.x, firstProjectile.velocity.y), 1400.0, 0.1)
    assert(
      firstProjectile.ttlMs.value < 30000L && firstProjectile.ttlMs.value >= 29900L,
      s"pistol projectile advances during its birth tick, ttl=${firstProjectile.ttlMs}"
    )
    assert(distanceBetweenForTest(initialAlice.position, firstProjectile.position) > 30.0, s"pistol projectile advances from muzzle birth, got ${firstProjectile.position}")

    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 8L,
        primaryHeld = true
      )
    ).fold(error => fail(s"second shot during cooldown failed: $error"), value => value)

    val duringCooldown = aliceWeapon(service, "during cooldown")
    assertEquals("cooldown blocks immediate second shot", duringCooldown.ammoInMagazine, AmmoCount(11))
    assertEquals("cooldown remains active", duringCooldown.fireCooldownMs, CooldownMillis(260))

    clock.now = 1_300L
    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 9L,
        primaryHeld = true
      )
    ).fold(error => fail(s"shot after cooldown failed: $error"), value => value)

    val afterCooldownShot = aliceWeapon(service, "after cooldown shot")
    assertEquals("shot after cooldown consumes ammo", afterCooldownShot.ammoInMagazine, AmmoCount(10))
    assert(
      afterCooldownShot.fireCooldownMs.value > 0 && afterCooldownShot.fireCooldownMs.value <= 260,
      s"shot after cooldown leaves active cooldown, got ${afterCooldownShot.fireCooldownMs}"
    )

    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 10L,
        reloadPressed = true
      )
    ).fold(error => fail(s"reload command failed: $error"), value => value)

    val reloadCommandFrame = aliceWeapon(service, "reload command frame")
    assertEquals("reload command records intent without immediate timer", reloadCommandFrame.reloadRemainingMs, CooldownMillis(0))

    clock.now = 1_333L
    val duringReload = aliceWeapon(service, "during reload")
    assertEquals("reload starts", duringReload.reloadRemainingMs, CooldownMillis(1000))

    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 11L,
        primaryHeld = true
      )
    ).fold(error => fail(s"fire during reload failed: $error"), value => value)

    val stillReloading = aliceWeapon(service, "still reloading")
    assertEquals("reload blocks firing", stillReloading.ammoInMagazine, AmmoCount(10))

    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 12L,
        primaryHeld = false
      )
    ).fold(error => fail(s"release during reload failed: $error"), value => value)

    clock.now = 2_500L
    val afterReload = aliceWeapon(service, "after reload")
    assertEquals("reload fills magazine", afterReload.ammoInMagazine, AmmoCount(12))
    assertEquals("reload consumes reserve", afterReload.reserveAmmo, Some(AmmoCount(46)))
    assertEquals("reload completes", afterReload.reloadRemainingMs, CooldownMillis(0))
  }

  private def pistolDamageWaitsForVisibleProjectileTravel(): Unit = {
    val clock = TestClock(1_000L)
    val service = battleStateService(
      clock = clock,
      seed = sessionSeed(
        aliceSpawnPointIndex = SpawnPointIndex(6),
        botSpawnPointIndex = SpawnPointIndex(7),
        secondIsBot = false
      )
    )

    val initial = battleState(service, "pistol visible projectile initial")
    val alice = initial.players.find(_.playerId == PlayerId("alice")).getOrElse(fail("missing alice"))
    val bot = initial.players.find(_.playerId == PlayerId("bot-one")).getOrElse(fail("missing bot"))

    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 90L,
        aim = BattleCommandVector(bot.position.x - alice.position.x, bot.position.y - alice.position.y),
        primaryHeld = true
      )
    ).fold(error => fail(s"pistol visible projectile shot failed: $error"), value => value)

    val commandFrame = battleState(service, "pistol visible projectile command frame")
    assertEquals("pistol direct hit command frame has no projectile", commandFrame.projectiles.exists(_.ownerHeroId == alice.heroId), false)

    clock.now = 1_033L
    val afterShot = battleState(service, "pistol visible projectile after shot")
    val afterShotBot = afterShot.players.find(_.playerId == PlayerId("bot-one")).getOrElse(fail("missing bot after shot"))
    val projectile = afterShot.projectiles.lastOption.getOrElse(fail("missing visible pistol projectile"))
    assertEquals("pistol direct hit does not damage in first runtime frame", afterShotBot.hp, HitPoints(100))
    assertEquals("pistol direct hit creates live projectile", projectile.projectileKind, ProjectileKind.PistolBullet)
    assertEquals("pistol direct hit has no terminal yet", afterShot.projectileTerminals.exists(_.projectileId == projectile.projectileId), false)

    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 91L,
        primaryHeld = false
      )
    ).fold(error => fail(s"pistol visible projectile release failed: $error"), value => value)

    clock.now = 1_300L
    val afterTravel = battleState(service, "pistol visible projectile after travel")
    val damagedBot = afterTravel.players.find(_.playerId == PlayerId("bot-one")).getOrElse(fail("missing bot after travel"))
    val terminal = afterTravel.projectileTerminals.lastOption.getOrElse(fail("missing pistol hit terminal after travel"))
    assertEquals("pistol projectile damages after travel", damagedBot.hp, HitPoints(88))
    assertEquals("pistol projectile terminal kind", terminal.projectileKind, ProjectileKind.PistolBullet)
    assertEquals("pistol projectile terminal reason", terminal.reason, ProjectileTerminalReason.Hit)
    assertEquals("pistol projectile terminal target", terminal.targetPlayerId, Some(PlayerId("bot-one")))
    assertClose(
      "pistol hit terminal uses target collision contact",
      distanceBetweenForTest(terminal.terminalPosition, damagedBot.position),
      32.0,
      0.5
    )
    assert(
      distanceBetweenForTest(terminal.start, terminal.terminalPosition) < distanceBetweenForTest(terminal.start, damagedBot.position),
      s"pistol hit terminal should stop at first target contact, terminal=${terminal.terminalPosition}, target=${damagedBot.position}"
    )
    assert(
      terminal.end.x > terminal.terminalPosition.x,
      s"pistol hit terminal should preserve full segment end beyond hit point, terminal=${terminal.terminalPosition}, end=${terminal.end}"
    )
  }

  private def heldPrimaryContinuesPistolFireDuringRuntimeAdvance(): Unit = {
    val clock = TestClock(1_000L)
    val service = battleStateService(clock = clock)
    battleState(service, "held pistol initial")

    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 95L,
        primaryHeld = true
      )
    ).fold(error => fail(s"held pistol first command failed: $error"), value => value)

    val afterCommand = battleState(service, "held pistol command frame")
    val afterCommandAlice = afterCommand.players.find(_.playerId == PlayerId("alice")).getOrElse(fail("missing held pistol alice"))
    val afterCommandWeapon = afterCommandAlice.weapons.head
    assertEquals("held pistol command keeps primary held", afterCommandAlice.primaryHeld, true)
    assertEquals("held pistol command does not fire before runtime step", afterCommandWeapon.ammoInMagazine, AmmoCount(12))
    assertEquals("held pistol command creates no projectile", afterCommand.projectiles.count(_.projectileKind == ProjectileKind.PistolBullet), 0)

    clock.now = 1_300L
    val afterFirstCooldown = battleState(service, "held pistol after first cooldown")
    val afterFirstCooldownAlice = afterFirstCooldown.players.find(_.playerId == PlayerId("alice")).getOrElse(fail("missing held pistol alice after cooldown"))
    val afterFirstCooldownWeapon = afterFirstCooldownAlice.weapons.head
    assert(
      afterFirstCooldownWeapon.ammoInMagazine == AmmoCount(10),
      s"held pistol runtime fires second shot: ammo=${afterFirstCooldownWeapon.ammoInMagazine}, cooldown=${afterFirstCooldownWeapon.fireCooldownMs}, primaryHeld=${afterFirstCooldownAlice.primaryHeld}, tick=${afterFirstCooldown.tick}, elapsed=${afterFirstCooldown.elapsedMs}"
    )
    assert(
      afterFirstCooldown.projectiles.count(_.projectileKind == ProjectileKind.PistolBullet) >= 2,
      s"expected at least two live/visible pistol projectiles, got ${afterFirstCooldown.projectiles.map(_.projectileId)}"
    )

    clock.now = 1_600L
    val afterSecondCooldown = battleState(service, "held pistol after second cooldown")
    val afterSecondCooldownWeapon = afterSecondCooldown.players.find(_.playerId == PlayerId("alice")).getOrElse(fail("missing held pistol alice after second cooldown")).weapons.head
    assertEquals("held pistol runtime fires third shot", afterSecondCooldownWeapon.ammoInMagazine, AmmoCount(9))
  }

  private def fixedStepCatchUpAdvancesHeldFireAcrossLargeReadGap(): Unit = {
    val clock = TestClock(1_000L)
    val service = battleStateService(
      clock = clock,
      seed = sessionSeed(
        aliceSpawnPointIndex = SpawnPointIndex(0),
        botSpawnPointIndex = SpawnPointIndex(8),
        secondIsBot = false
      )
    )
    battleState(service, "fixed-step held fire initial")

    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 99L,
        primaryHeld = true
      )
    ).fold(error => fail(s"fixed-step held pistol command failed: $error"), value => value)

    clock.now = 2_000L
    val afterGap = battleState(service, "fixed-step held fire after one second gap")
    val alice = afterGap.players.find(_.playerId == PlayerId("alice")).getOrElse(fail("missing fixed-step alice"))
    val pistol = alice.weapons.head
    val alicePistolProjectiles =
      afterGap.projectiles.count(projectile => projectile.ownerHeroId == alice.heroId && projectile.projectileKind == ProjectileKind.PistolBullet) +
        afterGap.projectileTerminals.count(terminal => terminal.ownerHeroId == alice.heroId && terminal.projectileKind == ProjectileKind.PistolBullet)

    assert(
      pistol.ammoInMagazine.value <= 8,
      s"expected fixed-step catch-up to fire across multiple pistol cooldowns, ammo=${pistol.ammoInMagazine}"
    )
    assert(
      alicePistolProjectiles >= 4,
      s"expected at least four pistol projectiles/terminals after fixed-step catch-up, got $alicePistolProjectiles"
    )
    assert(afterGap.tick.value >= 30L, s"expected battle tick to reach one second, got ${afterGap.tick}")
  }

  private def projectilesDoNotExpireAtOldShortRange(): Unit = {
    val clock = TestClock(1_000L)
    val service = battleStateService(
      clock = clock,
      seed = sessionSeed(
        aliceSpawnPointIndex = SpawnPointIndex(0),
        botSpawnPointIndex = SpawnPointIndex(2)
      )
    )
    battleState(service, "long range projectile initial")

    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 96L,
        aim = BattleCommandVector(1.0, 0.0),
        primaryHeld = true
      )
    ).fold(error => fail(s"long range pistol shot failed: $error"), value => value)
    clock.now = 1_033L
    battleState(service, "long range projectile first runtime step")
    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 97L,
        primaryHeld = false
      )
    ).fold(error => fail(s"long range pistol release failed: $error"), value => value)

    clock.now = 2_000L
    val afterOldShortRange = battleState(service, "long range projectile after old ttl")
    val ownerProjectiles = afterOldShortRange.projectiles.filter(projectile =>
      projectile.ownerHeroId == HeroId("hero-alice") && projectile.projectileKind == ProjectileKind.PistolBullet
    )
    assertEquals("projectile remains live past old short ttl", ownerProjectiles.length, 1)
    assertEquals("projectile did not emit expired terminal", afterOldShortRange.projectileTerminals.exists(_.reason == ProjectileTerminalReason.Expired), false)
    assert(afterOldShortRange.projectileTerminals.forall(_.targetPlayerId.isEmpty), "long range projectile should not hit the off-lane bot")
  }

  private def projectileObstacleTerminalUsesFirstIntersection(): Unit = {
    val clock = TestClock(1_000L)
    val service = battleStateService(
      clock = clock,
      seed = sessionSeed(
        aliceSpawnPointIndex = SpawnPointIndex(5),
        botSpawnPointIndex = SpawnPointIndex(4),
        secondIsBot = false
      )
    )
    val initial = battleState(service, "exact projectile block initial")
    val alice = initial.players.find(_.playerId == PlayerId("alice")).getOrElse(fail("missing exact block alice"))

    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 198L,
        aim = BattleCommandVector(1.0, 0.0),
        primaryHeld = true
      )
    ).fold(error => fail(s"exact block shot failed: $error"), value => value)
    clock.now = 1_033L
    battleState(service, "exact projectile first runtime step")
    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 199L,
        aim = BattleCommandVector(1.0, 0.0),
        primaryHeld = false
      )
    ).fold(error => fail(s"exact block release failed: $error"), value => value)

    clock.now = 1_500L
    val blocked = battleState(service, "exact projectile block terminal")
    val terminal = blocked.projectileTerminals
      .find(terminal => terminal.ownerHeroId == alice.heroId && terminal.projectileKind == ProjectileKind.PistolBullet)
      .getOrElse(fail("missing exact blocked pistol terminal"))

    assertEquals("exact projectile block reason", terminal.reason, ProjectileTerminalReason.Blocked)
    assertClose("exact projectile block terminal x", terminal.terminalPosition.x, 2488.0, 0.001)
    assertClose("exact projectile block terminal y", terminal.terminalPosition.y, 800.0, 0.001)
    assert(
      terminal.end.x > terminal.terminalPosition.x,
      s"blocked terminal should preserve full segment end beyond blocker, terminal=${terminal.terminalPosition}, end=${terminal.end}"
    )
  }

  private def projectileTerminalHistoryIsBounded(): Unit = {
    val clock = TestClock(1_000L)
    val service = battleStateService(
      clock = clock,
      seed = sessionSeed(
        aliceSpawnPointIndex = SpawnPointIndex(5),
        botSpawnPointIndex = SpawnPointIndex(1)
      )
    )
    battleState(service, "terminal retention initial")

    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 130L,
        movement = BattleCommandVector(1.0, 0.0)
      )
    ).fold(error => fail(s"shotgun pickup movement failed: $error"), value => value)

    clock.now = 1_300L
    val pickedUp = battleState(service, "terminal retention shotgun pickup")
    val alice = pickedUp.players.find(_.playerId == PlayerId("alice")).getOrElse(fail("missing alice after shotgun pickup"))
    val shotgunIndex = alice.weapons.indexWhere(_.weaponKind == WeaponKind.Shotgun)
    assert(shotgunIndex >= 0, "shotgun pickup should add Shotgun")

    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 131L,
        switchWeaponIndex = Some(shotgunIndex)
      )
    ).fold(error => fail(s"terminal retention switch to shotgun failed: $error"), value => value)

    var firstTerminalProjectileIds = Set.empty[ProjectileId]
    (0 until 14).foreach { shot =>
      val fireSeq = 140L + shot.toLong * 2L
      service.acceptCommand(
        command(
          playerId = PlayerId("alice"),
          ticketId = TicketId("ticket-alice"),
          seq = fireSeq,
          aim = BattleCommandVector(1.0, 0.0),
          primaryHeld = true
        )
      ).fold(error => fail(s"terminal retention shotgun shot $shot failed: $error"), value => value)
      clock.now = clock.now + 33L
      battleState(service, s"terminal retention shotgun shot $shot runtime step")
      service.acceptCommand(
        command(
          playerId = PlayerId("alice"),
          ticketId = TicketId("ticket-alice"),
          seq = fireSeq + 1L,
          aim = BattleCommandVector(1.0, 0.0),
          primaryHeld = false
        )
      ).fold(error => fail(s"terminal retention shotgun release $shot failed: $error"), value => value)

      clock.now = clock.now + 1_367L
      val advanced = battleState(service, s"terminal retention after shot $shot")
      if shot == 0 then {
        firstTerminalProjectileIds = advanced.projectileTerminals.map(_.projectileId).toSet
        assertEquals("first shotgun shot emits five terminals", firstTerminalProjectileIds.size, 5)
      }
    }

    val retained = battleState(service, "terminal retention final")
    assertEquals("projectile terminals are capped", retained.projectileTerminals.length, 64)
    assertEquals(
      "oldest shotgun terminals are pruned",
      retained.projectileTerminals.exists(terminal => firstTerminalProjectileIds.contains(terminal.projectileId)),
      false
    )
  }

  private def battleEventHistoryIsBounded(): Unit = {
    val clock = TestClock(1_000L)
    val service = battleStateService(
      clock = clock,
      battleDuration = DurationMillis(240_000L),
      seed = sessionSeed(secondIsBot = false)
    )
    battleState(service, "event retention initial")

    var firstEventId: Option[BattleEventId] = None
    (0 until 14).foreach { index =>
      clock.now = 1_100L + index.toLong * 10_050L
      val state = battleState(service, s"event retention pickup $index")
      val latestEvent = state.events.lastOption.getOrElse(fail(s"missing pickup event $index"))
      assertEquals(s"event retention event kind $index", latestEvent.eventKind, BattleEventKind.Pickup)
      if index == 0 then firstEventId = Some(latestEvent.eventId)
    }

    val retained = battleState(service, "event retention final")
    assertEquals("battle events are capped", retained.events.length, 12)
    assertEquals(
      "oldest battle event is pruned",
      retained.events.exists(event => firstEventId.contains(event.eventId)),
      false
    )
  }

  private def replayFramesCaptureRuntimeAndFinish(): Unit = {
    val clock = TestClock(1_000L)
    val service = battleStateService(
      clock = clock,
      battleDuration = DurationMillis(2_200L),
      seed = sessionSeed(secondIsBot = false)
    )
    val initial = battleState(service, "replay capture initial")
    assertEquals("initial replay frame count", initial.replayFrames.length, 1)
    assertEquals("initial replay frame elapsed", initial.replayFrames.head.elapsedMs, ElapsedMillis(0L))
    assert(initial.replayFrames.head.pickups.nonEmpty, "initial replay frame should capture pickups")

    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 700L,
        aim = BattleCommandVector(1.0, 0.0),
        primaryHeld = true
      )
    ).fold(error => fail(s"replay capture fire failed: $error"), value => value)

    clock.now = 1_033L
    val eventFrameState = battleState(service, "replay capture event frame")
    assert(
      eventFrameState.replayFrames.exists(_.elapsedMs == ElapsedMillis(33L)),
      s"expected pickup event replay frame at 33ms, got ${eventFrameState.replayFrames.map(_.elapsedMs.value)}"
    )
    assert(
      eventFrameState.replayFrames.exists(_.projectiles.nonEmpty),
      "event replay frame should capture live projectile state"
    )

    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 701L,
        aim = BattleCommandVector(1.0, 0.0),
        primaryHeld = false
      )
    ).fold(error => fail(s"replay capture release failed: $error"), value => value)

    clock.now = 2_050L
    val sampled = battleState(service, "replay capture interval frame")
    assert(
      sampled.replayFrames.exists(_.elapsedMs.value >= 1_000L),
      s"expected interval replay frame after 1000ms, got ${sampled.replayFrames.map(_.elapsedMs.value)}"
    )

    clock.now = 3_300L
    val finished = battleState(service, "replay capture final frame")
    assertEquals("replay capture finished phase", finished.phase, BattlePhase.Finished)
    assertEquals("replay capture first frame preserved", finished.replayFrames.head.elapsedMs, ElapsedMillis(0L))
    assertEquals("replay capture final frame elapsed", finished.replayFrames.last.elapsedMs, ElapsedMillis(2_200L))
    assertEquals("replay capture final frame clears projectiles", finished.replayFrames.last.projectiles, Vector.empty)
  }

  private def replayFrameHistoryIsBounded(): Unit = {
    val clock = TestClock(1_000L)
    val service = battleStateService(
      clock = clock,
      battleDuration = DurationMillis(60_000L),
      seed = sessionSeed(secondIsBot = false)
    )
    battleState(service, "replay retention initial")

    (1 to 40).foreach { step =>
      clock.now = 1_000L + step.toLong * 1_000L
      battleState(service, s"replay retention step $step")
    }

    val retained = battleState(service, "replay retention final")
    assertEquals("replay frame history is capped", retained.replayFrames.length, 32)
    assertEquals("replay frame retention preserves initial", retained.replayFrames.head.elapsedMs, ElapsedMillis(0L))
    assert(
      !retained.replayFrames.exists(_.elapsedMs == ElapsedMillis(1_000L)),
      s"old replay interval frame should be pruned, got ${retained.replayFrames.map(_.elapsedMs.value)}"
    )
  }

  private def botRuntimeControlMovesAimsAndRespectsOpeningDelay(): Unit = {
    val clock = TestClock(1_000L)
    val service = battleStateService(
      clock = clock,
      seed = sessionSeed(
        aliceSpawnPointIndex = SpawnPointIndex(6),
        botSpawnPointIndex = SpawnPointIndex(7)
      )
    )

    val initial = battleState(service, "bot control initial")
    val initialBot = initial.players.find(_.playerId == PlayerId("bot-one")).getOrElse(fail("missing initial bot"))

    clock.now = 2_000L
    val beforeOpeningDelay = battleState(service, "bot control before opening delay")
    val movedBot = beforeOpeningDelay.players.find(_.playerId == PlayerId("bot-one")).getOrElse(fail("missing moved bot"))
    assert(
      distanceBetweenForTest(initialBot.position, movedBot.position) > 0.25,
      s"expected bot to move without client commands, initial=${initialBot.position}, moved=${movedBot.position}"
    )
    assert(vectorLengthForTest(movedBot.movement) > 0.9, s"expected bot movement intent, got ${movedBot.movement}")
    assert(vectorLengthForTest(movedBot.aim) > 0.9, s"expected bot aim intent, got ${movedBot.aim}")
    assertEquals("bot does not fire before human opening delay", beforeOpeningDelay.projectiles.exists(_.ownerHeroId == movedBot.heroId), false)
    assertEquals("bot primary held before opening delay", movedBot.primaryHeld, false)

    val nearOpeningDelay = (3_000L to 15_950L by 250L).foldLeft(beforeOpeningDelay) { (_, now) =>
      clock.now = now
      val state = battleState(service, s"bot control before opening delay $now")
      val bot = state.players.find(_.playerId == PlayerId("bot-one")).getOrElse(fail("missing waiting bot"))
      assertEquals("bot does not fire while human opening delay remains active", state.projectiles.exists(_.ownerHeroId == bot.heroId), false)
      state
    }
    val waitingBot = nearOpeningDelay.players.find(_.playerId == PlayerId("bot-one")).getOrElse(fail("missing waiting bot"))
    assertEquals("bot still does not hold primary before opening delay", waitingBot.primaryHeld, false)

    clock.now = 17_100L
    val afterOpeningDelay = battleState(service, "bot control after opening delay")
    val firingBot = afterOpeningDelay.players.find(_.playerId == PlayerId("bot-one")).getOrElse(fail("missing firing bot"))
    val firingAlice = afterOpeningDelay.players.find(_.playerId == PlayerId("alice")).getOrElse(fail("missing firing target"))
    val botProjectiles = afterOpeningDelay.projectiles.filter(_.ownerHeroId == firingBot.heroId)
    val botTerminals = afterOpeningDelay.projectileTerminals.filter(_.ownerHeroId == firingBot.heroId)
    val firedProjectileKind =
      botProjectiles.headOption.map(_.projectileKind).orElse(botTerminals.headOption.map(_.projectileKind))
    assert(
      botProjectiles.nonEmpty || botTerminals.nonEmpty,
      s"expected bot to fire after human opening delay, elapsed=${afterOpeningDelay.elapsedMs.value}, primaryHeld=${firingBot.primaryHeld}, distance=${distanceBetweenForTest(firingBot.position, firingAlice.position)}, bot=${firingBot.position}, target=${firingAlice.position}, active=${botProjectiles.length}, terminals=${botTerminals.length}, weapon=${firingBot.weapons.headOption}"
    )
    assertEquals("bot primary held after opening delay", firingBot.primaryHeld, true)
    assertEquals("bot projectile kind", firedProjectileKind, Some(ProjectileKind.PistolBullet))
    val firingBotWeapon = firingBot.weapons.headOption.getOrElse(fail("missing firing bot weapon"))
    assert(firingBotWeapon.ammoInMagazine.value > 0, s"bot should still have ammo after early firing window, weapon=$firingBotWeapon")
    assert(
      firingBotWeapon.ammoInMagazine.value < firingBotWeapon.magazineSize.value,
      s"bot should have spent ammo after firing, weapon=$firingBotWeapon"
    )
    assertEquals("bot does not reload until magazine is empty", firingBotWeapon.reloadRemainingMs, CooldownMillis(0))
    assertEquals("bot external commands remain rejected", service.acceptCommand(command(PlayerId("bot-one"), TicketId("ticket-alice"), 98L)), Left(BattleCommandSubmitError.BotCommandsNotSupported))
  }

  private def emptyMagazineStartsAutomaticReload(): Unit = {
    val clock = TestClock(1_000L)
    val service = battleStateService(clock = clock)
    battleState(service, "auto reload initial")

    (0 until 12).foreach { shot =>
      clock.now = 1_000L + shot.toLong * 300L
      service.acceptCommand(
        command(
          playerId = PlayerId("alice"),
          ticketId = TicketId("ticket-alice"),
          seq = 100L + shot,
          primaryHeld = true
        )
      ).fold(error => fail(s"auto reload shot $shot failed: $error"), value => value)
    }

    val emptied = aliceWeapon(service, "empty magazine")
    assertEquals("last shot empties magazine", emptied.ammoInMagazine, AmmoCount(0))
    assert(
      emptied.reloadRemainingMs.value > 0 && emptied.reloadRemainingMs.value <= 1000,
      s"empty magazine starts reload, got ${emptied.reloadRemainingMs}"
    )
    assertEquals("reserve waits until reload completes", emptied.reserveAmmo, Some(AmmoCount(48)))

    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 120L,
        primaryHeld = false
      )
    ).fold(error => fail(s"auto reload release failed: $error"), value => value)

    clock.now = 5_400L
    val reloaded = aliceWeapon(service, "auto reload completed")
    assertEquals("auto reload fills magazine", reloaded.ammoInMagazine, AmmoCount(12))
    assertEquals("auto reload consumes reserve", reloaded.reserveAmmo, Some(AmmoCount(36)))
    assertEquals("auto reload clears timer", reloaded.reloadRemainingMs, CooldownMillis(0))
  }

  private def weaponAndMedkitPickupsMatchFrontendMap(): Unit = {
    val clock = TestClock(1_000L)
    val service = battleStateService(
      clock = clock,
      seed = sessionSeed(aliceSpawnPointIndex = SpawnPointIndex(3))
    )

    val initial = service.currentState(BattleId("battle-state-runtime"))
      .fold(error => fail(s"state not found: $error"), value => value)
    assertEquals(
      "initial weapon pickups",
      initial.pickups.filter(_.pickupKind == PickupKind.Weapon).map(pickup => pickup.pickupId -> pickup.weaponKind -> pickup.position),
      Vector(
        PickupId("pickup-rocket-1") -> Some(WeaponKind.RocketLauncher) -> BattleVector2(1280.0, 256.0),
        PickupId("pickup-gatling-1") -> Some(WeaponKind.Gatling) -> BattleVector2(704.0, 800.0),
        PickupId("pickup-shotgun-1") -> Some(WeaponKind.Shotgun) -> BattleVector2(1856.0, 800.0),
        PickupId("pickup-rocket-2") -> Some(WeaponKind.RocketLauncher) -> BattleVector2(1280.0, 1344.0),
        PickupId("pickup-gatling-2") -> Some(WeaponKind.Gatling) -> BattleVector2(448.0, 800.0),
        PickupId("pickup-shotgun-2") -> Some(WeaponKind.Shotgun) -> BattleVector2(2112.0, 800.0)
      )
    )
    assertEquals(
      "initial medkit pickups",
      initial.pickups.filter(_.pickupKind == PickupKind.Medkit).map(pickup => pickup.pickupId -> pickup.position),
      Vector(
        PickupId("pickup-medkit-1") -> BattleVector2(960.0, 608.0),
        PickupId("pickup-medkit-2") -> BattleVector2(1600.0, 992.0)
      )
    )

    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 12L,
        movement = BattleCommandVector(-5.0, -1.0)
      )
    ).fold(error => fail(s"rocket pickup movement failed: $error"), value => value)

    clock.now = 2_300L
    val pickedUpRocket = service.currentState(BattleId("battle-state-runtime"))
      .fold(error => fail(s"state not found after rocket pickup: $error"), value => value)
    val rocketAlice = pickedUpRocket.players.find(_.playerId == PlayerId("alice")).getOrElse(fail("missing alice after rocket pickup"))
    val rocket = rocketAlice.weapons.find(_.weaponKind == WeaponKind.RocketLauncher).getOrElse(fail("missing rocket launcher"))
    val rocketPickup = pickedUpRocket.pickups.find(_.pickupId == PickupId("pickup-rocket-1")).getOrElse(fail("missing rocket pickup"))

    assertEquals("rocket pickup consumed", rocketPickup.available, false)
    assert(
      rocketPickup.respawnMs.value > 9800L && rocketPickup.respawnMs.value <= 10000L,
      s"rocket pickup respawns, got ${rocketPickup.respawnMs}"
    )
    assertEquals("rocket launcher magazine", rocket.ammoInMagazine, AmmoCount(1))
    assertEquals("rocket launcher reserve", rocket.reserveAmmo, Some(AmmoCount(3)))
    assertEquals("rocket launcher cooldown clear", rocket.fireCooldownMs, CooldownMillis(0))
    val rocketPickupEvent = pickedUpRocket.events.lastOption.getOrElse(fail("missing rocket pickup event"))
    assertEquals("rocket pickup event kind", rocketPickupEvent.eventKind, BattleEventKind.Pickup)
    assertEquals(
      "rocket pickup event id includes pickup id",
      rocketPickupEvent.eventId,
      BattleEventId(s"pickup-${rocketPickupEvent.elapsedMs.value}-pickup-rocket-1-alice")
    )
    assert(
      rocketPickupEvent.message.contains(WeaponKind.wireValue(WeaponKind.RocketLauncher)),
      s"rocket pickup event should name weapon, got ${rocketPickupEvent.message}"
    )

    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 14L,
        switchWeaponIndex = Some(1)
      )
    ).fold(error => fail(s"switch to rocket failed: $error"), value => value)

    val switchedToRocket = service.currentState(BattleId("battle-state-runtime"))
      .fold(error => fail(s"state not found after switch to rocket: $error"), value => value)
    val rocketSwitchedAlice = switchedToRocket.players.find(_.playerId == PlayerId("alice")).getOrElse(fail("missing alice after switch to rocket"))

    assertEquals("switch index selects rocket", rocketSwitchedAlice.currentWeaponIndex, 1)
    assertEquals("switch index syncs current kind", rocketSwitchedAlice.currentWeaponKind, WeaponKind.RocketLauncher)

    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 15L,
        switchWeaponDirection = 1
      )
    ).fold(error => fail(s"switch direction failed: $error"), value => value)

    val switchedBack = service.currentState(BattleId("battle-state-runtime"))
      .fold(error => fail(s"state not found after switch direction: $error"), value => value)
    val switchedBackAlice = switchedBack.players.find(_.playerId == PlayerId("alice")).getOrElse(fail("missing alice after switch direction"))

    assertEquals("switch direction cycles to pistol", switchedBackAlice.currentWeaponIndex, 0)
    assertEquals("switch direction syncs current kind", switchedBackAlice.currentWeaponKind, WeaponKind.Pistol)

    val medkitClock = TestClock(1_000L)
    val medkitService = battleStateService(
      clock = medkitClock,
      seed = sessionSeed(aliceSpawnPointIndex = SpawnPointIndex(4))
    )
    medkitService.currentState(BattleId("battle-state-runtime"))
      .fold(error => fail(s"medkit state not found: $error"), value => value)

    medkitService.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 13L,
        movement = BattleCommandVector(0.0, -1.0)
      )
    ).fold(error => fail(s"medkit pickup movement failed: $error"), value => value)

    medkitClock.now = 2_000L
    val medkitPickedUp = medkitService.currentState(BattleId("battle-state-runtime"))
      .fold(error => fail(s"state not found after medkit pickup: $error"), value => value)
    val medkitPickup = medkitPickedUp.pickups.find(_.pickupId == PickupId("pickup-medkit-2")).getOrElse(fail("missing medkit"))

    assertEquals("medkit consumed", medkitPickup.available, false)
    assertEquals("medkit respawns", medkitPickup.respawnMs, DurationMillis(10000L))
    val medkitPickupEvent = medkitPickedUp.events.lastOption.getOrElse(fail("missing medkit pickup event"))
    assertEquals("medkit pickup event kind", medkitPickupEvent.eventKind, BattleEventKind.Heal)
    assertEquals(
      "medkit pickup event id includes pickup id",
      medkitPickupEvent.eventId,
      BattleEventId(s"heal-${medkitPickupEvent.elapsedMs.value}-pickup-medkit-2-alice")
    )
    assert(medkitPickupEvent.message.contains("medkit"), s"medkit pickup event should stay medkit-specific, got ${medkitPickupEvent.message}")
  }

  private def nonPistolWeaponsFireAuthoritatively(): Unit = {
    val gatlingClock = TestClock(1_100L)
    val gatlingService = battleStateService(clock = gatlingClock)
    val gatlingInitial = battleState(gatlingService, "gatling initial pickup")
    val gatlingInitialAlice = gatlingInitial.players.find(_.playerId == PlayerId("alice")).getOrElse(fail("missing gatling alice"))
    assertEquals("spawn pickup adds gatling", gatlingInitialAlice.weapons.exists(_.weaponKind == WeaponKind.Gatling), true)

    gatlingService.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 21L,
        switchWeaponIndex = Some(1)
      )
    ).fold(error => fail(s"switch to gatling failed: $error"), value => value)
    val gatlingReady = battleState(gatlingService, "gatling ready before fire")
    val gatlingReadyAlice = gatlingReady.players.find(_.playerId == PlayerId("alice")).getOrElse(fail("missing gatling alice before fire"))
    gatlingService.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 22L,
        primaryHeld = true
      )
    ).fold(error => fail(s"gatling fire failed: $error"), value => value)

    gatlingClock.now = 1_133L
    val afterGatlingFire = battleState(gatlingService, "after gatling fire")
    val gatlingAlice = afterGatlingFire.players.find(_.playerId == PlayerId("alice")).getOrElse(fail("missing alice after gatling"))
    val gatlingWeapon = gatlingAlice.weapons(gatlingAlice.currentWeaponIndex)
    val gatlingProjectile = afterGatlingFire.projectiles.lastOption.getOrElse(fail("missing gatling projectile"))
    assertEquals("gatling current kind", gatlingAlice.currentWeaponKind, WeaponKind.Gatling)
    assertEquals("gatling adds heat", gatlingWeapon.heat, 8)
    assertEquals("gatling reserve is zero", gatlingWeapon.reserveAmmo, Some(AmmoCount(0)))
    assertEquals("gatling cooldown", gatlingWeapon.fireCooldownMs, CooldownMillis(72))
    assertEquals("gatling projectile kind", gatlingProjectile.projectileKind, ProjectileKind.GatlingBullet)
    assertEquals("gatling projectile damage", gatlingProjectile.damage, Damage(5))
    assertEquals("gatling projectile radius", gatlingProjectile.radius, Radius(7.0))
    assert(
      gatlingProjectile.ttlMs.value < 30000L && gatlingProjectile.ttlMs.value >= 29900L,
      s"gatling projectile should retain long authoritative lifetime after birth tick, ttl=${gatlingProjectile.ttlMs}"
    )
    assertClose("gatling single projectile has no spread x", gatlingProjectile.velocity.x, 980.0, 0.001)
    assertClose("gatling single projectile has no spread y", gatlingProjectile.velocity.y, 0.0, 0.001)
    assertClose("gatling recoil moves shooter backward", gatlingReadyAlice.position.x - gatlingAlice.position.x, 1.44, 0.01)
    assertClose("gatling recoil keeps lane", gatlingAlice.position.y, gatlingReadyAlice.position.y, 0.01)
    assert(distanceBetweenForTest(gatlingAlice.position, gatlingProjectile.position) > 29.0, s"gatling projectile advances from muzzle birth, got ${gatlingProjectile.position}")

    gatlingService.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 23L,
        primaryHeld = true
      )
    ).fold(error => fail(s"gatling cooldown fire failed: $error"), value => value)
    assertEquals("gatling cooldown blocks second projectile", battleState(gatlingService, "gatling cooldown").projectiles.length, 1)

    gatlingClock.now = 1_400L
    val afterGatlingHeld = battleState(gatlingService, "gatling held runtime fire")
    val gatlingHeldAlice = afterGatlingHeld.players.find(_.playerId == PlayerId("alice")).getOrElse(fail("missing alice after held gatling"))
    val gatlingHeldWeapon = gatlingHeldAlice.weapons(gatlingHeldAlice.currentWeaponIndex)
    val gatlingHeldProjectiles = afterGatlingHeld.projectiles.filter(_.projectileKind == ProjectileKind.GatlingBullet)
    assert(gatlingHeldProjectiles.length >= 2, s"expected held Gatling to create more projectiles, got ${gatlingHeldProjectiles.length}")
    assertEquals("gatling held projectile ids are unique", gatlingHeldProjectiles.map(_.projectileId).distinct.length, gatlingHeldProjectiles.length)
    assert(gatlingHeldWeapon.heat >= 8, s"expected held Gatling heat to include the runtime shot, got ${gatlingHeldWeapon.heat}")

    val holsteredHeatBefore = gatlingHeldWeapon.heat
    gatlingService.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 24L,
        primaryHeld = false,
        switchWeaponIndex = Some(0)
      )
    ).fold(error => fail(s"switch away from gatling failed: $error"), value => value)

    gatlingClock.now = 3_400L
    val afterHolsteredCooldown = battleState(gatlingService, "gatling holstered heat cooldown")
    val holsteredAlice = afterHolsteredCooldown.players.find(_.playerId == PlayerId("alice")).getOrElse(fail("missing alice after holstered gatling cooldown"))
    val holsteredGatling = holsteredAlice.weapons.find(_.weaponKind == WeaponKind.Gatling).getOrElse(fail("missing holstered Gatling"))
    assertEquals("gatling switched back to pistol", holsteredAlice.currentWeaponKind, WeaponKind.Pistol)
    assert(
      holsteredGatling.heat < holsteredHeatBefore,
      s"expected holstered Gatling heat to cool, before=$holsteredHeatBefore, after=${holsteredGatling.heat}"
    )
    assertEquals("holstered Gatling cools to zero", holsteredGatling.heat, 0)

    val rocketClock = TestClock(1_000L)
    val rocketService = battleStateService(
      clock = rocketClock,
      seed = sessionSeed(aliceSpawnPointIndex = SpawnPointIndex(3))
    )
    rocketService.currentState(BattleId("battle-state-runtime"))
      .fold(error => fail(s"rocket state not found: $error"), value => value)
    rocketService.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 24L,
        movement = BattleCommandVector(-5.0, -1.0)
      )
    ).fold(error => fail(s"rocket pickup move failed: $error"), value => value)
    rocketClock.now = 2_300L
    val rocketPickedUp = battleState(rocketService, "rocket picked up")
    val rocketAlice = rocketPickedUp.players.find(_.playerId == PlayerId("alice")).getOrElse(fail("missing rocket alice"))
    val rocketIndex = rocketAlice.weapons.indexWhere(_.weaponKind == WeaponKind.RocketLauncher)
    assert(rocketIndex >= 0, "rocket pickup should add RocketLauncher")

    rocketService.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 25L,
        switchWeaponIndex = Some(rocketIndex)
      )
    ).fold(error => fail(s"switch to rocket failed: $error"), value => value)
    val rocketReady = battleState(rocketService, "rocket ready before fire")
    val rocketReadyAlice = rocketReady.players.find(_.playerId == PlayerId("alice")).getOrElse(fail("missing rocket alice before fire"))
    rocketService.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 26L,
        primaryHeld = true
      )
    ).fold(error => fail(s"rocket fire failed: $error"), value => value)

    rocketClock.now = 2_333L
    val afterRocketFire = battleState(rocketService, "after rocket fire")
    val rocketFireAlice = afterRocketFire.players.find(_.playerId == PlayerId("alice")).getOrElse(fail("missing alice after rocket fire"))
    val rocketWeapon = rocketFireAlice.weapons(rocketFireAlice.currentWeaponIndex)
    val rocketProjectile = afterRocketFire.projectiles.lastOption.getOrElse(fail("missing rocket projectile"))
    assertEquals("rocket consumes shell", rocketWeapon.ammoInMagazine, AmmoCount(0))
    assertEquals("rocket keeps reserve before reload", rocketWeapon.reserveAmmo, Some(AmmoCount(3)))
    assertEquals("rocket cooldown", rocketWeapon.fireCooldownMs, CooldownMillis(160))
    assertEquals("rocket auto reload starts", rocketWeapon.reloadRemainingMs, CooldownMillis(2500))
    assertEquals("rocket projectile kind", rocketProjectile.projectileKind, ProjectileKind.Rocket)
    assertEquals("rocket projectile damage", rocketProjectile.damage, Damage(60))
    assertEquals("rocket projectile radius", rocketProjectile.radius, Radius(14.0))
    assertEquals("rocket splash radius", rocketProjectile.splashRadius, Radius(132.0))
    assertClose("rocket projectile speed", vectorLengthForTest(rocketProjectile.velocity), 340.0, 0.001)
    assert(
      rocketProjectile.ttlMs.value < 30000L && rocketProjectile.ttlMs.value >= 29900L,
      s"rocket projectile should retain long authoritative lifetime after birth tick, ttl=${rocketProjectile.ttlMs}"
    )
    assertClose("rocket recoil moves shooter backward", rocketReadyAlice.position.x - rocketFireAlice.position.x, 21.6, 0.01)
    assertClose("rocket recoil keeps lane", rocketFireAlice.position.y, rocketReadyAlice.position.y, 0.01)
    assert(distanceBetweenForTest(rocketFireAlice.position, rocketProjectile.position) > 36.0, s"rocket projectile advances from muzzle birth, got ${rocketProjectile.position}")

    rocketService.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 27L,
        reloadPressed = true
      )
    ).fold(error => fail(s"rocket reload failed: $error"), value => value)
    val rocketReloading = rocketService.currentState(BattleId("battle-state-runtime"))
      .fold(error => fail(s"rocket reload state not found: $error"), value => value)
    val rocketReloadingAlice = rocketReloading.players.find(_.playerId == PlayerId("alice")).getOrElse(fail("missing alice during rocket reload"))
    assertEquals("rocket reload remains active", rocketReloadingAlice.weapons(rocketReloadingAlice.currentWeaponIndex).reloadRemainingMs, CooldownMillis(2500))
    rocketClock.now = 4_900L
    val rocketReloaded = battleState(rocketService, "rocket reloaded")
    val rocketReloadedAlice = rocketReloaded.players.find(_.playerId == PlayerId("alice")).getOrElse(fail("missing alice after rocket reload"))
    assertEquals("rocket reload fills magazine", rocketReloadedAlice.weapons(rocketReloadedAlice.currentWeaponIndex).ammoInMagazine, AmmoCount(1))
    assertEquals("rocket reload consumes reserve", rocketReloadedAlice.weapons(rocketReloadedAlice.currentWeaponIndex).reserveAmmo, Some(AmmoCount(2)))

    val shotgunClock = TestClock(1_000L)
    val shotgunService = battleStateService(
      clock = shotgunClock,
      seed = sessionSeed(aliceSpawnPointIndex = SpawnPointIndex(5))
    )
    shotgunService.currentState(BattleId("battle-state-runtime"))
      .fold(error => fail(s"shotgun state not found: $error"), value => value)
    shotgunService.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 28L,
        movement = BattleCommandVector(1.0, 0.0)
      )
    ).fold(error => fail(s"shotgun pickup move failed: $error"), value => value)
    shotgunClock.now = 1_300L
    val shotgunPickedUp = battleState(shotgunService, "shotgun picked up")
    val shotgunAlice = shotgunPickedUp.players.find(_.playerId == PlayerId("alice")).getOrElse(fail("missing shotgun alice"))
    val shotgunIndex = shotgunAlice.weapons.indexWhere(_.weaponKind == WeaponKind.Shotgun)
    assert(shotgunIndex >= 0, "shotgun pickup should add Shotgun")

    shotgunService.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 29L,
        switchWeaponIndex = Some(shotgunIndex)
      )
    ).fold(error => fail(s"switch to shotgun failed: $error"), value => value)
    val shotgunReady = battleState(shotgunService, "shotgun ready before fire")
    val shotgunReadyAlice = shotgunReady.players.find(_.playerId == PlayerId("alice")).getOrElse(fail("missing shotgun alice before fire"))
    shotgunService.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 30L,
        primaryHeld = true
      )
    ).fold(error => fail(s"shotgun fire failed: $error"), value => value)

    shotgunClock.now = 1_333L
    val afterShotgunFire = battleState(shotgunService, "after shotgun fire")
    val shotgunFireAlice = afterShotgunFire.players.find(_.playerId == PlayerId("alice")).getOrElse(fail("missing alice after shotgun fire"))
    val shotgunWeapon = shotgunFireAlice.weapons(shotgunFireAlice.currentWeaponIndex)
    val shotgunProjectiles = afterShotgunFire.projectiles.filter(_.projectileKind == ProjectileKind.ShotgunPellet)
    assertEquals("shotgun consumes shell", shotgunWeapon.ammoInMagazine, AmmoCount(5))
    assertEquals("shotgun cooldown", shotgunWeapon.fireCooldownMs, CooldownMillis(760))
    assertEquals("shotgun pellet count", shotgunProjectiles.length, 5)
    assertEquals("shotgun pellet damage", shotgunProjectiles.head.damage, Damage(8))
    assert(shotgunProjectiles.forall(_.radius == Radius(7.0)), s"shotgun pellet radii should match content, got ${shotgunProjectiles.map(_.radius)}")
    shotgunProjectiles.foreach { projectile =>
      assertClose("shotgun pellet speed", vectorLengthForTest(projectile.velocity), 720.0, 0.001)
    }
    assertClose("shotgun recoil moves shooter backward", shotgunReadyAlice.position.x - shotgunFireAlice.position.x, 14.4, 0.01)
    assertClose("shotgun recoil keeps lane", shotgunFireAlice.position.y, shotgunReadyAlice.position.y, 0.01)
    assert(
      shotgunProjectiles.forall(projectile => projectile.ttlMs.value < 30000L && projectile.ttlMs.value >= 29900L),
      s"shotgun pellets advance during their birth tick, ttls=${shotgunProjectiles.map(_.ttlMs)}"
    )
    shotgunProjectiles.zipWithIndex.foreach { case (projectile, index) =>
      assert(distanceBetweenForTest(shotgunFireAlice.position, projectile.position) > 29.0, s"shotgun pellet $index advances from muzzle birth, got ${projectile.position}")
    }
  }

  private def medkitHealsDamagedPlayer(): Unit = {
    val clock = TestClock(1_000L)
    val service = battleStateService(
      clock = clock,
      seed = sessionSeed(
        aliceSpawnPointIndex = SpawnPointIndex(6),
        botSpawnPointIndex = SpawnPointIndex(7),
        secondIsBot = false
      )
    )

    val initial = battleState(service, "medkit heal initial")
    val alice = initial.players.find(_.playerId == PlayerId("alice")).getOrElse(fail("missing medkit heal alice"))
    val target = initial.players.find(_.playerId == PlayerId("bot-one")).getOrElse(fail("missing medkit heal target"))

    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 19L,
        aim = BattleCommandVector(target.position.x - alice.position.x, target.position.y - alice.position.y),
        primaryHeld = true
      )
    ).fold(error => fail(s"medkit heal damage shot failed: $error"), value => value)

    clock.now = 1_033L
    battleState(service, "medkit heal shot spawned")
    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 20L,
        aim = BattleCommandVector(target.position.x - alice.position.x, target.position.y - alice.position.y),
        primaryHeld = false
      )
    ).fold(error => fail(s"medkit heal damage release failed: $error"), value => value)

    clock.now = 1_400L
    val damaged = battleState(service, "medkit heal damaged")
    val damagedTarget = damaged.players.find(_.playerId == PlayerId("bot-one")).getOrElse(fail("missing damaged medkit heal target"))
    assertEquals("medkit heal setup damages target", damagedTarget.hp, HitPoints(88))

    val medkitContactPoints = Vector(
      BattleVector2(996.0, 800.0),
      BattleVector2(996.0, 608.0)
    )
    var moveSeq = 21L
    var healed = battleState(service, "medkit heal before pickup movement")
    medkitContactPoints.foreach { contactPoint =>
      var step = 0
      while step < 42 && healed.pickups.exists(pickup => pickup.pickupId == PickupId("pickup-medkit-1") && pickup.available) do {
        val movingTarget = healed.players.find(_.playerId == PlayerId("bot-one")).getOrElse(fail("missing moving medkit heal target"))
        service.acceptCommand(
          command(
            playerId = PlayerId("bot-one"),
            ticketId = TicketId("ticket-bot-one"),
            seq = moveSeq,
            movement = BattleCommandVector(contactPoint.x - movingTarget.position.x, contactPoint.y - movingTarget.position.y)
          )
        ).fold(error => fail(s"medkit heal pickup movement failed: $error"), value => value)
        moveSeq += 1L
        clock.now += 120L
        healed = battleState(service, s"medkit heal pickup movement $moveSeq")
        step += 1
      }
    }
    val healedTarget = healed.players.find(_.playerId == PlayerId("bot-one")).getOrElse(fail("missing healed medkit target"))
    val medkitPickup = healed.pickups.find(_.pickupId == PickupId("pickup-medkit-1")).getOrElse(fail("missing medkit heal pickup"))

    assertEquals("medkit heals and clamps to max hp", healedTarget.hp, HitPoints(100))
    assertEquals("medkit heal consumes pickup", medkitPickup.available, false)
    assertEquals("medkit heal event kind", healed.events.lastOption.map(_.eventKind), Some(BattleEventKind.Heal))
  }

  private def nonPistolActiveProjectilesDamageTargets(): Unit = {
    val clock = TestClock(1_100L)
    val service = battleStateService(
      clock = clock,
      seed = sessionSeed(
        aliceSpawnPointIndex = SpawnPointIndex(0),
        botSpawnPointIndex = SpawnPointIndex(8),
        secondIsBot = false
      )
    )

    val initial = battleState(service, "gatling damage initial")
    val alice = initial.players.find(_.playerId == PlayerId("alice")).getOrElse(fail("missing alice for gatling damage"))
    val bot = initial.players.find(_.playerId == PlayerId("bot-one")).getOrElse(fail("missing bot for gatling damage"))
    val gatlingIndex = alice.weapons.indexWhere(_.weaponKind == WeaponKind.Gatling)
    assert(gatlingIndex >= 0, "spawn pickup should add Gatling for damage test")

    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 31L,
        switchWeaponIndex = Some(gatlingIndex)
      )
    ).fold(error => fail(s"switch to gatling for damage failed: $error"), value => value)
    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 32L,
        aim = BattleCommandVector(bot.position.x - alice.position.x, bot.position.y - alice.position.y),
        primaryHeld = true
      )
    ).fold(error => fail(s"gatling damage fire failed: $error"), value => value)

    clock.now = 1_133L
    battleState(service, "gatling damage first runtime step")
    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 33L,
        aim = BattleCommandVector(bot.position.x - alice.position.x, bot.position.y - alice.position.y),
        primaryHeld = false
      )
    ).fold(error => fail(s"gatling damage release failed: $error"), value => value)

    clock.now = 1_400L
    val afterHit = battleState(service, "gatling projectile hit")
    val damagedBot = afterHit.players.find(_.playerId == PlayerId("bot-one")).getOrElse(fail("missing damaged bot"))
    val terminal = afterHit.projectileTerminals.lastOption.getOrElse(fail("missing gatling hit terminal"))

    assertEquals("gatling projectile damages target", damagedBot.hp, HitPoints(95))
    assertEquals("gatling projectile terminal kind", terminal.projectileKind, ProjectileKind.GatlingBullet)
    assertEquals("gatling projectile terminal reason", terminal.reason, ProjectileTerminalReason.Hit)
    assertEquals("gatling projectile terminal target", terminal.targetPlayerId, Some(PlayerId("bot-one")))
    assertEquals("gatling projectile terminal hp before", terminal.hpBefore, Some(HitPoints(100)))
    assertEquals("gatling projectile terminal hp after", terminal.hpAfter, Some(HitPoints(95)))
    assertEquals("gatling projectile terminal damage", terminal.damage, Some(Damage(5)))
    assert(
      terminal.end.x > terminal.terminalPosition.x,
      s"gatling hit terminal should preserve full segment end beyond hit point, terminal=${terminal.terminalPosition}, end=${terminal.end}"
    )
  }

  private def eliminationDoesNotRespawnAndFinishesBattle(): Unit = {
    val clock = TestClock(1_000L)
    val service = battleStateService(
      clock = clock,
      seed = sessionSeed(
        aliceSpawnPointIndex = SpawnPointIndex(6),
        botSpawnPointIndex = SpawnPointIndex(7),
        secondIsBot = false
      )
    )

    val initial = service.currentState(BattleId("battle-state-runtime"))
      .fold(error => fail(s"state not found: $error"), value => value)
    val alice = initial.players.find(_.playerId == PlayerId("alice")).getOrElse(fail("missing alice"))
    val bot = initial.players.find(_.playerId == PlayerId("bot-one")).getOrElse(fail("missing bot"))
    val aimAtBot = BattleCommandVector(bot.position.x - alice.position.x, bot.position.y - alice.position.y)

    (0 until 9).foreach { shot =>
      clock.now = 1_000L + shot.toLong * 260L
      service.acceptCommand(
        command(
          playerId = PlayerId("alice"),
          ticketId = TicketId("ticket-alice"),
          seq = 14L + shot,
          aim = aimAtBot,
          primaryHeld = true
        )
      ).fold(error => fail(s"lethal shot $shot failed: $error"), value => value)
    }

    clock.now = 3_700L
    val finished = service.currentState(BattleId("battle-state-runtime"))
      .fold(error => fail(s"state not found after elimination: $error"), value => value)
    val eliminatedBot = finished.players.find(_.playerId == PlayerId("bot-one")).getOrElse(fail("missing eliminated bot"))

    assertEquals("battle finishes after one survivor remains", finished.phase, BattlePhase.Finished)
    assertEquals("survivor wins", finished.winnerPlayerId, Some(PlayerId("alice")))
    assertEquals("eliminated bot dead", eliminatedBot.alive, false)
    assertEquals("eliminated bot hp", eliminatedBot.hp, HitPoints(0))
    assertEquals("eliminated bot has no respawn timer", eliminatedBot.respawnMs, DurationMillis(0L))
    assertEquals("no respawn event emitted", finished.events.exists(_.eventKind == BattleEventKind.Respawn), false)

    clock.now = 5_000L
    val later = service.currentState(BattleId("battle-state-runtime"))
      .fold(error => fail(s"state not found after no-respawn wait: $error"), value => value)
    val laterBot = later.players.find(_.playerId == PlayerId("bot-one")).getOrElse(fail("missing later bot"))

    assertEquals("battle remains finished", later.phase, BattlePhase.Finished)
    assertEquals("eliminated bot stays dead", laterBot.alive, false)
    assertEquals("eliminated bot still has no respawn timer", laterBot.respawnMs, DurationMillis(0L))
  }

  private def eliminationClearsDeadPlayerRuntimeBeforeBattleFinish(): Unit = {
    val clock = TestClock(1_000L)
    val seats = Vector(
      seat(
        playerId = PlayerId("alice"),
        heroId = HeroId("hero-alice"),
        handle = PlayerHandle("Alice"),
        displayName = DisplayName("Alice"),
        seat = SeatIndex(0),
        isBot = false,
        spawnPointIndex = Some(SpawnPointIndex(0))
      ),
      seat(
        playerId = PlayerId("target"),
        heroId = HeroId("hero-target"),
        handle = PlayerHandle("Target"),
        displayName = DisplayName("Target"),
        seat = SeatIndex(1),
        isBot = false,
        spawnPointIndex = Some(SpawnPointIndex(5))
      ),
      seat(
        playerId = PlayerId("bystander"),
        heroId = HeroId("hero-bystander"),
        handle = PlayerHandle("Bystander"),
        displayName = DisplayName("Bystander"),
        seat = SeatIndex(2),
        isBot = false,
        spawnPointIndex = Some(SpawnPointIndex(1))
      )
    )
    val service = battleStateService(
      clock = clock,
      seed = sessionSeedWithSeats(
        seats,
        Vector(
          BattleCommandOwnership(PlayerId("alice"), TicketId("ticket-alice")),
          BattleCommandOwnership(PlayerId("target"), TicketId("ticket-target"))
        )
      )
    )
    val initial = battleState(service, "dead runtime cleanup initial")
    val alice = initial.players.find(_.playerId == PlayerId("alice")).getOrElse(fail("missing cleanup alice"))
    val target = initial.players.find(_.playerId == PlayerId("target")).getOrElse(fail("missing cleanup target"))
    val aimAtTarget = BattleCommandVector(target.position.x - alice.position.x, target.position.y - alice.position.y)

    service.acceptCommand(
      command(
        playerId = PlayerId("target"),
        ticketId = TicketId("ticket-target"),
        seq = 1L,
        castFreeze = true,
        pointerWorld = Some(BattleCommandVector(target.position.x, target.position.y))
      )
    ).fold(error => fail(s"cleanup target freeze command failed: $error"), value => value)
    service.acceptCommand(
      command(
        playerId = PlayerId("target"),
        ticketId = TicketId("ticket-target"),
        seq = 2L,
        aim = BattleCommandVector(1.0, 0.0),
        primaryHeld = true
      )
    ).fold(error => fail(s"cleanup target fire command failed: $error"), value => value)

    (0 until 12).foreach { shot =>
      clock.now = 1_000L + shot.toLong * 260L
      service.acceptCommand(
        command(
          playerId = PlayerId("alice"),
          ticketId = TicketId("ticket-alice"),
          seq = 10L + shot,
          aim = aimAtTarget,
          primaryHeld = true
        )
      ).fold(error => fail(s"cleanup setup shot $shot failed: $error"), value => value)
    }

    clock.now = 9_000L
    val afterElimination = battleState(service, "dead runtime cleanup after elimination")
    val eliminatedTarget = afterElimination.players.find(_.playerId == PlayerId("target")).getOrElse(fail("missing eliminated cleanup target"))
    val eliminatedWeapon = eliminatedTarget.weapons(eliminatedTarget.currentWeaponIndex)

    assertEquals("battle continues with bystander alive", afterElimination.phase, BattlePhase.Active)
    assert(
      !eliminatedTarget.alive,
      s"expected target eliminated, hp=${eliminatedTarget.hp}, projectiles=${afterElimination.projectiles.length}, terminals=${afterElimination.projectileTerminals.takeRight(5)}"
    )
    assertEquals("target hp zero", eliminatedTarget.hp, HitPoints(0))
    assertEquals("target movement cleared", eliminatedTarget.movement, BattleVector2(0.0, 0.0))
    assertEquals("target sprint cleared", eliminatedTarget.sprint, false)
    assertEquals("target primary cleared", eliminatedTarget.primaryHeld, false)
    assertEquals("target reload cleared", eliminatedTarget.reloadPressed, false)
    assertEquals("target respawn remains disabled", eliminatedTarget.respawnMs, DurationMillis(0L))
    assert(eliminatedTarget.skills.forall(_.activeMs == DurationMillis(0L)), s"expected eliminated target active skills cleared, got ${eliminatedTarget.skills}")
    assertEquals("target weapon fire cooldown cleared", eliminatedWeapon.fireCooldownMs, CooldownMillis(0))
    assertEquals("target weapon reload runtime cleared", eliminatedWeapon.reloadRemainingMs, CooldownMillis(0))
  }

  private def dashAndBlinkRespectArenaCollision(): Unit = {
    val dashClock = TestClock(1_000L)
    val dashService = battleStateService(
      clock = dashClock,
      seed = sessionSeed(aliceSpawnPointIndex = SpawnPointIndex(1))
    )

    val dashInitial = dashService.currentState(BattleId("battle-state-runtime"))
      .fold(error => fail(s"state not found: $error"), value => value)
    val dashInitialAlice = dashInitial.players.find(_.playerId == PlayerId("alice")).getOrElse(fail("missing alice"))
    assertEquals("dash starts at northwest spawn", dashInitialAlice.position, BattleVector2(512.0, 544.0))

    val dashAccepted = dashService.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 17L,
        movement = BattleCommandVector(-1.0, -1.0),
        sprint = true,
        castDash = true
      )
    ).fold(error => fail(s"dash command failed: $error"), value => value)
    assertEquals("dash outcome", dashAccepted.outcomes.headOption.map(_.outcomeStatus), Some(SkillOutcomeStatus.Applied))

    val dashed = dashService.currentState(BattleId("battle-state-runtime"))
      .fold(error => fail(s"state not found after dash: $error"), value => value)
    val dashedAlice = dashed.players.find(_.playerId == PlayerId("alice")).getOrElse(fail("missing alice after dash"))
    val dashSkill = dashedAlice.skills.find(_.skillKind == SkillKind.Dash).getOrElse(fail("missing dash skill"))
    val dashDistance = math.hypot(
      dashedAlice.position.x - dashInitialAlice.position.x,
      dashedAlice.position.y - dashInitialAlice.position.y
    )
    assert(
      dashDistance > 0.0 && dashDistance < 180.0,
      s"expected dash to stop before northwest cover, got distance=$dashDistance position=${dashedAlice.position}"
    )
    assertEquals("dash cooldown matches content", dashSkill.cooldownMs, CooldownMillis(5000))
    assertEquals("dash active duration matches content", dashSkill.activeMs, DurationMillis(180L))
    assertEquals("dash preserves held sprint", dashedAlice.sprint, true)
    assertClose("dash preserves held movement x", dashedAlice.movement.x, -0.70710678, 0.001)
    assertClose("dash preserves held movement y", dashedAlice.movement.y, -0.70710678, 0.001)

    val blinkClock = TestClock(1_000L)
    val blinkService = battleStateService(clock = blinkClock)
    val blinkInitial = blinkService.currentState(BattleId("battle-state-runtime"))
      .fold(error => fail(s"state not found: $error"), value => value)
    val blinkInitialAlice = blinkInitial.players.find(_.playerId == PlayerId("alice")).getOrElse(fail("missing alice"))

    val blinkAccepted = blinkService.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 18L,
        castBlink = true,
        pointerWorld = Some(BattleCommandVector(416.0, 416.0))
      )
    ).fold(error => fail(s"blocked blink command failed: $error"), value => value)

    assertEquals("blocked blink outcome", blinkAccepted.outcomes.headOption.map(_.outcomeStatus), Some(SkillOutcomeStatus.Noop))
    assertEquals("blocked blink reason", blinkAccepted.outcomes.headOption.flatMap(_.reason), Some(SkillOutcomeReason.Blocked))

    val afterBlockedBlink = blinkService.currentState(BattleId("battle-state-runtime"))
      .fold(error => fail(s"state not found after blocked blink: $error"), value => value)
    val blinkAlice = afterBlockedBlink.players.find(_.playerId == PlayerId("alice")).getOrElse(fail("missing alice after blink"))
    assertEquals("blocked blink keeps position", blinkAlice.position, blinkInitialAlice.position)

    val nearBorderBlinkAccepted = blinkService.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 181L,
        castBlink = true,
        pointerWorld = Some(BattleCommandVector(8.0, blinkInitialAlice.position.y))
      )
    ).fold(error => fail(s"near-border blink command failed: $error"), value => value)

    assertEquals("near-border blink outcome", nearBorderBlinkAccepted.outcomes.headOption.map(_.outcomeStatus), Some(SkillOutcomeStatus.Noop))
    assertEquals("near-border blink reason", nearBorderBlinkAccepted.outcomes.headOption.flatMap(_.reason), Some(SkillOutcomeReason.InvalidTarget))

    val validBlinkTarget = BattleCommandVector(blinkInitialAlice.position.x + 240.0, blinkInitialAlice.position.y)
    val validBlinkAccepted = blinkService.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 19L,
        movement = BattleCommandVector(1.0, 0.0),
        sprint = true,
        castBlink = true,
        pointerWorld = Some(validBlinkTarget)
      )
    ).fold(error => fail(s"valid blink command failed: $error"), value => value)

    assertEquals("valid blink outcome", validBlinkAccepted.outcomes.headOption.map(_.outcomeStatus), Some(SkillOutcomeStatus.Applied))

    val afterValidBlink = blinkService.currentState(BattleId("battle-state-runtime"))
      .fold(error => fail(s"state not found after valid blink: $error"), value => value)
    val validBlinkAlice = afterValidBlink.players.find(_.playerId == PlayerId("alice")).getOrElse(fail("missing alice after valid blink"))
    val blinkSkill = validBlinkAlice.skills.find(_.skillKind == SkillKind.Blink).getOrElse(fail("missing blink skill"))
    assertEquals("valid blink lands on target", validBlinkAlice.position, BattleVector2(validBlinkTarget.x, validBlinkTarget.y))
    assertEquals("blink cooldown matches content", blinkSkill.cooldownMs, CooldownMillis(2200))
    assertEquals("blink active duration matches content", blinkSkill.activeMs, DurationMillis(240L))
    assertEquals("blink preserves held sprint", validBlinkAlice.sprint, true)
    assertEquals("blink preserves held movement", validBlinkAlice.movement, BattleVector2(1.0, 0.0))

    val blinkCooldownAccepted = blinkService.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 191L,
        movement = BattleCommandVector(1.0, 0.0),
        sprint = true,
        castBlink = true,
        pointerWorld = Some(BattleCommandVector(-1.0, -1.0))
      )
    ).fold(error => fail(s"blink cooldown command failed: $error"), value => value)

    assertEquals("blink cooldown precedes invalid target outcome", blinkCooldownAccepted.outcomes.headOption.map(_.outcomeStatus), Some(SkillOutcomeStatus.Noop))
    assertEquals("blink cooldown precedes invalid target reason", blinkCooldownAccepted.outcomes.headOption.flatMap(_.reason), Some(SkillOutcomeReason.Cooldown))

    blinkClock.now = 1_100L
    val afterBlinkHeldTick = blinkService.currentState(BattleId("battle-state-runtime"))
      .fold(error => fail(s"state not found after blink held tick: $error"), value => value)
    val afterBlinkHeldAlice = afterBlinkHeldTick.players.find(_.playerId == PlayerId("alice")).getOrElse(fail("missing alice after blink held tick"))
    assert(
      afterBlinkHeldAlice.position.x > validBlinkAlice.position.x,
      s"blink held movement should continue after skill, before=${validBlinkAlice.position}, after=${afterBlinkHeldAlice.position}"
    )
    assert(afterBlinkHeldAlice.stamina.value < validBlinkAlice.stamina.value, s"blink held sprint should consume stamina, before=${validBlinkAlice.stamina}, after=${afterBlinkHeldAlice.stamina}")

    val rangeClock = TestClock(1_000L)
    val rangeService = battleStateService(clock = rangeClock)
    val rangeInitial = rangeService.currentState(BattleId("battle-state-runtime"))
      .fold(error => fail(s"state not found for blink range: $error"), value => value)
    val rangeAlice = rangeInitial.players.find(_.playerId == PlayerId("alice")).getOrElse(fail("missing range alice"))
    val outOfRangeTarget = BattleCommandVector(rangeAlice.position.x + 260.0, rangeAlice.position.y)
    val outOfRangeAccepted = rangeService.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 20L,
        castBlink = true,
        pointerWorld = Some(outOfRangeTarget)
      )
    ).fold(error => fail(s"out-of-range blink command failed: $error"), value => value)

    assertEquals("out-of-range blink outcome", outOfRangeAccepted.outcomes.headOption.map(_.outcomeStatus), Some(SkillOutcomeStatus.Noop))
    assertEquals("out-of-range blink reason", outOfRangeAccepted.outcomes.headOption.flatMap(_.reason), Some(SkillOutcomeReason.OutOfRange))
  }

  private def skillCommandSuppressesPrimaryFire(): Unit = {
    val clock = TestClock(1_000L)
    val service = battleStateService(
      clock = clock,
      seed = sessionSeed(secondIsBot = false)
    )
    battleState(service, "skill fire suppression initial")

    val accepted = service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 190L,
        movement = BattleCommandVector(1.0, 0.0),
        aim = BattleCommandVector(1.0, 0.0),
        primaryHeld = true,
        castDash = true
      )
    ).fold(error => fail(s"skill fire suppression command failed: $error"), value => value)

    assertEquals("skill fire suppression dash outcome", accepted.outcomes.headOption.map(_.outcomeStatus), Some(SkillOutcomeStatus.Applied))

    val afterCommand = battleState(service, "skill fire suppression after command")
    val afterCommandAlice = afterCommand.players.find(_.playerId == PlayerId("alice")).getOrElse(fail("missing skill fire alice after command"))
    assertEquals("skill command does not retain primary held", afterCommandAlice.primaryHeld, false)
    assertEquals("skill command does not consume pistol ammo", afterCommandAlice.weapons.head.ammoInMagazine, AmmoCount(12))
    assertEquals("skill command creates no projectile immediately", afterCommand.projectiles, Vector.empty)

    clock.now = 1_033L
    val afterTick = battleState(service, "skill fire suppression after tick")
    val afterTickAlice = afterTick.players.find(_.playerId == PlayerId("alice")).getOrElse(fail("missing skill fire alice after tick"))
    assertEquals("skill command still has full pistol ammo after runtime step", afterTickAlice.weapons.head.ammoInMagazine, AmmoCount(12))
    assertEquals("skill command creates no projectile on next tick", afterTick.projectiles, Vector.empty)
  }

  private def freezeUsesFrontendContentConstants(): Unit = {
    val clock = TestClock(1_000L)
    val service = battleStateService(clock = clock)

    val initial = service.currentState(BattleId("battle-state-runtime"))
      .fold(error => fail(s"state not found: $error"), value => value)
    val alice = initial.players.find(_.playerId == PlayerId("alice")).getOrElse(fail("missing alice"))
    val target = BattleCommandVector(alice.position.x + 520.0, alice.position.y)

    val accepted = service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 19L,
        castFreeze = true,
        pointerWorld = Some(target)
      )
    ).fold(error => fail(s"freeze command failed: $error"), value => value)

    assertEquals("freeze outcome", accepted.outcomes.headOption.map(_.outcomeStatus), Some(SkillOutcomeStatus.Applied))

    val frozen = service.currentState(BattleId("battle-state-runtime"))
      .fold(error => fail(s"state not found after freeze: $error"), value => value)
    val frozenAlice = frozen.players.find(_.playerId == PlayerId("alice")).getOrElse(fail("missing alice after freeze"))
    val freezeSkill = frozenAlice.skills.find(_.skillKind == SkillKind.Freeze).getOrElse(fail("missing freeze skill"))
    val slowField = frozen.slowFields.lastOption.getOrElse(fail("missing slow field"))

    assertEquals("freeze cooldown matches content", freezeSkill.cooldownMs, CooldownMillis(12000))
    assertEquals("freeze active duration matches content", freezeSkill.activeMs, DurationMillis(10000L))
    assertEquals("freeze field radius matches content", slowField.radius, Radius(150.0))
    assertEquals("freeze field ttl matches content", slowField.ttlMs, DurationMillis(10000L))
    assertEquals("freeze field position", slowField.position, BattleVector2(target.x, target.y))

    val cooldownAccepted = service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 20L,
        castFreeze = true,
        pointerWorld = Some(BattleCommandVector(-1.0, -1.0))
      )
    ).fold(error => fail(s"freeze cooldown command failed: $error"), value => value)

    assertEquals("freeze cooldown precedes invalid target outcome", cooldownAccepted.outcomes.headOption.map(_.outcomeStatus), Some(SkillOutcomeStatus.Noop))
    assertEquals("freeze cooldown precedes invalid target reason", cooldownAccepted.outcomes.headOption.flatMap(_.reason), Some(SkillOutcomeReason.Cooldown))

    val obstacleClock = TestClock(1_000L)
    val obstacleService = battleStateService(clock = obstacleClock)
    battleState(obstacleService, "freeze obstacle target initial")
    val obstacleTarget = BattleCommandVector(416.0, 416.0)

    val obstacleAccepted = obstacleService.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 21L,
        castFreeze = true,
        pointerWorld = Some(obstacleTarget)
      )
    ).fold(error => fail(s"freeze obstacle command failed: $error"), value => value)

    assertEquals("freeze obstacle target follows legacy outcome", obstacleAccepted.outcomes.headOption.map(_.outcomeStatus), Some(SkillOutcomeStatus.Applied))

    val obstacleFrozen = battleState(obstacleService, "freeze obstacle target applied")
    val obstacleSlowField = obstacleFrozen.slowFields.lastOption.getOrElse(fail("missing obstacle slow field"))
    assertEquals("freeze obstacle field position", obstacleSlowField.position, BattleVector2(obstacleTarget.x, obstacleTarget.y))
  }

  private def expiringSlowFieldDoesNotAffectMovementOnExpiryTick(): Unit = {
    val clock = TestClock(1_000L)
    val service = battleStateService(clock = clock)

    val initial = battleState(service, "slow field expiry initial")
    val alice = initial.players.find(_.playerId == PlayerId("alice")).getOrElse(fail("missing slow expiry alice"))

    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 200L,
        castFreeze = true,
        pointerWorld = Some(BattleCommandVector(alice.position.x, alice.position.y))
      )
    ).fold(error => fail(s"slow expiry freeze command failed: $error"), value => value)

    clock.now = 10_999L
    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 201L,
        movement = BattleCommandVector(1.0, 0.0)
      )
    ).fold(error => fail(s"slow expiry movement command failed: $error"), value => value)

    val beforeExpiryStep = battleState(service, "slow field before expiry tick")
    val fieldBeforeExpiry = beforeExpiryStep.slowFields.headOption.getOrElse(fail("missing slow field before expiry tick"))
    assertEquals("slow field has one millisecond before expiry tick", fieldBeforeExpiry.ttlMs, DurationMillis(1L))

    clock.now = 11_032L
    val afterExpiryStep = battleState(service, "slow field after expiry tick")
    val movedAlice = afterExpiryStep.players.find(_.playerId == PlayerId("alice")).getOrElse(fail("missing slow expiry moved alice"))

    assertEquals("slow field expired before movement slow check", afterExpiryStep.slowFields, Vector.empty)
    assertClose("expired slow field does not slow final tick", movedAlice.position.x - alice.position.x, 8.415, 0.01)
    assertClose("expired slow field keeps lane", movedAlice.position.y, alice.position.y, 0.01)
  }

  private def finishedStateClearsRuntimeButPreservesSlowFieldsWithoutInventingTimeoutWinner(): Unit = {
    val clock = TestClock(1_000L)
    val service = battleStateService(clock = clock, battleDuration = DurationMillis(500L))
    val initial = service.currentState(BattleId("battle-state-runtime"))
      .fold(error => fail(s"state not found: $error"), value => value)
    val alice = initial.players.find(_.playerId == PlayerId("alice")).getOrElse(fail("missing timeout cleanup alice"))

    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 88L,
        movement = BattleCommandVector(1.0, 0.0),
        aim = BattleCommandVector(1.0, 0.0),
        primaryHeld = true
      )
    ).fold(error => fail(s"timeout cleanup fire command failed: $error"), value => value)

    clock.now = 1_033L
    val activeProjectile = service.currentState(BattleId("battle-state-runtime"))
      .fold(error => fail(s"active projectile state not found: $error"), value => value)
    assertEquals("runtime setup has projectile", activeProjectile.projectiles.nonEmpty, true)

    service.acceptCommand(
      command(
        playerId = PlayerId("alice"),
        ticketId = TicketId("ticket-alice"),
        seq = 89L,
        movement = BattleCommandVector(1.0, 0.0),
        aim = BattleCommandVector(1.0, 0.0),
        castFreeze = true,
        pointerWorld = Some(BattleCommandVector(alice.position.x + 120.0, alice.position.y))
      )
    ).fold(error => fail(s"timeout cleanup command failed: $error"), value => value)

    val active = service.currentState(BattleId("battle-state-runtime"))
      .fold(error => fail(s"active state not found: $error"), value => value)
    assertEquals("runtime setup has projectile", active.projectiles.nonEmpty, true)
    assertEquals("runtime setup has slow field", active.slowFields.nonEmpty, true)
    val activeSlowField = active.slowFields.head

    clock.now = 1_700L
    val finished = service.currentState(BattleId("battle-state-runtime"))
      .fold(error => fail(s"finished state not found: $error"), value => value)
    val finishedAlice = finished.players.find(_.playerId == PlayerId("alice")).getOrElse(fail("missing finished alice"))
    val finishedSlowField = finished.slowFields.headOption.getOrElse(fail("missing finished slow field"))

    assertEquals("timeout cleanup finished phase", finished.phase, BattlePhase.Finished)
    assertEquals("timeout with multiple survivors has no winner", finished.winnerPlayerId, None)
    assertEquals("timeout clears projectiles", finished.projectiles, Vector.empty)
    assertEquals("timeout keeps slow field position", finishedSlowField.position, activeSlowField.position)
    assert(
      finishedSlowField.ttlMs.value > 0L && finishedSlowField.ttlMs.value < activeSlowField.ttlMs.value,
      s"expected finished slow field ttl to be advanced and retained, active=${activeSlowField.ttlMs}, finished=${finishedSlowField.ttlMs}"
    )
    assertEquals("timeout clears movement", finishedAlice.movement, BattleVector2(0.0, 0.0))
    assertEquals("timeout clears sprint", finishedAlice.sprint, false)
    assertEquals("timeout clears primary", finishedAlice.primaryHeld, false)
    assertEquals("timeout clears reload", finishedAlice.reloadPressed, false)
    assert(finishedAlice.skills.forall(_.activeMs == DurationMillis(0L)), s"expected no active skills after finish, got ${finishedAlice.skills}")
  }

  private def finishedStateProjectsArtifactsOnce(): Unit = {
    val clock = TestClock(1_000L)
    val projector = RecordingProjector(BattleFinishProjectionOutcome.Projected)
    val service = battleStateService(clock = clock, battleDuration = DurationMillis(1_000L), finishProjector = projector)

    val active = service.currentState(BattleId("battle-state-runtime")).fold(error => fail(s"state not found: $error"), value => value)
    assertEquals("initial phase", active.phase, BattlePhase.Active)
    assertEquals("projector before finish", projector.projectedStates.length, 0)

    clock.now = 2_500L
    val finished = service.currentState(BattleId("battle-state-runtime")).fold(error => fail(s"state not found: $error"), value => value)

    assertEquals("finished phase", finished.phase, BattlePhase.Finished)
    assertEquals("finished elapsed is capped", finished.elapsedMs, ElapsedMillis(1_000L))
    assertEquals("finished artifact status", finished.artifactStatus, BattleArtifactStatus.Ready)
    assertEquals("timeout finish does not invent winner", finished.winnerPlayerId, None)
    assertEquals("projector called once", projector.projectedStates.length, 1)
    assertEquals("projected state is finished", projector.projectedStates.head.phase, BattlePhase.Finished)
    assertEquals("projected artifact is pending", projector.projectedStates.head.artifactStatus, BattleArtifactStatus.Pending)

    val reread = service.currentState(BattleId("battle-state-runtime")).fold(error => fail(s"state not found: $error"), value => value)
    assertEquals("reread keeps artifacts ready", reread.artifactStatus, BattleArtifactStatus.Ready)
    assertEquals("projector is not called again", projector.projectedStates.length, 1)
  }

  private def finishedStateTracksPartialArtifactReadiness(): Unit = {
    val resultOnly = finishedStateAfterProjection(BattleFinishProjectionOutcome.ResultProjectedReplayFailed("replay unavailable"))
    assertEquals("result-only artifact status", resultOnly.artifactStatus, BattleArtifactStatus.ResultOnlyReady)
    assertEquals("result-only result ready", BattleArtifactStatus.isResultReady(resultOnly.artifactStatus), true)
    assertEquals("result-only replay not ready", BattleArtifactStatus.isReplayReady(resultOnly.artifactStatus), false)

    val replayOnly = finishedStateAfterProjection(BattleFinishProjectionOutcome.ResultFailedReplayProjected("result unavailable"))
    assertEquals("replay-only artifact status", replayOnly.artifactStatus, BattleArtifactStatus.ReplayOnlyReady)
    assertEquals("replay-only result not ready", BattleArtifactStatus.isResultReady(replayOnly.artifactStatus), false)
    assertEquals("replay-only replay ready", BattleArtifactStatus.isReplayReady(replayOnly.artifactStatus), true)
  }

  private def finishedStateAfterProjection(outcome: BattleFinishProjectionOutcome): BattleAggregateState = {
    val clock = TestClock(1_000L)
    val service = battleStateService(
      clock = clock,
      battleDuration = DurationMillis(1_000L),
      finishProjector = RecordingProjector(outcome)
    )

    battleState(service, "partial artifact initial")
    clock.now = 2_500L
    battleState(service, "partial artifact finished")
  }

  private def ignoredFinishedCommandUsesStoredClientSequence(): Unit = {
    val clock = TestClock(1_000L)
    val service = battleStateService(clock = clock, battleDuration = DurationMillis(1_000L))

    battleState(service, "ignored finished command initial")
    val applied = service.acceptCommand(command(PlayerId("alice"), TicketId("ticket-alice"), 41L))
      .fold(error => fail(s"active command failed: $error"), value => value)
    assertEquals("active command seq", applied.acceptedCommandSeq, ClientCommandSeq(41L))

    clock.now = 2_500L
    val finished = battleState(service, "ignored finished command finished")
    assertEquals("ignored command test finished phase", finished.phase, BattlePhase.Finished)

    val ignored = service.acceptCommand(command(PlayerId("alice"), TicketId("ticket-alice"), 99L))
      .fold(error => fail(s"finished command failed: $error"), value => value)
    assertEquals("finished command ignored", ignored.commandStatus, BattleCommandStatus.Ignored)
    assertEquals("finished command reason", ignored.commandReason, Some(BattleCommandReason.BattleFinished))
    assertEquals("ignored command keeps stored seq", ignored.acceptedCommandSeq, ClientCommandSeq(41L))
  }

  private def finishedStateMarksQueueRoomFinished(): Unit = {
    val clock = TestClock(1_000L)
    val lifecycleSink = RecordingRoomLifecycleSink()
    val service = battleStateService(
      clock = clock,
      battleDuration = DurationMillis(1_000L),
      finishProjector = RecordingProjector(BattleFinishProjectionOutcome.Projected),
      roomLifecycleSink = lifecycleSink
    )

    battleState(service, "room finish lifecycle initial")
    clock.now = 2_500L
    battleState(service, "room finish lifecycle finished")
    battleState(service, "room finish lifecycle reread")

    assertEquals(
      "room finish lifecycle notifications",
      lifecycleSink.finishedRooms,
      Vector(RoomId("room-state-runtime") -> EpochMillis(2_000L))
    )
  }

  private def battleStateService(
    clock: TestClock,
    battleDuration: DurationMillis = DurationMillis(60_000L),
    finishProjector: BattleFinishProjector = RecordingProjector(BattleFinishProjectionOutcome.NotConfigured),
    roomLifecycleSink: BattleRoomLifecycleSink = RecordingRoomLifecycleSink(),
    seed: BattleSessionSeed = sessionSeed()
  ): InMemoryBattleStateService =
    new InMemoryBattleStateService(
      sessionLookup = FixedBattleSessionLookup(seed),
      currentTimeMillis = clock.millis,
      battleDuration = battleDuration,
      finishProjector = finishProjector,
      roomLifecycleSink = roomLifecycleSink
    )

  private def sessionSeed(
    aliceSpawnPointIndex: SpawnPointIndex = SpawnPointIndex(0),
    botSpawnPointIndex: SpawnPointIndex = SpawnPointIndex(1),
    secondIsBot: Boolean = true
  ): BattleSessionSeed =
    BattleSessionSeed(
      roomId = RoomId("room-state-runtime"),
      descriptor = BattleSessionDescriptor(
        battleId = BattleId("battle-state-runtime"),
        startedAt = EpochMillis(1_000L),
        serverTime = EpochMillis(1_000L),
        roster = Vector.empty,
        capacity = BattleCapacity(2),
        bootstrap = Some(
          BattleSessionBootstrap(
            Vector(
              seat(
                playerId = PlayerId("alice"),
                heroId = HeroId("hero-alice"),
                handle = PlayerHandle("Alice"),
                displayName = DisplayName("Alice"),
                seat = SeatIndex(0),
                isBot = false,
                spawnPointIndex = Some(aliceSpawnPointIndex)
              ),
              seat(
                playerId = PlayerId("bot-one"),
                heroId = HeroId("hero-bot-one"),
                handle = PlayerHandle("Bot 1"),
                displayName = DisplayName("Bot 1"),
                seat = SeatIndex(1),
                isBot = secondIsBot,
                spawnPointIndex = Some(botSpawnPointIndex)
              )
            )
          )
        )
      ),
      commandOwnership =
        if secondIsBot then Vector(BattleCommandOwnership(PlayerId("alice"), TicketId("ticket-alice")))
        else Vector(
          BattleCommandOwnership(PlayerId("alice"), TicketId("ticket-alice")),
          BattleCommandOwnership(PlayerId("bot-one"), TicketId("ticket-bot-one"))
        )
    )

  private def sessionSeedWithSeats(
    seats: Vector[BattleSessionBootstrapSeat],
    commandOwnership: Vector[BattleCommandOwnership] = Vector.empty
  ): BattleSessionSeed =
    BattleSessionSeed(
      roomId = RoomId("room-state-runtime"),
      descriptor = BattleSessionDescriptor(
        battleId = BattleId("battle-state-runtime"),
        startedAt = EpochMillis(1_000L),
        serverTime = EpochMillis(1_000L),
        roster = Vector.empty,
        capacity = BattleCapacity(seats.length),
        bootstrap = Some(BattleSessionBootstrap(seats))
      ),
      commandOwnership =
        if commandOwnership.nonEmpty then commandOwnership
        else seats.headOption.map(seat => BattleCommandOwnership(seat.playerId, TicketId("ticket-alice"))).toVector
    )

  private def seat(
    playerId: PlayerId,
    heroId: HeroId,
    handle: PlayerHandle,
    displayName: DisplayName,
    seat: SeatIndex,
    isBot: Boolean,
    spawnPointIndex: Option[SpawnPointIndex] = None
  ): BattleSessionBootstrapSeat =
    BattleSessionBootstrapSeat(
      seat = seat,
      playerId = playerId,
      heroId = heroId,
      handle = handle,
      displayName = displayName,
      joinedAt = EpochMillis(1_000L),
      isBot = isBot,
      spawnPointIndex = spawnPointIndex.getOrElse(SpawnPointIndex(seat.value)),
      rating = Some(Rating(1200)),
      avatar = None,
      skin = None
    )

  private def command(
    playerId: PlayerId,
    ticketId: TicketId,
    seq: Long,
    movement: BattleCommandVector = BattleCommandVector(0.0, 0.0),
    aim: BattleCommandVector = BattleCommandVector(1.0, 0.0),
    primaryHeld: Boolean = false,
    sprint: Boolean = false,
    reloadPressed: Boolean = false,
    castDash: Boolean = false,
    castBlink: Boolean = false,
    castFreeze: Boolean = false,
    pointerWorld: Option[BattleCommandVector] = None,
    switchWeaponDirection: Int = 0,
    switchWeaponIndex: Option[Int] = None
  ): BattleCommandRequest =
    BattleCommandRequest(
      battleId = BattleId("battle-state-runtime"),
      playerId = playerId,
      ticketId = ticketId,
      clientTick = BattleTick(0L),
      clientCommandSeq = ClientCommandSeq(seq),
      movement = movement,
      aim = aim,
      primaryHeld = primaryHeld,
      sprint = sprint,
      reloadPressed = reloadPressed,
      castDash = castDash,
      castBlink = castBlink,
      castFreeze = castFreeze,
      pointerWorld = pointerWorld,
      switchWeaponDirection = switchWeaponDirection,
      switchWeaponIndex = switchWeaponIndex
    )

  private final case class TestClock(var now: Long) {
    def millis(): Long = now
  }

  private final case class FixedBattleSessionLookup(seed: BattleSessionSeed) extends BattleSessionLookup {
    override def activeBattleSession(battleId: BattleId): Option[BattleSessionSeed] =
      Option.when(seed.descriptor.battleId == battleId)(seed)
  }

  private final case class RecordingProjector(outcome: BattleFinishProjectionOutcome) extends BattleFinishProjector {
    var projectedStates: Vector[BattleAggregateState] = Vector.empty

    override def project(state: BattleAggregateState): BattleFinishProjectionOutcome = {
      projectedStates = projectedStates :+ state
      outcome
    }
  }

  private final case class RecordingRoomLifecycleSink() extends BattleRoomLifecycleSink {
    var finishedRooms: Vector[(RoomId, EpochMillis)] = Vector.empty

    override def markBattleFinished(roomId: RoomId, finishedAt: EpochMillis): Unit =
      if !finishedRooms.exists(_._1 == roomId) then finishedRooms = finishedRooms :+ (roomId -> finishedAt)
  }

  private def assertEquals[A](label: String, actual: A, expected: A): Unit =
    assert(actual == expected, s"$label: expected $expected, got $actual")

  private def assertClose(label: String, actual: Double, expected: Double, tolerance: Double): Unit =
    assert(math.abs(actual - expected) <= tolerance, s"$label: expected $expected +/- $tolerance, got $actual")

  private def assertProjectileMuzzleBirth(
    label: String,
    ownerPosition: BattleVector2,
    projectile: BattleProjectileState,
    expectedDistance: Double
  ): Unit = {
    val offset = BattleVector2(projectile.position.x - ownerPosition.x, projectile.position.y - ownerPosition.y)
    val offsetLength = math.hypot(offset.x, offset.y)
    val velocityLength = math.hypot(projectile.velocity.x, projectile.velocity.y)
    assertClose(s"$label distance", offsetLength, expectedDistance, 0.01)
    assert(velocityLength > 0.0, s"$label velocity should be non-zero")
    assertClose(s"$label direction x", offset.x / offsetLength, projectile.velocity.x / velocityLength, 0.001)
    assertClose(s"$label direction y", offset.y / offsetLength, projectile.velocity.y / velocityLength, 0.001)
  }

  private def vectorLengthForTest(vector: BattleVector2): Double =
    math.hypot(vector.x, vector.y)

  private def distanceBetweenForTest(left: BattleVector2, right: BattleVector2): Double =
    math.hypot(left.x - right.x, left.y - right.y)

  private def aliceWeapon(service: InMemoryBattleStateService, context: String): BattleWeaponState = {
    val state = battleState(service, context)
    val alice = state.players.find(_.playerId == PlayerId("alice")).getOrElse(fail(s"missing alice for $context"))
    alice.weapons.headOption.getOrElse(fail(s"missing pistol for $context"))
  }

  private def battleState(service: InMemoryBattleStateService, context: String): BattleAggregateState =
    service.currentState(BattleId("battle-state-runtime"))
      .fold(error => fail(s"state not found for $context: $error"), value => value)

  private def fail(message: String): Nothing =
    throw AssertionError(message)
}
