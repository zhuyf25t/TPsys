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
final case class RatingDelta(value: Int) extends AnyVal
final case class BattleResultLabel private (value: String) extends AnyVal
final case class BattleModeLabel private (value: String) extends AnyVal
final case class BattleMapLabel private (value: String) extends AnyVal
final case class BattleHighlightLine private (value: String) extends AnyVal
final case class BattlePlayersLine private (value: String) extends AnyVal
final case class BattleTimelineHint private (value: String) extends AnyVal
final case class BattlePlacement private (value: Int) extends AnyVal
final case class Score(value: Int) extends AnyVal
final case class HitPoints(value: Int) extends AnyVal
final case class Stamina(value: Double) extends AnyVal
final case class AmmoCount(value: Int) extends AnyVal
final case class CooldownMillis(value: Int) extends AnyVal
final case class FacingRadians(value: Double) extends AnyVal
final case class Radius(value: Double) extends AnyVal
final case class Damage(value: Int) extends AnyVal

final case class BattleVector2(x: Double, y: Double)

object BattlePlacement {
  def fromWire(value: Int): Option[BattlePlacement] =
    Option.when(value > 0)(new BattlePlacement(value))

  def unsafe(value: Int): BattlePlacement =
    fromWire(value).getOrElse {
      throw new IllegalArgumentException(s"Battle placement must be positive: $value")
    }
}

object BattleResultLabel {
  def fromWire(value: String): BattleResultLabel =
    new BattleResultLabel(Option(value).getOrElse(""))
}

object BattleModeLabel {
  def fromWire(value: String): BattleModeLabel =
    new BattleModeLabel(Option(value).getOrElse(""))
}

object BattleMapLabel {
  def fromWire(value: String): BattleMapLabel =
    new BattleMapLabel(Option(value).getOrElse(""))
}

object BattleHighlightLine {
  def fromWire(value: String): BattleHighlightLine =
    new BattleHighlightLine(Option(value).getOrElse(""))
}

object BattlePlayersLine {
  def fromWire(value: String): BattlePlayersLine =
    new BattlePlayersLine(Option(value).getOrElse(""))
}

object BattleTimelineHint {
  def fromWire(value: String): BattleTimelineHint =
    new BattleTimelineHint(Option(value).getOrElse(""))
}
