package slaydemo.backend.battle.objects

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
