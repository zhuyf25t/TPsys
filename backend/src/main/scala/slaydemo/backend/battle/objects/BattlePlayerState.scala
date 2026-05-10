package slaydemo.backend.battle.objects

import slaydemo.backend.identity.objects.{DisplayName, PlayerHandle}

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
  participantKind: BattleParticipantKind,
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
  lifeState: BattlePlayerLifeState
) {
  def alive: Boolean =
    BattlePlayerLifeState.aliveFlag(lifeState)

  def eliminatedAtMs: Option[ElapsedMillis] =
    BattlePlayerLifeState.eliminatedAtMs(lifeState)

  def respawnMs: DurationMillis =
    BattlePlayerLifeState.respawnMs(lifeState)

  def isBot: Boolean =
    BattleParticipantKind.isBot(participantKind)
}
