package slaydemo.backend.battle.services

import io.circe.Json
import io.circe.parser.parse

import slaydemo.backend.battle.objects.*
import slaydemo.backend.identity.objects.{DisplayName, PlayerHandle}
import slaydemo.backend.replay.objects.ReplayFrameCount

object BattleFinishProjectionPlanContractTest {
  def main(args: Array[String]): Unit = {
    val state = finishedBattleState()
    val plan = BattleFinishProjectionPlanner.build(
      state,
      BattlePreviousRatings.fromRatings(
        Vector(PlayerHandle("Alice") -> Rating(1300), PlayerHandle("Bob") -> Rating(900))
      )
    )

    settlementsExcludeBotsButUseOverallPlacement(state, plan)
    previousRatingsCarryForward(plan)
    labelsFollowLegacyFinishProjection(state, plan)
    replayMirrorsHumanSettlements(plan)
    replayOwnerPrefersPlayableWinner(state)
    visitorLikeHumansFallBackToServerSettlement(state)
    frameJsonEscapesBattleText(plan)
    fallbackReplayFramesUseEventsAndFinalElapsed(state)
    capturedReplayFramesAreClampedSortedAndDeduplicated(state)
    missingPreviousRatingUsesDefault(state)

    println("BattleFinishProjectionPlan contract checks passed")
  }

  private def settlementsExcludeBotsButUseOverallPlacement(
    state: BattleAggregateState,
    plan: BattleFinishProjectionPlan
  ): Unit = {
    assertEquals(
      "human players by placement excludes bot",
      BattleFinishProjectionPlanner.humanPlayersByPlacement(state).map(_.handle.value),
      Vector("Alice", "Bob")
    )
    assertEquals(
      "settlements are created only for human players",
      plan.settlements.map(_.result.handle.value),
      Vector("Alice", "Bob")
    )
    assertEquals(
      "human placements still count the leading bot",
      plan.settlements.map(_.result.placement),
      Vector(Some(BattlePlacement.unsafe(2)), Some(BattlePlacement.unsafe(3)))
    )
    assertEquals(
      "placement scores follow the legacy settlement table",
      plan.settlements.map(_.result.score.value),
      Vector(9, 7)
    )
    assertEquals(
      "players line preserves legacy seat order",
      plan.settlements.map(_.result.playersLine.value).distinct,
      Vector("Bob | Bot Zero | Alice \"Ace\"")
    )
  }

  private def previousRatingsCarryForward(plan: BattleFinishProjectionPlan): Unit = {
    val alice = settlementFor(plan, "alice")
    val bob = settlementFor(plan, "bob")

    assertEquals("alice rating before", alice.ratingBefore, Rating(1300))
    assertEquals("alice rating delta", alice.ratingDelta, RatingDelta(14))
    assertEquals("alice rating after", alice.ratingAfter, Rating(1314))
    assertEquals("bob rating before", bob.ratingBefore, Rating(900))
    assertEquals("bob rating delta", bob.ratingDelta, RatingDelta(6))
    assertEquals("bob rating after", bob.ratingAfter, Rating(906))
  }

  private def labelsFollowLegacyFinishProjection(
    state: BattleAggregateState,
    plan: BattleFinishProjectionPlan
  ): Unit = {
    val alice = settlementFor(plan, "alice")
    val bob = settlementFor(plan, "bob")

    assertEquals("finished at uses started plus capped elapsed", alice.finishedAt, EpochMillis(state.startedAt.value + state.durationMs.value))
    assert(alice.finishedAtLabel.nonEmpty && alice.finishedAtLabel != "Authoritative finish", s"unexpected finishedAtLabel ${alice.finishedAtLabel}")
    assertEquals("mode label", alice.modeLabel.value, "权威对战")
    assertEquals("map label", alice.mapLabel.value, "权威竞技场")
    assertEquals("alive result label", alice.resultLabel.value, "存活结算")
    assertEquals("dead result label", bob.resultLabel.value, "淘汰结算")
    assertContains("alive highlight", alice.highlightLine.value, "最终排名第 2 名")
    assertContains("dead timeline", bob.timelineHint.value, "1 秒被淘汰")
    assertEquals("current loadout omitted", alice.currentLoadout, None)
  }

