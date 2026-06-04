package services.battle.microservices.extraction.objects.extraction

import services.battle.objects.core.{BattleVector2, DurationMillis, ElapsedMillis, HeroId, PlayerId, Radius}

final case class BattleExtractionZoneId(value: String) extends AnyVal
final case class BattleLootCacheId(value: String) extends AnyVal
final case class BattleGasStageIndex(value: Int) extends AnyVal
final case class BattleGasDamagePerSecond(value: Double) extends AnyVal
final case class BattleExtractionProgressMillis(value: Long) extends AnyVal
final case class BattleLootSearchProgressMillis(value: Long) extends AnyVal
final case class BattleLootScoreValue(value: Int) extends AnyVal

enum BattleGasPhase {
  case Waiting
  case Advancing
  case Final
}

object BattleGasPhase {
  def wireValue(value: BattleGasPhase): String =
    value match {
      case BattleGasPhase.Waiting   => "waiting"
      case BattleGasPhase.Advancing => "advancing"
      case BattleGasPhase.Final     => "final"
    }
}

final case class BattleGasStageDefinition(
  stageIndex: BattleGasStageIndex,
  startsAt: ElapsedMillis,
  duration: DurationMillis,
  fromRadius: Radius,
  toRadius: Radius,
  damagePerSecond: BattleGasDamagePerSecond
)

final case class BattleGasPlanDefinition(
  center: BattleVector2,
  stages: Vector[BattleGasStageDefinition]
)

final case class BattleGasZoneState(
  phase: BattleGasPhase,
  center: BattleVector2,
  radius: Radius,
  nextRadius: Radius,
  damagePerSecond: BattleGasDamagePerSecond,
  stageIndex: BattleGasStageIndex,
  progressMs: DurationMillis,
  startsAt: ElapsedMillis,
  endsAt: ElapsedMillis
)

final case class BattleExtractionZoneDefinition(
  zoneId: BattleExtractionZoneId,
  position: BattleVector2,
  radius: Radius,
  availableFrom: ElapsedMillis,
  channelDuration: DurationMillis
)

enum BattleExtractionInterruptReason {
  case LeftZone
  case Eliminated
}

object BattleExtractionInterruptReason {
  def wireValue(value: BattleExtractionInterruptReason): String =
    value match {
      case BattleExtractionInterruptReason.LeftZone   => "left_zone"
      case BattleExtractionInterruptReason.Eliminated => "eliminated"
    }
}

enum BattleExtractionStatus {
  case Inactive
  case Available
  case Extracting(
    playerId: PlayerId,
    heroId: HeroId,
    zoneId: BattleExtractionZoneId,
    progressMs: BattleExtractionProgressMillis
  )
  case Extracted(
    playerId: PlayerId,
    heroId: HeroId,
    zoneId: BattleExtractionZoneId,
    atElapsedMs: ElapsedMillis
  )
  case Interrupted(
    playerId: PlayerId,
    heroId: HeroId,
    zoneId: BattleExtractionZoneId,
    reason: BattleExtractionInterruptReason,
    atElapsedMs: ElapsedMillis
  )
}

object BattleExtractionStatus {
  def wireValue(value: BattleExtractionStatus): String =
    value match {
      case BattleExtractionStatus.Inactive         => "inactive"
      case BattleExtractionStatus.Available        => "available"
      case BattleExtractionStatus.Extracting(_, _, _, _) => "extracting"
      case BattleExtractionStatus.Extracted(_, _, _, _)  => "extracted"
      case BattleExtractionStatus.Interrupted(_, _, _, _, _) => "interrupted"
    }
}

final case class BattleExtractionState(
  zones: Vector[BattleExtractionZoneDefinition],
  status: BattleExtractionStatus
)

final case class BattleLootCacheDefinition(
  cacheId: BattleLootCacheId,
  position: BattleVector2,
  radius: Radius,
  searchDuration: DurationMillis,
  scoreValue: BattleLootScoreValue
)

enum BattleLootCacheStatus {
  case Available
  case Searching(
    playerId: PlayerId,
    heroId: HeroId,
    progressMs: BattleLootSearchProgressMillis
  )
  case Searched(
    playerId: PlayerId,
    heroId: HeroId,
    atElapsedMs: ElapsedMillis
  )
}

object BattleLootCacheStatus {
  def wireValue(value: BattleLootCacheStatus): String =
    value match {
      case BattleLootCacheStatus.Available          => "available"
      case BattleLootCacheStatus.Searching(_, _, _) => "searching"
      case BattleLootCacheStatus.Searched(_, _, _)  => "searched"
    }
}

final case class BattleLootCacheState(
  cacheId: BattleLootCacheId,
  position: BattleVector2,
  radius: Radius,
  searchDuration: DurationMillis,
  scoreValue: BattleLootScoreValue,
  status: BattleLootCacheStatus
)
