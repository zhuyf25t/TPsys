package slaydemo.backend.battle.objects

enum MatchmakingRoomPhase {
  case Waiting
  case Active
  case Finished
  case Unknown
}

object MatchmakingRoomPhase {
  def wireValue(value: MatchmakingRoomPhase): String =
    value match {
      case MatchmakingRoomPhase.Waiting  => "waiting"
      case MatchmakingRoomPhase.Active   => "active"
      case MatchmakingRoomPhase.Finished => "finished"
      case MatchmakingRoomPhase.Unknown  => "unknown"
    }
}

enum BattlePhase {
  case Waiting
  case Active
  case Finished
}

object BattlePhase {
  def wireValue(value: BattlePhase): String =
    value match {
      case BattlePhase.Waiting  => "waiting"
      case BattlePhase.Active   => "active"
      case BattlePhase.Finished => "finished"
    }
}

enum BattleArtifactStatus {
  case Pending
  case ResultOnlyReady
  case ReplayOnlyReady
  case Ready
}

object BattleArtifactStatus {
  def isResultReady(value: BattleArtifactStatus): Boolean =
    value match {
      case BattleArtifactStatus.Pending         => false
      case BattleArtifactStatus.ResultOnlyReady => true
      case BattleArtifactStatus.ReplayOnlyReady => false
      case BattleArtifactStatus.Ready           => true
    }

  def isReplayReady(value: BattleArtifactStatus): Boolean =
    value match {
      case BattleArtifactStatus.Pending         => false
      case BattleArtifactStatus.ResultOnlyReady => false
      case BattleArtifactStatus.ReplayOnlyReady => true
      case BattleArtifactStatus.Ready           => true
    }

  def fromReadiness(resultReady: Boolean, replayReady: Boolean): BattleArtifactStatus =
    (resultReady, replayReady) match {
      case (false, false) => BattleArtifactStatus.Pending
      case (true, false)  => BattleArtifactStatus.ResultOnlyReady
      case (false, true)  => BattleArtifactStatus.ReplayOnlyReady
      case (true, true)   => BattleArtifactStatus.Ready
    }

  def merge(current: BattleArtifactStatus, update: BattleArtifactStatus): BattleArtifactStatus =
    fromReadiness(
      isResultReady(current) || isResultReady(update),
      isReplayReady(current) || isReplayReady(update)
    )
}

enum WeaponKind {
  case Pistol
  case RocketLauncher
  case Gatling
  case Shotgun
}

object WeaponKind {
  def wireValue(value: WeaponKind): String =
    value match {
      case WeaponKind.Pistol         => "Pistol"
      case WeaponKind.RocketLauncher => "RocketLauncher"
      case WeaponKind.Gatling        => "Gatling"
      case WeaponKind.Shotgun        => "Shotgun"
    }
}

enum ProjectileKind {
  case PistolBullet
  case Rocket
  case GatlingBullet
  case ShotgunPellet
}

object ProjectileKind {
  def wireValue(value: ProjectileKind): String =
    value match {
      case ProjectileKind.PistolBullet  => "pistol-bullet"
      case ProjectileKind.Rocket        => "rocket"
      case ProjectileKind.GatlingBullet => "gatling-bullet"
      case ProjectileKind.ShotgunPellet => "shotgun-pellet"
    }
}

enum SkillKind {
  case Blink
  case Dash
  case Freeze
}

object SkillKind {
  def wireValue(value: SkillKind): String =
    value match {
      case SkillKind.Blink  => "Blink"
      case SkillKind.Dash   => "Dash"
      case SkillKind.Freeze => "Freeze"
    }
}

enum BattleCommandStatus {
  case Applied
  case Ignored
}

object BattleCommandStatus {
  def wireValue(value: BattleCommandStatus): String =
    value match {
      case BattleCommandStatus.Applied => "applied"
      case BattleCommandStatus.Ignored => "ignored"
    }
}

enum BattleCommandReason {
  case BattleFinished
  case BattleInactive
  case PlayerDead
}

object BattleCommandReason {
  def wireValue(value: BattleCommandReason): String =
    value match {
      case BattleCommandReason.BattleFinished => "battle_finished"
      case BattleCommandReason.BattleInactive => "battle_inactive"
      case BattleCommandReason.PlayerDead     => "player_dead"
    }
}

enum SkillOutcomeStatus {
  case Applied
  case Noop
}

object SkillOutcomeStatus {
  def wireValue(value: SkillOutcomeStatus): String =
    value match {
      case SkillOutcomeStatus.Applied => "applied"
      case SkillOutcomeStatus.Noop    => "noop"
    }
}

enum SkillOutcomeReason {
  case SkillNotOwned
  case Cooldown
  case MissingTarget
  case OutOfRange
  case InvalidTarget
  case NoDirection
  case Blocked
}

object SkillOutcomeReason {
  def wireValue(value: SkillOutcomeReason): String =
    value match {
      case SkillOutcomeReason.SkillNotOwned  => "skill_not_owned"
      case SkillOutcomeReason.Cooldown       => "cooldown"
      case SkillOutcomeReason.MissingTarget  => "missing_target"
      case SkillOutcomeReason.OutOfRange     => "out_of_range"
      case SkillOutcomeReason.InvalidTarget  => "invalid_target"
      case SkillOutcomeReason.NoDirection    => "no_direction"
      case SkillOutcomeReason.Blocked        => "blocked"
    }
}

enum PickupKind {
  case Medkit
  case Weapon
}

object PickupKind {
  def wireValue(value: PickupKind): String =
    value match {
      case PickupKind.Medkit => "Medkit"
      case PickupKind.Weapon => "Weapon"
    }
}

enum ProjectileTerminalReason {
  case Hit
  case Expired
  case Blocked
  case OutOfBounds
}

object ProjectileTerminalReason {
  def wireValue(value: ProjectileTerminalReason): String =
    value match {
      case ProjectileTerminalReason.Hit         => "hit"
      case ProjectileTerminalReason.Expired     => "ttl"
      case ProjectileTerminalReason.Blocked     => "obstacle"
      case ProjectileTerminalReason.OutOfBounds => "world"
    }
}

enum BattleEventKind {
  case Kill
  case Heal
  case Pickup
  case Respawn
}

object BattleEventKind {
  def wireValue(value: BattleEventKind): String =
    value match {
      case BattleEventKind.Kill    => "kill"
      case BattleEventKind.Heal    => "heal"
      case BattleEventKind.Pickup  => "pickup"
      case BattleEventKind.Respawn => "respawn"
    }
}
