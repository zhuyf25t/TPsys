package services.battle.objects.core

final case class EpochMillis(value: Long) extends AnyVal
final case class DurationMillis(value: Long) extends AnyVal
final case class ElapsedMillis(value: Long) extends AnyVal
final case class BattleTick(value: Long) extends AnyVal
final case class ClientCommandSeq(value: Long) extends AnyVal
final case class SeatIndex(value: Int) extends AnyVal
final case class SpawnPointIndex(value: Int) extends AnyVal
final case class BattleMapId(value: String) extends AnyVal
final case class BattleModeLabel private (value: String) extends AnyVal
final case class BattleMapLabel private (value: String) extends AnyVal
final case class CooldownMillis(value: Int) extends AnyVal
final case class FacingRadians(value: Double) extends AnyVal
final case class Radius(value: Double) extends AnyVal

final case class BattleVector2(x: Double, y: Double)

object BattleModeLabel {
  def fromWire(value: String): BattleModeLabel =
    new BattleModeLabel(Option(value).getOrElse(""))
}

object BattleMapLabel {
  def fromWire(value: String): BattleMapLabel =
    new BattleMapLabel(Option(value).getOrElse(""))
}
