package services.battle.microservices.abilities.objects.abilities

import services.battle.microservices.abilities.objects.skill.SkillKind
import services.battle.objects.core.{CooldownMillis, DurationMillis, Radius}

private[services] final case class SkillDistance(value: Double) extends AnyVal

private[services] final case class BattleSkillRuntime(
  cooldownMs: CooldownMillis,
  activeMs: DurationMillis
)

private[services] final case class BlinkConfig(
  range: SkillDistance,
  runtime: BattleSkillRuntime
)

private[services] final case class DashConfig(
  distance: SkillDistance,
  runtime: BattleSkillRuntime
)

private[services] final case class FreezeConfig(
  radius: Radius,
  castRange: SkillDistance,
  runtime: BattleSkillRuntime
)

private[services] final case class BattleSkillRuleDefinition(
  skillKind: SkillKind,
  range: Option[SkillDistance],
  distance: Option[SkillDistance],
  radius: Option[Radius],
  castRange: Option[SkillDistance],
  runtime: BattleSkillRuntime
)

private[services] final case class BattleSkillRuleSet(
  blink: BlinkConfig,
  dash: DashConfig,
  freeze: FreezeConfig
)

private[services] final case class BattlePickupRuleConfig(
  contactRadius: Radius,
  respawnDuration: DurationMillis,
  medkitHeal: services.battle.microservices.actors.objects.player.HitPoints
)
