package services.battle.objects.core

import services.battle.objects.*
import services.battle.objects.core.*
import services.battle.objects.event.*
import services.battle.objects.pickup.*
import services.battle.objects.player.*
import services.battle.objects.projectile.*
import services.battle.objects.queue.*
import services.battle.objects.replay.*
import services.battle.objects.result.*
import services.battle.objects.skill.*
import services.battle.objects.weapon.*

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
final case class BattleMapId(value: String) extends AnyVal
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
  /**
   * 中文名：从协议（fromWire）。
   * 游戏视线：BattlePlacement 是正整数名次值对象，用来避免把 0 或负数当成合法战斗排名。
   */
  def fromWire(value: Int): Option[BattlePlacement] =
    Option.when(value > 0)(new BattlePlacement(value))

  /**
   * 中文名：不安全构造（unsafe）。
   * 游戏视线：仅用于调用方已经确认名次合法的内部场景；如果 value 不是正整数会立即抛错。
   */
  def unsafe(value: Int): BattlePlacement =
    fromWire(value).getOrElse {
      throw new IllegalArgumentException(s"Battle placement must be positive: $value")
    }
}

object BattleResultLabel {
  /**
   * 中文名：从协议（fromWire）。
   * 游戏视线：BattleResultLabel 是战报标题值对象，用来承载“胜利/失败/对战结束”等前端展示文本；null 会收敛为空字符串。
   */
  def fromWire(value: String): BattleResultLabel =
    new BattleResultLabel(Option(value).getOrElse(""))
}

object BattleModeLabel {
  /**
   * 中文名：从协议（fromWire）。
   * 游戏视线：BattleModeLabel 是战报模式名值对象，例如竞技模式或狩猎模式；这里把外部字符串包成明确领域类型。
   */
  def fromWire(value: String): BattleModeLabel =
    new BattleModeLabel(Option(value).getOrElse(""))
}

object BattleMapLabel {
  /**
   * 中文名：从协议（fromWire）。
   * 游戏视线：BattleMapLabel 是战报地图名值对象，例如权威竞技场、岛屿或冬季地图；避免地图展示名散落成普通 String。
   */
  def fromWire(value: String): BattleMapLabel =
    new BattleMapLabel(Option(value).getOrElse(""))
}

object BattleHighlightLine {
  /**
   * 中文名：从协议（fromWire）。
   * 游戏视线：BattleHighlightLine 是战报高亮文案值对象，用来展示本局最重要的一句话摘要。
   */
  def fromWire(value: String): BattleHighlightLine =
    new BattleHighlightLine(Option(value).getOrElse(""))
}

object BattlePlayersLine {
  /**
   * 中文名：从协议（fromWire）。
   * 游戏视线：BattlePlayersLine 是战报参赛者展示行值对象，用来承载本局玩家列表摘要。
   */
  def fromWire(value: String): BattlePlayersLine =
    new BattlePlayersLine(Option(value).getOrElse(""))
}

object BattleTimelineHint {
  /**
   * 中文名：从协议（fromWire）。
   * 游戏视线：BattleTimelineHint 是战报时间线提示值对象，用来描述本局结束、回放或关键节点的展示提示。
   */
  def fromWire(value: String): BattleTimelineHint =
    new BattleTimelineHint(Option(value).getOrElse(""))
}
