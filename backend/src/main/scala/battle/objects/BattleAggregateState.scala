package slaydemo.backend.battle.objects

import slaydemo.backend.shared.objects.{BattleId, UserId}

final case class BattleVector2(
  x: Double,
  y: Double
)

final case class BattleWeaponState(
  weaponKind: String,
  ammoInMagazine: Int,
  magazineSize: Int,
  reserveAmmo: Int,
  fireCooldownMs: Long,
  reloadRemainingMs: Long
)

final case class BattlePlayerState(
  playerId: UserId,
  heroId: String,
  handle: String,
  displayName: String,
  seat: Int,
  isBot: Boolean,
  spawnAnchor: BattleVector2,
  position: BattleVector2,
  velocity: BattleVector2,
  aim: BattleVector2,
  facing: Double,
  movementIntent: BattleVector2,
  primaryHeld: Boolean,
  reloadPressed: Boolean,
  lastClientCommandSeq: Long,
  currentWeaponIndex: Int,
  weapons: Vector[BattleWeaponState],
  currentWeaponKind: String,
  ammoInMagazine: Int,
  magazineSize: Int,
  reserveAmmo: Int,
  fireCooldownMs: Long,
  reloadRemainingMs: Long,
  hp: Int,
  maxHp: Int,
  stamina: Double,
  maxStamina: Double,
  score: Int,
  kills: Int,
  skills: Vector[BattlePlayerSkillState],
  alive: Boolean,
  eliminatedAtMs: Option[Long],
  respawnMs: Long
)

final case class BattlePlayerSkillState(
  kind: String,
  cooldownMs: Long,
  activeMs: Long
)

final case class BattleProjectileState(
  projectileId: String,
  ownerPlayerId: UserId,
  ownerHeroId: String,
  kind: String,
  position: BattleVector2,
  velocity: BattleVector2,
  facing: Double,
  radius: Double,
  damage: Int,
  ttlMs: Long,
  maxLifetimeMs: Long,
  splashRadius: Double
)

final case class BattleProjectileTerminalState(
  projectileId: String,
  kind: String,
  ownerPlayerId: UserId,
  ownerHeroId: String,
  reason: String,
  start: BattleVector2,
  end: BattleVector2,
  terminalPosition: BattleVector2,
  ttlBefore: Long,
  ttlAfter: Long,
  elapsedMs: Long,
  targetPlayerId: Option[UserId],
  targetHeroId: Option[String],
  hpBefore: Option[Int],
  hpAfter: Option[Int],
  damage: Option[Int]
)

final case class BattleSlowFieldState(
  fieldId: String,
  ownerPlayerId: UserId,
  ownerHeroId: String,
  position: BattleVector2,
  radius: Double,
  ttlMs: Long,
  durationMs: Long
)

final case class BattlePickupState(
  pickupId: String,
  kind: String,
  weaponKind: Option[String],
  position: BattleVector2,
  available: Boolean,
  respawnMs: Long
)

final case class BattleReplayHeroFrameState(
  playerId: UserId,
  heroId: String,
  handle: String,
  displayName: String,
  seat: Int,
  position: BattleVector2,
  hp: Int,
  maxHp: Int,
  alive: Boolean,
  score: Int,
  facing: Double,
  currentWeaponKind: String,
  eliminatedAtMs: Option[Long]
)

final case class BattleReplayProjectileFrameState(
  projectileId: String,
  kind: String,
  position: BattleVector2,
  facing: Double,
  ttlMs: Long,
  splashRadius: Double
)

final case class BattleReplayPickupFrameState(
  pickupId: String,
  kind: String,
  weaponKind: Option[String],
  position: BattleVector2,
  available: Boolean,
  respawnMs: Long
)

final case class BattleReplayFrameState(
  elapsedMs: Long,
  heroes: Vector[BattleReplayHeroFrameState],
  projectiles: Vector[BattleReplayProjectileFrameState],
  pickups: Vector[BattleReplayPickupFrameState]
)

final case class BattleEventParticipant(
  playerId: UserId,
  heroId: String,
  displayName: String
)

final case class BattleEventState(
  eventId: String,
  eventType: String,
  kind: String,
  elapsedMs: Long,
  message: String,
  source: BattleEventParticipant,
  target: BattleEventParticipant
)

final case class BattleAggregateState(
  battleId: BattleId,
  roomId: String,
  phase: String,
  serverTime: Long,
  startedAt: Long,
  durationMs: Long,
  elapsedMs: Long,
  endsAt: Long,
  worldSize: BattleVector2,
  tick: Long,
  players: Vector[BattlePlayerState],
  projectiles: Vector[BattleProjectileState],
  projectileTerminals: Vector[BattleProjectileTerminalState],
  slowFields: Vector[BattleSlowFieldState],
  pickups: Vector[BattlePickupState],
  replayFrames: Vector[BattleReplayFrameState],
  events: Vector[BattleEventState],
  winnerPlayerId: Option[UserId],
  winnerHeroId: Option[String]
)
