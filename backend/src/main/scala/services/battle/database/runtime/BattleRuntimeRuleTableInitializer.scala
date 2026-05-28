package services.battle.database.runtime

import java.sql.Connection

import cats.effect.IO
import cats.syntax.all.*

import system.database.PostgresSupport

private[services] object BattleRuntimeRuleTableInitializer {
  def initialize(connection: Connection): IO[Unit] =
    Vector(createRuntimeRulesTableSql, activeRuntimeRulesIndexSql, createHistoryRulesTableSql, activeHistoryRulesIndexSql, createSessionPlayerRulesTableSql, activeSessionPlayerRulesIndexSql)
      .traverse_(sql => IO.blocking(PostgresSupport.withStatement(connection, sql)(_.executeUpdate())))
      .void

  private val createRuntimeRulesTableSql: String =
    """CREATE TABLE IF NOT EXISTS battle_runtime_rules (
      |  rule_id UUID PRIMARY KEY,
      |  active BOOLEAN NOT NULL,
      |  default_battle_duration_ms BIGINT NOT NULL,
      |  tick_step_ms BIGINT NOT NULL,
      |  updated_at TIMESTAMPTZ NOT NULL
      |)""".stripMargin

  private val activeRuntimeRulesIndexSql: String =
    "CREATE INDEX IF NOT EXISTS battle_runtime_rules_active_updated_idx ON battle_runtime_rules (active, updated_at DESC)"

  private val createHistoryRulesTableSql: String =
    """CREATE TABLE IF NOT EXISTS battle_history_rules (
      |  rule_id UUID PRIMARY KEY,
      |  active BOOLEAN NOT NULL,
      |  retained_projectile_terminal_count INTEGER NOT NULL,
      |  retained_battle_event_count INTEGER NOT NULL,
      |  replay_frame_sample_interval_ms BIGINT NOT NULL,
      |  retained_replay_frame_count INTEGER NOT NULL,
      |  updated_at TIMESTAMPTZ NOT NULL
      |)""".stripMargin

  private val activeHistoryRulesIndexSql: String =
    "CREATE INDEX IF NOT EXISTS battle_history_rules_active_updated_idx ON battle_history_rules (active, updated_at DESC)"

  private val createSessionPlayerRulesTableSql: String =
    """CREATE TABLE IF NOT EXISTS battle_session_player_rules (
      |  rule_id UUID PRIMARY KEY,
      |  active BOOLEAN NOT NULL,
      |  initial_hp INTEGER NOT NULL,
      |  max_hp INTEGER NOT NULL,
      |  initial_stamina DOUBLE PRECISION NOT NULL,
      |  max_stamina DOUBLE PRECISION NOT NULL,
      |  default_weapon_kind TEXT NOT NULL,
      |  updated_at TIMESTAMPTZ NOT NULL
      |)""".stripMargin

  private val activeSessionPlayerRulesIndexSql: String =
    "CREATE INDEX IF NOT EXISTS battle_session_player_rules_active_updated_idx ON battle_session_player_rules (active, updated_at DESC)"
}
