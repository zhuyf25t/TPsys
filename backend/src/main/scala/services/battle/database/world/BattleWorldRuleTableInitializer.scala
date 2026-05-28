package services.battle.database.world

import java.sql.Connection

import cats.effect.IO
import cats.syntax.all.*

import system.database.PostgresSupport

private[services] object BattleWorldRuleTableInitializer {
  def initialize(connection: Connection): IO[Unit] =
    Vector(createWorldRulesTableSql, activeWorldRulesIndexSql, createMovementRulesTableSql, activeMovementRulesIndexSql, createMapRulesTableSql, activeMapRulesIndexSql)
      .traverse_(sql => IO.blocking(PostgresSupport.withStatement(connection, sql)(_.executeUpdate())))
      .void

  private val createWorldRulesTableSql: String =
    """CREATE TABLE IF NOT EXISTS battle_world_rules (
      |  rule_id UUID PRIMARY KEY,
      |  active BOOLEAN NOT NULL,
      |  floor_tile_size INTEGER NOT NULL,
      |  motion_step_size DOUBLE PRECISION NOT NULL,
      |  player_collision_radius DOUBLE PRECISION NOT NULL,
      |  projectile_birth_clearance DOUBLE PRECISION NOT NULL,
      |  projectile_shooter_advantage_radius DOUBLE PRECISION NOT NULL,
      |  updated_at TIMESTAMPTZ NOT NULL
      |)""".stripMargin

  private val activeWorldRulesIndexSql: String =
    "CREATE INDEX IF NOT EXISTS battle_world_rules_active_updated_idx ON battle_world_rules (active, updated_at DESC)"

  private val createMovementRulesTableSql: String =
    """CREATE TABLE IF NOT EXISTS battle_world_movement_rules (
      |  rule_id UUID PRIMARY KEY,
      |  active BOOLEAN NOT NULL,
      |  walk_speed DOUBLE PRECISION NOT NULL,
      |  sprint_speed DOUBLE PRECISION NOT NULL,
      |  stamina_drain_per_second DOUBLE PRECISION NOT NULL,
      |  stamina_recover_per_second DOUBLE PRECISION NOT NULL,
      |  slow_field_movement_factor DOUBLE PRECISION NOT NULL,
      |  slow_field_projectile_factor DOUBLE PRECISION NOT NULL,
      |  updated_at TIMESTAMPTZ NOT NULL
      |)""".stripMargin

  private val activeMovementRulesIndexSql: String =
    "CREATE INDEX IF NOT EXISTS battle_world_movement_rules_active_updated_idx ON battle_world_movement_rules (active, updated_at DESC)"

  private val createMapRulesTableSql: String =
    """CREATE TABLE IF NOT EXISTS battle_world_map_rules (
      |  map_id TEXT PRIMARY KEY,
      |  active BOOLEAN NOT NULL,
      |  theme_id TEXT NOT NULL,
      |  world_size_x DOUBLE PRECISION NOT NULL,
      |  world_size_y DOUBLE PRECISION NOT NULL,
      |  map_spec_json TEXT NOT NULL,
      |  updated_at TIMESTAMPTZ NOT NULL
      |)""".stripMargin

  private val activeMapRulesIndexSql: String =
    "CREATE INDEX IF NOT EXISTS battle_world_map_rules_active_updated_idx ON battle_world_map_rules (active, updated_at DESC)"
}
