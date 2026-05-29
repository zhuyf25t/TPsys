package services.battle.microservices.combat.database

import java.nio.charset.StandardCharsets
import java.sql.{Connection, PreparedStatement, ResultSet, Timestamp, Types}
import java.time.Instant
import java.util.UUID

import cats.effect.IO
import cats.syntax.all.*

import services.battle.microservices.combat.objects.combat.*
import services.battle.microservices.combat.objects.projectile.ProjectileKind
import services.battle.microservices.combat.objects.weapon.{BattleWeaponHeat, BattleWeaponHeatRatePerSecond, WeaponKind}
import services.battle.objects.core.{
  CooldownMillis,
  DurationMillis,
  FacingRadians,
  Radius
}
import system.database.PostgresSupport

private[services] object BattleCombatRuleTable {
  private val upsertSql: String =
    """INSERT INTO battle_combat_weapon_rules (
      |  rule_id, weapon_kind, magazine_size, reserve_ammo, pickup_ammo, reload_ms,
      |  firing_resource, cooldown_ms, projectile_kind, projectile_speed,
      |  damage, projectile_radius, projectile_lifetime_ms, splash_radius,
      |  projectile_count, spread_radians, recoil_strength, max_heat,
      |  heat_per_shot, cool_rate_per_second, overheat_lock_ms, updated_at
      |) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      |ON CONFLICT (weapon_kind) DO UPDATE SET
      |  magazine_size = EXCLUDED.magazine_size,
      |  reserve_ammo = EXCLUDED.reserve_ammo,
      |  pickup_ammo = EXCLUDED.pickup_ammo,
      |  reload_ms = EXCLUDED.reload_ms,
      |  firing_resource = EXCLUDED.firing_resource,
      |  cooldown_ms = EXCLUDED.cooldown_ms,
      |  projectile_kind = EXCLUDED.projectile_kind,
      |  projectile_speed = EXCLUDED.projectile_speed,
      |  damage = EXCLUDED.damage,
      |  projectile_radius = EXCLUDED.projectile_radius,
      |  projectile_lifetime_ms = EXCLUDED.projectile_lifetime_ms,
      |  splash_radius = EXCLUDED.splash_radius,
      |  projectile_count = EXCLUDED.projectile_count,
      |  spread_radians = EXCLUDED.spread_radians,
      |  recoil_strength = EXCLUDED.recoil_strength,
      |  max_heat = EXCLUDED.max_heat,
      |  heat_per_shot = EXCLUDED.heat_per_shot,
      |  cool_rate_per_second = EXCLUDED.cool_rate_per_second,
      |  overheat_lock_ms = EXCLUDED.overheat_lock_ms,
      |  updated_at = EXCLUDED.updated_at""".stripMargin

  def upsertAll(connection: Connection, rules: Vector[BattleWeaponRuleDefinition]): IO[Unit] =
    rules.traverse_(rule => IO.blocking(upsert(connection, rule)))

  def list(connection: Connection): IO[Vector[BattleWeaponRuleDefinition]] =
    IO.blocking {
      PostgresSupport.withStatement(
        connection,
        """SELECT weapon_kind, magazine_size, reserve_ammo, pickup_ammo, reload_ms,
          |  firing_resource, cooldown_ms, projectile_kind, projectile_speed,
          |  damage, projectile_radius, projectile_lifetime_ms, splash_radius,
          |  projectile_count, spread_radians, recoil_strength, max_heat,
          |  heat_per_shot, cool_rate_per_second, overheat_lock_ms
          |FROM battle_combat_weapon_rules
          |ORDER BY weapon_kind ASC""".stripMargin
      ) { statement =>
        PostgresSupport.withResultSet(statement) { resultSet =>
          val rules = Vector.newBuilder[BattleWeaponRuleDefinition]
          while resultSet.next() do rules += readRule(resultSet)
          rules.result()
        }
      }
    }

  private def upsert(connection: Connection, rule: BattleWeaponRuleDefinition): Unit =
    PostgresSupport.withStatement(connection, upsertSql) { statement =>
      bindRule(statement, rule)
      statement.executeUpdate()
    }

  private def bindRule(statement: PreparedStatement, rule: BattleWeaponRuleDefinition): Unit = {
    val inventory = rule.inventory
    val fire = rule.fire
    val projectile = fire.projectile
    statement.setObject(1, ruleId(rule.weaponKind))
    statement.setString(2, WeaponKind.wireValue(rule.weaponKind))
    statement.setInt(3, inventory.magazineSize)
    inventory.reserveAmmo match {
      case Some(value) => statement.setInt(4, value)
      case None        => statement.setNull(4, Types.INTEGER)
    }
    statement.setInt(5, inventory.pickupAmmo)
    statement.setInt(6, inventory.reloadMs)
    statement.setString(7, BattleWeaponFiringResource.wireValue(inventory.firingResource))
    statement.setInt(8, fire.cooldownMs.value)
    statement.setString(9, ProjectileKind.wireValue(projectile.projectileKind))
    statement.setDouble(10, projectile.speed.value)
    statement.setInt(11, projectile.damage.value)
    statement.setDouble(12, projectile.radius.value)
    statement.setLong(13, projectile.lifetime.value)
    statement.setDouble(14, projectile.splashRadius.value)
    statement.setInt(15, projectile.projectileCount.value)
    statement.setDouble(16, projectile.spread.value)
    statement.setDouble(17, fire.recoilStrength.value)
    fire.heat match {
      case Some(heat) =>
        statement.setInt(18, heat.maxHeat.value)
        statement.setInt(19, heat.heatPerShot.value)
        statement.setInt(20, heat.coolRatePerSecond.value)
        statement.setInt(21, heat.overheatLockMs.value)
      case None =>
        statement.setNull(18, Types.INTEGER)
        statement.setNull(19, Types.INTEGER)
        statement.setNull(20, Types.INTEGER)
        statement.setNull(21, Types.INTEGER)
    }
    statement.setTimestamp(22, Timestamp.from(Instant.now()))
  }

  private def readRule(resultSet: ResultSet): BattleWeaponRuleDefinition = {
    val weaponKind = required(WeaponKind.fromWire(resultSet.getString("weapon_kind")), "weapon_kind")
    val projectileKind = required(ProjectileKind.fromWire(resultSet.getString("projectile_kind")), "projectile_kind")
    val firingResource =
      required(BattleWeaponFiringResource.fromWire(resultSet.getString("firing_resource")), "firing_resource")
    BattleWeaponRuleDefinition(
      inventory = BattleWeaponInventoryDefinition(
        weaponKind = weaponKind,
        magazineSize = resultSet.getInt("magazine_size"),
        reserveAmmo = nullableInt(resultSet, "reserve_ammo"),
        pickupAmmo = resultSet.getInt("pickup_ammo"),
        reloadMs = resultSet.getInt("reload_ms"),
        firingResource = firingResource
      ),
      fire = BattleWeaponFireDefinition(
        weaponKind = weaponKind,
        cooldownMs = CooldownMillis(resultSet.getInt("cooldown_ms")),
        projectile = BattleWeaponProjectileDefinition(
          projectileKind = projectileKind,
          speed = BattleWeaponProjectileSpeed(resultSet.getDouble("projectile_speed")),
          damage = Damage(resultSet.getInt("damage")),
          radius = Radius(resultSet.getDouble("projectile_radius")),
          lifetime = DurationMillis(resultSet.getLong("projectile_lifetime_ms")),
          splashRadius = Radius(resultSet.getDouble("splash_radius")),
          projectileCount = BattleWeaponProjectileCount(resultSet.getInt("projectile_count")),
          spread = FacingRadians(resultSet.getDouble("spread_radians"))
        ),
        recoilStrength = BattleWeaponRecoilStrength(resultSet.getDouble("recoil_strength")),
        heat = nullableInt(resultSet, "max_heat").map { maxHeat =>
          BattleWeaponHeatDefinition(
            maxHeat = BattleWeaponHeat(maxHeat),
            heatPerShot = BattleWeaponHeat(resultSet.getInt("heat_per_shot")),
            coolRatePerSecond = BattleWeaponHeatRatePerSecond(resultSet.getInt("cool_rate_per_second")),
            overheatLockMs = CooldownMillis(resultSet.getInt("overheat_lock_ms"))
          )
        }
      )
    )
  }

  private def nullableInt(resultSet: ResultSet, column: String): Option[Int] = {
    val value = resultSet.getInt(column)
    Option.when(!resultSet.wasNull())(value)
  }

  private def required[A](value: Option[A], column: String): A =
    value.getOrElse(throw IllegalStateException(s"Invalid battle combat rule column: $column"))

  private def ruleId(weaponKind: WeaponKind): UUID =
    UUID.nameUUIDFromBytes(s"battle-combat-rule:${WeaponKind.wireValue(weaponKind)}".getBytes(StandardCharsets.UTF_8))
}