  private def replayMirrorsHumanSettlements(plan: BattleFinishProjectionPlan): Unit = {
    val replay = plan.replay.getOrElse(fail("expected replay for human settlements"))
    val alice = settlementFor(plan, "alice")
    val bob = settlementFor(plan, "bob")

    assertEquals("replay owner is first human settlement", replay.handle, alice.handle)
    assertEquals("replay placement follows first human settlement", replay.placement, alice.placement)
    assertEquals("replay score follows first human settlement", replay.score, alice.score)
    assertEquals("replay top-level result follows battle winner", replay.resultLabel, "胜者已决")
    assertEquals("replay cover label", replay.coverLabel, "服务器战报")
    assertEquals("replay frame count", replay.frameCount, ReplayFrameCount.fromWire(3))
    assertEquals("replay playback flag", replay.playbackAvailable, true)
    assertEquals(
      "replay settlements mirror battle settlements",
      replay.settlements.map(settlement => (settlement.handle, settlement.placement, settlement.ratingAfter)),
      Vector(
        (alice.handle, alice.placement, Some(alice.ratingAfter)),
        (bob.handle, bob.placement, Some(bob.ratingAfter))
      )
    )
  }

  private def replayOwnerPrefersPlayableWinner(state: BattleAggregateState): Unit = {
    val bob = state.players.find(_.playerId == PlayerId("bob")).getOrElse(fail("missing bob"))
    val plan = BattleFinishProjectionPlanner.build(
      state.copy(winnerPlayerId = Some(bob.playerId), winnerHeroId = Some(bob.heroId)),
      BattlePreviousRatings.empty
    )
    val replay = plan.replay.getOrElse(fail("expected replay"))

    assertEquals("playable winner owns replay", replay.handle, PlayerHandle("Bob"))
  }

  private def visitorLikeHumansFallBackToServerSettlement(state: BattleAggregateState): Unit = {
    val bot = state.players.find(_.isBot).getOrElse(fail("missing bot"))
    val visitor = player(
      id = "visitor-player",
      handle = "visitor",
      displayName = "Visitor",
      seat = 0,
      isBot = false,
      alive = true,
      score = 50,
      kills = 0,
      weaponKind = WeaponKind.Pistol
    )
    val serverOnlyState = state.copy(players = Vector(visitor, bot), winnerPlayerId = None, winnerHeroId = None)
    val plan = BattleFinishProjectionPlanner.build(serverOnlyState, BattlePreviousRatings.empty)
    val replay = plan.replay.getOrElse(fail("expected server replay"))

    assertEquals("visitor-like humans are excluded", BattleFinishProjectionPlanner.humanPlayersByPlacement(serverOnlyState), Vector.empty)
    assertEquals("server fallback result handle", plan.settlements.map(_.result.handle), Vector(PlayerHandle("server")))
    assertEquals("server fallback result label", plan.settlements.head.result.resultLabel.value, "对战结束")
    assertEquals("server fallback replay owner", replay.handle, PlayerHandle("server"))
    assertEquals("server fallback replay result label", replay.resultLabel, "对战结束")
  }

