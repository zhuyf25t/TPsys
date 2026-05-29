package services.battle.microservices.abilities.database

import java.sql.Connection

import cats.effect.IO
import cats.syntax.all.*

import system.database.PostgresSupport

private[services] object BattleAbilityRuleTableInitializer {
  def initialize(connection: Connection): IO[Unit] =
    Vector(createSkillTableSql, uniqueSkillIndexSql, createPickupTableSql, activePickupIndexSql)
      .traverse_(sql => IO.blocking(PostgresSupport.withStatement(connection, sql)(_.executeUpdate())))
      .void

  private val createSkillTableSql: String =
    """CREATE TABLE IF NOT EXISTS battle_ability_skill_rules (
      |  rule_id UUID PRIMARY KEY,
      |  skill_kind TEXT NOT NULL,
      |  range DOUBLE PRECISION NULL,
      |  distance DOUBLE PRECISION NULL,
      |  radius DOUBLE PRECISION NULL,
      |  cast_range DOUBLE PRECISION NULL,
      |  cooldown_ms INTEGER NOT NULL,
      |  active_ms BIGINT NOT NULL,
      |  updated_at TIMESTAMPTZ NOT NULL
      |)""".stripMargin

  private val uniqueSkillIndexSql: String =
    "CREATE UNIQUE INDEX IF NOT EXISTS battle_ability_skill_rules_skill_kind_idx ON battle_ability_skill_rules (skill_kind)"

  private val createPickupTableSql: String =
    """CREATE TABLE IF NOT EXISTS battle_ability_pickup_rules (
      |  rule_id UUID PRIMARY KEY,
      |  active BOOLEAN NOT NULL,
      |  contact_radius DOUBLE PRECISION NOT NULL,
      |  respawn_duration_ms BIGINT NOT NULL,
      |  medkit_heal INTEGER NOT NULL,
      |  updated_at TIMESTAMPTZ NOT NULL
      |)""".stripMargin

  private val activePickupIndexSql: String =
    "CREATE INDEX IF NOT EXISTS battle_ability_pickup_rules_active_updated_idx ON battle_ability_pickup_rules (active, updated_at DESC)"
}
