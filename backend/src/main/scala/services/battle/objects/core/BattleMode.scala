package services.battle.objects.core

enum BattleMode {
  case Default
  case Autumn
  case Winter
  case Normal
}

object BattleMode {
  def default: BattleMode =
    BattleMode.Default

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
        case BattleMode.Default => "竞技模式"
        case BattleMode.Autumn  => "秋季模式"
        case BattleMode.Winter  => "冬季模式"
        case BattleMode.Normal  => "岛屿模式"
      }
    )

  def mapLabel(value: BattleMode): BattleMapLabel =
    BattleMapLabel.fromWire(
      value match {
        case BattleMode.Default => "竞技场"
        case BattleMode.Autumn  => "秋季地图"
        case BattleMode.Winter  => "冬季地图"
        case BattleMode.Normal  => "岛屿地图"
      }
    )
}