  private def frameJsonEscapesBattleText(plan: BattleFinishProjectionPlan): Unit = {
    val replay = plan.replay.getOrElse(fail("expected replay for JSON checks"))
    val frames = parseReplayFrames(replay.framesJson.value)
    val firstFrame = frames.headOption.getOrElse(fail("expected first replay frame"))
    val firstHero = arrayField(firstFrame, "heroes").headOption.getOrElse(fail("expected first replay hero"))
    val firstPickup = arrayField(firstFrame, "pickups").headOption.getOrElse(fail("expected first replay pickup"))
    val projectileFrame = frames.find(frame => arrayField(frame, "projectiles").nonEmpty).getOrElse(fail("expected projectile replay frame"))
    val projectile = arrayField(projectileFrame, "projectiles").headOption.getOrElse(fail("expected replay projectile"))

    assertContains("quoted display name is escaped", replay.framesJson.value, "Alice \\\"Ace\\\"")
    assertContains(
      "event message escapes quotes, backslashes, and newlines",
      replay.framesJson.value,
      "First \\\"hit\\\"\\\\line\\nnext"
    )
    assertContains("captured projectile is rendered", replay.framesJson.value, "\"projectileId\":\"replay-projectile\"")
    assertContains("captured pickup is rendered", replay.framesJson.value, "\"id\":\"pickup-replay-gatling\"")
    assertEquals("parsed first frame elapsed", longField(firstFrame, "elapsedMs"), 0L)
    assertEquals("parsed first hero id", stringField(firstHero, "heroId"), "hero-bob")
    assertEquals("parsed first hero life state", stringField(firstHero, "lifeState"), "dead")
    assertEquals("parsed first hero eliminated at", longField(firstHero, "eliminatedAtMs"), 1600L)
    assertEquals("parsed projectile id", stringField(projectile, "projectileId"), "replay-projectile")
    assertEquals("parsed pickup weapon kind", stringField(firstPickup, "weaponKind"), WeaponKind.wireValue(WeaponKind.Gatling))
  }

  private def fallbackReplayFramesUseEventsAndFinalElapsed(state: BattleAggregateState): Unit = {
    val plan = BattleFinishProjectionPlanner.build(state.copy(replayFrames = Vector.empty), BattlePreviousRatings.empty)
    val replay = plan.replay.getOrElse(fail("expected replay"))

    assertEquals("fallback frame count uses initial, event, and final elapsed", replay.frameCount, ReplayFrameCount.fromWire(3))
    assertEquals("fallback playback is available", replay.playbackAvailable, true)
    assertEquals("fallback renders exactly three frame objects", countOccurrences(replay.framesJson.value, "\"elapsedMs\":"), 3)
    assertContains("fallback initial frame", replay.framesJson.value, "\"elapsedMs\":0")
    assertContains("fallback event frame", replay.framesJson.value, "\"elapsedMs\":1200")
    assertContains("fallback final frame", replay.framesJson.value, "\"elapsedMs\":1800")
    assertContains("fallback keeps escaped event text", replay.framesJson.value, "First \\\"hit\\\"\\\\line\\nnext")
    assertNotContains("fallback does not leak captured projectile frames", replay.framesJson.value, "replay-projectile")
  }

