package services.battle.objects.core

import services.battle.microservices.runtime.objects.event.BattleEventState
import services.battle.microservices.abilities.objects.pickup.BattlePickupState
import services.battle.microservices.actors.objects.player.BattlePlayerState
import services.battle.microservices.combat.objects.projectile.{BattleProjectileState, BattleProjectileTerminalState}
import services.battle.microservices.extraction.objects.extraction.{
  BattleExtractionState,
  BattleGasZoneState,
  BattleLootCacheState
}
import services.battle.microservices.projections.objects.replay.BattleReplayFrameState
import services.battle.microservices.abilities.objects.skill.BattleSlowFieldState

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
  gasZone: Option[BattleGasZoneState],
  extraction: Option[BattleExtractionState],
  lootCaches: Vector[BattleLootCacheState],
  replayFrames: Vector[BattleReplayFrameState],
  events: Vector[BattleEventState],
  winnerPlayerId: Option[PlayerId],
  winnerHeroId: Option[HeroId]
)
