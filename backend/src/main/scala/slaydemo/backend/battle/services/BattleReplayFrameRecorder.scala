package slaydemo.backend.battle.services

import slaydemo.backend.battle.objects.*

private[services] object BattleReplayFrameRecorder {
  def updateFrames(
    frames: Vector[BattleReplayFrameState],
    elapsedMs: ElapsedMillis,
    players: Vector[BattlePlayerState],
    projectiles: Vector[BattleProjectileState],
    pickups: Vector[BattlePickupState],
    hasRuntimeEvents: Boolean,
    finished: Boolean
  ): Vector[BattleReplayFrameState] =
    if hasRuntimeEvents || finished || shouldRecordIntervalFrame(frames, elapsedMs) then
      appendFrame(frames, elapsedMs, players, projectiles, pickups)
    else frames

  def appendFrame(
    frames: Vector[BattleReplayFrameState],
    elapsedMs: ElapsedMillis,
    players: Vector[BattlePlayerState],
    projectiles: Vector[BattleProjectileState],
    pickups: Vector[BattlePickupState]
  ): Vector[BattleReplayFrameState] =
    retainFrames(
      frames.filterNot(_.elapsedMs == elapsedMs) :+ captureFrame(elapsedMs, players, projectiles, pickups)
    )

  def captureFrame(
    elapsedMs: ElapsedMillis,
    players: Vector[BattlePlayerState],
    projectiles: Vector[BattleProjectileState],
    pickups: Vector[BattlePickupState]
  ): BattleReplayFrameState =
    BattleReplayFrameState(
      elapsedMs = ElapsedMillis(math.max(0L, elapsedMs.value)),
      heroes = players.sortBy(_.seat.value).map { player =>
        BattleReplayHeroFrameState(
          playerId = player.playerId,
          heroId = player.heroId,
          handle = player.handle,
          displayName = player.displayName,
          seat = player.seat,
          position = player.position,
          hp = HitPoints(math.max(0, player.hp.value)),
          maxHp = HitPoints(math.max(1, player.maxHp.value)),
          lifeState = BattleReplayHeroLifeState.fromAliveFlag(
            player.alive,
            player.eliminatedAtMs.map(value => ElapsedMillis(math.max(0L, value.value)))
          ),
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
          ttlMs = DurationMillis(math.max(0L, projectile.ttlMs.value)),
          splashRadius = Radius(math.max(0.0, projectile.splashRadius.value))
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

  private def shouldRecordIntervalFrame(frames: Vector[BattleReplayFrameState], elapsedMs: ElapsedMillis): Boolean =
    elapsedMs.value > 0L && {
      val latestElapsedMs = frames.map(_.elapsedMs.value).maxOption.getOrElse(0L)
      elapsedMs.value / BattleHistoryCatalog.ReplayFrameSampleInterval.value >
        latestElapsedMs / BattleHistoryCatalog.ReplayFrameSampleInterval.value
    }

  private def retainFrames(frames: Vector[BattleReplayFrameState]): Vector[BattleReplayFrameState] = {
    val distinctFrames = frames.sortBy(_.elapsedMs.value).foldLeft(Vector.empty[BattleReplayFrameState]) {
      case (accumulator, frame) if accumulator.lastOption.exists(_.elapsedMs == frame.elapsedMs) =>
        accumulator.dropRight(1) :+ frame
      case (accumulator, frame) =>
        accumulator :+ frame
    }

    if distinctFrames.length <= BattleHistoryCatalog.RetainedReplayFrameCount.value then distinctFrames
    else
      val initialFrame = distinctFrames.headOption.filter(_.elapsedMs.value == 0L).toVector
      val retainedTail =
        distinctFrames
          .drop(initialFrame.length)
          .takeRight(BattleHistoryCatalog.RetainedReplayFrameCount.value - initialFrame.length)
      initialFrame ++ retainedTail
  }
}
