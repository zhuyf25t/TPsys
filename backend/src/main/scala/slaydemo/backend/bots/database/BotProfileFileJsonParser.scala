package slaydemo.backend.bots.database

import java.util.Locale

import slaydemo.backend.bots.objects.{
  BotAvatarKey,
  BotId,
  BotInitialRating,
  BotProfileOrder,
  BotProfileRecord,
  BotProfileTone,
  BotSkinLabel,
  BotSkinProfile,
  BotStrategyLabel,
  BotTextureKey
}
import slaydemo.backend.identity.objects.{DisplayName, PlayerHandle}

private[database] object BotProfileFileJsonParser {
  def parseProfiles(raw: String): Vector[BotProfileRecord] =
    BotProfileFileJsonObjectScanner.extractProfileObjects(raw).zipWithIndex.flatMap { case (chunk, index) =>
      parseRecord(chunk, index)
    }

  private def parseRecord(chunk: String, fallbackOrder: Int): Option[BotProfileRecord] =
    for {
      botId <- extractString(chunk, "botId")
      handle <- extractString(chunk, "handle")
      displayName <- extractString(chunk, "displayName")
      initialRating <- extractInt(chunk, "initialRating")
      profileTone <- extractString(chunk, "profileTone").map(parseTone)
      strategyLabel <- extractString(chunk, "strategyLabel")
      avatarKey <- extractString(chunk, "avatarKey")
      textureKey <- extractString(chunk, "textureKey")
      label <- extractString(chunk, "label")
    } yield BotProfileRecord(
      botId = BotId(botId),
      handle = PlayerHandle(handle),
      displayName = DisplayName(displayName),
      initialRating = BotInitialRating(initialRating),
      profileTone = profileTone,
      strategyLabel = BotStrategyLabel(strategyLabel),
      skin = BotSkinProfile(
        avatarKey = BotAvatarKey(avatarKey),
        textureKey = BotTextureKey(textureKey),
        label = BotSkinLabel(label)
      ),
      profileOrder = BotProfileOrder(extractInt(chunk, "profileOrder").getOrElse(fallbackOrder))
    )

  private def parseTone(value: String): BotProfileTone =
    Option(value).map(_.trim.toLowerCase(Locale.ROOT)).getOrElse("") match {
      case "scrappy"     => BotProfileTone.Scrappy
      case "aggressive"  => BotProfileTone.Aggressive
      case "patient"     => BotProfileTone.Patient
      case "opportunist" => BotProfileTone.Opportunist
      case _             => BotProfileTone.Steady
    }

  private def extractString(raw: String, field: String): Option[String] = {
    val pattern = s""""$field"\\s*:\\s*"((?:\\\\.|[^"\\\\])*)"""".r
    pattern.findFirstMatchIn(raw).map(matchResult => unescape(matchResult.group(1)))
  }

  private def extractInt(raw: String, field: String): Option[Int] = {
    val pattern = s""""$field"\\s*:\\s*(-?\\d+)""".r
    pattern.findFirstMatchIn(raw).map(_.group(1).toInt)
  }

  private def unescape(value: String): String =
    value
      .replace("\\\\", "\u0000")
      .replace("\\n", "\n")
      .replace("\\r", "\r")
      .replace("\\t", "\t")
      .replace("\\\"", "\"")
      .replace("\u0000", "\\")
}
