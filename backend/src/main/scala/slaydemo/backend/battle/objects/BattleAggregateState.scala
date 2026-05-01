package slaydemo.backend.battle.objects

import slaydemo.backend.identity.objects.{DisplayName, PlayerHandle}

final case class BattleWeaponState(
  weaponKind: WeaponKind,
  ammoInMagazine: AmmoCount,
  magazineSize: AmmoCount,
  reserveAmmo: Option[AmmoCount],
  fireCooldownMs: CooldownMillis,
  reloadRemainingMs: CooldownMillis,
  heat: Int,
  overheated: Boolean,
  overheatRemainingMs: CooldownMillis
)

final case class BattlePlayerSkillState(
  skillKind: SkillKind,
  cooldownMs: CooldownMillis,
  activeMs: DurationMillis
)

final case class BattlePlayerState(
  playerId: PlayerId,
  heroId: HeroId,
  handle: PlayerHandle,
  displayName: DisplayName,
  seat: SeatIndex,
  isBot: Boolean,
  position: BattleVector2,
  aim: BattleVector2,
  facing: FacingRadians,
  movement: BattleVector2,
  sprint: Boolean,
  primaryHeld: Boolean,
  reloadPressed: Boolean,
  lastClientCommandSeq: ClientCommandSeq,
  currentWeaponIndex: Int,
  weapons: Vector[BattleWeaponState],
  currentWeaponKind: WeaponKind,
  hp: HitPoints,
  maxHp: HitPoints,
  stamina: Stamina,
  maxStamina: Stamina,
  score: Score,
  kills: Int,
  skills: Vector[BattlePlayerSkillState],
  alive: Boolean,
  eliminatedAtMs: Option[ElapsedMillis],
  respawnMs: DurationMillis
)

final case class BattleProjectileState(
  projectileId: ProjectileId,
  ownerHeroId: HeroId,
  projectileKind: ProjectileKind,
  position: BattleVector2,
  velocity: BattleVector2,
  facing: FacingRadians,
  radius: Radius,
  damage: Damage,
  ttlMs: DurationMillis,
  maxLifetimeMs: DurationMillis,
  splashRadius: Radius
)

final case class BattleProjectileTerminalState(
  projectileId: ProjectileId,
  projectileKind: ProjectileKind,
  ownerPlayerId: PlayerId,
  ownerHeroId: HeroId,
  reason: ProjectileTerminalReason,
  start: BattleVector2,
  end: BattleVector2,
  terminalPosition: BattleVector2,
  ttlBefore: DurationMillis,
  ttlAfter: DurationMillis,
  elapsedMs: ElapsedMillis,
  targetPlayerId: Option[PlayerId],
  targetHeroId: Option[HeroId],
  hpBefore: Option[HitPoints],
  hpAfter: Option[HitPoints],
  damage: Option[Damage]
)

final case class BattleSlowFieldState(
  fieldId: SlowFieldId,
  ownerPlayerId: PlayerId,
  ownerHeroId: HeroId,
  position: BattleVector2,
  radius: Radius,
  ttlMs: DurationMillis,
  durationMs: DurationMillis
)

final case class BattlePickupState(
  pickupId: PickupId,
  pickupKind: PickupKind,
  weaponKind: Option[WeaponKind],
  position: BattleVector2,
  available: Boolean,
  respawnMs: DurationMillis
)

final case class BattleReplayHeroFrameState(
  playerId: PlayerId,
  heroId: HeroId,
  handle: PlayerHandle,
  displayName: DisplayName,
  seat: SeatIndex,
  position: BattleVector2,
  hp: HitPoints,
  maxHp: HitPoints,
  alive: Boolean,
  score: Score,
  facing: FacingRadians,
  currentWeaponKind: WeaponKind,
  eliminatedAtMs: Option[ElapsedMillis]
)

final case class BattleReplayProjectileFrameState(
  projectileId: ProjectileId,
  projectileKind: ProjectileKind,
  position: BattleVector2,
  facing: FacingRadians,
  ttlMs: DurationMillis,
  splashRadius: Radius
)

final case class BattleReplayPickupFrameState(
  pickupId: PickupId,
  pickupKind: PickupKind,
  weaponKind: Option[WeaponKind],
  position: BattleVector2,
  available: Boolean,
  respawnMs: DurationMillis
)

final case class BattleReplayFrameState(
  elapsedMs: ElapsedMillis,
  heroes: Vector[BattleReplayHeroFrameState],
  projectiles: Vector[BattleReplayProjectileFrameState],
  pickups: Vector[BattleReplayPickupFrameState]
)

final case class BattleEventParticipant(
  playerId: PlayerId,
  heroId: HeroId,
  displayName: DisplayName
)

final case class BattleEventState(
  eventId: BattleEventId,
  eventKind: BattleEventKind,
  elapsedMs: ElapsedMillis,
  message: String,
  source: BattleEventParticipant,
  target: BattleEventParticipant
)

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
