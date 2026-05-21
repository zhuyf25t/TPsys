package slaydemo.backend.bots.objects.apiTypes

import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.syntax.*

import slaydemo.backend.bots.objects.{BotProfileRecord, BotProfileTone}

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

  def renderRecords(records: Vector[BotProfileRecord]): String =
    fromRecords(records).asJson.noSpaces
}
