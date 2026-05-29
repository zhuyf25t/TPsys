package services.battle.microservices.results.objects.result

final case class RatingDelta(value: Int) extends AnyVal
final case class BattleResultLabel private (value: String) extends AnyVal
final case class BattleHighlightLine private (value: String) extends AnyVal
final case class BattlePlayersLine private (value: String) extends AnyVal
final case class BattleTimelineHint private (value: String) extends AnyVal
final case class BattlePlacement private (value: Int) extends AnyVal

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
