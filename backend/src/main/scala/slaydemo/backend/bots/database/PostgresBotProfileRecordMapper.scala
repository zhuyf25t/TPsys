package slaydemo.backend.bots.database

import java.sql.{PreparedStatement, ResultSet}

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

private[database] object PostgresBotProfileRecordMapper {
  def bindRecord(statement: PreparedStatement, record: BotProfileRecord): Unit = {
    statement.setString(1, record.botId.value)
    statement.setString(2, record.handle.value)
    statement.setString(3, record.displayName.value)
    statement.setInt(4, record.initialRating.value)
    statement.setString(5, BotProfileTone.wireValue(record.profileTone))
    statement.setString(6, record.strategyLabel.value)
    statement.setString(7, record.skin.avatarKey.value)
    statement.setString(8, record.skin.textureKey.value)
    statement.setString(9, record.skin.label.value)
    statement.setInt(10, record.profileOrder.value)
  }

  def readRecords(resultSet: ResultSet): Vector[BotProfileRecord] = {
    val records = Vector.newBuilder[BotProfileRecord]
    while (resultSet.next()) {
      records += readRecord(resultSet)
    }
    records.result()
  }

  private def readRecord(resultSet: ResultSet): BotProfileRecord =
    BotProfileRecord(
      botId = BotId(resultSet.getString("bot_id")),
      handle = PlayerHandle(resultSet.getString("handle")),
      displayName = DisplayName(resultSet.getString("display_name")),
      initialRating = BotInitialRating(resultSet.getInt("initial_rating")),
      profileTone = readTone(resultSet.getString("profile_tone")),
      strategyLabel = BotStrategyLabel(resultSet.getString("strategy_label")),
      skin = BotSkinProfile(
        avatarKey = BotAvatarKey(resultSet.getString("avatar_key")),
        textureKey = BotTextureKey(resultSet.getString("texture_key")),
        label = BotSkinLabel(resultSet.getString("skin_label"))
      ),
      profileOrder = BotProfileOrder(resultSet.getInt("profile_order"))
    )

  private def readTone(value: String): BotProfileTone =
    BotProfileTone.fromWireValue(value).getOrElse(BotProfileTone.Steady)
}
