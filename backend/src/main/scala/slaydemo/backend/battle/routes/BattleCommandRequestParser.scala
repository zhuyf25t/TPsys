package slaydemo.backend.battle.routes

import slaydemo.backend.battle.api.{BattleCommandRequest, BattleCommandVector}
import slaydemo.backend.battle.objects.*

private[routes] enum BattleCommandRequestParseError {
  case BadRequest(message: String)
  case MissingTicket
}

private[routes] object BattleCommandRequestParser {
  def parse(fields: Map[String, BattleJsonValue]): Either[BattleCommandRequestParseError, BattleCommandRequest] =
    for {
      battleId <- readRequiredCommandString(fields, "battleId", "missing_battle_id").map(BattleId.apply).left.map(BattleCommandRequestParseError.BadRequest.apply)
      playerId <- readRequiredCommandString(fields, "playerId", "missing_player_id").map(PlayerId.apply).left.map(BattleCommandRequestParseError.BadRequest.apply)
      ticketId <- readCommandString(fields, "ticketId").flatMap(nonEmptyText).map(TicketId.apply).toRight(BattleCommandRequestParseError.MissingTicket)
      clientTick <- readRequiredCommandLong(fields, "clientTick", "missing_client_tick").map(BattleTick.apply).left.map(BattleCommandRequestParseError.BadRequest.apply)
      clientCommandSeq = readCommandLong(fields, "clientCommandSeq").map(ClientCommandSeq.apply).getOrElse(ClientCommandSeq(clientTick.value))
      movement <- readCommandVector(fields, "movement").toRight(BattleCommandRequestParseError.BadRequest("missing_movement"))
      aim <- readCommandVector(fields, "aim").toRight(BattleCommandRequestParseError.BadRequest("missing_aim"))
      primaryHeld <- readCommandBoolean(fields, "primaryHeld").toRight(BattleCommandRequestParseError.BadRequest("missing_primary_held"))
      reloadPressed <- readCommandBoolean(fields, "reloadPressed").toRight(BattleCommandRequestParseError.BadRequest("missing_reload_pressed"))
      switchWeaponDirection <- readCommandInt(fields, "switchWeaponDirection")
        .map(BattleWeaponSwitchDirection.fromWire)
        .toRight(BattleCommandRequestParseError.BadRequest("missing_switch_weapon_direction"))
    } yield BattleCommandRequest(
      battleId = battleId,
      playerId = playerId,
      ticketId = ticketId,
      clientTick = clientTick,
      clientCommandSeq = clientCommandSeq,
      movement = movement,
      aim = aim,
      primaryHeld = primaryHeld,
      sprint = readCommandBoolean(fields, "sprint").getOrElse(false),
      reloadPressed = reloadPressed,
      skillIntents = BattleCommandSkillIntents.fromLegacyFlags(
        castDash = readCommandBoolean(fields, "castDash").getOrElse(false),
        castBlink = readCommandBoolean(fields, "castBlink").getOrElse(false),
        castFreeze = readCommandBoolean(fields, "castFreeze").getOrElse(false)
      ),
      pointerWorld = readCommandVector(fields, "pointerWorld"),
      switchWeaponDirection = switchWeaponDirection,
      switchWeaponIndex = readCommandInt(fields, "switchWeaponIndex").flatMap(BattleWeaponSwitchIndex.fromWire)
    )

  private def readCommandString(fields: Map[String, BattleJsonValue], key: String): Option[String] =
    fields.get(key) match {
      case Some(BattleJsonValue.StringValue(value)) => Some(value)
      case _                                        => None
    }

  private def readRequiredCommandString(
    fields: Map[String, BattleJsonValue],
    key: String,
    error: String
  ): Either[String, String] =
    readCommandString(fields, key).flatMap(nonEmptyText).toRight(error)

  private def readCommandLong(fields: Map[String, BattleJsonValue], key: String): Option[Long] =
    fields.get(key) match {
      case Some(BattleJsonValue.NumberValue(value)) if isValidLong(value) => Some(value.toLong)
      case _ => None
    }

  private def readRequiredCommandLong(
    fields: Map[String, BattleJsonValue],
    key: String,
    error: String
  ): Either[String, Long] =
    readCommandLong(fields, key).toRight(error)

  private def readCommandInt(fields: Map[String, BattleJsonValue], key: String): Option[Int] =
    fields.get(key) match {
      case Some(BattleJsonValue.NumberValue(value)) if isValidInt(value) => Some(value.toInt)
      case _ => None
    }

  private def readCommandDouble(fields: Map[String, BattleJsonValue], key: String): Option[Double] =
    fields.get(key) match {
      case Some(BattleJsonValue.NumberValue(value)) if value.isFinite => Some(value)
      case _ => None
    }

  private def readCommandBoolean(fields: Map[String, BattleJsonValue], key: String): Option[Boolean] =
    fields.get(key) match {
      case Some(BattleJsonValue.BooleanValue(value)) => Some(value)
      case _                                         => None
    }

  private def readCommandVector(fields: Map[String, BattleJsonValue], key: String): Option[BattleCommandVector] =
    fields.get(key) match {
      case Some(BattleJsonValue.ObjectValue(vectorFields)) =>
        for {
          x <- readCommandDouble(vectorFields, "x")
          y <- readCommandDouble(vectorFields, "y")
        } yield BattleCommandVector(x, y)
      case _ => None
    }

  private def isValidInt(value: Double): Boolean =
    value.isWhole && value >= Int.MinValue.toDouble && value <= Int.MaxValue.toDouble

  private def isValidLong(value: Double): Boolean =
    value.isWhole && value >= Long.MinValue.toDouble && value <= Long.MaxValue.toDouble

  private def nonEmptyText(value: String): Option[String] =
    Option(value).map(_.trim).filter(_.nonEmpty)
}
