package slaydemo.backend.battle.objects.core

import slaydemo.backend.battle.objects.*
import slaydemo.backend.battle.objects.core.*
import slaydemo.backend.battle.objects.event.*
import slaydemo.backend.battle.objects.pickup.*
import slaydemo.backend.battle.objects.player.*
import slaydemo.backend.battle.objects.projectile.*
import slaydemo.backend.battle.objects.queue.*
import slaydemo.backend.battle.objects.replay.*
import slaydemo.backend.battle.objects.result.*
import slaydemo.backend.battle.objects.skill.*
import slaydemo.backend.battle.objects.weapon.*

final case class BattleAggregateState(
  battleId: BattleId,
  roomId: RoomId,
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
