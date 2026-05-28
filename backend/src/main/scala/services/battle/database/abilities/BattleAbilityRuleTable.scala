package services.battle.database.abilities

import java.nio.charset.StandardCharsets
import java.sql.{Connection, PreparedStatement, ResultSet, Types}
import java.time.Instant
import java.util.UUID

import cats.effect.IO
import cats.syntax.all.*

import services.battle.objects.abilities.*
import services.battle.objects.SkillKind
import services.battle.objects.core.{CooldownMillis, DurationMillis, HitPoints, Radius}
import system.database.PostgresSupport

private[services] object BattleAbilityRuleTable {
  private val skillUpsertSql: String =
    """INSERT INTO battle_ability_skill_rules (
      |  rule_id, skill_kind, range, distance, radius, cast_range,
      |  cooldown_ms, active_ms, updated_at
      |) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
      |ON CONFLICT (skill_kind) DO UPDATE SET
      |  range = EXCLUDED.range,
      |  distance = EXCLUDED.distance,
      |  radius = EXCLUDED.radius,
      |  cast_range = EXCLUDED.cast_range,
      |  cooldown_ms = EXCLUDED.cooldown_ms,
      |  active_ms = EXCLUDED.active_ms,
      |  updated_at = EXCLUDED.updated_at""".stripMargin

  private val pickupUpsertSql: String =
    """INSERT INTO battle_ability_pickup_rules (
      |  rule_id, active, contact_radius, respawn_duration_ms, medkit_heal, updated_at
      |) VALUES (?, ?, ?, ?, ?, ?)
      |ON CONFLICT (rule_id) DO UPDATE SET
      |  active = EXCLUDED.active,
      |  contact_radius = EXCLUDED.contact_radius,
      |  respawn_duration_ms = EXCLUDED.respawn_duration_ms,
      |  medkit_heal = EXCLUDED.medkit_heal,
      |  updated_at = EXCLUDED.updated_at""".stripMargin

  def upsertAll(connection: Connection, rules: Vector[BattleSkillRuleDefinition]): IO[Unit] =
    rules.traverse_(rule => IO.blocking(upsert(connection, rule)))

  def upsertPickup(connection: Connection, config: BattlePickupRuleConfig): IO[Unit] =
    IO.blocking {
      PostgresSupport.withStatement(connection, pickupUpsertSql) { statement =>
        statement.setObject(1, namedRuleId("battle-ability-pickup-rules"))
        statement.setBoolean(2, true)
        statement.setDouble(3, config.contactRadius.value)
        statement.setLong(4, config.respawnDuration.value)
        statement.setInt(5, config.medkitHeal.value)
        statement.setObject(6, Instant.now())
        statement.executeUpdate()
      }
    }

  def loadRuleSet(connection: Connection): IO[BattleSkillRuleSet] =
    list(connection).map(buildRuleSet)

  def list(connection: Connection): IO[Vector[BattleSkillRuleDefinition]] =
    IO.blocking {
      PostgresSupport.withStatement(
        connection,
        """SELECT skill_kind, range, distance, radius, cast_range, cooldown_ms, active_ms
          |FROM battle_ability_skill_rules
          |ORDER BY skill_kind ASC""".stripMargin
      ) { statement =>
        PostgresSupport.withResultSet(statement) { resultSet =>
          val rules = Vector.newBuilder[BattleSkillRuleDefinition]
          while resultSet.next() do rules += readRule(resultSet)
          rules.result()
        }
      }
    }

  def loadActivePickup(connection: Connection): IO[BattlePickupRuleConfig] =
    IO.blocking {
      PostgresSupport.withStatement(
        connection,
        """SELECT contact_radius, respawn_duration_ms, medkit_heal
          |FROM battle_ability_pickup_rules
          |WHERE active = TRUE
          |ORDER BY updated_at DESC
          |LIMIT 1""".stripMargin
      ) { statement =>
        PostgresSupport.withResultSet(statement) { resultSet =>
          if resultSet.next() then
            BattlePickupRuleConfig(
              contactRadius = Radius(resultSet.getDouble("contact_radius")),
              respawnDuration = DurationMillis(resultSet.getLong("respawn_duration_ms")),
              medkitHeal = HitPoints(resultSet.getInt("medkit_heal"))
            )
          else throw IllegalStateException("Missing active battle_ability_pickup_rules row.")
        }
      }
    }

  private def upsert(connection: Connection, rule: BattleSkillRuleDefinition): Unit =
    PostgresSupport.withStatement(connection, skillUpsertSql) { statement =>
      bindRule(statement, rule)
      statement.executeUpdate()
    }

  private def bindRule(statement: PreparedStatement, rule: BattleSkillRuleDefinition): Unit = {
    statement.setObject(1, ruleId(rule.skillKind))
    statement.setString(2, SkillKind.wireValue(rule.skillKind))
    bindOptionalDouble(statement, 3, rule.range.map(_.value))
    bindOptionalDouble(statement, 4, rule.distance.map(_.value))
    bindOptionalDouble(statement, 5, rule.radius.map(_.value))
    bindOptionalDouble(statement, 6, rule.castRange.map(_.value))
    statement.setInt(7, rule.runtime.cooldownMs.value)
    statement.setLong(8, rule.runtime.activeMs.value)
    statement.setObject(9, Instant.now())
  }

  private def readRule(resultSet: ResultSet): BattleSkillRuleDefinition = {
    val skillKind = required(SkillKind.fromWire(resultSet.getString("skill_kind")), "skill_kind")
    BattleSkillRuleDefinition(
      skillKind = skillKind,
      range = nullableDouble(resultSet, "range").map(SkillDistance.apply),
      distance = nullableDouble(resultSet, "distance").map(SkillDistance.apply),
      radius = nullableDouble(resultSet, "radius").map(Radius.apply),
      castRange = nullableDouble(resultSet, "cast_range").map(SkillDistance.apply),
      runtime = BattleSkillRuntime(
        cooldownMs = CooldownMillis(resultSet.getInt("cooldown_ms")),
        activeMs = DurationMillis(resultSet.getLong("active_ms"))
      )
    )
  }

  private def buildRuleSet(rules: Vector[BattleSkillRuleDefinition]): BattleSkillRuleSet = {
    val byKind = rules.map(rule => rule.skillKind -> rule).toMap
    val blink = required(byKind.get(SkillKind.Blink), "Blink")
    val dash = required(byKind.get(SkillKind.Dash), "Dash")
    val freeze = required(byKind.get(SkillKind.Freeze), "Freeze")
    BattleSkillRuleSet(
      blink = BlinkConfig(required(blink.range, "Blink.range"), blink.runtime),
      dash = DashConfig(required(dash.distance, "Dash.distance"), dash.runtime),
      freeze = FreezeConfig(
        radius = required(freeze.radius, "Freeze.radius"),
        castRange = required(freeze.castRange, "Freeze.castRange"),
        runtime = freeze.runtime
      )
    )
  }

  private def bindOptionalDouble(statement: PreparedStatement, index: Int, value: Option[Double]): Unit =
    value match {
      case Some(number) => statement.setDouble(index, number)
      case None         => statement.setNull(index, Types.DOUBLE)
    }

  private def nullableDouble(resultSet: ResultSet, column: String): Option[Double] = {
    val value = resultSet.getDouble(column)
    Option.when(!resultSet.wasNull())(value)
  }

  private def required[A](value: Option[A], column: String): A =
    value.getOrElse(throw IllegalStateException(s"Missing battle ability rule column or row: $column"))

  private def ruleId(skillKind: SkillKind): UUID =
    UUID.nameUUIDFromBytes(s"battle-ability-rule:${SkillKind.wireValue(skillKind)}".getBytes(StandardCharsets.UTF_8))

  private def namedRuleId(name: String): UUID =
    UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8))
}
