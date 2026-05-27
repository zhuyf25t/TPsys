package services.battle.objects.core

import services.battle.objects.{BattleArtifactStatus, BattlePhase}
import services.battle.objects.event.BattleEventState
import services.battle.objects.pickup.BattlePickupState
import services.battle.objects.player.BattlePlayerState
import services.battle.objects.projectile.{BattleProjectileState, BattleProjectileTerminalState}
import services.battle.objects.replay.BattleReplayFrameState
import services.battle.objects.skill.BattleSlowFieldState

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