  private def capturedReplayFramesAreClampedSortedAndDeduplicated(state: BattleAggregateState): Unit = {
    val alice = state.players.find(_.playerId == PlayerId("alice")).getOrElse(fail("missing alice"))
    val players = state.players
    val duplicateProjectile = projectile(
      projectileId = "duplicate-kept-projectile",
      owner = alice,
      projectileKind = ProjectileKind.PistolBullet,
      position = BattleVector2(200.0, 100.0)
    )
    val droppedDuplicateProjectile = projectile(
      projectileId = "duplicate-dropped-projectile",
      owner = alice,
      projectileKind = ProjectileKind.PistolBullet,
      position = BattleVector2(180.0, 100.0)
    )
    val plan = BattleFinishProjectionPlanner.build(
      state.copy(
        replayFrames = Vector(
          replayFrame(9999L, players),
          replayFrame(1000L, players, projectiles = Vector(droppedDuplicateProjectile)),
          replayFrame(-50L, players),
          replayFrame(1000L, players, projectiles = Vector(duplicateProjectile))
        )
      ),
      BattlePreviousRatings.empty
    )
    val replay = plan.replay.getOrElse(fail("expected replay"))

    assertEquals("normalized captured frame count", replay.frameCount, ReplayFrameCount.fromWire(3))
    assertEquals("normalized frames render one object per unique clamped elapsed", countOccurrences(replay.framesJson.value, "\"elapsedMs\":"), 3)
    assertContains("negative elapsed is clamped to zero", replay.framesJson.value, "\"elapsedMs\":0")
    assertContains("duplicate elapsed is kept once", replay.framesJson.value, "\"elapsedMs\":1000")
    assertContains("overlong elapsed is clamped to duration", replay.framesJson.value, "\"elapsedMs\":1800")
    assertEquals("duplicate elapsed occurs once", countOccurrences(replay.framesJson.value, "\"elapsedMs\":1000"), 1)
    assertContains("last duplicate frame wins", replay.framesJson.value, "duplicate-kept-projectile")
    assertNotContains("earlier duplicate frame is dropped", replay.framesJson.value, "duplicate-dropped-projectile")
    assertBefore("normalized frames are sorted", replay.framesJson.value, "\"elapsedMs\":0", "\"elapsedMs\":1000")
    assertBefore("normalized final frame follows duplicate frame", replay.framesJson.value, "\"elapsedMs\":1000", "\"elapsedMs\":1800")
  }

  private def missingPreviousRatingUsesDefault(state: BattleAggregateState): Unit = {
    val plan = BattleFinishProjectionPlanner.build(state, BattlePreviousRatings.empty)
    val alice = settlementFor(plan, "alice")

    assertEquals("missing previous rating defaults to 1200", alice.ratingBefore, Rating(1200))
    assertEquals("default rating applies legacy settlement delta", alice.ratingAfter, Rating(1214))
  }

  private def settlementFor(plan: BattleFinishProjectionPlan, handleKey: String) =
    plan.settlements
      .map(_.result)
      .find(_.handle.key == handleKey)
      .getOrElse(fail(s"missing settlement for $handleKey"))

  private def finishedBattleState(): BattleAggregateState = {
    val bot = player(
      id = "bot-zero",
      handle = "BotZero",
      displayName = "Bot Zero",
      seat = 1,
      isBot = true,
      alive = true,
      score = 90,
      kills = 1,
      weaponKind = WeaponKind.Gatling
    )
    val alice = player(
      id = "alice",
      handle = "Alice",
      displayName = "Alice \"Ace\"",
      seat = 2,
      isBot = false,
      alive = true,
      score = 70,
      kills = 3,
      weaponKind = WeaponKind.RocketLauncher
    )
    val bob = player(
      id = "bob",
      handle = "Bob",
      displayName = "Bob",
      seat = 0,
      isBot = false,
      alive = false,
      score = 999,
      kills = 8,
      weaponKind = WeaponKind.Shotgun
    )
    val replayPickup = pickup("pickup-replay-gatling", PickupKind.Weapon, Some(WeaponKind.Gatling), BattleVector2(64.0, 96.0))
    val replayProjectile = projectile("replay-projectile", alice, ProjectileKind.Rocket, BattleVector2(120.0, 120.0))
    val replayFrames = Vector(
      replayFrame(0L, Vector(bob, alice, bot), pickups = Vector(replayPickup)),
      replayFrame(1000L, Vector(bob, alice, bot), projectiles = Vector(replayProjectile), pickups = Vector(replayPickup)),
      replayFrame(1800L, Vector(bob, alice, bot), pickups = Vector(replayPickup))
    )

    BattleAggregateState(
      battleId = BattleId("battle-finish-plan"),
      roomId = RoomId("room-finish-plan"),
      phase = BattlePhase.Finished,
      serverTime = EpochMillis(1710000000000L),
      startedAt = EpochMillis(1709999998200L),
      durationMs = DurationMillis(1800L),
      elapsedMs = ElapsedMillis(1800L),
      endsAt = EpochMillis(1710000000000L),
      worldSize = BattleVector2(1280.0, 720.0),
      tick = BattleTick(60L),
      artifactStatus = BattleArtifactStatus.Pending,
      players = Vector(bob, alice, bot),
      projectiles = Vector.empty,
      projectileTerminals = Vector.empty,
      slowFields = Vector.empty,
      pickups = Vector(replayPickup),
      replayFrames = replayFrames,
      events = Vector(
        BattleEventState(
          eventId = BattleEventId("event-1"),
          eventKind = BattleEventKind.Kill,
          elapsedMs = ElapsedMillis(1200L),
          message = "First \"hit\"\\line\nnext",
          source = participant(alice),
          target = participant(bob)
        )
      ),
      winnerPlayerId = Some(bot.playerId),
      winnerHeroId = Some(bot.heroId)
    )
  }

