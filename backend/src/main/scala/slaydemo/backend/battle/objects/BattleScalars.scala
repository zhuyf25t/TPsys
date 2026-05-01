package slaydemo.backend.battle.objects

final case class EpochMillis(value: Long) extends AnyVal
final case class DurationMillis(value: Long) extends AnyVal
final case class ElapsedMillis(value: Long) extends AnyVal
final case class BattleTick(value: Long) extends AnyVal
final case class ClientCommandSeq(value: Long) extends AnyVal
final case class SeatIndex(value: Int) extends AnyVal
final case class SpawnPointIndex(value: Int) extends AnyVal
final case class BattleCapacity(value: Int) extends AnyVal
final case class Rating(value: Int) extends AnyVal
final case class Score(value: Int) extends AnyVal
final case class HitPoints(value: Int) extends AnyVal
final case class Stamina(value: Double) extends AnyVal
final case class AmmoCount(value: Int) extends AnyVal
final case class CooldownMillis(value: Int) extends AnyVal
final case class FacingRadians(value: Double) extends AnyVal
final case class Radius(value: Double) extends AnyVal
final case class Damage(value: Int) extends AnyVal

final case class BattleVector2(x: Double, y: Double)
