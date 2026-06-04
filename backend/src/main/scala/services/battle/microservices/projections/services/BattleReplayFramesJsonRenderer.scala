package services.battle.microservices.projections.services

import cats.effect.IO
import cats.syntax.all.*

import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.syntax.*

import services.battle.microservices.combat.objects.projectile.ProjectileKind
import services.battle.microservices.combat.objects.weapon.WeaponKind
import services.battle.microservices.abilities.objects.pickup.{PickupId, PickupKind}
import services.battle.objects.core.{
  BattleAggregateState,
  BattleVector2,
  DurationMillis,
  ElapsedMillis,
  FacingRadians,
  HeroId,
  PlayerId,
  ProjectileId,
  Radius
}
import services.battle.microservices.abilities.objects.pickup.BattlePickupState
import services.battle.microservices.actors.objects.player.{BattlePlayerState, HitPoints, Score}
import services.battle.microservices.combat.objects.projectile.BattleProjectileState
import services.battle.microservices.projections.objects.replay.{
  BattleReplayFrameState,
  BattleReplayHeroFrameState,
  BattleReplayPickupFrameState,
  BattleReplayProjectileFrameState
}
import services.identity.objects.{DisplayName, PlayerHandle}

private[battle] final case class BattleReplayFramesJson(frameCount: Int, json: String)

