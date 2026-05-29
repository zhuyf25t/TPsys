package services.battle.microservices.combat.database

import java.sql.Connection

import cats.effect.IO
import cats.syntax.all.*

import system.database.PostgresSupport

private[services] object BattleCombatRuleTableInitializer {
  def initialize(connection: Connection): IO[Unit] =
    Vector(createTableSql, uniqueWeaponIndexSql)
      .traverse_(sql => IO.blocking(PostgresSupport.withStatement(connection, sql)(_.executeUpdate())))
      .void

  private val createTableSql: String =
    """CREATE TABLE IF NOT EXISTS battle_combat_weapon_rules (
      |  rule_id UUID PRIMARY KEY,
      |  weapon_kind TEXT NOT NULL,
      |  magazine_size INTEGER NOT NULL,
      |  reserve_ammo INTEGER NULL,
      |  pickup_ammo INTEGER NOT NULL,
      |  reload_ms INTEGER NOT NULL,
      |  firing_resource TEXT NOT NULL,
      |  cooldown_ms INTEGER NOT NULL,
      |  projectile_kind TEXT NOT NULL,
      |  projectile_speed DOUBLE PRECISION NOT NULL,
      |  damage INTEGER NOT NULL,
      |  projectile_radius DOUBLE PRECISION NOT NULL,
      |  projectile_lifetime_ms BIGINT NOT NULL,
      |  splash_radius DOUBLE PRECISION NOT NULL,
      |  projectile_count INTEGER NOT NULL,
      |  spread_radians DOUBLE PRECISION NOT NULL,
      |  recoil_strength DOUBLE PRECISION NOT NULL,
      |  max_heat INTEGER NULL,
      |  heat_per_shot INTEGER NULL,
      |  cool_rate_per_second INTEGER NULL,
      |  overheat_lock_ms INTEGER NULL,
      |  updated_at TIMESTAMPTZ NOT NULL
      |)""".stripMargin

  private val uniqueWeaponIndexSql: String =
    "CREATE UNIQUE INDEX IF NOT EXISTS battle_combat_weapon_rules_weapon_kind_idx ON battle_combat_weapon_rules (weapon_kind)"
}
