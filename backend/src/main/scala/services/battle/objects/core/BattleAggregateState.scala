package services.battle.objects.core

import services.battle.objects.*
import services.battle.objects.core.*
import services.battle.objects.event.*
import services.battle.objects.pickup.*
import services.battle.objects.player.*
import services.battle.objects.projectile.*
import services.battle.objects.queue.*
import services.battle.objects.replay.*
import services.battle.objects.result.*
import services.battle.objects.skill.*
import services.battle.objects.weapon.*

final case class BattleAggregateState(
  battleId: BattleId,
  roomId: RoomId,
  mapId: BattleMapId,
  phase: BattlePhase,
  serverTime: EpochMillis,
  startedAt: EpochMillis,
  durationMs: DurationMillis,
  elapsedMs: ElapsedMillis,
  endsAt: EpochMillis,
  worldSize: BattleVector2,
  tick: BattleTick,
  artifactStatus: BattleArtifactStatus,
  players: Vector[BattlePlayerState],
  projectiles: Vector[BattleProjectileState],
  projectileTerminals: Vector[BattleProjectileTerminalState],
  slowFields: Vector[BattleSlowFieldState],
  pickups: Vector[BattlePickupState],
  replayFrames: Vector[BattleReplayFrameState],
  events: Vector[BattleEventState],
  winnerPlayerId: Option[PlayerId],
  winnerHeroId: Option[HeroId]
)
