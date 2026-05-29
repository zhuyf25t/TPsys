package services.battle.microservices.runtime.database

import java.sql.{Connection, PreparedStatement, ResultSet, Timestamp}
import java.time.Instant
import java.util.UUID

import cats.effect.IO

import services.battle.microservices.runtime.objects.runtime.*
import services.battle.microservices.combat.objects.weapon.WeaponKind
import services.battle.objects.core.DurationMillis
import services.battle.microservices.actors.objects.player.{HitPoints, Stamina}
import system.database.PostgresSupport

private[services] object BattleRuntimeRuleTable {
  private val upsertRuntimeSql: String =
    """INSERT INTO battle_runtime_rules (
      |  rule_id, active, default_battle_duration_ms, tick_step_ms, updated_at
      |) VALUES (?, ?, ?, ?, ?)
      |ON CONFLICT (rule_id) DO UPDATE SET
      |  active = EXCLUDED.active,
      |  default_battle_duration_ms = EXCLUDED.default_battle_duration_ms,
      |  tick_step_ms = EXCLUDED.tick_step_ms,
      |  updated_at = EXCLUDED.updated_at""".stripMargin

  private val upsertHistorySql: String =
    """INSERT INTO battle_history_rules (
      |  rule_id, active, retained_projectile_terminal_count,
      |  retained_battle_event_count, replay_frame_sample_interval_ms,
      |  retained_replay_frame_count, updated_at
      |) VALUES (?, ?, ?, ?, ?, ?, ?)
      |ON CONFLICT (rule_id) DO UPDATE SET
      |  active = EXCLUDED.active,
      |  retained_projectile_terminal_count = EXCLUDED.retained_projectile_terminal_count,
      |  retained_battle_event_count = EXCLUDED.retained_battle_event_count,
      |  replay_frame_sample_interval_ms = EXCLUDED.replay_frame_sample_interval_ms,
      |  retained_replay_frame_count = EXCLUDED.retained_replay_frame_count,
      |  updated_at = EXCLUDED.updated_at""".stripMargin

  private val upsertSessionPlayerSql: String =
    """INSERT INTO battle_session_player_rules (
      |  rule_id, active, initial_hp, max_hp, initial_stamina,
      |  max_stamina, default_weapon_kind, updated_at
      |) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
      |ON CONFLICT (rule_id) DO UPDATE SET
      |  active = EXCLUDED.active,
      |  initial_hp = EXCLUDED.initial_hp,
      |  max_hp = EXCLUDED.max_hp,
      |  initial_stamina = EXCLUDED.initial_stamina,
      |  max_stamina = EXCLUDED.max_stamina,
      |  default_weapon_kind = EXCLUDED.default_weapon_kind,
      |  updated_at = EXCLUDED.updated_at""".stripMargin

  def upsertRuntime(
    connection: Connection,
    ruleId: UUID,
    active: Boolean,
    config: BattleRuntimeRuleConfig,
    updatedAt: Instant
  ): IO[Unit] =
    IO.blocking {
      PostgresSupport.withStatement(connection, upsertRuntimeSql) { statement =>
        bindRuntime(statement, ruleId, active, config, updatedAt)
        statement.executeUpdate()
      }
      ()
    }

  def upsertHistory(
    connection: Connection,
    ruleId: UUID,
    active: Boolean,
    config: BattleHistoryRuleConfig,
    updatedAt: Instant
  ): IO[Unit] =
    IO.blocking {
      PostgresSupport.withStatement(connection, upsertHistorySql) { statement =>
        bindHistory(statement, ruleId, active, config, updatedAt)
        statement.executeUpdate()
      }
      ()
    }

  def upsertSessionPlayer(
    connection: Connection,
    ruleId: UUID,
    active: Boolean,
    config: BattleSessionPlayerRuleConfig,
    updatedAt: Instant
  ): IO[Unit] =
    IO.blocking {
      PostgresSupport.withStatement(connection, upsertSessionPlayerSql) { statement =>
        bindSessionPlayer(statement, ruleId, active, config, updatedAt)
        statement.executeUpdate()
      }
      ()
    }

  def load(connection: Connection): IO[BattleRuntimeRuleSet] =
    for {
      runtime <- loadActiveRuntime(connection)
      history <- loadActiveHistory(connection)
      sessionPlayer <- loadActiveSessionPlayer(connection)
    } yield BattleRuntimeRuleSet(
      runtime = runtime,
      history = history,
      sessionPlayer = sessionPlayer
    )

  private def loadActiveRuntime(connection: Connection): IO[BattleRuntimeRuleConfig] =
    IO.blocking {
      PostgresSupport.withStatement(
        connection,
        """SELECT default_battle_duration_ms, tick_step_ms
          |FROM battle_runtime_rules
          |WHERE active = TRUE
          |ORDER BY updated_at DESC
          |LIMIT 1""".stripMargin
      ) { statement =>
        PostgresSupport.withResultSet(statement) { resultSet =>
          if resultSet.next() then
            BattleRuntimeRuleConfig(
              defaultBattleDuration = DurationMillis(resultSet.getLong("default_battle_duration_ms")),
              tickStep = DurationMillis(resultSet.getLong("tick_step_ms"))
            )
          else throw IllegalStateException("Missing active battle_runtime_rules row.")
        }
      }
    }

  private def loadActiveHistory(connection: Connection): IO[BattleHistoryRuleConfig] =
    IO.blocking {
      PostgresSupport.withStatement(
        connection,
        """SELECT retained_projectile_terminal_count, retained_battle_event_count,
          |  replay_frame_sample_interval_ms, retained_replay_frame_count
          |FROM battle_history_rules
          |WHERE active = TRUE
          |ORDER BY updated_at DESC
          |LIMIT 1""".stripMargin
      ) { statement =>
        PostgresSupport.withResultSet(statement) { resultSet =>
          if resultSet.next() then
            BattleHistoryRuleConfig(
              retainedProjectileTerminalCount = BattleHistoryCount(resultSet.getInt("retained_projectile_terminal_count")),
              retainedBattleEventCount = BattleHistoryCount(resultSet.getInt("retained_battle_event_count")),
              replayFrameSampleInterval = DurationMillis(resultSet.getLong("replay_frame_sample_interval_ms")),
              retainedReplayFrameCount = BattleHistoryCount(resultSet.getInt("retained_replay_frame_count"))
            )
          else throw IllegalStateException("Missing active battle_history_rules row.")
        }
      }
    }

  private def loadActiveSessionPlayer(connection: Connection): IO[BattleSessionPlayerRuleConfig] =
    IO.blocking {
      PostgresSupport.withStatement(
        connection,
        """SELECT initial_hp, max_hp, initial_stamina, max_stamina, default_weapon_kind
          |FROM battle_session_player_rules
          |WHERE active = TRUE
          |ORDER BY updated_at DESC
          |LIMIT 1""".stripMargin
      ) { statement =>
        PostgresSupport.withResultSet(statement) { resultSet =>
          if resultSet.next() then readSessionPlayer(resultSet)
          else throw IllegalStateException("Missing active battle_session_player_rules row.")
        }
      }
    }

  private def readSessionPlayer(resultSet: ResultSet): BattleSessionPlayerRuleConfig =
    BattleSessionPlayerRuleConfig(
      initialHp = HitPoints(resultSet.getInt("initial_hp")),
      maxHp = HitPoints(resultSet.getInt("max_hp")),
      initialStamina = Stamina(resultSet.getDouble("initial_stamina")),
      maxStamina = Stamina(resultSet.getDouble("max_stamina")),
      defaultWeaponKind = required(WeaponKind.fromWire(resultSet.getString("default_weapon_kind")), "default_weapon_kind")
    )

  private def required[A](value: Option[A], column: String): A =
    value.getOrElse(throw IllegalStateException(s"Invalid battle runtime rule column: $column"))

  private def bindRuntime(
    statement: PreparedStatement,
    ruleId: UUID,
    active: Boolean,
    config: BattleRuntimeRuleConfig,
    updatedAt: Instant
  ): Unit = {
    statement.setObject(1, ruleId)
    statement.setBoolean(2, active)
    statement.setLong(3, config.defaultBattleDuration.value)
    statement.setLong(4, config.tickStep.value)
    statement.setTimestamp(5, Timestamp.from(updatedAt))
  }

  private def bindHistory(
    statement: PreparedStatement,
    ruleId: UUID,
    active: Boolean,
    config: BattleHistoryRuleConfig,
    updatedAt: Instant
  ): Unit = {
    statement.setObject(1, ruleId)
    statement.setBoolean(2, active)
    statement.setInt(3, config.retainedProjectileTerminalCount.value)
    statement.setInt(4, config.retainedBattleEventCount.value)
    statement.setLong(5, config.replayFrameSampleInterval.value)
    statement.setInt(6, config.retainedReplayFrameCount.value)
    statement.setTimestamp(7, Timestamp.from(updatedAt))
  }

  private def bindSessionPlayer(
    statement: PreparedStatement,
    ruleId: UUID,
    active: Boolean,
    config: BattleSessionPlayerRuleConfig,
    updatedAt: Instant
  ): Unit = {
    statement.setObject(1, ruleId)
    statement.setBoolean(2, active)
    statement.setInt(3, config.initialHp.value)
    statement.setInt(4, config.maxHp.value)
    statement.setDouble(5, config.initialStamina.value)
    statement.setDouble(6, config.maxStamina.value)
    statement.setString(7, WeaponKind.wireValue(config.defaultWeaponKind))
    statement.setTimestamp(8, Timestamp.from(updatedAt))
  }
}
