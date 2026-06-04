package services.battle.microservices.actors.database

import java.nio.charset.StandardCharsets
import java.sql.{Connection, PreparedStatement, ResultSet, Timestamp}
import java.time.Instant
import java.util.UUID

import cats.effect.IO
import cats.syntax.all.*

import services.battle.microservices.actors.objects.actors.*
import services.battle.objects.core.{DurationMillis, Radius}
import system.database.PostgresSupport

private[services] object BattleActorRuleTable {
  private val DefaultBotRules: BattleBotRuleConfig =
    BattleBotRuleConfig(
      moveSpeed = BattleBotMoveSpeed(180.0),
      preferredRange = Radius(260.0),
      preferredRangeAdvanceMargin = Radius(80.0),
      preferredRangeRetreatMargin = Radius(90.0),
      botFireRange = Radius(520.0),
      humanFireRange = Radius(360.0),
      openingFireDelay = DurationMillis(5_000L),
      firePulseInterval = DurationMillis(1_000L),
      firePulseWindow = DurationMillis(1_000L),
      movementProbeDistance = Radius(96.0),
      coverProbeDistance = Radius(220.0),
      pickupSeekRange = Radius(380.0),
      aimLeadDistance = Radius(0.16),
      aimErrorRadius = Radius(0.02),
      lowHealthRatio = 0.38,
      pickupHealthRatio = 0.52,
      tacticalReloadRatio = 0.28
    )

  private val upsertSql: String =
    """INSERT INTO battle_actor_bot_rules (
      |  rule_id, active, move_speed, preferred_range, preferred_range_advance_margin,
      |  preferred_range_retreat_margin, bot_fire_range, human_fire_range,
      |  opening_fire_delay_ms, fire_pulse_interval_ms, fire_pulse_window_ms,
      |  movement_probe_distance, cover_probe_distance, pickup_seek_range,
      |  aim_lead_distance, aim_error_radius, low_health_ratio,
      |  pickup_health_ratio, tactical_reload_ratio, updated_at
      |) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      |ON CONFLICT (rule_id) DO UPDATE SET
      |  active = EXCLUDED.active,
      |  move_speed = EXCLUDED.move_speed,
      |  preferred_range = EXCLUDED.preferred_range,
      |  preferred_range_advance_margin = EXCLUDED.preferred_range_advance_margin,
      |  preferred_range_retreat_margin = EXCLUDED.preferred_range_retreat_margin,
      |  bot_fire_range = EXCLUDED.bot_fire_range,
      |  human_fire_range = EXCLUDED.human_fire_range,
      |  opening_fire_delay_ms = EXCLUDED.opening_fire_delay_ms,
      |  fire_pulse_interval_ms = EXCLUDED.fire_pulse_interval_ms,
      |  fire_pulse_window_ms = EXCLUDED.fire_pulse_window_ms,
      |  movement_probe_distance = EXCLUDED.movement_probe_distance,
      |  cover_probe_distance = EXCLUDED.cover_probe_distance,
      |  pickup_seek_range = EXCLUDED.pickup_seek_range,
      |  aim_lead_distance = EXCLUDED.aim_lead_distance,
      |  aim_error_radius = EXCLUDED.aim_error_radius,
      |  low_health_ratio = EXCLUDED.low_health_ratio,
      |  pickup_health_ratio = EXCLUDED.pickup_health_ratio,
      |  tactical_reload_ratio = EXCLUDED.tactical_reload_ratio,
      |  updated_at = EXCLUDED.updated_at""".stripMargin

  def upsert(connection: Connection, config: BattleBotRuleConfig): IO[Unit] =
    IO.blocking {
      PostgresSupport.withStatement(connection, upsertSql) { statement =>
        bindConfig(statement, config)
        statement.executeUpdate()
      }
    }

  def upsertDefaultBotRules(connection: Connection): IO[Unit] =
    upsert(connection, DefaultBotRules)

  def loadActive(connection: Connection): IO[BattleBotRuleConfig] =
    IO.blocking {
      PostgresSupport.withStatement(
        connection,
        """SELECT move_speed, preferred_range, preferred_range_advance_margin,
          |  preferred_range_retreat_margin, bot_fire_range, human_fire_range,
          |  opening_fire_delay_ms, fire_pulse_interval_ms, fire_pulse_window_ms,
          |  movement_probe_distance, cover_probe_distance, pickup_seek_range,
          |  aim_lead_distance, aim_error_radius, low_health_ratio,
          |  pickup_health_ratio, tactical_reload_ratio
          |FROM battle_actor_bot_rules
          |WHERE active = TRUE
          |ORDER BY updated_at DESC
          |LIMIT 1""".stripMargin
      ) { statement =>
        PostgresSupport.withResultSet(statement) { resultSet =>
          if resultSet.next() then readConfig(resultSet)
          else throw IllegalStateException("Missing active battle_actor_bot_rules row.")
        }
      }
    }

  private def bindConfig(statement: PreparedStatement, config: BattleBotRuleConfig): Unit = {
    statement.setObject(1, ruleId)
    statement.setBoolean(2, true)
    statement.setDouble(3, config.moveSpeed.value)
    statement.setDouble(4, config.preferredRange.value)
    statement.setDouble(5, config.preferredRangeAdvanceMargin.value)
    statement.setDouble(6, config.preferredRangeRetreatMargin.value)
    statement.setDouble(7, config.botFireRange.value)
    statement.setDouble(8, config.humanFireRange.value)
    statement.setLong(9, config.openingFireDelay.value)
    statement.setLong(10, config.firePulseInterval.value)
    statement.setLong(11, config.firePulseWindow.value)
    statement.setDouble(12, config.movementProbeDistance.value)
    statement.setDouble(13, config.coverProbeDistance.value)
    statement.setDouble(14, config.pickupSeekRange.value)
    statement.setDouble(15, config.aimLeadDistance.value)
    statement.setDouble(16, config.aimErrorRadius.value)
    statement.setDouble(17, config.lowHealthRatio)
    statement.setDouble(18, config.pickupHealthRatio)
    statement.setDouble(19, config.tacticalReloadRatio)
    statement.setTimestamp(20, Timestamp.from(Instant.now()))
  }

  private def readConfig(resultSet: ResultSet): BattleBotRuleConfig =
    BattleBotRuleConfig(
      moveSpeed = BattleBotMoveSpeed(resultSet.getDouble("move_speed")),
      preferredRange = Radius(resultSet.getDouble("preferred_range")),
      preferredRangeAdvanceMargin = Radius(resultSet.getDouble("preferred_range_advance_margin")),
      preferredRangeRetreatMargin = Radius(resultSet.getDouble("preferred_range_retreat_margin")),
      botFireRange = Radius(resultSet.getDouble("bot_fire_range")),
      humanFireRange = Radius(resultSet.getDouble("human_fire_range")),
      openingFireDelay = DurationMillis(resultSet.getLong("opening_fire_delay_ms")),
      firePulseInterval = DurationMillis(resultSet.getLong("fire_pulse_interval_ms")),
      firePulseWindow = DurationMillis(resultSet.getLong("fire_pulse_window_ms")),
      movementProbeDistance = Radius(resultSet.getDouble("movement_probe_distance")),
      coverProbeDistance = Radius(resultSet.getDouble("cover_probe_distance")),
      pickupSeekRange = Radius(resultSet.getDouble("pickup_seek_range")),
      aimLeadDistance = Radius(resultSet.getDouble("aim_lead_distance")),
      aimErrorRadius = Radius(resultSet.getDouble("aim_error_radius")),
      lowHealthRatio = resultSet.getDouble("low_health_ratio"),
      pickupHealthRatio = resultSet.getDouble("pickup_health_ratio"),
      tacticalReloadRatio = resultSet.getDouble("tactical_reload_ratio")
    )

  private def ruleId: UUID =
    UUID.nameUUIDFromBytes("battle-actor-bot-rules".getBytes(StandardCharsets.UTF_8))
}
