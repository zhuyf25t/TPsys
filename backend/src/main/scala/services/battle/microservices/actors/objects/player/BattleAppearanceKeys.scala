package services.battle.microservices.actors.objects.player

final case class BattleAvatarKey private (value: String) extends AnyVal
final case class BattleSkinKey private (value: String) extends AnyVal

object BattleAvatarKey {
  def fromWire(value: String): Option[BattleAvatarKey] =
    Option(value).map(_.trim).filter(_.nonEmpty).map(new BattleAvatarKey(_))
}

object BattleSkinKey {
  def fromWire(value: String): Option[BattleSkinKey] =
    Option(value).map(_.trim).filter(_.nonEmpty).map(new BattleSkinKey(_))
}
