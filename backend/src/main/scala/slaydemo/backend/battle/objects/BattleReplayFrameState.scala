package slaydemo.backend.battle.objects

import slaydemo.backend.identity.objects.{DisplayName, PlayerHandle}

final case class BattleReplayHeroFrameState(
  playerId: PlayerId,
  heroId: HeroId,
  handle: PlayerHandle,
  displayName: DisplayName,
  seat: SeatIndex,
  position: BattleVector2,
  hp: HitPoints,
  maxHp: HitPoints,
  lifeState: BattleReplayHeroLifeState,
  score: Score,
  facing: FacingRadians,
  currentWeaponKind: WeaponKind
) {
  def alive: Boolean =
    BattleReplayHeroLifeState.aliveFlag(lifeState)

  def eliminatedAtMs: Option[ElapsedMillis] =
    BattleReplayHeroLifeState.eliminatedAtMs(lifeState)
}

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
  pickupAvailability: BattlePickupAvailability
) {
  def available: Boolean =
    BattlePickupAvailability.availableFlag(pickupAvailability)

  def respawnMs: DurationMillis =
    BattlePickupAvailability.respawnMs(pickupAvailability)
}

final case class BattleReplayFrameState(
  elapsedMs: ElapsedMillis,
  heroes: Vector[BattleReplayHeroFrameState],
  projectiles: Vector[BattleReplayProjectileFrameState],
  pickups: Vector[BattleReplayPickupFrameState]
)
