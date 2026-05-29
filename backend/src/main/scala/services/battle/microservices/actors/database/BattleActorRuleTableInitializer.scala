package services.battle.microservices.actors.database

import java.sql.Connection

import cats.effect.IO
import cats.syntax.all.*

import system.database.PostgresSupport

private[services] object BattleActorRuleTableInitializer {
  def initialize(connection: Connection): IO[Unit] =
    Vector(createTableSql, activeRuleIndexSql)
      .traverse_(sql => IO.blocking(PostgresSupport.withStatement(connection, sql)(_.executeUpdate())))
      .void

  private val createTableSql: String =
    """CREATE TABLE IF NOT EXISTS battle_actor_bot_rules (
      |  rule_id UUID PRIMARY KEY,
      |  active BOOLEAN NOT NULL,
      |  move_speed DOUBLE PRECISION NOT NULL,
      |  preferred_range DOUBLE PRECISION NOT NULL,
      |  preferred_range_advance_margin DOUBLE PRECISION NOT NULL,
      |  preferred_range_retreat_margin DOUBLE PRECISION NOT NULL,
      |  bot_fire_range DOUBLE PRECISION NOT NULL,
      |  human_fire_range DOUBLE PRECISION NOT NULL,
      |  opening_fire_delay_ms BIGINT NOT NULL,
      |  fire_pulse_interval_ms BIGINT NOT NULL,
      |  fire_pulse_window_ms BIGINT NOT NULL,
      |  movement_probe_distance DOUBLE PRECISION NOT NULL,
      |  cover_probe_distance DOUBLE PRECISION NOT NULL,
      |  pickup_seek_range DOUBLE PRECISION NOT NULL,
      |  aim_lead_distance DOUBLE PRECISION NOT NULL,
      |  aim_error_radius DOUBLE PRECISION NOT NULL,
      |  low_health_ratio DOUBLE PRECISION NOT NULL,
      |  pickup_health_ratio DOUBLE PRECISION NOT NULL,
      |  tactical_reload_ratio DOUBLE PRECISION NOT NULL,
      |  updated_at TIMESTAMPTZ NOT NULL
      |)""".stripMargin

  private val activeRuleIndexSql: String =
    "CREATE INDEX IF NOT EXISTS battle_actor_bot_rules_active_updated_idx ON battle_actor_bot_rules (active, updated_at DESC)"
}
