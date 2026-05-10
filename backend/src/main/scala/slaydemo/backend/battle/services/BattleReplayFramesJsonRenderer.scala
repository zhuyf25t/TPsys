package slaydemo.backend.battle.services

import slaydemo.backend.battle.objects.*
import slaydemo.backend.identity.objects.{DisplayName, PlayerHandle}

private[services] final case class BattleReplayFramesJson(frameCount: Int, json: String)

private[services] object BattleReplayFramesJsonRenderer {
  def render(state: BattleAggregateState, durationMs: DurationMillis): BattleReplayFramesJson = {
    val frameJson =
      if state.replayFrames.nonEmpty then
        BattleReplayFrameTimelineRules.normalizeReplayFrames(state.replayFrames, durationMs)
          .map(frame => replayFrameJson(state, frame))
      else fallbackReplayFrameJson(state, durationMs)

    BattleReplayFramesJson(
      frameCount = frameJson.length,
      json = frameJson.mkString("[", ",", "]")
    )
  }

  private def fallbackReplayFrameJson(state: BattleAggregateState, durationMs: DurationMillis): Vector[String] = {
    val timeline = BattleReplayFrameTimelineRules.fallbackTimeline(state.events, durationMs)
    timeline.frameElapsedMs.map(elapsedMs => replayFrameJson(state, elapsedMs, timeline.finalElapsedMs))
  }

  private def replayFrameJson(state: BattleAggregateState, frame: BattleReplayFrameState): String =
    replayFrameJson(
      state = state,
      elapsedMs = frame.elapsedMs,
      heroesJson = frame.heroes.sortBy(_.seat.value).map(heroFrameJson).mkString("[", ",", "]"),
      projectilesJson = frame.projectiles.map(projectileFrameJson).mkString("[", ",", "]"),
      pickupsJson = frame.pickups.map(pickupFrameJson).mkString("[", ",", "]")
    )

  private def replayFrameJson(
    state: BattleAggregateState,
    elapsedMs: ElapsedMillis,
    finalElapsedMs: ElapsedMillis
  ): String =
    replayFrameJson(
      state = state,
      elapsedMs = elapsedMs,
      heroesJson = state.players.sortBy(_.seat.value).map(player => heroFrameJson(player, elapsedMs, finalElapsedMs)).mkString("[", ",", "]"),
      projectilesJson = if elapsedMs == finalElapsedMs then state.projectiles.map(projectileFrameJson).mkString("[", ",", "]") else "[]",
      pickupsJson = state.pickups.map(pickupFrameJson).mkString("[", ",", "]")
    )

  private def replayFrameJson(
    state: BattleAggregateState,
    elapsedMs: ElapsedMillis,
    heroesJson: String,
    projectilesJson: String,
    pickupsJson: String
  ): String =
    renderObject(
      Vector(
        "elapsedMs" -> elapsedMs.value.toString,
        "worldSize" -> renderVector(state.worldSize),
        "heroes" -> heroesJson,
        "projectiles" -> projectilesJson,
        "pickups" -> pickupsJson,
        "eventMessages" -> eventMessagesJson(state, elapsedMs)
      )
    )

  private def heroFrameJson(hero: BattleReplayHeroFrameState): String =
    heroFrameJson(
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

  private def heroFrameJson(
    player: BattlePlayerState,
    elapsedMs: ElapsedMillis,
    finalElapsedMs: ElapsedMillis
  ): String = {
    val aliveAtFrame =
      if elapsedMs == finalElapsedMs then player.alive
      else player.eliminatedAtMs.forall(_.value > elapsedMs.value)
    heroFrameJson(
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

  private def heroFrameJson(
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
  ): String =
    renderObject(
      Vector(
        "heroId" -> jsonString(heroId.value),
        "displayName" -> jsonString(replayDisplayName(displayName, handle, playerId)),
        "position" -> renderVector(position),
        "hp" -> (if alive then math.max(0, hp.value) else 0).toString,
        "maxHp" -> math.max(1, maxHp.value).toString,
        "alive" -> alive.toString,
        "lifeState" -> jsonString(if alive then "alive" else "dead"),
        "score" -> score.value.toString,
        "facing" -> facing.value.toString,
        "currentWeaponKind" -> jsonString(WeaponKind.wireValue(currentWeaponKind)),
        "eliminatedAtMs" -> eliminatedAtMs.map(_.value.toString).getOrElse("null")
      )
    )

  private def projectileFrameJson(projectile: BattleReplayProjectileFrameState): String =
    projectileFrameJson(
      projectileId = projectile.projectileId,
      projectileKind = projectile.projectileKind,
      position = projectile.position,
      facing = projectile.facing,
      ttlMs = projectile.ttlMs,
      splashRadius = projectile.splashRadius
    )

  private def projectileFrameJson(projectile: BattleProjectileState): String =
    projectileFrameJson(
      projectileId = projectile.projectileId,
      projectileKind = projectile.projectileKind,
      position = projectile.position,
      facing = projectile.facing,
      ttlMs = projectile.ttlMs,
      splashRadius = projectile.splashRadius
    )

  private def projectileFrameJson(
    projectileId: ProjectileId,
    projectileKind: ProjectileKind,
    position: BattleVector2,
    facing: FacingRadians,
    ttlMs: DurationMillis,
    splashRadius: Radius
  ): String =
    renderObject(
      Vector(
        "projectileId" -> jsonString(projectileId.value),
        "kind" -> jsonString(ProjectileKind.wireValue(projectileKind)),
        "position" -> renderVector(position),
        "facing" -> facing.value.toString,
        "alive" -> true.toString,
        "ttlMs" -> math.max(0L, ttlMs.value).toString,
        "splashRadius" -> math.max(0.0, splashRadius.value).toString
      )
    )

  private def pickupFrameJson(pickup: BattleReplayPickupFrameState): String =
    pickupFrameJson(
      pickupId = pickup.pickupId,
      pickupKind = pickup.pickupKind,
      weaponKind = pickup.weaponKind,
      position = pickup.position,
      available = pickup.available
    )

  private def pickupFrameJson(pickup: BattlePickupState): String =
    pickupFrameJson(
      pickupId = pickup.pickupId,
      pickupKind = pickup.pickupKind,
      weaponKind = pickup.weaponKind,
      position = pickup.position,
      available = pickup.available
    )

  private def pickupFrameJson(
    pickupId: PickupId,
    pickupKind: PickupKind,
    weaponKind: Option[WeaponKind],
    position: BattleVector2,
    available: Boolean
  ): String =
    renderObject(
      Vector(
        "id" -> jsonString(pickupId.value),
        "kind" -> jsonString(replayPickupKind(pickupKind)),
        "position" -> renderVector(position),
        "available" -> available.toString
      ) ++ optionalStringField("weaponKind", weaponKind.map(WeaponKind.wireValue))
    )

  private def eventMessagesJson(state: BattleAggregateState, elapsedMs: ElapsedMillis): String =
    state.events
      .filter(_.elapsedMs.value <= elapsedMs.value)
      .sortBy(_.elapsedMs.value)
      .takeRight(6)
      .map(event => jsonString(event.message))
      .mkString("[", ",", "]")

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

  private def renderVector(vector: BattleVector2): String =
    renderObject(Vector("x" -> vector.x.toString, "y" -> vector.y.toString))

  private def optionalStringField(key: String, value: Option[String]): Vector[(String, String)] =
    value.filter(_.trim.nonEmpty).map(text => Vector(key -> jsonString(text))).getOrElse(Vector.empty)

  private def renderObject(fields: Vector[(String, String)]): String =
    fields.map { case (key, value) => s"${jsonString(key)}:$value" }.mkString("{", ",", "}")

  private def jsonString(value: String): String =
    s""""${escapeJson(value)}""""

  private def escapeJson(value: String): String =
    value.flatMap {
      case '"'  => "\\\""
      case '\\' => "\\\\"
      case '\b' => "\\b"
      case '\f' => "\\f"
      case '\n' => "\\n"
      case '\r' => "\\r"
      case '\t' => "\\t"
      case char if char.isControl => f"\\u${char.toInt}%04x"
      case char => char.toString
    }

}
