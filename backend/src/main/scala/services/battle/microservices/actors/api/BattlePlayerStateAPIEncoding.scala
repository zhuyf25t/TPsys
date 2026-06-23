package services.battle.microservices.actors.api

import io.circe.{Encoder, JsonObject}
import services.battle.microservices.abilities.api.BattleSkillStateAPIEncoding.given
import services.battle.microservices.actors.objects.player.{BattlePlayerSkillState, BattlePlayerState}
import services.battle.microservices.combat.api.BattleWeaponStateAPIEncoding.given
import services.battle.microservices.combat.objects.weapon.{AmmoCount, BattleWeaponHeat, BattleWeaponState, WeaponKind}
import services.battle.microservices.world.api.BattleVectorAPIEncoding.given
import services.battle.objects.core.{BattleVector2, CooldownMillis}

object BattlePlayerStateAPIEncoding {
  given Encoder[BattlePlayerState] =
    Encoder[BattlePlayerStatePayload].contramap(player =>
      val currentWeapon = player.weapons.lift(player.currentWeaponIndex)
      BattlePlayerStatePayload(
        identity = BattlePlayerIdentityPayload(
          playerId = player.playerId.value,
          heroId = player.heroId.value,
          handle = player.handle.value,
          displayName = player.displayName.value,
          seat = player.seat.value,
          isBot = player.isBot
        ),
        motion = BattlePlayerMotionPayload(
          position = player.position,
          aim = player.aim,
          facing = player.facing.value,
          movement = player.movement
        ),
        controls = BattlePlayerControlsPayload(
          sprint = player.sprint,
          primaryHeld = player.primaryHeld,
          reloadPressed = player.reloadPressed,
          lastClientCommandSeq = player.lastClientCommandSeq.value
        ),
        weapon = BattlePlayerWeaponPayload(
          currentWeaponIndex = player.currentWeaponIndex,
          weapons = player.weapons,
          currentWeaponKind = WeaponKind.wireValue(player.currentWeaponKind),
          ammoInMagazine = currentWeapon.map(_.ammoInMagazine).getOrElse(AmmoCount(0)).value,
          magazineSize = currentWeapon.map(_.magazineSize).getOrElse(AmmoCount(0)).value,
          reserveAmmo = currentWeapon.flatMap(_.reserveAmmo).map(_.value),
          fireCooldownMs = currentWeapon.map(_.fireCooldownMs).getOrElse(CooldownMillis(0)).value,
          reloadRemainingMs = currentWeapon.map(_.reloadRemainingMs).getOrElse(CooldownMillis(0)).value,
          heat = currentWeapon.map(_.heat).getOrElse(BattleWeaponHeat(0)).value,
          overheated = currentWeapon.map(_.overheated).getOrElse(false),
          overheatRemainingMs = currentWeapon.map(_.overheatRemainingMs).getOrElse(CooldownMillis(0)).value
        ),
        vitals = BattlePlayerVitalsPayload(
          hp = player.hp.value,
          maxHp = player.maxHp.value,
          stamina = player.stamina.value,
          maxStamina = player.maxStamina.value
        ),
        progress = BattlePlayerProgressPayload(
          score = player.score.value,
          kills = player.kills.value,
          skills = player.skills,
          alive = player.alive,
          eliminatedAtMs = player.eliminatedAtMs.map(_.value),
          respawnMs = player.respawnMs.value
        )
      )
    )

  private final case class BattlePlayerStatePayload(
    identity: BattlePlayerIdentityPayload,
    motion: BattlePlayerMotionPayload,
    controls: BattlePlayerControlsPayload,
    weapon: BattlePlayerWeaponPayload,
    vitals: BattlePlayerVitalsPayload,
    progress: BattlePlayerProgressPayload
  )

