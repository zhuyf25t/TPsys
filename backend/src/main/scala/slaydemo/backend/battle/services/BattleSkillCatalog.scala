package slaydemo.backend.battle.services

import slaydemo.backend.battle.objects.{CooldownMillis, DurationMillis, Radius}

private[services] object BattleSkillCatalog {
  final case class SkillDistance(value: Double) extends AnyVal
  final case class SkillRuntime(cooldownMs: CooldownMillis, activeMs: DurationMillis)
  final case class BlinkConfig(range: SkillDistance, runtime: SkillRuntime)
  final case class DashConfig(distance: SkillDistance, runtime: SkillRuntime)
  final case class FreezeConfig(radius: Radius, castRange: SkillDistance, runtime: SkillRuntime)

  val Blink: BlinkConfig =
    BlinkConfig(
      range = SkillDistance(250.0),
      runtime = SkillRuntime(CooldownMillis(2200), DurationMillis(240L))
    )

  val Dash: DashConfig =
    DashConfig(
      distance = SkillDistance(180.0),
      runtime = SkillRuntime(CooldownMillis(5000), DurationMillis(180L))
    )

  val Freeze: FreezeConfig =
    FreezeConfig(
      radius = Radius(150.0),
      castRange = SkillDistance(520.0),
      runtime = SkillRuntime(CooldownMillis(12000), DurationMillis(10000L))
    )
}
