package services.battle.objects.skill

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

final case class BattleCommandSkillIntents(values: Vector[SkillKind]) {
  /**
   * 中文名：是否存在技能意图（nonEmpty）。
   * 游戏视线：一次玩家指令可能只移动/射击，也可能同时请求释放技能；这里用于判断本帧是否需要进入技能规则处理。
   * 建模原因：技能意图被收拢为 `Vector[SkillKind]`，调用方不需要再读取多个裸 Boolean 来推断玩家想释放哪些技能。
   */
  def nonEmpty: Boolean =
    values.nonEmpty
}

object BattleCommandSkillIntents {
  val empty: BattleCommandSkillIntents =
    BattleCommandSkillIntents(Vector.empty)

  /**
   * 中文名：从旧版技能布尔标记生成技能意图（fromLegacyFlags）。
   * 游戏视线：旧指令协议把冲刺、闪现、冰冻分别放在 `castDash/castBlink/castFreeze` 三个字段里，这里把它们合并成有序技能列表。
   * 建模原因：保留边界兼容层，但领域内部统一使用 `SkillKind` 枚举集合，避免后续新增技能时继续扩散多个布尔字段。
   */
  def fromLegacyFlags(
    castDash: Boolean,
    castBlink: Boolean,
    castFreeze: Boolean
  ): BattleCommandSkillIntents =
    BattleCommandSkillIntents(
      Vector(
        Option.when(castBlink)(SkillKind.Blink),
        Option.when(castDash)(SkillKind.Dash),
        Option.when(castFreeze)(SkillKind.Freeze)
      ).flatten
    )
}
