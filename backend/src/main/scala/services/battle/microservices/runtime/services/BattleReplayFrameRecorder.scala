package services.battle.microservices.runtime.services

import cats.effect.IO

import services.battle.microservices.actors.objects.player.{BattlePlayerState, HitPoints}
import services.battle.microservices.abilities.objects.pickup.BattlePickupState
import services.battle.microservices.combat.objects.projectile.BattleProjectileState
import services.battle.microservices.projections.objects.replay.{
  BattleReplayFrameState,
  BattleReplayHeroFrameState,
  BattleReplayHeroLifeState,
  BattleReplayPickupFrameState,
  BattleReplayProjectileFrameState
}
import services.battle.microservices.runtime.objects.runtime.BattleHistoryCount
import services.battle.objects.{
  DurationMillis,
  ElapsedMillis,
  Radius
}

private[battle] object BattleReplayFrameRecorder {
  def updateFrames(
    frames: Vector[BattleReplayFrameState],
    elapsedMs: ElapsedMillis,
    players: Vector[BattlePlayerState],
    projectiles: Vector[BattleProjectileState],
    pickups: Vector[BattlePickupState],
    hasRuntimeEvents: Boolean,
    finished: Boolean,
    replayFrameSampleInterval: DurationMillis,
    retainedReplayFrameCount: BattleHistoryCount
  ): IO[Vector[BattleReplayFrameState]] =
    for
      shouldRecord <-
        if hasRuntimeEvents || finished then IO.pure(true)
        else shouldRecordIntervalFrame(frames, elapsedMs, replayFrameSampleInterval)
      nextFrames <-
        if shouldRecord then appendFrame(frames, elapsedMs, players, projectiles, pickups, retainedReplayFrameCount)
        else IO.pure(frames)
    yield nextFrames

  def appendFrame(
    frames: Vector[BattleReplayFrameState],
    elapsedMs: ElapsedMillis,
    players: Vector[BattlePlayerState],
    projectiles: Vector[BattleProjectileState],
    pickups: Vector[BattlePickupState],
    retainedReplayFrameCount: BattleHistoryCount
  ): IO[Vector[BattleReplayFrameState]] =
    for
      frame <- captureFrame(elapsedMs, players, projectiles, pickups)
      retained <- retainFrames(frames.filterNot(_.elapsedMs == elapsedMs) :+ frame, retainedReplayFrameCount)
    yield retained

  def captureFrame(
    elapsedMs: ElapsedMillis,
    players: Vector[BattlePlayerState],
    projectiles: Vector[BattleProjectileState],
    pickups: Vector[BattlePickupState]
  ): IO[BattleReplayFrameState] =
    IO.pure(BattleReplayFrameState(
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
    ))

  private def shouldRecordIntervalFrame(
    frames: Vector[BattleReplayFrameState],
    elapsedMs: ElapsedMillis,
    replayFrameSampleInterval: DurationMillis
  ): IO[Boolean] =
    IO.pure(elapsedMs.value > 0L && {
      val latestElapsedMs = frames.map(_.elapsedMs.value).maxOption.getOrElse(0L)
      elapsedMs.value / replayFrameSampleInterval.value > latestElapsedMs / replayFrameSampleInterval.value
    })

  private def retainFrames(
    frames: Vector[BattleReplayFrameState],
    retainedReplayFrameCount: BattleHistoryCount
  ): IO[Vector[BattleReplayFrameState]] = IO.pure {
    val distinctFrames = frames.sortBy(_.elapsedMs.value).foldLeft(Vector.empty[BattleReplayFrameState]) {
      case (accumulator, frame) if accumulator.lastOption.exists(_.elapsedMs == frame.elapsedMs) =>
        accumulator.dropRight(1) :+ frame
      case (accumulator, frame) =>
        accumulator :+ frame
    }

    if distinctFrames.length <= retainedReplayFrameCount.value then distinctFrames
    else {
      val initialFrame = distinctFrames.headOption.filter(_.elapsedMs.value == 0L).toVector
      val retainedTail =
        distinctFrames
          .drop(initialFrame.length)
          .takeRight(retainedReplayFrameCount.value - initialFrame.length)
      initialFrame ++ retainedTail
    }
  }
}
