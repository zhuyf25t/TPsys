package services.battle.application

import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.syntax.*

import services.battle.objects.*
import services.identity.objects.{DisplayName, PlayerHandle}

private[services] final case class BattleReplayFramesJson(frameCount: Int, json: String)

private[services] object BattleReplayFramesJsonRenderer {
  /** 中文名：渲染回放帧 JSON（render replay frames JSON）。游戏职责：把权威 battle state/replay frames 转成前端战报播放器使用的 wire JSON；这里只做 DTO 转换和编码，不推进战斗业务。 */
  def render(state: BattleAggregateState, durationMs: DurationMillis): BattleReplayFramesJson = {
    val frames =
      if state.replayFrames.nonEmpty then
        BattleReplayFrameTimelineRules.normalizeReplayFrames(state.replayFrames, durationMs)
          .map(frame => replayFramePayload(state, frame))
      else fallbackReplayFramePayloads(state, durationMs)

    BattleReplayFramesJson(
      frameCount = frames.length,
      json = frames.asJson.noSpaces
    )
  }

  private def fallbackReplayFramePayloads(
    state: BattleAggregateState,
    durationMs: DurationMillis
  ): Vector[BattleReplayFramePayload] = {
    val timeline = BattleReplayFrameTimelineRules.fallbackTimeline(state.events, durationMs)
    timeline.frameElapsedMs.map(elapsedMs => replayFramePayload(state, elapsedMs, timeline.finalElapsedMs))
  }

  private def replayFramePayload(
    state: BattleAggregateState,
    frame: BattleReplayFrameState
  ): BattleReplayFramePayload =
    replayFramePayload(
      elapsedMs = frame.elapsedMs,
      worldSize = state.worldSize,
      heroes = frame.heroes.sortBy(_.seat.value).map(heroFramePayload),
      projectiles = frame.projectiles.map(projectileFramePayload),
      pickups = frame.pickups.map(pickupFramePayload),
      eventMessages = eventMessages(state, frame.elapsedMs)
    )

  private def replayFramePayload(
    state: BattleAggregateState,
    elapsedMs: ElapsedMillis,
    finalElapsedMs: ElapsedMillis
  ): BattleReplayFramePayload =
    replayFramePayload(
      elapsedMs = elapsedMs,
      worldSize = state.worldSize,
      heroes = state.players.sortBy(_.seat.value).map(player => heroFramePayload(player, elapsedMs, finalElapsedMs)),
      projectiles = if elapsedMs == finalElapsedMs then state.projectiles.map(projectileFramePayload) else Vector.empty,
      pickups = state.pickups.map(pickupFramePayload),
      eventMessages = eventMessages(state, elapsedMs)
    )

  private def replayFramePayload(
    elapsedMs: ElapsedMillis,
    worldSize: BattleVector2,
    heroes: Vector[BattleReplayHeroPayload],
    projectiles: Vector[BattleReplayProjectilePayload],
    pickups: Vector[BattleReplayPickupPayload],
    eventMessages: Vector[String]
  ): BattleReplayFramePayload =
    BattleReplayFramePayload(
      elapsedMs = elapsedMs.value,
      worldSize = vectorPayload(worldSize),
      heroes = heroes,
      projectiles = projectiles,
      pickups = pickups,
      eventMessages = eventMessages
    )

  private def heroFramePayload(hero: BattleReplayHeroFrameState): BattleReplayHeroPayload =
    heroFramePayload(
      playerId = hero.playerId,
      heroId = hero.heroId,
      displayName = hero.displayName,
      handle = hero.handle,
      position = hero.position,
      hp = hero.hp,
      maxHp = hero.maxHp,
      alive = hero.alive,
      score = hero.score,
      facing = hero.facing,
      currentWeaponKind = hero.currentWeaponKind,
      eliminatedAtMs = hero.eliminatedAtMs
    )

  private def heroFramePayload(
    player: BattlePlayerState,
    elapsedMs: ElapsedMillis,
    finalElapsedMs: ElapsedMillis
  ): BattleReplayHeroPayload = {
    val aliveAtFrame =
      if elapsedMs == finalElapsedMs then player.alive
      else player.eliminatedAtMs.forall(_.value > elapsedMs.value)
    heroFramePayload(
      playerId = player.playerId,
      heroId = player.heroId,
      displayName = player.displayName,
      handle = player.handle,
      position = player.position,
      hp = player.hp,
      maxHp = player.maxHp,
      alive = aliveAtFrame,
      score = player.score,
      facing = player.facing,
      currentWeaponKind = player.currentWeaponKind,
      eliminatedAtMs = player.eliminatedAtMs
    )
  }

  private def heroFramePayload(
    playerId: PlayerId,
    heroId: HeroId,
    displayName: DisplayName,
    handle: PlayerHandle,
    position: BattleVector2,
    hp: HitPoints,
    maxHp: HitPoints,
    alive: Boolean,
    score: Score,
    facing: FacingRadians,
    currentWeaponKind: WeaponKind,
    eliminatedAtMs: Option[ElapsedMillis]
  ): BattleReplayHeroPayload =
    BattleReplayHeroPayload(
      heroId = heroId.value,
      displayName = replayDisplayName(displayName, handle, playerId),
      position = vectorPayload(position),
      hp = if alive then math.max(0, hp.value) else 0,
      maxHp = math.max(1, maxHp.value),
      alive = alive,
      lifeState = if alive then "alive" else "dead",
      score = score.value,
      facing = facing.value,
      currentWeaponKind = WeaponKind.wireValue(currentWeaponKind),
      eliminatedAtMs = eliminatedAtMs.map(_.value)
    )

  private def projectileFramePayload(projectile: BattleReplayProjectileFrameState): BattleReplayProjectilePayload =
    projectileFramePayload(
      projectileId = projectile.projectileId,
      projectileKind = projectile.projectileKind,
      position = projectile.position,
      facing = projectile.facing,
      ttlMs = projectile.ttlMs,
      splashRadius = projectile.splashRadius
    )

  private def projectileFramePayload(projectile: BattleProjectileState): BattleReplayProjectilePayload =
    projectileFramePayload(
      projectileId = projectile.projectileId,
      projectileKind = projectile.projectileKind,
      position = projectile.position,
      facing = projectile.facing,
      ttlMs = projectile.ttlMs,
      splashRadius = projectile.splashRadius
    )

  private def projectileFramePayload(
    projectileId: ProjectileId,
    projectileKind: ProjectileKind,
    position: BattleVector2,
    facing: FacingRadians,
    ttlMs: DurationMillis,
    splashRadius: Radius
  ): BattleReplayProjectilePayload =
    BattleReplayProjectilePayload(
      projectileId = projectileId.value,
      kind = ProjectileKind.wireValue(projectileKind),
      position = vectorPayload(position),
      facing = facing.value,
      alive = true,
      ttlMs = math.max(0L, ttlMs.value),
      splashRadius = math.max(0.0, splashRadius.value)
    )

  private def pickupFramePayload(pickup: BattleReplayPickupFrameState): BattleReplayPickupPayload =
    pickupFramePayload(
      pickupId = pickup.pickupId,
      pickupKind = pickup.pickupKind,
      weaponKind = pickup.weaponKind,
      position = pickup.position,
      available = pickup.available
    )

  private def pickupFramePayload(pickup: BattlePickupState): BattleReplayPickupPayload =
    pickupFramePayload(
      pickupId = pickup.pickupId,
      pickupKind = pickup.pickupKind,
      weaponKind = pickup.weaponKind,
      position = pickup.position,
      available = pickup.available
    )

  private def pickupFramePayload(
    pickupId: PickupId,
    pickupKind: PickupKind,
    weaponKind: Option[WeaponKind],
    position: BattleVector2,
    available: Boolean
  ): BattleReplayPickupPayload =
    weaponKind.map(WeaponKind.wireValue).filter(_.trim.nonEmpty) match {
      case Some(weaponKind) =>
        BattleReplayWeaponPickupPayload(
          id = pickupId.value,
          kind = replayPickupKind(pickupKind),
          position = vectorPayload(position),
          available = available,
          weaponKind = weaponKind
        )
      case None =>
        BattleReplaySimplePickupPayload(
          id = pickupId.value,
          kind = replayPickupKind(pickupKind),
          position = vectorPayload(position),
          available = available
        )
    }

  private def eventMessages(state: BattleAggregateState, elapsedMs: ElapsedMillis): Vector[String] =
    state.events
      .filter(_.elapsedMs.value <= elapsedMs.value)
      .sortBy(_.elapsedMs.value)
      .takeRight(6)
      .map(_.message)

  private def replayDisplayName(displayName: DisplayName, handle: PlayerHandle, playerId: PlayerId): String = {
    val display = displayName.value.trim
    if display.nonEmpty then display
    else
      val handleValue = handle.value.trim
      if handleValue.nonEmpty then handleValue else playerId.value
  }

  private def replayPickupKind(kind: PickupKind): String =
    kind match {
      case PickupKind.Weapon => "weapon"
      case PickupKind.Medkit => "medkit"
    }

  private def vectorPayload(vector: BattleVector2): BattleReplayVectorPayload =
    BattleReplayVectorPayload(x = vector.x, y = vector.y)

  private final case class BattleReplayFramePayload(
    elapsedMs: Long,
    worldSize: BattleReplayVectorPayload,
    heroes: Vector[BattleReplayHeroPayload],
    projectiles: Vector[BattleReplayProjectilePayload],
    pickups: Vector[BattleReplayPickupPayload],
    eventMessages: Vector[String]
  )

  private final case class BattleReplayHeroPayload(
    heroId: String,
    displayName: String,
    position: BattleReplayVectorPayload,
    hp: Int,
    maxHp: Int,
    alive: Boolean,
    lifeState: String,
    score: Int,
    facing: Double,
    currentWeaponKind: String,
    eliminatedAtMs: Option[Long]
  )

  private final case class BattleReplayProjectilePayload(
    projectileId: String,
    kind: String,
    position: BattleReplayVectorPayload,
    facing: Double,
    alive: Boolean,
    ttlMs: Long,
    splashRadius: Double
  )

  private sealed trait BattleReplayPickupPayload

  private final case class BattleReplayWeaponPickupPayload(
    id: String,
    kind: String,
    position: BattleReplayVectorPayload,
    available: Boolean,
    weaponKind: String
  ) extends BattleReplayPickupPayload

  private final case class BattleReplaySimplePickupPayload(
    id: String,
    kind: String,
    position: BattleReplayVectorPayload,
    available: Boolean
  ) extends BattleReplayPickupPayload

  private final case class BattleReplayVectorPayload(x: Double, y: Double)

  private given Encoder[BattleReplayVectorPayload] = deriveEncoder
  private given Encoder[BattleReplayHeroPayload] = deriveEncoder
  private given Encoder[BattleReplayProjectilePayload] = deriveEncoder
  private given Encoder[BattleReplayWeaponPickupPayload] = deriveEncoder
  private given Encoder[BattleReplaySimplePickupPayload] = deriveEncoder
  private given Encoder[BattleReplayFramePayload] = deriveEncoder

  private given Encoder[BattleReplayPickupPayload] =
    Encoder.instance { pickup =>
      pickup match {
        case weaponPickup: BattleReplayWeaponPickupPayload => weaponPickup.asJson
        case simplePickup: BattleReplaySimplePickupPayload => simplePickup.asJson
      }
    }

}