  private def player(
    id: String,
    handle: String,
    displayName: String,
    seat: Int,
    isBot: Boolean,
    alive: Boolean,
    score: Int,
    kills: Int,
    weaponKind: WeaponKind
  ): BattlePlayerState =
    BattlePlayerState(
      playerId = PlayerId(id),
      heroId = HeroId(s"hero-$id"),
      handle = PlayerHandle(handle),
      displayName = DisplayName(displayName),
      seat = SeatIndex(seat),
      participantKind = BattleParticipantKind.fromBotFlag(isBot),
      position = BattleVector2(seat.toDouble * 10.0, seat.toDouble * 5.0),
      aim = BattleVector2(1.0, 0.0),
      facing = FacingRadians(0.0),
      movement = BattleVector2(0.0, 0.0),
      sprint = false,
      primaryHeld = false,
      reloadPressed = false,
      lastClientCommandSeq = ClientCommandSeq(0L),
      currentWeaponIndex = 0,
      weapons = Vector(weapon(weaponKind)),
      currentWeaponKind = weaponKind,
      hp = HitPoints(if alive then 100 else 0),
      maxHp = HitPoints(100),
      stamina = Stamina(100),
      maxStamina = Stamina(100),
      score = Score(score),
      kills = kills,
      skills = Vector.empty,
      lifeState = BattlePlayerLifeState.fromAliveFlag(
        alive,
        Option.when(!alive)(ElapsedMillis(1600L)),
        DurationMillis(0L)
      )
    )

  private def weapon(weaponKind: WeaponKind): BattleWeaponState =
    BattleWeaponState(
      weaponKind = weaponKind,
      ammoInMagazine = AmmoCount(5),
      magazineSize = AmmoCount(5),
      reserveAmmo = Some(AmmoCount(20)),
      fireCooldownMs = CooldownMillis(0),
      reloadRemainingMs = CooldownMillis(0),
      heat = 0,
      thermalState = BattleWeaponThermalState.Ready
    )

  private def projectile(
    projectileId: String,
    owner: BattlePlayerState,
    projectileKind: ProjectileKind,
    position: BattleVector2
  ): BattleProjectileState =
    BattleProjectileState(
      projectileId = ProjectileId(projectileId),
      ownerHeroId = owner.heroId,
      projectileKind = projectileKind,
      position = position,
      velocity = BattleVector2(1.0, 0.0),
      facing = FacingRadians(0.0),
      radius = Radius(8.0),
      damage = Damage(20),
      ttlMs = DurationMillis(5000L),
      maxLifetimeMs = DurationMillis(5000L),
      splashRadius = Radius(120.0)
    )

  private def pickup(
    pickupId: String,
    pickupKind: PickupKind,
    weaponKind: Option[WeaponKind],
    position: BattleVector2
  ): BattlePickupState =
    BattlePickupState(
      pickupId = PickupId(pickupId),
      pickupKind = pickupKind,
      weaponKind = weaponKind,
      position = position,
      pickupAvailability = BattlePickupAvailability.Available
    )