private[battle] object BattleReplayFramesJsonRenderer {
  /** 中文名：渲染回放帧 JSON（render replay frames JSON）。游戏职责：把权威 battle state/replay frames 转成前端战报播放器使用的 wire JSON；这里只做 DTO 转换和编码，不推进战斗业务。 */
  def render(state: BattleAggregateState, durationMs: DurationMillis): IO[BattleReplayFramesJson] = {
    val frames =
      if state.replayFrames.nonEmpty then
        BattleReplayFrameTimelineRules.normalizeReplayFrames(state.replayFrames, durationMs)
          .flatMap(_.traverse(frame => replayFramePayload(state, frame)))
      else fallbackReplayFramePayloads(state, durationMs)

    frames.map { values =>
      BattleReplayFramesJson(
        frameCount = values.length,
        json = values.asJson.noSpaces
      )
    }
  }

  private def fallbackReplayFramePayloads(
    state: BattleAggregateState,
    durationMs: DurationMillis
  ): IO[Vector[BattleReplayFramePayload]] =
    BattleReplayFrameTimelineRules.fallbackTimeline(state.events, durationMs).flatMap { timeline =>
      timeline.frameElapsedMs.traverse(elapsedMs => replayFramePayload(state, elapsedMs, timeline.finalElapsedMs))
    }

  private def replayFramePayload(
    state: BattleAggregateState,
    frame: BattleReplayFrameState
  ): IO[BattleReplayFramePayload] =
    for
      heroes <- frame.heroes.sortBy(_.seat.value).traverse(heroFramePayload)
      projectiles <- frame.projectiles.traverse(projectileFramePayload)
      pickups <- frame.pickups.traverse(pickupFramePayload)
      messages <- eventMessages(state, frame.elapsedMs)
      payload <- replayFramePayload(
        elapsedMs = frame.elapsedMs,
        worldSize = state.worldSize,
        heroes = heroes,
        projectiles = projectiles,
        pickups = pickups,
        eventMessages = messages
      )
    yield payload

  private def replayFramePayload(
    state: BattleAggregateState,
    elapsedMs: ElapsedMillis,
    finalElapsedMs: ElapsedMillis
  ): IO[BattleReplayFramePayload] =
    for
      heroes <- state.players.sortBy(_.seat.value).traverse(player => heroFramePayload(player, elapsedMs, finalElapsedMs))
      projectiles <-
        if elapsedMs == finalElapsedMs then state.projectiles.traverse(projectileFramePayload)
        else IO.pure(Vector.empty)
      pickups <- state.pickups.traverse(pickupFramePayload)
      messages <- eventMessages(state, elapsedMs)
      payload <- replayFramePayload(
        elapsedMs = elapsedMs,
        worldSize = state.worldSize,
        heroes = heroes,
        projectiles = projectiles,
        pickups = pickups,
        eventMessages = messages
      )
    yield payload

  private def replayFramePayload(
    elapsedMs: ElapsedMillis,
    worldSize: BattleVector2,
    heroes: Vector[BattleReplayHeroPayload],
    projectiles: Vector[BattleReplayProjectilePayload],
    pickups: Vector[BattleReplayPickupPayload],
    eventMessages: Vector[String]
  ): IO[BattleReplayFramePayload] =
    vectorPayload(worldSize).map { worldSizePayload =>
      BattleReplayFramePayload(
        elapsedMs = elapsedMs.value,
        worldSize = worldSizePayload,
        heroes = heroes,
        projectiles = projectiles,
        pickups = pickups,
        eventMessages = eventMessages
      )
    }

  private def heroFramePayload(hero: BattleReplayHeroFrameState): IO[BattleReplayHeroPayload] =
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
  ): IO[BattleReplayHeroPayload] = {
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
  ): IO[BattleReplayHeroPayload] =
    for
      display <- replayDisplayName(displayName, handle, playerId)
      positionPayload <- vectorPayload(position)
    yield BattleReplayHeroPayload(
      heroId = heroId.value,
      displayName = display,
      position = positionPayload,
      hp = if alive then math.max(0, hp.value) else 0,
      maxHp = math.max(1, maxHp.value),
      alive = alive,
      lifeState = if alive then "alive" else "dead",
      score = score.value,
      facing = facing.value,
      currentWeaponKind = WeaponKind.wireValue(currentWeaponKind),
      eliminatedAtMs = eliminatedAtMs.map(_.value)
    )

  private def projectileFramePayload(projectile: BattleReplayProjectileFrameState): IO[BattleReplayProjectilePayload] =
    projectileFramePayload(
      projectileId = projectile.projectileId,
      projectileKind = projectile.projectileKind,
      position = projectile.position,
      facing = projectile.facing,
      ttlMs = projectile.ttlMs,
      splashRadius = projectile.splashRadius
    )

  private def projectileFramePayload(projectile: BattleProjectileState): IO[BattleReplayProjectilePayload] =
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
  ): IO[BattleReplayProjectilePayload] =
    vectorPayload(position).map { positionPayload =>
      BattleReplayProjectilePayload(
        projectileId = projectileId.value,
        kind = ProjectileKind.wireValue(projectileKind),
        position = positionPayload,
        facing = facing.value,
        alive = true,
        ttlMs = math.max(0L, ttlMs.value),
        splashRadius = math.max(0.0, splashRadius.value)
      )
    }

  private def pickupFramePayload(pickup: BattleReplayPickupFrameState): IO[BattleReplayPickupPayload] =
    pickupFramePayload(
      pickupId = pickup.pickupId,
      pickupKind = pickup.pickupKind,
      weaponKind = pickup.weaponKind,
      position = pickup.position,
      available = pickup.available
    )

  private def pickupFramePayload(pickup: BattlePickupState): IO[BattleReplayPickupPayload] =
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
  ): IO[BattleReplayPickupPayload] =
    for
      pickupKindLabel <- replayPickupKind(pickupKind)
      positionPayload <- vectorPayload(position)
      payload <- weaponKind.map(WeaponKind.wireValue).filter(_.trim.nonEmpty) match {
        case Some(weaponKind) =>
          IO.pure[BattleReplayPickupPayload](
            BattleReplayWeaponPickupPayload(
              id = pickupId.value,
              kind = pickupKindLabel,
              position = positionPayload,
              available = available,
              weaponKind = weaponKind
            )
          )
        case None =>
          IO.pure[BattleReplayPickupPayload](
            BattleReplaySimplePickupPayload(
              id = pickupId.value,
              kind = pickupKindLabel,
              position = positionPayload,
              available = available
            )
          )
      }
    yield payload

  private def eventMessages(state: BattleAggregateState, elapsedMs: ElapsedMillis): IO[Vector[String]] =
    IO.pure(state.events
      .filter(_.elapsedMs.value <= elapsedMs.value)
      .sortBy(_.elapsedMs.value)
      .takeRight(6)
      .map(_.message))

  private def replayDisplayName(displayName: DisplayName, handle: PlayerHandle, playerId: PlayerId): IO[String] = IO.pure {
    val display = displayName.value.trim
    if display.nonEmpty then display
    else
      val handleValue = handle.value.trim
      if handleValue.nonEmpty then handleValue else playerId.value
  }

  private def replayPickupKind(kind: PickupKind): IO[String] =
    IO.pure(kind match {
      case PickupKind.Weapon => "weapon"
      case PickupKind.Medkit => "medkit"
    })

  private def vectorPayload(vector: BattleVector2): IO[BattleReplayVectorPayload] =
    IO.pure(BattleReplayVectorPayload(x = vector.x, y = vector.y))

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
