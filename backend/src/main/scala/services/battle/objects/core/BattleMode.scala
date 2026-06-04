package services.battle.objects.core

enum BattleMode {
  case Default
  case Autumn
  case Winter
  case Normal
}

object BattleMode {
  def default: BattleMode =
    BattleMode.Winter

  def wireValue(value: BattleMode): String =
    value match {
      case BattleMode.Default => "default"
      case BattleMode.Autumn  => "autumn"
      case BattleMode.Winter  => "winter"
      case BattleMode.Normal  => "normal"
    }

  def fromWire(value: String): Option[BattleMode] =
    Option(value).map(_.trim.toLowerCase).flatMap {
      case "default" | "default-mode" | "default_mode" =>
        Some(BattleMode.Default)
      case "autumn" | "fall" | "fall-hunt" | "fall_hunt" =>
        Some(BattleMode.Autumn)
      case "winter" | "winter-hunt" | "winter_hunt" =>
        Some(BattleMode.Winter)
      case "normal" | "normal-hunt" | "normal_hunt" =>
        Some(BattleMode.Normal)
      case _ =>
        None
    }

  def mapId(value: BattleMode): BattleMapId =
    value match {
      case BattleMode.Default => BattleMapId("default-industrial-arena")
      case BattleMode.Autumn  => BattleMapId("fall-hunt-v1")
      case BattleMode.Winter  => BattleMapId("winter-hunt-v1")
      case BattleMode.Normal  => BattleMapId("normal-hunt-v1")
    }

  def modeLabel(value: BattleMode): BattleModeLabel =
    BattleModeLabel.fromWire(
      value match {
        case BattleMode.Default => "\u7ade\u6280\u6a21\u5f0f"
        case BattleMode.Autumn  => "\u79cb\u5b63\u6a21\u5f0f"
        case BattleMode.Winter  => "\u4e27\u5c38\u6a21\u5f0f"
        case BattleMode.Normal  => "\u68ee\u6797\u6a21\u5f0f"
      }
    )

  def mapLabel(value: BattleMode): BattleMapLabel =
    BattleMapLabel.fromWire(
      value match {
        case BattleMode.Default => "\u7ade\u6280\u5730\u56fe"
        case BattleMode.Autumn  => "\u79cb\u5b63\u5730\u56fe"
        case BattleMode.Winter  => "Suroi \u51ac\u5b63\u5730\u56fe"
        case BattleMode.Normal  => "\u68ee\u6797\u5730\u56fe"
      }
    )
}
