package slaydemo.backend.bots.objects.apiTypes

import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder

import slaydemo.backend.bots.objects.{BotProfileRecord, BotProfileTone}

object BotProfileRequestTarget {
  private val AllowedProfilePaths: Set[String] =
    Set("/bots/profiles", "/bot/profiles", "/api/bots/profiles", "/api/bot/profiles")

  def isProfilePath(path: String): Boolean =
    AllowedProfilePaths.contains(path)
}

enum BotProfileApiErrorCode {
  case MethodNotAllowed
}

object BotProfileApiErrorCode {
  def wireValue(code: BotProfileApiErrorCode): String =
    code match {
      case BotProfileApiErrorCode.MethodNotAllowed => "method_not_allowed"
    }

  def message(code: BotProfileApiErrorCode): String =
    code match {
      case BotProfileApiErrorCode.MethodNotAllowed => "Method is not allowed."
    }

  def statusCode(code: BotProfileApiErrorCode): Int =
    code match {
      case BotProfileApiErrorCode.MethodNotAllowed => 405
    }
}

final case class BotSkinProfileResponse(
  avatarKey: String,
  textureKey: String,
  label: String
)

object BotSkinProfileResponse {
  given Encoder[BotSkinProfileResponse] = deriveEncoder

  def fromRecord(record: BotProfileRecord): BotSkinProfileResponse =
    BotSkinProfileResponse(
      avatarKey = record.skin.avatarKey.value,
      textureKey = record.skin.textureKey.value,
      label = record.skin.label.value
    )
}

final case class BotProfileResponse(
  botId: String,
  handle: String,
  displayName: String,
  initialRating: Int,
  profileTone: String,
  strategyLabel: String,
  skin: BotSkinProfileResponse
)

object BotProfileResponse {
  given Encoder[BotProfileResponse] = deriveEncoder

  def fromRecord(record: BotProfileRecord): BotProfileResponse =
    BotProfileResponse(
      botId = record.botId.value,
      handle = record.handle.value,
      displayName = record.displayName.value,
      initialRating = record.initialRating.value,
      profileTone = BotProfileTone.wireValue(record.profileTone),
      strategyLabel = record.strategyLabel.value,
      skin = BotSkinProfileResponse.fromRecord(record)
    )
}

final case class BotProfilesResponse(profiles: Vector[BotProfileResponse])

object BotProfilesResponse {
  given Encoder[BotProfilesResponse] = deriveEncoder

  def fromRecords(records: Vector[BotProfileRecord]): BotProfilesResponse =
    BotProfilesResponse(records.map(BotProfileResponse.fromRecord))
}