  private def replayFrame(
    elapsedMs: Long,
    players: Vector[BattlePlayerState],
    projectiles: Vector[BattleProjectileState] = Vector.empty,
    pickups: Vector[BattlePickupState] = Vector.empty
  ): BattleReplayFrameState =
    BattleReplayFrameState(
      elapsedMs = ElapsedMillis(elapsedMs),
      heroes = players.map { player =>
        BattleReplayHeroFrameState(
          playerId = player.playerId,
          heroId = player.heroId,
          handle = player.handle,
          displayName = player.displayName,
          seat = player.seat,
          position = player.position,
          hp = player.hp,
          maxHp = player.maxHp,
          lifeState = BattleReplayHeroLifeState.fromAliveFlag(player.alive, player.eliminatedAtMs),
          score = player.score,
          facing = player.facing,
          currentWeaponKind = player.currentWeaponKind
        )
      },
      projectiles = projectiles.map { projectile =>
        BattleReplayProjectileFrameState(
          projectileId = projectile.projectileId,
          projectileKind = projectile.projectileKind,
          position = projectile.position,
          facing = projectile.facing,
          ttlMs = projectile.ttlMs,
          splashRadius = projectile.splashRadius
        )
      },
      pickups = pickups.map { pickup =>
        BattleReplayPickupFrameState(
          pickupId = pickup.pickupId,
          pickupKind = pickup.pickupKind,
          weaponKind = pickup.weaponKind,
          position = pickup.position,
          pickupAvailability = pickup.pickupAvailability
        )
      }
    )

  private def participant(player: BattlePlayerState): BattleEventParticipant =
    BattleEventParticipant(
      playerId = player.playerId,
      heroId = player.heroId,
      displayName = player.displayName
    )

  private def assertEquals[A](label: String, actual: A, expected: A): Unit =
    assert(actual == expected, s"$label: expected $expected, got $actual")

  private def assertContains(label: String, text: String, expectedPart: String): Unit =
    assert(text.contains(expectedPart), s"$label: expected to find $expectedPart in $text")

  private def assertNotContains(label: String, text: String, unexpectedPart: String): Unit =
    assert(!text.contains(unexpectedPart), s"$label: did not expect to find $unexpectedPart in $text")

  private def assertBefore(label: String, text: String, first: String, second: String): Unit = {
    val firstIndex = text.indexOf(first)
    val secondIndex = text.indexOf(second)
    assert(firstIndex >= 0, s"$label: expected to find $first in $text")
    assert(secondIndex >= 0, s"$label: expected to find $second in $text")
    assert(firstIndex < secondIndex, s"$label: expected $first before $second in $text")
  }

  private def countOccurrences(text: String, part: String): Int = {
    var count = 0
    var index = text.indexOf(part)
    while index >= 0 do {
      count += 1
      index = text.indexOf(part, index + part.length)
    }
    count
  }

  private def parseReplayFrames(framesJson: String): Vector[Json] =
    parse(framesJson)
      .fold(error => fail(s"invalid replay frames json: ${error.getMessage}"), identity)
      .asArray
      .map(_.toVector)
      .getOrElse(fail(s"replay frames json must be an array: $framesJson"))

  private def arrayField(json: Json, field: String): Vector[Json] =
    json.hcursor.downField(field).focus
      .flatMap(_.asArray.map(_.toVector))
      .getOrElse(fail(s"expected JSON array field `$field` in ${json.noSpaces}"))

  private def stringField(json: Json, field: String): String =
    json.hcursor.downField(field).as[String]
      .getOrElse(fail(s"expected JSON string field `$field` in ${json.noSpaces}"))

  private def longField(json: Json, field: String): Long =
    json.hcursor.downField(field).as[Long]
      .getOrElse(fail(s"expected JSON long field `$field` in ${json.noSpaces}"))

  private def fail(message: String): Nothing =
    throw AssertionError(message)
}