  private given Encoder.AsObject[BattlePlayerStatePayload] =
    Encoder.AsObject.instance(payload =>
      JsonObject.fromIterable(
        Vector(
          Encoder.AsObject[BattlePlayerIdentityPayload].encodeObject(payload.identity),
          Encoder.AsObject[BattlePlayerMotionPayload].encodeObject(payload.motion),
          Encoder.AsObject[BattlePlayerControlsPayload].encodeObject(payload.controls),
          Encoder.AsObject[BattlePlayerWeaponPayload].encodeObject(payload.weapon),
          Encoder.AsObject[BattlePlayerVitalsPayload].encodeObject(payload.vitals),
          Encoder.AsObject[BattlePlayerProgressPayload].encodeObject(payload.progress)
        ).flatMap(_.toIterable)
      )
    )

  private final case class BattlePlayerIdentityPayload(
    playerId: String,
    heroId: String,
    handle: String,
    displayName: String,
    seat: Int,
    isBot: Boolean
  )

  private given Encoder.AsObject[BattlePlayerIdentityPayload] =
    Encoder.forProduct6("playerId", "heroId", "handle", "displayName", "seat", "isBot")(payload =>
      (payload.playerId, payload.heroId, payload.handle, payload.displayName, payload.seat, payload.isBot)
    )

  private final case class BattlePlayerMotionPayload(
    position: BattleVector2,
    aim: BattleVector2,
    facing: Double,
    movement: BattleVector2
  )

  private given Encoder.AsObject[BattlePlayerMotionPayload] =
    Encoder.forProduct4("position", "aim", "facing", "movement")(payload =>
      (payload.position, payload.aim, payload.facing, payload.movement)
    )

  private final case class BattlePlayerControlsPayload(
    sprint: Boolean,
    primaryHeld: Boolean,
    reloadPressed: Boolean,
    lastClientCommandSeq: Long
  )

  private given Encoder.AsObject[BattlePlayerControlsPayload] =
    Encoder.forProduct4("sprint", "primaryHeld", "reloadPressed", "lastClientCommandSeq")(payload =>
      (payload.sprint, payload.primaryHeld, payload.reloadPressed, payload.lastClientCommandSeq)
    )

  private final case class BattlePlayerWeaponPayload(
    currentWeaponIndex: Int,
    weapons: Vector[BattleWeaponState],
    currentWeaponKind: String,
    ammoInMagazine: Int,
    magazineSize: Int,
    reserveAmmo: Option[Int],
    fireCooldownMs: Int,
    reloadRemainingMs: Int,
    heat: Int,
    overheated: Boolean,
    overheatRemainingMs: Int
  )

  private given Encoder.AsObject[BattlePlayerWeaponPayload] =
    Encoder.forProduct11(
      "currentWeaponIndex",
      "weapons",
      "currentWeaponKind",
      "ammoInMagazine",
      "magazineSize",
      "reserveAmmo",
      "fireCooldownMs",
      "reloadRemainingMs",
      "heat",
      "overheated",
      "overheatRemainingMs"
    )(payload =>
      (
        payload.currentWeaponIndex,
        payload.weapons,
        payload.currentWeaponKind,
        payload.ammoInMagazine,
        payload.magazineSize,
        payload.reserveAmmo,
        payload.fireCooldownMs,
        payload.reloadRemainingMs,
        payload.heat,
        payload.overheated,
        payload.overheatRemainingMs
      )
    )

  private final case class BattlePlayerVitalsPayload(
    hp: Int,
    maxHp: Int,
    stamina: Double,
    maxStamina: Double
  )

  private given Encoder.AsObject[BattlePlayerVitalsPayload] =
    Encoder.forProduct4("hp", "maxHp", "stamina", "maxStamina")(payload =>
      (payload.hp, payload.maxHp, payload.stamina, payload.maxStamina)
    )

  private final case class BattlePlayerProgressPayload(
    score: Int,
    kills: Int,
    skills: Vector[BattlePlayerSkillState],
    alive: Boolean,
    eliminatedAtMs: Option[Long],
    respawnMs: Long
  )

  private given Encoder.AsObject[BattlePlayerProgressPayload] =
    Encoder.forProduct6("score", "kills", "skills", "alive", "eliminatedAtMs", "respawnMs")(payload =>
      (payload.score, payload.kills, payload.skills, payload.alive, payload.eliminatedAtMs, payload.respawnMs)
    )
}
